package com.openclaw.android.data.db

import androidx.room.*

@Entity(
    tableName = "messages",
    foreignKeys = [ForeignKey(
        entity = ConversationEntity::class,
        parentColumns = ["id"],
        childColumns = ["conversationId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("conversationId")]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val role: String, // "user", "assistant", "tool"
    val contentJson: String, // Serialized List<ContentPart>
    val toolCallId: String? = null,
    val toolCallsJson: String? = null, // Serialized List<ToolCallRequest>
    val timestamp: Long = System.currentTimeMillis(),
    val orderIndex: Int = 0,
)
