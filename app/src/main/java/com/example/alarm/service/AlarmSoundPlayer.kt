package com.example.alarm.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AlarmSoundPlayer(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var isPlaying = false
    private var volumeEscalationJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    init {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    /**
     * Starts alarm playback with automatic progressive volume escalation:
     * - 0–10s: 0.40f (moderately loud)
     * - 10–20s: 0.65f (louder)
     * - 20–30s: 0.85f (very loud)
     * - 30s+: 1.00f (maximum permitted alarm stream volume)
     */
    fun startAlarmSound(isVibrationEnabled: Boolean = true) {
        if (isPlaying) return
        isPlaying = true

        val initialVolume = 0.40f

        try {
            var alertUri: Uri? = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            if (alertUri == null) {
                alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            }
            if (alertUri == null) {
                alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            }

            if (alertUri != null) {
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    setDataSource(context, alertUri)
                    isLooping = true
                    setVolume(initialVolume, initialVolume)
                    prepare()
                    start()
                }
            } else {
                Log.e("AlarmSoundPlayer", "No alert URI found on device.")
            }
        } catch (e: Exception) {
            Log.e("AlarmSoundPlayer", "Error initializing alarm audio playback", e)
        }

        // Start progressive volume escalation
        volumeEscalationJob?.cancel()
        volumeEscalationJob = scope.launch {
            try {
                // Step 1: 0–10 seconds (0.40f)
                setVolume(0.40f)
                delay(10_000L)

                // Step 2: 10–20 seconds (0.65f)
                if (isPlaying) {
                    setVolume(0.65f)
                    Log.d("AlarmSoundPlayer", "Escalating alarm volume to 0.65f")
                }
                delay(10_000L)

                // Step 3: 20–30 seconds (0.85f)
                if (isPlaying) {
                    setVolume(0.85f)
                    Log.d("AlarmSoundPlayer", "Escalating alarm volume to 0.85f")
                }
                delay(10_000L)

                // Step 4: 30+ seconds (1.00f max)
                if (isPlaying) {
                    setVolume(1.00f)
                    Log.d("AlarmSoundPlayer", "Escalating alarm volume to maximum (1.00f)")
                }
            } catch (e: Exception) {
                // Coroutine cancelled during stop()
            }
        }

        if (isVibrationEnabled) {
            startVibration()
        }
    }

    fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0.1f, 1.0f)
        try {
            mediaPlayer?.setVolume(clamped, clamped)
        } catch (e: Exception) {
            Log.e("AlarmSoundPlayer", "Error adjusting media volume", e)
        }
    }

    private fun startVibration() {
        try {
            val pattern = longArrayOf(0, 500, 300, 500, 300, 800)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            Log.e("AlarmSoundPlayer", "Error starting vibration", e)
        }
    }

    fun stop() {
        isPlaying = false
        volumeEscalationJob?.cancel()
        volumeEscalationJob = null

        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
            }
            mediaPlayer?.release()
        } catch (e: Exception) {
            Log.e("AlarmSoundPlayer", "Error stopping audio", e)
        } finally {
            mediaPlayer = null
        }

        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            Log.e("AlarmSoundPlayer", "Error stopping vibration", e)
        }
    }
}
