package com.example.alarm.model

enum class MissionType(val displayName: String, val description: String) {
    NONE("Simple Dismiss", "Normal dismissal without puzzle"),
    BRAIN("Brain Challenge", "Math arithmetic, pattern memory, or logic challenge to activate your brain"),
    OBJECT("Object Mission", "Physically find and photograph a target object with camera"),
    MOVEMENT("Movement Mission", "Walk a required step count to wake up your body"),
    MULTI_STAGE("Multi-Stage Protocol", "Movement step check + Brain challenge combo")
}

enum class MissionDifficulty(val displayName: String, val levelNumber: Int) {
    EASY("Easy (Gentle wake)", 1),
    MEDIUM("Medium (Focused)", 2),
    HARD("Hard (Maximum wakefulness)", 3),
    ADAPTIVE("Adaptive (Learns from you)", 0)
}
