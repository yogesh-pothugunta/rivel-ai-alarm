package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "focus_sessions")
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long? = null,
    val taskName: String = "",
    val subject: String = "",
    val targetMinutes: Int,
    val actualMinutes: Int,
    val wasCompleted: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
)
