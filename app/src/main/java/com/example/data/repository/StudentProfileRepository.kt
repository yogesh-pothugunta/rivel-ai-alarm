package com.example.data.repository

import com.example.data.local.dao.StudentProfileDao
import com.example.data.local.entity.StudentProfileEntity
import kotlinx.coroutines.flow.Flow

class StudentProfileRepository(private val dao: StudentProfileDao) {
    val profile: Flow<StudentProfileEntity?> = dao.getProfile()

    suspend fun getProfileOnce(): StudentProfileEntity? = dao.getProfileOnce()

    suspend fun saveProfile(profile: StudentProfileEntity) {
        dao.insertOrUpdateProfile(profile)
    }

    suspend fun completeOnboarding(
        name: String,
        educationLevel: String,
        course: String,
        year: String,
        mainGoal: String,
        wakeTime: String,
        sleepTime: String,
        subjects: String,
        upcomingExam: String
    ) {
        val existing = dao.getProfileOnce()
        val entity = (existing ?: StudentProfileEntity()).copy(
            id = 1,
            name = name.trim(),
            educationLevel = educationLevel.trim(),
            course = course.trim(),
            year = year.trim(),
            mainGoal = mainGoal.trim(),
            preferredWakeTime = wakeTime,
            preferredSleepTime = sleepTime,
            subjects = subjects.trim(),
            upcomingExam = upcomingExam.trim(),
            isOnboarded = true,
            updatedAt = System.currentTimeMillis()
        )
        dao.insertOrUpdateProfile(entity)
    }
}
