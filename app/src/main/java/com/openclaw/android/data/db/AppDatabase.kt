package com.openclaw.android.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ConversationEntity::class, MessageEntity::class, MemoryEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun memoryDao(): MemoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** v1 → v2: adds the memories table for the AI memory system. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `memories` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `category` TEXT NOT NULL,
                        `key` TEXT NOT NULL,
                        `value` TEXT NOT NULL,
                        `source` TEXT NOT NULL DEFAULT '',
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `accessCount` INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "openclaw.db",
                )
                .addMigrations(MIGRATION_1_2)
                .build()
                .also { INSTANCE = it }
            }
        }
    }
}
