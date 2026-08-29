package com.example.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.model.AIService
import com.example.ai.model.StudentContext
import com.example.data.local.entity.ChatMessageEntity
import com.example.data.repository.ChatMessageRepository
import com.example.data.repository.DailyProgressRepository
import com.example.data.repository.StudentProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AIChatViewModel(
    private val aiService: AIService,
    private val chatRepository: ChatMessageRepository,
    private val profileRepository: StudentProfileRepository,
    private val progressRepository: DailyProgressRepository
) : ViewModel() {

    val chatMessages: StateFlow<List<ChatMessageEntity>> = chatRepository.allMessages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun sendMessage(userText: String) {
        if (userText.isBlank() || _isLoading.value) return

        viewModelScope.launch {
            _isLoading.value = true

            // 1. Save user message
            chatRepository.saveMessage(sender = "USER", content = userText)

            // 2. Fetch context
            val profile = profileRepository.getProfileOnce()
            val progress = progressRepository.getTodayProgressOnce()

            val studentContext = StudentContext(
                studentName = profile?.name ?: "Student",
                educationLevel = profile?.educationLevel ?: "",
                course = profile?.course ?: "",
                year = profile?.year ?: "",
                mainGoal = profile?.mainGoal ?: "",
                subjects = profile?.subjects?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList(),
                upcomingExam = profile?.upcomingExam ?: "",
                todayCompletedMinutes = progress.totalStudyMinutes,
                activeStreak = progress.currentStreak
            )

            val currentHistory = chatMessages.value.map { it.sender to it.content }

            // 3. Query AI Service
            val response = aiService.generateStudentAdvice(
                userMessage = userText,
                context = studentContext,
                chatHistory = currentHistory
            )

            // 4. Save AI reply with responseSource
            chatRepository.saveMessage(
                sender = "RIVEL",
                content = response.replyText,
                actionType = response.suggestedActionType,
                actionPayload = response.suggestedActionPayload,
                responseSource = response.responseSource.name
            )

            _isLoading.value = false
        }
    }

    fun clearChat() {
        viewModelScope.launch {
            chatRepository.clearHistory()
        }
    }
}
