package com.openclaw.android.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PerChatSettingsDao {
    @Query("SELECT * FROM per_chat_settings WHERE conversationId = :conversationId")
    suspend fun getSettings(conversationId: String): PerChatSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSettings(settings: PerChatSettingsEntity)

    @Query("DELETE FROM per_chat_settings WHERE conversationId = :conversationId")
    suspend fun deleteSettings(conversationId: String)

    @Query("SELECT * FROM per_chat_settings")
    fun getAllSettings(): Flow<List<PerChatSettingsEntity>>
}
