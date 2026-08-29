package com.example.ai.model

enum class AIResponseSource {
    LIVE_GEMINI,
    LOCAL_FALLBACK,
    ERROR
}

data class StudentContext(
    val studentName: String = "",
    val educationLevel: String = "",
    val course: String = "",
    val year: String = "",
    val mainGoal: String = "",
    val subjects: List<String> = emptyList(),
    val upcomingExam: String = "",
    val todayCompletedMinutes: Int = 0,
    val activeStreak: Int = 0
)

data class AIResponse(
    val replyText: String,
    val responseSource: AIResponseSource = AIResponseSource.LOCAL_FALLBACK,
    val suggestedActionType: String? = null, // e.g. "START_FOCUS", "CREATE_TASK"
    val suggestedActionPayload: String? = null,
    val suggestedActionLabel: String? = null
)

interface AIService {
    suspend fun generateStudentAdvice(
        userMessage: String,
        context: StudentContext,
        chatHistory: List<Pair<String, String>>
    ): AIResponse
}
