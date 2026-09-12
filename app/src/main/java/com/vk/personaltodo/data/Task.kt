package com.vk.personaltodo.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val priority: Int = 2, // 1 high, 2 medium, 3 low
    val category: String = "Personal",
    val dueAt: Long? = null,
    val reminderAt: Long? = null,
    val recurrence: String = "None",
    val completed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)
