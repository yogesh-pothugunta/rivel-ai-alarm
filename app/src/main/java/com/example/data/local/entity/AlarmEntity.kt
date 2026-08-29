package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hour: Int,
    val minute: Int,
    val daysOfWeek: String, // Comma separated: "MON,TUE,WED,THU,FRI" or "ALL" or "ONCE"
    val label: String,
    val isEnabled: Boolean = true,
    val missionType: String = "BRAIN", // NONE, OBJECT, MOVEMENT, BRAIN, MULTI_STAGE
    val missionDifficulty: String = "MEDIUM", // EASY, MEDIUM, HARD, ADAPTIVE
    val targetObject: String = "Book", // For object mission (e.g. Book, Bottle, Toothbrush, Backpack, Keys)
    val movementTargetSteps: Int = 20, // For movement mission
    val snoozeMinutes: Int = 5,
    val isVibrationEnabled: Boolean = true,
    val isEscalationEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
