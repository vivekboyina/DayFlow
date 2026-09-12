package com.vk.personaltodo.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY completed ASC, CASE WHEN dueAt IS NULL THEN 1 ELSE 0 END, dueAt ASC")
    fun observeAll(): Flow<List<Task>>

    @Insert
    suspend fun insert(task: Task): Long

    @Update
    suspend fun update(task: Task)

    @Delete
    suspend fun delete(task: Task)

    @Query("DELETE FROM tasks")
    suspend fun deleteAll()

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun get(id: Long): Task?

    @Query("SELECT * FROM tasks WHERE title = :title AND recurrence = :recurrence AND completed = 0 LIMIT 1")
    suspend fun findFutureRecurring(title: String, recurrence: String): Task?
}
