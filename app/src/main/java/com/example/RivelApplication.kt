package com.example

import android.app.Application
import com.example.alarm.scheduler.AndroidAlarmScheduler
import com.example.alarm.service.AlarmSoundPlayer
import com.example.data.local.AppDatabase
import com.example.data.repository.AlarmRepository
import com.example.data.repository.ChatMessageRepository
import com.example.data.repository.DailyProgressRepository
import com.example.data.repository.FocusSessionRepository
import com.example.data.repository.StudentProfileRepository
import com.example.data.repository.StudyTaskRepository
import com.example.ai.service.RivelAIService

class RivelApplication : Application() {

    val database by lazy { AppDatabase.getDatabase(this) }

    val studentProfileRepository by lazy { StudentProfileRepository(database.studentProfileDao()) }
    val alarmRepository by lazy { AlarmRepository(database.alarmDao()) }
    val studyTaskRepository by lazy { StudyTaskRepository(database.studyTaskDao()) }
    val focusSessionRepository by lazy { FocusSessionRepository(database.focusSessionDao()) }
    val dailyProgressRepository by lazy { DailyProgressRepository(database.dailyProgressDao()) }
    val chatMessageRepository by lazy { ChatMessageRepository(database.chatMessageDao()) }

    val alarmScheduler by lazy { AndroidAlarmScheduler(this) }
    val alarmSoundPlayer by lazy { AlarmSoundPlayer(this) }
    val aiService by lazy { RivelAIService() }

    override fun onCreate() {
        super.onCreate()
        com.example.ai.BackendConfig.init(this)
    }
}
