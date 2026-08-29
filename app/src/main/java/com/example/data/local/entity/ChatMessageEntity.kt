package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String, // "USER" or "RIVEL"
    val content: String,
    val actionType: String? = null, // e.g. "START_FOCUS", "ADD_TASK", "SET_ALARM"
    val actionPayload: String? = null,
    val responseSource: String? = null, // "LIVE_GEMINI", "LOCAL_FALLBACK", "ERROR"
    val timestamp: Long = System.currentTimeMillis()
)
