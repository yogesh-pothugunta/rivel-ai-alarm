package com.example.alarm.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ActiveAlarmData(
    val alarmId: Long,
    val label: String,
    val missionType: String,
    val missionDifficulty: String,
    val targetObject: String = "Book",
    val movementTargetSteps: Int = 20,
    val triggerTimestamp: Long = System.currentTimeMillis()
)

object AlarmActiveState {
    private val _currentRingingAlarm = MutableStateFlow<ActiveAlarmData?>(null)
    val currentRingingAlarm: StateFlow<ActiveAlarmData?> = _currentRingingAlarm.asStateFlow()

    fun triggerRinging(alarm: ActiveAlarmData) {
        _currentRingingAlarm.value = alarm
    }

    fun dismissAlarm() {
        _currentRingingAlarm.value = null
    }

    fun isRinging(): Boolean = _currentRingingAlarm.value != null
}
