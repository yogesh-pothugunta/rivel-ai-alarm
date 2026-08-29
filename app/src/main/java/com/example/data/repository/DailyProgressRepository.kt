package com.example.data.repository

import com.example.data.local.dao.DailyProgressDao
import com.example.data.local.entity.DailyProgressEntity
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DailyProgressRepository(private val dao: DailyProgressDao) {

    private fun getTodayDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(Date())
    }

    val todayProgress: Flow<DailyProgressEntity?> = dao.getProgressForDate(getTodayDateString())
    val recentWeekProgress: Flow<List<DailyProgressEntity>> = dao.getRecentWeekProgress()

    suspend fun getTodayProgressOnce(): DailyProgressEntity {
        val today = getTodayDateString()
        return dao.getProgressForDateOnce(today) ?: DailyProgressEntity(dateString = today)
    }

    suspend fun addStudyMinutes(minutes: Int) {
        val current = getTodayProgressOnce()
        val updated = current.copy(
            totalStudyMinutes = current.totalStudyMinutes + minutes,
            completedSessionsCount = current.completedSessionsCount + 1,
            currentStreak = if (current.currentStreak == 0) 1 else current.currentStreak
        )
        dao.insertOrUpdateProgress(updated)
    }

    suspend fun incrementCompletedTasks() {
        val current = getTodayProgressOnce()
        val updated = current.copy(
            completedTasksCount = current.completedTasksCount + 1
        )
        dao.insertOrUpdateProgress(updated)
    }

    suspend fun recordWakeAlarmMissionSuccess() {
        val current = getTodayProgressOnce()
        val updated = current.copy(
            wakeAlarmsCompleted = current.wakeAlarmsCompleted + 1,
            missionsCompleted = current.missionsCompleted + 1,
            currentStreak = if (current.currentStreak == 0) 1 else current.currentStreak
        )
        dao.insertOrUpdateProgress(updated)
    }
}
