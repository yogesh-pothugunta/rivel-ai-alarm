package com.example.mission.model

sealed interface MissionState {
    object Idle : MissionState
    data class InProgress(
        val stage: Int = 1,
        val totalStages: Int = 1,
        val description: String,
        val progressPercent: Float = 0f,
        val attempts: Int = 0
    ) : MissionState
    data class Failed(val reason: String, val canRetry: Boolean = true) : MissionState
    object Success : MissionState
}
