package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.dao.AlarmDao
import com.example.data.local.dao.ChatMessageDao
import com.example.data.local.dao.DailyProgressDao
import com.example.data.local.dao.FocusSessionDao
import com.example.data.local.dao.StudentProfileDao
import com.example.data.local.dao.StudyTaskDao
import com.example.data.local.entity.AlarmEntity
import com.example.data.local.entity.ChatMessageEntity
import com.example.data.local.entity.DailyProgressEntity
import com.example.data.local.entity.FocusSessionEntity
import com.example.data.local.entity.StudentProfileEntity
import com.example.data.local.entity.StudyTaskEntity

@Database(
    entities = [
        StudentProfileEntity::class,
        AlarmEntity::class,
        StudyTaskEntity::class,
        FocusSessionEntity::class,
        DailyProgressEntity::class,
        ChatMessageEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun studentProfileDao(): StudentProfileDao
    abstract fun alarmDao(): AlarmDao
    abstract fun studyTaskDao(): StudyTaskDao
    abstract fun focusSessionDao(): FocusSessionDao
    abstract fun dailyProgressDao(): DailyProgressDao
    abstract fun chatMessageDao(): ChatMessageDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "rivel_student_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
