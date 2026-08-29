package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "study_tasks")
data class StudyTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subject: String,
    val topic: String,
    val taskDescription: String,
    val deadline: String = "", // e.g. "Today", "Tomorrow", "Next Friday"
    val estimatedMinutes: Int = 30,
    val priority: String = "HIGH", // LOW, MEDIUM, HIGH
    val isCompleted: Boolean = false,
    val isPriorityToday: Boolean = false,
    val completedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)
