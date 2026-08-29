package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.FocusSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FocusSessionDao {
    @Query("SELECT * FROM focus_sessions ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<FocusSessionEntity>>

    @Query("SELECT * FROM focus_sessions WHERE timestamp >= :sinceTimestamp ORDER BY timestamp DESC")
    fun getRecentSessions(sinceTimestamp: Long): Flow<List<FocusSessionEntity>>

    @Query("SELECT SUM(actualMinutes) FROM focus_sessions WHERE timestamp >= :sinceTimestamp")
    fun getTotalMinutesSince(sinceTimestamp: Long): Flow<Int?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: FocusSessionEntity): Long
}
