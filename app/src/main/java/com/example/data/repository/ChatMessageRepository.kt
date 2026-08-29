package com.example.data.repository

import com.example.data.local.dao.ChatMessageDao
import com.example.data.local.entity.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

class ChatMessageRepository(private val dao: ChatMessageDao) {
    val allMessages: Flow<List<ChatMessageEntity>> = dao.getAllMessages()

    suspend fun saveMessage(
        sender: String,
        content: String,
        actionType: String? = null,
        actionPayload: String? = null,
        responseSource: String? = null
    ): Long {
        val msg = ChatMessageEntity(
            sender = sender,
            content = content,
            actionType = actionType,
            actionPayload = actionPayload,
            responseSource = responseSource,
            timestamp = System.currentTimeMillis()
        )
        return dao.insertMessage(msg)
    }

    suspend fun clearHistory() {
        dao.clearAllMessages()
    }
}
