package com.example.alarm.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.alarm.engine.ActiveAlarmData
import com.example.alarm.engine.AlarmActiveState

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("AlarmReceiver", "Received alarm intent: $action")

        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1L)
        val label = intent.getStringExtra(EXTRA_ALARM_LABEL) ?: "RIVEL Study Wake Alarm"
        val missionType = intent.getStringExtra(EXTRA_MISSION_TYPE) ?: "BRAIN"
        val missionDifficulty = intent.getStringExtra(EXTRA_MISSION_DIFFICULTY) ?: "MEDIUM"
        val targetObject = intent.getStringExtra(EXTRA_TARGET_OBJECT) ?: "Book"
        val targetSteps = intent.getIntExtra(EXTRA_TARGET_STEPS, 20)

        val activeData = ActiveAlarmData(
            alarmId = alarmId,
            label = label,
            missionType = missionType,
            missionDifficulty = missionDifficulty,
            targetObject = targetObject,
            movementTargetSteps = targetSteps,
            triggerTimestamp = System.currentTimeMillis()
        )

        // 1. Update State for in-app UI overlay
        AlarmActiveState.triggerRinging(activeData)

        // 2. Wake lock to wake device screen
        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            @Suppress("DEPRECATION")
            val wakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                "Rivel:AlarmWakeLock"
            )
            wakeLock.acquire(3 * 60 * 1000L /* 3 minutes */)
        } catch (e: Exception) {
            Log.e("AlarmReceiver", "Failed to acquire wake lock", e)
        }

        // 3. Post full screen notification
        showAlarmNotification(context, activeData)

        // 4. Launch MainActivity
        try {
            val activityIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_ALARM_ID, alarmId)
                putExtra(EXTRA_ALARM_LABEL, label)
                putExtra(EXTRA_MISSION_TYPE, missionType)
                putExtra(EXTRA_MISSION_DIFFICULTY, missionDifficulty)
                putExtra(EXTRA_TARGET_OBJECT, targetObject)
                putExtra(EXTRA_TARGET_STEPS, targetSteps)
            }
            context.startActivity(activityIntent)
        } catch (e: Exception) {
            Log.e("AlarmReceiver", "Failed to start MainActivity directly", e)
        }
    }

    private fun showAlarmNotification(context: Context, data: ActiveAlarmData) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channelId = "rivel_alarm_channel_v1"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "RIVEL Wake Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High-priority wake alarms with missions for students"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 300, 500, 300, 800)
                setBypassDnd(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                setSound(
                    alarmSound,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }
            notificationManager.createNotificationChannel(channel)
        }

        val fullScreenIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_ALARM_ID, data.alarmId)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            data.alarmId.toInt(),
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("⏰ " + data.label)
            .setContentText("Wake up & activate your brain! Tap to begin mission.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val ACTION_TRIGGER_ALARM = "com.aistudio.rivel.ACTION_TRIGGER_ALARM"
        const val EXTRA_ALARM_ID = "extra_alarm_id"
        const val EXTRA_ALARM_LABEL = "extra_alarm_label"
        const val EXTRA_MISSION_TYPE = "extra_mission_type"
        const val EXTRA_MISSION_DIFFICULTY = "extra_mission_difficulty"
        const val EXTRA_TARGET_OBJECT = "extra_target_object"
        const val EXTRA_TARGET_STEPS = "extra_target_steps"
        const val NOTIFICATION_ID = 1001

        fun cancelNotification(context: Context) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(NOTIFICATION_ID)
        }
    }
}
