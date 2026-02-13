package com.openclaw.android.agent

import com.openclaw.android.data.db.ConversationDao
import com.openclaw.android.data.db.ConversationEntity
import com.openclaw.android.data.db.MessageEntity
import com.openclaw.android.llm.ChatMessage
import com.openclaw.android.llm.ContentPart
import com.openclaw.android.llm.ToolCallRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

class ConversationManager(
    private val dao: ConversationDao,
    private val maxMessages: Int = 50,
) {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val _messages = mutableListOf<ChatMessage>()
    val messages: List<ChatMessage> get() = _messages.toList()

    private var _activeConversationId: String? = null
    val activeConversationId: String? get() = _activeConversationId

    private var _activeSpaceId: String? = null

    // Get all conversations as Flow
    val allConversations: Flow<List<ConversationEntity>> = dao.getAllConversations()

    fun getConversationsForSpace(spaceId: String?): Flow<List<ConversationEntity>> {
        return if (spaceId == null) dao.getGeneralConversations()
        else dao.getConversationsForSpace(spaceId)
    }

    suspend fun setActiveSpace(spaceId: String?) {
        _activeSpaceId = spaceId
    }

    // Start a new conversation
    suspend fun startNewConversation(spaceId: String? = _activeSpaceId): String = mutex.withLock {
        val id = UUID.randomUUID().toString()
        dao.deactivateAll()
        dao.insertConversation(
            ConversationEntity(
                id = id,
                title = "New conversation",
                spaceId = spaceId,
                isActive = true,
            )
        )
        _activeConversationId = id
        _messages.clear()
        id
    }

    // Load an existing conversation
    suspend fun loadConversation(conversationId: String) = mutex.withLock {
        dao.deactivateAll()
        dao.activate(conversationId)
        _activeConversationId = conversationId

        val entities = dao.getMessages(conversationId)
        _messages.clear()
        for (entity in entities) {
            _messages.add(entityToMessage(entity))
        }
    }

    // Ensure there's an active conversation (creates one if needed)
    private suspend fun ensureActiveConversation(): String {
        if (_activeConversationId != null) return _activeConversationId!!
        // Check DB for active
        val active = dao.getActiveConversation()
        if (active != null) {
            _activeConversationId = active.id
            return active.id
        }
        // Create new
        return startNewConversation()
    }

    suspend fun addUserMessage(text: String, media: List<ContentPart> = emptyList()) = mutex.withLock {
        val convId = ensureActiveConversation()

        val parts = mutableListOf<ContentPart>()
        if (text.isNotBlank()) parts.add(ContentPart(type = "text", text = text))
        parts.addAll(media)

        val msg = ChatMessage(role = "user", content = parts)
        _messages.add(msg)

        // Auto-title from first user message
        if (_messages.count { it.role == "user" } == 1 && text.isNotBlank()) {
            val title = text.take(60).let { if (text.length > 60) "$it..." else it }
            dao.updateTitle(convId, title)
        }

        persistMessage(convId, msg)
        trimIfNeeded()
    }

    suspend fun addAssistantMessage(text: String, toolCalls: List<ToolCallRequest>? = null) = mutex.withLock {
        val convId = ensureActiveConversation()
        val parts = if (text.isNotBlank()) listOf(ContentPart(type = "text", text = text)) else emptyList()
        val msg = ChatMessage(role = "assistant", content = parts, toolCalls = toolCalls)
        _messages.add(msg)
        persistMessage(convId, msg)
        trimIfNeeded()
    }

    suspend fun addToolResult(toolCallId: String, toolName: String, result: String) = mutex.withLock {
        val convId = ensureActiveConversation()
        val msg = ChatMessage(
            role = "tool",
            content = listOf(ContentPart(type = "text", text = result)),
            toolCallId = toolCallId,
        )
        _messages.add(msg)
        persistMessage(convId, msg)
        trimIfNeeded()
    }

    suspend fun clear() = mutex.withLock {
        _messages.clear()
        _activeConversationId = null
    }

    suspend fun deleteConversation(conversationId: String) = mutex.withLock {
        dao.deleteConversation(conversationId)
        if (_activeConversationId == conversationId) {
            _messages.clear()
            _activeConversationId = null
        }
    }

    suspend fun renameConversation(conversationId: String, newTitle: String) {
        dao.updateTitle(conversationId, newTitle)
    }

    fun getMessagesForRequest(): List<ChatMessage> = _messages.toList()

    /**
     * Export the conversation as human-readable text for the export_chat tool.
     */
    suspend fun getConversationAsText(): String = mutex.withLock {
        val sb = StringBuilder()
        for (msg in _messages) {
            when (msg.role) {
                "user" -> {
                    sb.appendLine("**User:**")
                    for (part in msg.content) {
                        when (part.type) {
                            "text" -> sb.appendLine(part.text)
                            "image_base64" -> sb.appendLine("[Image attachment]")
                            "audio_base64" -> sb.appendLine("[Audio attachment]")
                        }
                    }
                    sb.appendLine()
                }
                "assistant" -> {
                    sb.appendLine("**Assistant:**")
                    for (part in msg.content) {
                        if (part.type == "text") sb.appendLine(part.text)
                    }
                    msg.toolCalls?.forEach { tc -> sb.appendLine("[Used tool: ${tc.name}]") }
                    sb.appendLine()
                }
                "tool" -> {
                    sb.appendLine("**Tool (${msg.toolCallId}):**")
                    sb.appendLine(msg.content.firstOrNull()?.text?.take(500) ?: "")
                    sb.appendLine()
                }
            }
        }
        sb.toString().trim()
    }

    suspend fun searchConversations(query: String): List<ConversationEntity> {
        return dao.searchConversations(query)
    }

    fun estimateTokens(): Int {
        var tokens = 0
        for (msg in _messages) {
            for (part in msg.content) {
                when (part.type) {
                    "text" -> tokens += (part.text?.length ?: 0) / 4
                    "image_base64" -> tokens += 1000
                    "audio_base64" -> tokens += 500
                }
            }
        }
        return tokens
    }

    private suspend fun persistMessage(conversationId: String, msg: ChatMessage) {
        val entity = MessageEntity(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            role = msg.role,
            contentJson = json.encodeToString(msg.content),
            toolCallId = msg.toolCallId,
            toolCallsJson = msg.toolCalls?.let { json.encodeToString(it) },
            orderIndex = _messages.size - 1,
        )
        dao.insertMessage(entity)
        dao.updateMessageCount(conversationId, _messages.size)
    }

    private fun entityToMessage(entity: MessageEntity): ChatMessage {
        val content = try {
            json.decodeFromString<List<ContentPart>>(entity.contentJson)
        } catch (_: Exception) { emptyList() }

        val toolCalls = entity.toolCallsJson?.let {
            try { json.decodeFromString<List<ToolCallRequest>>(it) } catch (_: Exception) { null }
        }

        return ChatMessage(
            role = entity.role,
            content = content,
            toolCallId = entity.toolCallId,
            toolCalls = toolCalls,
        )
    }

    private fun trimIfNeeded() {
        while (_messages.size > maxMessages) {
            _messages.removeAt(0)
        }
    }
}
