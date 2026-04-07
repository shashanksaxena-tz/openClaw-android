package com.openclaw.android.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "per_chat_settings")
data class PerChatSettingsEntity(
    @PrimaryKey
    val conversationId: String,

    val temperature: Float = 0.7f,
    val topK: Int = 40,
    val topP: Float = 0.95f,
    val maxTokens: Int = 4096,
    val preferredModelId: String? = null,
    val enabledTools: String? = null  // comma-separated tool names
)
