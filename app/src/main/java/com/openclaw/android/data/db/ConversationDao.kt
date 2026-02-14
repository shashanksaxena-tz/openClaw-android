package com.openclaw.android.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    // Conversations
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun getAllConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE spaceId = :spaceId ORDER BY updatedAt DESC")
    fun getConversationsForSpace(spaceId: String): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE spaceId IS NULL ORDER BY updatedAt DESC")
    fun getGeneralConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getConversation(id: String): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveConversation(): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: ConversationEntity)

    @Update
    suspend fun updateConversation(conversation: ConversationEntity)

    @Query("UPDATE conversations SET isActive = 0")
    suspend fun deactivateAll()

    @Query("UPDATE conversations SET isActive = 1, updatedAt = :now WHERE id = :id")
    suspend fun activate(id: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE conversations SET title = :title, updatedAt = :now WHERE id = :id")
    suspend fun updateTitle(id: String, title: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE conversations SET messageCount = :count, updatedAt = :now WHERE id = :id")
    suspend fun updateMessageCount(id: String, count: Int, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversation(id: String)

    @Query("DELETE FROM conversations WHERE spaceId = :spaceId")
    suspend fun deleteConversationsForSpace(spaceId: String)

    @Query("SELECT COUNT(*) FROM conversations WHERE spaceId = :spaceId")
    suspend fun getConversationCountForSpace(spaceId: String): Int

    // Messages
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY orderIndex ASC")
    suspend fun getMessages(conversationId: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY orderIndex ASC")
    fun getMessagesFlow(conversationId: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteMessages(conversationId: String)

    @Query("SELECT COUNT(*) FROM messages WHERE conversationId = :conversationId")
    suspend fun getMessageCount(conversationId: String): Int

    // Expiry
    @Query("SELECT * FROM conversations WHERE updatedAt < :cutoff")
    suspend fun getExpiredConversations(cutoff: Long): List<ConversationEntity>

    // Search
    @Query("SELECT DISTINCT c.* FROM conversations c INNER JOIN messages m ON c.id = m.conversationId WHERE m.contentJson LIKE '%' || :query || '%' ORDER BY c.updatedAt DESC LIMIT 20")
    suspend fun searchConversations(query: String): List<ConversationEntity>
}
