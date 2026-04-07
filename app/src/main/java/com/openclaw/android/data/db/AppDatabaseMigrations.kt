package com.openclaw.android.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object AppDatabaseMigrations {
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Create spaces table
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `spaces` (
                    `id` TEXT PRIMARY KEY NOT NULL,
                    `name` TEXT NOT NULL,
                    `emoji` TEXT NOT NULL DEFAULT '📁',
                    `description` TEXT NOT NULL DEFAULT '',
                    `systemPrompt` TEXT,
                    `createdAt` INTEGER NOT NULL,
                    `lastUsedAt` INTEGER NOT NULL DEFAULT 0,
                    `sortOrder` INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )

            // Create per_chat_settings table
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `per_chat_settings` (
                    `conversationId` TEXT PRIMARY KEY NOT NULL,
                    `temperature` REAL NOT NULL DEFAULT 0.7,
                    `topK` INTEGER NOT NULL DEFAULT 40,
                    `topP` REAL NOT NULL DEFAULT 0.95,
                    `maxTokens` INTEGER NOT NULL DEFAULT 4096,
                    `preferredModelId` TEXT,
                    `enabledTools` TEXT,
                    FOREIGN KEY(`conversationId`) REFERENCES `conversations`(`id`) ON DELETE CASCADE
                )
                """.trimIndent()
            )

            // Create index for per_chat_settings
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_per_chat_settings_conversationId` ON `per_chat_settings` (`conversationId`)"
            )
        }
    }
}
