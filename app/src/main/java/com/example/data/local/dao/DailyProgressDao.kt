package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.DailyProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyProgressDao {
    @Query("SELECT * FROM daily_progress WHERE dateString = :dateString LIMIT 1")
    fun getProgressForDate(dateString: String): Flow<DailyProgressEntity?>

    @Query("SELECT * FROM daily_progress WHERE dateString = :dateString LIMIT 1")
    suspend fun getProgressForDateOnce(dateString: String): DailyProgressEntity?

    @Query("SELECT * FROM daily_progress ORDER BY dateString DESC LIMIT 7")
    fun getRecentWeekProgress(): Flow<List<DailyProgressEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateProgress(progress: DailyProgressEntity)

    @Update
    suspend fun updateProgress(progress: DailyProgressEntity)
}
