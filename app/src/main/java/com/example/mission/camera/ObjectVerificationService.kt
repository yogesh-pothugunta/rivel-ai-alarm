package com.example.mission.camera

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.ai.BackendConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

enum class ObjectVerificationStatus {
    VERIFIED,
    WRONG_OBJECT,
    LOW_IMAGE_QUALITY,
    NETWORK_ERROR,
    API_ERROR,
    NO_API_CONFIGURATION
}

data class ObjectVerificationResult(
    val status: ObjectVerificationStatus,
    val isDetected: Boolean,
    val confidence: Float,
    val explanation: String,
    val timestamp: Long = System.currentTimeMillis()
)

interface ObjectVerificationService {
    suspend fun verifyObjectInImage(bitmap: Bitmap, expectedObject: String): ObjectVerificationResult
}

/**
 * Backend-Proxied Object Verification Service
 * 
 * Routes verification images to the secure RIVEL backend proxy over HTTPS.
 * The Gemini API key and prompt evaluation remain strictly on the backend.
 */
class BackendObjectVerificationService : ObjectVerificationService {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun verifyObjectInImage(
        bitmap: Bitmap,
        expectedObject: String
    ): ObjectVerificationResult = withContext(Dispatchers.IO) {
        // 1. Basic image validation: check dimensions and luminance before network dispatch
        val isUsablePhoto = inspectBitmapQuality(bitmap)
        if (!isUsablePhoto) {
            Log.w("ObjectVerification", "Captured image failed quality inspection (too dark or corrupt)")
            return@withContext ObjectVerificationResult(
                status = ObjectVerificationStatus.LOW_IMAGE_QUALITY,
                isDetected = false,
                confidence = 0.0f,
                explanation = "The captured photo is too dark or blurry. Please turn on lights, point directly at your $expectedObject, and hold the device steady."
            )
        }

        // 2. Dispatch image to secure RIVEL backend proxy
        try {
            val base64Image = bitmapToBase64(bitmap)

            val jsonPayload = JSONObject().apply {
                put("targetObject", expectedObject)
                put("imageBase64", base64Image)
            }

            val requestBody = jsonPayload.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(BackendConfig.visionVerifyEndpoint)
                .post(requestBody)
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (responseBody.isNotBlank()) {
                val rootJson = JSONObject(responseBody)
                val statusStr = rootJson.optString("status", "")
                val detected = rootJson.optBoolean("detected", false)
                val conf = rootJson.optDouble("confidence", 0.0).toFloat()
                val reason = rootJson.optString("reason", rootJson.optString("error", ""))

                val parsedStatus = when (statusStr) {
                    "VERIFIED" -> ObjectVerificationStatus.VERIFIED
                    "WRONG_OBJECT" -> ObjectVerificationStatus.WRONG_OBJECT
                    "LOW_IMAGE_QUALITY" -> ObjectVerificationStatus.LOW_IMAGE_QUALITY
                    "NO_API_CONFIGURATION" -> ObjectVerificationStatus.NO_API_CONFIGURATION
                    "NETWORK_ERROR" -> ObjectVerificationStatus.NETWORK_ERROR
                    else -> ObjectVerificationStatus.API_ERROR
                }

                if (response.isSuccessful && parsedStatus == ObjectVerificationStatus.VERIFIED && detected) {
                    Log.i("ObjectVerification", "Object '$expectedObject' successfully verified via backend proxy (conf: $conf)")
                    return@withContext ObjectVerificationResult(
                        status = ObjectVerificationStatus.VERIFIED,
                        isDetected = true,
                        confidence = conf,
                        explanation = reason.ifBlank { "$expectedObject confirmed! Wake mission accomplished." }
                    )
                } else {
                    Log.w("ObjectVerification", "Backend rejected object verification: status=$parsedStatus, detected=$detected, reason=$reason")
                    return@withContext ObjectVerificationResult(
                        status = if (response.code == 503 || parsedStatus == ObjectVerificationStatus.NO_API_CONFIGURATION) {
                            ObjectVerificationStatus.NO_API_CONFIGURATION
                        } else if (parsedStatus == ObjectVerificationStatus.WRONG_OBJECT) {
                            ObjectVerificationStatus.WRONG_OBJECT
                        } else if (parsedStatus == ObjectVerificationStatus.LOW_IMAGE_QUALITY) {
                            ObjectVerificationStatus.LOW_IMAGE_QUALITY
                        } else {
                            ObjectVerificationStatus.API_ERROR
                        },
                        isDetected = false,
                        confidence = conf,
                        explanation = reason.ifBlank {
                            if (parsedStatus == ObjectVerificationStatus.WRONG_OBJECT) {
                                "Could not detect '$expectedObject' in this photo. Please clearly frame the requested item."
                            } else {
                                "Verification failed. Please try again."
                            }
                        }
                    )
                }
            } else {
                Log.e("ObjectVerification", "Empty response from verification backend. HTTP code: ${response.code}")
                return@withContext ObjectVerificationResult(
                    status = ObjectVerificationStatus.API_ERROR,
                    isDetected = false,
                    confidence = 0.0f,
                    explanation = "AI verification server returned an empty response. Please try taking another photo."
                )
            }
        } catch (e: IOException) {
            Log.e("ObjectVerification", "Network error during backend vision verification", e)
            return@withContext ObjectVerificationResult(
                status = ObjectVerificationStatus.NETWORK_ERROR,
                isDetected = false,
                confidence = 0.0f,
                explanation = "Network connection failed. An active internet connection is required for AI Object Verification."
            )
        } catch (e: Exception) {
            Log.e("ObjectVerification", "Unexpected error during object verification", e)
            return@withContext ObjectVerificationResult(
                status = ObjectVerificationStatus.API_ERROR,
                isDetected = false,
                confidence = 0.0f,
                explanation = "Verification error: ${e.localizedMessage ?: "Please try again."}"
            )
        }
    }

    private fun inspectBitmapQuality(bitmap: Bitmap): Boolean {
        if (bitmap.width < 10 || bitmap.height < 10) return false
        var totalLuma = 0L
        val stepX = bitmap.width / 5
        val stepY = bitmap.height / 5
        for (x in 1..4) {
            for (y in 1..4) {
                val pixel = bitmap.getPixel(x * stepX, y * stepY)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val luma = (r + g + b) / 3
                totalLuma += luma
            }
        }
        val avgLuma = totalLuma / 16
        return avgLuma > 10 // Reject pitch-black or covered cameras
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val scaled = if (bitmap.width > 800 || bitmap.height > 800) {
            val scale = 800f / maxOf(bitmap.width, bitmap.height)
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        } else {
            bitmap
        }
        val stream = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 80, stream)
        val bytes = stream.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}

/**
 * Backward compatibility alias for GeminiObjectVerificationService
 */
typealias GeminiObjectVerificationService = BackendObjectVerificationService
