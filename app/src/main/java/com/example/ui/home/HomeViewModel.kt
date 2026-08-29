package com.example.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.alarm.scheduler.AndroidAlarmScheduler
import com.example.data.local.entity.AlarmEntity
import com.example.data.local.entity.DailyProgressEntity
import com.example.data.local.entity.StudentProfileEntity
import com.example.data.local.entity.StudyTaskEntity
import com.example.data.repository.AlarmRepository
import com.example.data.repository.DailyProgressRepository
import com.example.data.repository.StudentProfileRepository
import com.example.data.repository.StudyTaskRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    private val profileRepository: StudentProfileRepository,
    private val alarmRepository: AlarmRepository,
    private val studyTaskRepository: StudyTaskRepository,
    private val progressRepository: DailyProgressRepository,
    private val alarmScheduler: AndroidAlarmScheduler
) : ViewModel() {

    val profile: StateFlow<StudentProfileEntity?> = profileRepository.profile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val nextAlarm: StateFlow<AlarmEntity?> = alarmRepository.nextAlarm
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val todayPriorityTask: StateFlow<StudyTaskEntity?> = studyTaskRepository.todayPriorityTask
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val pendingTasks: StateFlow<List<StudyTaskEntity>> = studyTaskRepository.pendingTasks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todayProgress: StateFlow<DailyProgressEntity?> = progressRepository.todayProgress
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

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

    fun completeTask(taskId: Long) {
        viewModelScope.launch {
            studyTaskRepository.toggleTaskCompleted(taskId, true)
            progressRepository.incrementCompletedTasks()
        }
    }

    fun triggerTestAlarm(missionType: String = "BRAIN") {
        alarmScheduler.scheduleTestAlarm(secondsFromNow = 5, label = "RIVEL Study Wake Alarm", missionType = missionType)
    }
}
