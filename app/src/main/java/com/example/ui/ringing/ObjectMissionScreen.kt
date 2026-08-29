package com.example.ui.ringing

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.mission.camera.BackendObjectVerificationService
import com.example.mission.camera.ObjectVerificationResult
import com.example.mission.camera.ObjectVerificationStatus
import com.example.mission.camera.WakeObjectPool
import com.example.ui.theme.RivelAmber
import com.example.ui.theme.RivelEmerald
import com.example.ui.theme.RivelRed
import kotlinx.coroutines.launch
import java.nio.ByteBuffer

@Composable
fun ObjectMissionScreen(
    targetObject: String,
    onMissionSuccess: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val verificationService = remember { BackendObjectVerificationService() }
    val wakeObject = remember(targetObject) { WakeObjectPool.findObjectByName(targetObject) }

    var isVerifying by remember { mutableStateOf(false) }
    var latestResult by remember { mutableStateOf<ObjectVerificationResult?>(null) }
    var captureErrorMessage by remember { mutableStateOf<String?>(null) }

    val imageCapture = remember { ImageCapture.Builder().build() }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("object_mission_screen"),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Header Banner
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(RivelAmber.copy(alpha = 0.15f))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(text = wakeObject.emoji, fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "PROOF-OF-WAKE SCAN",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = RivelAmber
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "FIND AND SCAN: ${targetObject.uppercase()}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Frame the $targetObject clearly in view and tap Capture to verify.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            // 2. Camera Viewfinder Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            try {
                                val cameraProvider = cameraProviderFuture.get()
                                val preview = Preview.Builder().build().also {
                                    it.setSurfaceProvider(previewView.surfaceProvider)
                                }
                                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    cameraSelector,
                                    preview,
                                    imageCapture
                                )
                            } catch (e: Exception) {
                                Log.e("ObjectMissionScreen", "Camera bind failure", e)
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Loading / Verifying Overlay
                if (isVerifying) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.75f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                color = RivelAmber,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Evaluating photo for $targetObject...",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "AI Vision Proof-of-Wake Analysis",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            // 3. Status card showing precise verification state
            if (captureErrorMessage != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = RivelRed.copy(alpha = 0.15f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = RivelRed,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = captureErrorMessage ?: "Camera capture error",
                            style = MaterialTheme.typography.bodySmall,
                            color = RivelRed,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            } else if (latestResult != null) {
                val res = latestResult!!
                val (cardColor, iconTint, iconVector) = when (res.status) {
                    ObjectVerificationStatus.VERIFIED -> Triple(RivelEmerald.copy(alpha = 0.15f), RivelEmerald, Icons.Default.CheckCircle)
                    ObjectVerificationStatus.WRONG_OBJECT,
                    ObjectVerificationStatus.LOW_IMAGE_QUALITY -> Triple(RivelAmber.copy(alpha = 0.15f), RivelAmber, Icons.Default.Warning)
                    ObjectVerificationStatus.NETWORK_ERROR -> Triple(RivelRed.copy(alpha = 0.15f), RivelRed, Icons.Default.WifiOff)
                    ObjectVerificationStatus.API_ERROR,
                    ObjectVerificationStatus.NO_API_CONFIGURATION -> Triple(RivelRed.copy(alpha = 0.15f), RivelRed, Icons.Default.ErrorOutline)
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = cardColor)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = iconVector,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = res.explanation,
                                style = MaterialTheme.typography.bodySmall,
                                color = iconTint,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (res.confidence > 0.0f) {
                                Text(
                                    text = "Confidence: ${(res.confidence * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = iconTint.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }

            // 4. Capture & Verify Button (No bypass / snooze / skip)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Button(
                    onClick = {
                        if (isVerifying) return@Button
                        isVerifying = true
                        latestResult = null
                        captureErrorMessage = null

                        imageCapture.takePicture(
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageCapturedCallback() {
                                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                                    val bitmap = imageProxyToBitmap(imageProxy)
                                    imageProxy.close()

                                    if (bitmap == null) {
                                        isVerifying = false
                                        captureErrorMessage = "Failed to decode camera frame. Please try again."
                                        return
                                    }

                                    scope.launch {
                                        val result = verificationService.verifyObjectInImage(bitmap, targetObject)
                                        isVerifying = false
                                        latestResult = result

                                        if (result.status == ObjectVerificationStatus.VERIFIED && result.isDetected) {
                                            onMissionSuccess()
                                        }
                                    }
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    Log.e("ObjectMissionScreen", "Capture error", exception)
                                    isVerifying = false
                                    captureErrorMessage = "Camera capture failed: ${exception.message}. Please try again."
                                }
                            }
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .testTag("capture_photo_button"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RivelAmber),
                    enabled = !isVerifying
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isVerifying) "Verifying with AI..." else "Capture & Verify",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
    return try {
        val planeProxy = imageProxy.planes[0]
        val buffer: ByteBuffer = planeProxy.buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (e: Exception) {
        Log.e("ObjectMissionScreen", "Failed to decode ImageProxy into Bitmap", e)
        null
    }
}
