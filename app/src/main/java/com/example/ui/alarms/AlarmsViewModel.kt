package com.example.ui.alarms

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.alarm.scheduler.AndroidAlarmScheduler
import com.example.data.local.entity.AlarmEntity
import com.example.data.repository.AlarmRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AlarmsViewModel(
    private val alarmRepository: AlarmRepository,
    private val alarmScheduler: AndroidAlarmScheduler
) : ViewModel() {

    val allAlarms: StateFlow<List<AlarmEntity>> = alarmRepository.allAlarms
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val canScheduleExactAlarms: Boolean
        get() = alarmScheduler.canScheduleExactAlarms()

    fun openExactAlarmSettings(context: Context) {
        alarmScheduler.openExactAlarmSettings(context)
    }

    fun toggleAlarm(alarm: AlarmEntity) {
        viewModelScope.launch {
            val newState = !alarm.isEnabled
            alarmRepository.setAlarmEnabled(alarm.id, newState)
            if (newState) {
                alarmScheduler.scheduleAlarm(alarm.copy(isEnabled = true))
            } else {
                alarmScheduler.cancelAlarm(alarm.id)
            }
        }
    }

    fun saveAlarm(
        id: Long = 0,
        hour: Int,
        minute: Int,
        daysOfWeek: String,
        label: String,
        missionType: String,
        missionDifficulty: String,
        targetObject: String,
        movementTargetSteps: Int,
        snoozeMinutes: Int,
        isVibrationEnabled: Boolean
    ) {
        viewModelScope.launch {
            val entity = AlarmEntity(
                id = id,
                hour = hour,
                minute = minute,
                daysOfWeek = daysOfWeek,
                label = label.ifBlank { "Study Wake Alarm" },
                isEnabled = true,
                missionType = missionType,
                missionDifficulty = missionDifficulty,
                targetObject = targetObject,
                movementTargetSteps = movementTargetSteps,
                snoozeMinutes = snoozeMinutes,
                isVibrationEnabled = isVibrationEnabled
            )

            val savedId = if (id == 0L) {
                alarmRepository.insertAlarm(entity)
            } else {
                alarmRepository.updateAlarm(entity)
                id
            }

            alarmScheduler.scheduleAlarm(entity.copy(id = savedId))
        }
    }

    fun deleteAlarm(alarm: AlarmEntity) {
        viewModelScope.launch {
            alarmScheduler.cancelAlarm(alarm.id)
            alarmRepository.deleteAlarm(alarm)
        }
    }

    fun testAlarmImmediately(alarm: AlarmEntity) {
        alarmScheduler.scheduleTestAlarm(
            secondsFromNow = 5,
            label = alarm.label,
            missionType = alarm.missionType
        )
    }
}
