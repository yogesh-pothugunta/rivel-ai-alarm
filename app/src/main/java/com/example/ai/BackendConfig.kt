package com.example.ai

import android.content.Context
import android.content.SharedPreferences

/**
 * Configuration for RIVEL Secure Backend AI Proxy
 * 
 * Direct Gemini API calls and API keys are strictly removed from the Android application.
 * All AI Chat and Vision Object Verification requests route through this secure backend proxy.
 */
object BackendConfig {

    private const val PREFS_NAME = "rivel_backend_prefs"
    private const val KEY_BACKEND_URL = "custom_backend_url"

    /**
     * Placeholder configuration for production deployment.
     */
    const val PRODUCTION_BASE_URL_PLACEHOLDER = "https://YOUR-BACKEND-SERVICE-URL"

    /**
     * Standard local loopback IP for Android Emulator communicating with host machine localhost.
     */
    const val LOCAL_EMULATOR_BASE_URL = "http://10.0.2.2:8080"

    /**
     * Active Base URL for the RIVEL AI Backend Proxy.
     */
    var baseUrl: String = PRODUCTION_BASE_URL_PLACEHOLDER
        private set

    /**
     * Initializes BackendConfig from SharedPreferences.
     */
    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedUrl = prefs.getString(KEY_BACKEND_URL, null)
        if (!savedUrl.isNullOrBlank()) {
            baseUrl = savedUrl.trim()
        }
    }

    /**
     * Returns true only when configured with a real non-placeholder production or local URL.
     */
    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() &&
                baseUrl != PRODUCTION_BASE_URL_PLACEHOLDER &&
                !baseUrl.contains("YOUR-BACKEND-SERVICE-URL") &&
                !baseUrl.contains("YOUR-CLOUD-RUN-SERVICE-URL")

    // Dynamic Endpoint Getters
    val chatEndpoint: String
        get() = "${baseUrl.trimEnd('/')}/api/ai/chat"

    val visionVerifyEndpoint: String
        get() = "${baseUrl.trimEnd('/')}/api/vision/verify-object"

    val healthEndpoint: String
        get() = "${baseUrl.trimEnd('/')}/api/health"

    /**
     * Sets and persists a custom backend URL at runtime.
     */
    fun setCustomBaseUrl(context: Context?, url: String) {
        val trimmed = url.trim()
        if (trimmed.isNotBlank()) {
            baseUrl = trimmed
            context?.let {
                val prefs = it.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().putString(KEY_BACKEND_URL, trimmed).apply()
            }
        }
    }

    /**
     * Switches backend URL to local development emulator address (e.g. http://10.0.2.2:8080)
     */
    fun useLocalDevelopmentServer(context: Context? = null, port: Int = 8080) {
        val url = "http://10.0.2.2:$port"
        setCustomBaseUrl(context, url)
    }

    /**
     * Configures the production HTTPS service URL
     */
    fun useProductionServer(context: Context? = null, productionUrl: String) {
        val trimmed = productionUrl.trim()
        if (trimmed.startsWith("https://", ignoreCase = true) || trimmed.startsWith("http://", ignoreCase = true)) {
            setCustomBaseUrl(context, trimmed)
        }
    }
}
