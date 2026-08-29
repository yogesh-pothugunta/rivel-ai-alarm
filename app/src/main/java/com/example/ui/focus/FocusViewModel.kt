package com.example.ui.focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.DailyProgressRepository
import com.example.data.repository.FocusSessionRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class FocusTimerState {
    IDLE, RUNNING, PAUSED, COMPLETED
}

class FocusViewModel(
    private val focusSessionRepository: FocusSessionRepository,
    private val progressRepository: DailyProgressRepository
) : ViewModel() {

    private val _timerState = MutableStateFlow(FocusTimerState.IDLE)
    val timerState: StateFlow<FocusTimerState> = _timerState.asStateFlow()

    private val _selectedMinutes = MutableStateFlow(25)
    val selectedMinutes: StateFlow<Int> = _selectedMinutes.asStateFlow()

    private val _remainingSeconds = MutableStateFlow(25 * 60)
    val remainingSeconds: StateFlow<Int> = _remainingSeconds.asStateFlow()

    private val _activeTaskId = MutableStateFlow<Long?>(null)
    val activeTaskId: StateFlow<Long?> = _activeTaskId.asStateFlow()

    private val _activeTaskName = MutableStateFlow("Deep Study Session")
    val activeTaskName: StateFlow<String> = _activeTaskName.asStateFlow()

    private val _activeSubject = MutableStateFlow("General")
    val activeSubject: StateFlow<String> = _activeSubject.asStateFlow()

    private var timerJob: Job? = null
    private var elapsedSeconds = 0

    fun setupSession(taskId: Long?, taskName: String, subject: String, minutes: Int) {
        if (_timerState.value == FocusTimerState.RUNNING) return
        _activeTaskId.value = taskId
        _activeTaskName.value = taskName.ifBlank { "Deep Study Session" }
        _activeSubject.value = subject.ifBlank { "General" }
        _selectedMinutes.value = minutes.coerceIn(5, 120)
        _remainingSeconds.value = minutes * 60
        elapsedSeconds = 0
        _timerState.value = FocusTimerState.IDLE
    }

    fun setDuration(minutes: Int) {
        if (_timerState.value == FocusTimerState.RUNNING) return
        _selectedMinutes.value = minutes
        _remainingSeconds.value = minutes * 60
    }

    fun startTimer() {
        if (_timerState.value == FocusTimerState.RUNNING) return

        if (_timerState.value == FocusTimerState.IDLE) {
            elapsedSeconds = 0
            _remainingSeconds.value = _selectedMinutes.value * 60
        }

        _timerState.value = FocusTimerState.RUNNING
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_remainingSeconds.value > 0) {
                delay(1000L)
                _remainingSeconds.value -= 1
                elapsedSeconds += 1
            }
            // Finished!
            finishSession(wasCompleted = true)
        }
    }

    fun pauseTimer() {
        if (_timerState.value == FocusTimerState.RUNNING) {
            timerJob?.cancel()
            _timerState.value = FocusTimerState.PAUSED
        }
    }

    fun resumeTimer() {
        if (_timerState.value == FocusTimerState.PAUSED) {
            startTimer()
        }
    }

    fun cancelTimer() {
        timerJob?.cancel()
        val minutesSpent = elapsedSeconds / 60
        if (minutesSpent >= 1) {
            recordSessionToDb(minutesSpent, wasCompleted = false)
        }
        _timerState.value = FocusTimerState.IDLE
        _remainingSeconds.value = _selectedMinutes.value * 60
        elapsedSeconds = 0
    }

    private fun finishSession(wasCompleted: Boolean) {
        _timerState.value = FocusTimerState.COMPLETED
        val minutesSpent = (_selectedMinutes.value)
        recordSessionToDb(minutesSpent, wasCompleted)
    }

    private fun recordSessionToDb(minutes: Int, wasCompleted: Boolean) {
        viewModelScope.launch {
            focusSessionRepository.recordSession(
                taskId = _activeTaskId.value,
                taskName = _activeTaskName.value,
                subject = _activeSubject.value,
                targetMinutes = _selectedMinutes.value,
                actualMinutes = minutes,
                wasCompleted = wasCompleted
            )
            progressRepository.addStudyMinutes(minutes)
        }
    }

    fun resetTimer() {
        timerJob?.cancel()
        _timerState.value = FocusTimerState.IDLE
        _remainingSeconds.value = _selectedMinutes.value * 60
        elapsedSeconds = 0
    }
}
