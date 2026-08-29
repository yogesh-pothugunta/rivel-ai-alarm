package com.example.ui.study

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.StudentProfileEntity
import com.example.data.local.entity.StudyTaskEntity
import com.example.data.repository.DailyProgressRepository
import com.example.data.repository.StudentProfileRepository
import com.example.data.repository.StudyTaskRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class StudyViewModel(
    private val studyTaskRepository: StudyTaskRepository,
    private val progressRepository: DailyProgressRepository,
    private val profileRepository: StudentProfileRepository
) : ViewModel() {

    val allTasks: StateFlow<List<StudyTaskEntity>> = studyTaskRepository.allTasks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val profile: StateFlow<StudentProfileEntity?> = profileRepository.profile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun toggleTask(taskId: Long, isCompleted: Boolean) {
        viewModelScope.launch {
            studyTaskRepository.toggleTaskCompleted(taskId, isCompleted)
            if (isCompleted) {
                progressRepository.incrementCompletedTasks()
            }
        }
    }

    fun setPriorityToday(taskId: Long, isPriority: Boolean) {
        viewModelScope.launch {
            studyTaskRepository.setTaskPriorityToday(taskId, isPriority)
        }
    }

    fun createTask(
        subject: String,
        topic: String,
        taskDescription: String,
        deadline: String,
        estimatedMinutes: Int,
        priority: String,
        isPriorityToday: Boolean
    ) {
        viewModelScope.launch {
            val task = StudyTaskEntity(
                subject = subject.trim().ifBlank { "General" },
                topic = topic.trim(),
                taskDescription = taskDescription.trim(),
                deadline = deadline.trim(),
                estimatedMinutes = estimatedMinutes,
                priority = priority,
                isPriorityToday = isPriorityToday
            )
            studyTaskRepository.insertTask(task)
        }
    }

    fun deleteTask(task: StudyTaskEntity) {
        viewModelScope.launch {
            studyTaskRepository.deleteTask(task)
        }
    }
}
