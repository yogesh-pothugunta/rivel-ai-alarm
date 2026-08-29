package com.example.data.repository

import com.example.data.local.dao.StudyTaskDao
import com.example.data.local.entity.StudyTaskEntity
import kotlinx.coroutines.flow.Flow

class StudyTaskRepository(private val dao: StudyTaskDao) {
    val allTasks: Flow<List<StudyTaskEntity>> = dao.getAllTasks()
    val pendingTasks: Flow<List<StudyTaskEntity>> = dao.getPendingTasks()
    val todayPriorityTask: Flow<StudyTaskEntity?> = dao.getTodayPriorityTask()

    suspend fun getTaskById(id: Long): StudyTaskEntity? = dao.getTaskById(id)

    suspend fun insertTask(task: StudyTaskEntity): Long = dao.insertTask(task)

    suspend fun updateTask(task: StudyTaskEntity) = dao.updateTask(task)

    suspend fun toggleTaskCompleted(id: Long, completed: Boolean) {
        val completedAt = if (completed) System.currentTimeMillis() else null
        dao.setTaskCompleted(id, completed, completedAt)
    }

    suspend fun setTaskPriorityToday(id: Long, isPriority: Boolean) {
        dao.setTaskPriorityToday(id, isPriority)
    }

    suspend fun deleteTask(task: StudyTaskEntity) = dao.deleteTask(task)

    suspend fun deleteTaskById(id: Long) = dao.deleteTaskById(id)
}
