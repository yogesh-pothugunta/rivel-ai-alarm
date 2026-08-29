package com.example.alarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.RivelApplication
import com.example.alarm.scheduler.AndroidAlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.d("BootReceiver", "Received broadcast action: $action - beginning alarm restoration")

        val app = context.applicationContext as? RivelApplication ?: return
        val alarmRepo = app.alarmRepository
        val scheduler = AndroidAlarmScheduler(context)

        // Use BroadcastReceiver.goAsync() to prevent Android OS from killing the receiver
        // process before asynchronous Room query and alarm rescheduling complete.
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val enabledAlarms = alarmRepo.getEnabledAlarmsOnce()
                Log.d("BootReceiver", "Found ${enabledAlarms.size} active alarms to restore after $action")

                for (alarm in enabledAlarms) {
                    val nextTrigger = scheduler.scheduleAlarm(alarm)
                    Log.d("BootReceiver", "Restored alarm #${alarm.id} (${alarm.label}) next trigger at timestamp $nextTrigger")
                }
            } catch (e: Exception) {
                Log.e("BootReceiver", "Error restoring active alarms during $action", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
