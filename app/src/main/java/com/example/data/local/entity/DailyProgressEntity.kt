package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_progress")
data class DailyProgressEntity(
    @PrimaryKey val dateString: String, // "YYYY-MM-DD"
    val totalStudyMinutes: Int = 0,
    val completedTasksCount: Int = 0,
    val completedSessionsCount: Int = 0,
    val wakeAlarmsCompleted: Int = 0,
    val missionsCompleted: Int = 0,
    val currentStreak: Int = 0
)
