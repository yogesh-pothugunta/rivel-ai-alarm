package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.StudyTaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StudyTaskDao {
    @Query("SELECT * FROM study_tasks ORDER BY isCompleted ASC, isPriorityToday DESC, id DESC")
    fun getAllTasks(): Flow<List<StudyTaskEntity>>

    @Query("SELECT * FROM study_tasks WHERE isCompleted = 0 ORDER BY isPriorityToday DESC, id DESC")
    fun getPendingTasks(): Flow<List<StudyTaskEntity>>

    @Query("SELECT * FROM study_tasks WHERE isCompleted = 0 AND isPriorityToday = 1 LIMIT 1")
    fun getTodayPriorityTask(): Flow<StudyTaskEntity?>

    @Query("SELECT * FROM study_tasks WHERE id = :id LIMIT 1")
    suspend fun getTaskById(id: Long): StudyTaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: StudyTaskEntity): Long

    @Update
    suspend fun updateTask(task: StudyTaskEntity)

    @Query("UPDATE study_tasks SET isCompleted = :isCompleted, completedAt = :completedAt WHERE id = :id")
    suspend fun setTaskCompleted(id: Long, isCompleted: Boolean, completedAt: Long?)

    @Query("UPDATE study_tasks SET isPriorityToday = :isPriority WHERE id = :id")
    suspend fun setTaskPriorityToday(id: Long, isPriority: Boolean)

    @Delete
    suspend fun deleteTask(task: StudyTaskEntity)

    @Query("DELETE FROM study_tasks WHERE id = :id")
    suspend fun deleteTaskById(id: Long)
}
