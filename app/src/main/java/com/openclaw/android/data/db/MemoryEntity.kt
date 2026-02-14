package com.openclaw.android.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persistent memory entry — the AI's "second brain".
 * Stores facts, preferences, and context the user wants remembered.
 */
@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,       // "preference", "fact", "person", "habit", "note"
    val key: String,            // short identifier, e.g. "dietary_restriction"
    val value: String,          // the remembered content
    val source: String = "",    // how it was learned: "user_told", "inferred", "tool_result"
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val accessCount: Int = 0,   // how often this memory is retrieved
)
