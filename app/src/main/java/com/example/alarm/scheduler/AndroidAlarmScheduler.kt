package com.example.alarm.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.example.MainActivity
import com.example.alarm.receiver.AlarmReceiver
import com.example.data.local.entity.AlarmEntity
import java.util.Calendar

class AndroidAlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                try {
                    val appSettingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(appSettingsIntent)
                } catch (ex: Exception) {
                    Log.e("AndroidAlarmScheduler", "Could not open system settings", ex)
                }
            }
        }
    }

    fun scheduleAlarm(alarm: AlarmEntity): Long {
        if (!alarm.isEnabled) {
            cancelAlarm(alarm.id)
            return 0
        }

        val triggerTime = computeNextTriggerTime(alarm.hour, alarm.minute, alarm.daysOfWeek)

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_TRIGGER_ALARM
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarm.id)
            putExtra(AlarmReceiver.EXTRA_ALARM_LABEL, alarm.label)
            putExtra(AlarmReceiver.EXTRA_MISSION_TYPE, alarm.missionType)
            putExtra(AlarmReceiver.EXTRA_MISSION_DIFFICULTY, alarm.missionDifficulty)
            putExtra(AlarmReceiver.EXTRA_TARGET_OBJECT, alarm.targetObject)
            putExtra(AlarmReceiver.EXTRA_TARGET_STEPS, alarm.movementTargetSteps)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Show Intent for user when tapping alarm clock in status bar / lockscreen
        val showIntent = PendingIntent.getActivity(
            context,
            alarm.id.toInt(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerTime, showIntent)
                alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
            Log.d("AndroidAlarmScheduler", "Exact alarm ${alarm.id} successfully scheduled for timestamp $triggerTime")
        } catch (e: SecurityException) {
            Log.w("AndroidAlarmScheduler", "Exact alarm permission missing on Android 12+. Falling back to standard alarm: ${e.message}")
            try {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            } catch (ex: Exception) {
                Log.e("AndroidAlarmScheduler", "Failed fallback alarm scheduling", ex)
            }
        }

        return triggerTime
    }

    fun scheduleTestAlarm(secondsFromNow: Int = 5, label: String = "Test Wake Alarm", missionType: String = "BRAIN"): Long {
        val triggerTime = System.currentTimeMillis() + (secondsFromNow * 1000L)
        val testAlarmId = 999999L

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_TRIGGER_ALARM
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, testAlarmId)
            putExtra(AlarmReceiver.EXTRA_ALARM_LABEL, label)
            putExtra(AlarmReceiver.EXTRA_MISSION_TYPE, missionType)
            putExtra(AlarmReceiver.EXTRA_MISSION_DIFFICULTY, "MEDIUM")
            putExtra(AlarmReceiver.EXTRA_TARGET_OBJECT, "Book")
            putExtra(AlarmReceiver.EXTRA_TARGET_STEPS, 15)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            testAlarmId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val showIntent = PendingIntent.getActivity(
            context,
            testAlarmId.toInt(),
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerTime, showIntent)
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
        } catch (e: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }
        return triggerTime
    }

    fun cancelAlarm(alarmId: Long) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_TRIGGER_ALARM
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarmId.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    companion object {
        fun computeNextTriggerTime(hour: Int, minute: Int, daysOfWeekStr: String): Long {
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            if (daysOfWeekStr.isEmpty() || daysOfWeekStr == "ONCE") {
                if (target.before(now) || target.equals(now)) {
                    target.add(Calendar.DAY_OF_YEAR, 1)
                }
                return target.timeInMillis
            }

            if (daysOfWeekStr == "ALL" || daysOfWeekStr == "EVERY_DAY") {
                if (target.before(now) || target.equals(now)) {
                    target.add(Calendar.DAY_OF_YEAR, 1)
                }
                return target.timeInMillis
            }

            // Specific days parsing: e.g. "MON,TUE,WED"
            val dayMapping = mapOf(
                "SUN" to Calendar.SUNDAY,
                "MON" to Calendar.MONDAY,
                "TUE" to Calendar.TUESDAY,
                "WED" to Calendar.WEDNESDAY,
                "THU" to Calendar.THURSDAY,
                "FRI" to Calendar.FRIDAY,
                "SAT" to Calendar.SATURDAY
            )

            val targetDays = daysOfWeekStr.split(",")
                .map { it.trim().uppercase() }
                .mapNotNull { dayMapping[it] }
                .toSet()

            if (targetDays.isEmpty()) {
                if (target.before(now)) target.add(Calendar.DAY_OF_YEAR, 1)
                return target.timeInMillis
            }

            // Find closest matching day in the future
            for (dayOffset in 0..7) {
                val candidate = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, dayOffset)
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val candidateDayOfWeek = candidate.get(Calendar.DAY_OF_WEEK)
                if (targetDays.contains(candidateDayOfWeek)) {
                    if (candidate.after(now)) {
                        return candidate.timeInMillis
                    }
                }
            }

            // Fallback
            target.add(Calendar.DAY_OF_YEAR, 1)
            return target.timeInMillis
        }
    }
}
