package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "student_profile")
data class StudentProfileEntity(
    @PrimaryKey val id: Int = 1,
    val name: String = "",
    val educationLevel: String = "", // High School, College/University, Graduate, Self-study
    val course: String = "", // e.g. Computer Science, Pre-Med, Engineering, etc.
    val year: String = "", // e.g. 1st Year, 2nd Year, Senior, etc.
    val mainGoal: String = "", // e.g. Ace finals, Build consistent morning study routine
    val preferredWakeTime: String = "06:30",
    val preferredSleepTime: String = "23:00",
    val subjects: String = "", // Comma-separated list
    val upcomingExam: String = "",
    val isOnboarded: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
