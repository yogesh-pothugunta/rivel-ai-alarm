package com.example.data.repository

import com.example.data.local.dao.FocusSessionDao
import com.example.data.local.entity.FocusSessionEntity
import kotlinx.coroutines.flow.Flow

class FocusSessionRepository(private val dao: FocusSessionDao) {
    val allSessions: Flow<List<FocusSessionEntity>> = dao.getAllSessions()

    fun getRecentSessions(sinceTimestamp: Long): Flow<List<FocusSessionEntity>> =
        dao.getRecentSessions(sinceTimestamp)

    fun getTotalMinutesSince(sinceTimestamp: Long): Flow<Int?> =
        dao.getTotalMinutesSince(sinceTimestamp)

    suspend fun recordSession(
        taskId: Long?,
        taskName: String,
        subject: String,
        targetMinutes: Int,
        actualMinutes: Int,
        wasCompleted: Boolean
    ): Long {
        val session = FocusSessionEntity(
            taskId = taskId,
            taskName = taskName,
            subject = subject,
            targetMinutes = targetMinutes,
            actualMinutes = actualMinutes,
            wasCompleted = wasCompleted,
            timestamp = System.currentTimeMillis()
        )
        return dao.insertSession(session)
    }
}
