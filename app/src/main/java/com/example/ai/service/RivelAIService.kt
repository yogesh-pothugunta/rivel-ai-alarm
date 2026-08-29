package com.example.ai.service

import android.util.Log
import com.example.ai.BackendConfig
import com.example.ai.model.AIResponse
import com.example.ai.model.AIResponseSource
import com.example.ai.model.AIService
import com.example.ai.model.StudentContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class RivelAIService : AIService {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    override suspend fun generateStudentAdvice(
        userMessage: String,
        context: StudentContext,
        chatHistory: List<Pair<String, String>>
    ): AIResponse = withContext(Dispatchers.IO) {
        // Attempt to reach secure RIVEL backend proxy
        try {
            val jsonPayload = JSONObject().apply {
                put("message", userMessage)

                val contextObj = JSONObject().apply {
                    put("studentName", context.studentName)
                    put("educationLevel", context.educationLevel)
                    put("course", context.course)
                    put("year", context.year)
                    put("mainGoal", context.mainGoal)
                    put("subjects", JSONArray(context.subjects))
                    put("upcomingExam", context.upcomingExam)
                    put("todayCompletedMinutes", context.todayCompletedMinutes)
                    put("activeStreak", context.activeStreak)
                }
                put("studentContext", contextObj)

                val historyArr = JSONArray()
                val recentTurns = chatHistory.takeLast(8)
                for ((role, text) in recentTurns) {
                    historyArr.put(JSONObject().apply {
                        put("role", if (role == "USER" || role == "user") "user" else "model")
                        put("text", text)
                    })
                }
                put("chatHistory", historyArr)
            }

            val requestBody = jsonPayload.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(BackendConfig.chatEndpoint)
                .post(requestBody)
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful && responseBody.isNotBlank()) {
                val root = JSONObject(responseBody)
                val success = root.optBoolean("success", false)
                val reply = root.optString("reply", "")

                if (success && reply.isNotBlank()) {
                    val actionType = root.optString("suggestedActionType").takeIf { it.isNotBlank() && it != "null" }
                    val actionPayload = root.optString("suggestedActionPayload").takeIf { it.isNotBlank() && it != "null" }
                    val actionLabel = root.optString("suggestedActionLabel").takeIf { it.isNotBlank() && it != "null" }

                    Log.i("RivelAI", "Successfully received live advice from secure backend proxy")
                    return@withContext AIResponse(
                        replyText = reply.trim(),
                        responseSource = AIResponseSource.LIVE_GEMINI,
                        suggestedActionType = actionType,
                        suggestedActionPayload = actionPayload,
                        suggestedActionLabel = actionLabel
                    )
                }
            } else {
                Log.w("RivelAI", "Backend returned HTTP ${response.code}: $responseBody. Falling back to local coach.")
            }
        } catch (e: Exception) {
            Log.w("RivelAI", "Unable to reach AI backend proxy (${e.message}). Falling back to local coach.")
        }

        // Local evidence-based Student Coaching Engine fallback
        return@withContext generateLocalStudentAdvice(userMessage, context)
    }

    private fun generateLocalStudentAdvice(prompt: String, context: StudentContext): AIResponse {
        val p = prompt.trim().lowercase()
        val studentName = if (context.studentName.isNotBlank()) context.studentName.trim() else "friend"

        // 1. Natural greetings and casual check-ins
        if (p == "hi" || p == "hello" || p == "hey" || p == "hola" || p.startsWith("hi ") || p.startsWith("hey ") || p.startsWith("hello ")) {
            return AIResponse(
                replyText = "Hey $studentName! 😊 How's your day going? Ready to make some progress, or need help organizing what to study next?",
                responseSource = AIResponseSource.LOCAL_FALLBACK,
                suggestedActionType = null,
                suggestedActionPayload = null,
                suggestedActionLabel = null
            )
        }

        if (p.contains("how are you") || p.contains("how're you") || p.contains("how do you do")) {
            return AIResponse(
                replyText = "I'm doing great and pumped to help you conquer your goals today, $studentName! 💪 How are you feeling about your classes and study tasks right now?",
                responseSource = AIResponseSource.LOCAL_FALLBACK,
                suggestedActionType = null,
                suggestedActionPayload = null,
                suggestedActionLabel = null
            )
        }

        // 2. Laziness / Procrastination / Low Motivation
        if (p.contains("procrastinat") || p.contains("can't start") || p.contains("lazy") || p.contains("don't want to study") || p.contains("no motivation")) {
            return AIResponse(
                replyText = "I completely get it, $studentName. Low motivation is totally normal — starting is always the hardest part because your brain overestimates the effort.\n\n" +
                        "Let's not worry about studying for hours right now. Just commit to **5 small minutes** of opening your notes. Once you take that first step, the momentum takes care of the rest.\n\n" +
                        "Want to knock out a quick 15-minute low-pressure focus sprint with me?",
                responseSource = AIResponseSource.LOCAL_FALLBACK,
                suggestedActionType = "START_FOCUS",
                suggestedActionPayload = "15",
                suggestedActionLabel = "Start 15-Min Focus Sprint"
            )
        }

        // 3. Exam stress / Urgent deadlines
        if (p.contains("exam") || p.contains("test") || p.contains("finals") || p.contains("haven't studied") || p.contains("tomorrow")) {
            val examName = if (context.upcomingExam.isNotBlank()) context.upcomingExam else "your upcoming test"
            return AIResponse(
                replyText = "Take a deep breath — panic wastes energy, and you still have time to make high-impact progress on $examName. 🎯\n\n" +
                        "Here is our triage game plan:\n" +
                        "1. **Triage Top 20%:** Identify the high-yield formulas and past questions that appear most frequently.\n" +
                        "2. **Active Testing:** Don't passively re-read; test yourself with practice problems right away.\n" +
                        "3. **Targeted Review:** Look up only what you get wrong.\n\n" +
                        "What is the single highest-priority topic you should start with right now?",
                responseSource = AIResponseSource.LOCAL_FALLBACK,
                suggestedActionType = "CREATE_TASK",
                suggestedActionPayload = "High-Yield Exam Practice",
                suggestedActionLabel = "Add Exam Prep Task"
            )
        }

        // 4. Stress / Overwhelm
        if (p.contains("stress") || p.contains("anxious") || p.contains("overwhelm") || p.contains("burnout") || p.contains("tired")) {
            return AIResponse(
                replyText = "Take a slow, deep breath, $studentName. 🌿 You don't have to carry everything all at once.\n\n" +
                        "When you're overwhelmed, the brain needs simplicity: pick just **one single small task** for today. Let everything else wait.\n\n" +
                        "Grab a glass of water, step away from screens for two minutes, and let's take it one step at a time.",
                responseSource = AIResponseSource.LOCAL_FALLBACK,
                suggestedActionType = null,
                suggestedActionPayload = null,
                suggestedActionLabel = null
            )
        }

        // 5. Concept explanations (Data Structures, Math, Coding, etc.)
        if (p.contains("explain") || p.contains("data structure") || p.contains("algorithm") || p.contains("how does") || p.contains("what is") || p.contains("teach me")) {
            return AIResponse(
                replyText = "Great question! When learning new concepts like that, the best way is using the **Feynman Technique**: breaking it down with real-world analogies.\n\n" +
                        "For instance, think of **Data Structures** like organizing your physical room:\n" +
                        "• **Array/List:** A row of numbered lockers side-by-side (fast to access if you know the number).\n" +
                        "• **Stack:** A stack of dinner plates (Last-In, First-Out: you can only grab the top plate).\n" +
                        "• **Queue:** A line at a coffee shop (First-In, First-Out).\n" +
                        "• **Tree/Graph:** A family ancestry chart or web of subway stops.\n\n" +
                        "Which specific concept or problem would you like to dive deeper into?",
                responseSource = AIResponseSource.LOCAL_FALLBACK,
                suggestedActionType = null,
                suggestedActionPayload = null,
                suggestedActionLabel = null
            )
        }

        // 6. Study plan requests
        if (p.contains("study plan") || p.contains("routine") || p.contains("schedule") || p.contains("how to study")) {
            val enrolled = if (context.subjects.isNotEmpty()) context.subjects.joinToString(", ") else "your main subjects"
            return AIResponse(
                replyText = "Let's build a clean, sustainable study routine for $enrolled: 📅\n\n" +
                        "• **Morning (Prime Focus):** 1 deep sprint (45 min) on your hardest subject right after waking up.\n" +
                        "• **Afternoon (Application):** Solve 3–5 active practice problems rather than passive reading.\n" +
                        "• **Night (Memory Lock):** 10 minutes of flashcards or Feynman recall before bed.\n\n" +
                        "Would you like to schedule your primary focus block for today?",
                responseSource = AIResponseSource.LOCAL_FALLBACK,
                suggestedActionType = "CREATE_TASK",
                suggestedActionPayload = "Prime Morning Focus Block",
                suggestedActionLabel = "Add to Today's Plan"
            )
        }

        // 7. General friendly conversational catch-all
        return AIResponse(
            replyText = "I'm right here with you, $studentName! Whether you need to break down a tough subject, build a quick study plan, or just need a push to get started, let me know how I can help.",
            responseSource = AIResponseSource.LOCAL_FALLBACK,
            suggestedActionType = "START_FOCUS",
            suggestedActionPayload = "25",
            suggestedActionLabel = "Start 25-Min Focus Sprint"
        )
    }
}
