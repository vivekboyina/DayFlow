package com.vk.personaltodo.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Task::class, Expense::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun expenseDao(): ExpenseDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // To prevent crashes for existing users whose version 1 database did not have createdAt,
                // we safely add the column with a default value.
                // We wrap it in a try-catch or check if column exists, but SQLite ALTER TABLE ADD COLUMN
                // is safe if we know it's missing in v1. Since SQLite doesn't have "ADD COLUMN IF NOT EXISTS",
                // we just execute it. If it fails (e.g. they already had it), we catch the exception.
                try {
                    db.execSQL("ALTER TABLE tasks ADD COLUMN createdAt INTEGER NOT NULL DEFAULT ${System.currentTimeMillis()}")
                } catch (_: Exception) {
                    // Column might already exist if they had a weird schema state.
                }
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `expenses` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `amount` REAL NOT NULL, `category` TEXT NOT NULL, `description` TEXT NOT NULL, `date` INTEGER NOT NULL)")
            }
        }
    }
}
