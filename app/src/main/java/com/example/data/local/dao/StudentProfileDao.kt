package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.StudentProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StudentProfileDao {
    @Query("SELECT * FROM student_profile WHERE id = 1 LIMIT 1")
    fun getProfile(): Flow<StudentProfileEntity?>

    @Query("SELECT * FROM student_profile WHERE id = 1 LIMIT 1")
    suspend fun getProfileOnce(): StudentProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateProfile(profile: StudentProfileEntity)

    @Update
    suspend fun updateProfile(profile: StudentProfileEntity)
}
