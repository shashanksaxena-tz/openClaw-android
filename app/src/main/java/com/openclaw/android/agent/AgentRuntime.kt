package com.openclaw.android.agent

import com.openclaw.android.data.SpaceManager
import com.openclaw.android.llm.*
import com.openclaw.android.tools.ToolRegistry
import com.openclaw.android.tools.ToolResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The agent runtime: orchestrates the LLM <-> Tool loop.
 *
 * Flow:
 * 1. User sends message (text + optional media)
 * 2. Runtime sends to LLM with tool definitions
 * 3. If LLM returns tool calls, execute them and feed results back
 * 4. Repeat until LLM returns a text response (no tool calls)
 * 5. Display final response to user
 *
 * Supports spaces/projects with per-space context and knowledge bases.
 */
class AgentRuntime(
    private val modelRouter: ModelRouter,
    private val toolRegistry: ToolRegistry,
    private val conversationManager: ConversationManager,
    private val spaceManager: SpaceManager? = null,
) {
    companion object {
        const val MAX_TOOL_ITERATIONS = 10
    }

    private val _state = MutableStateFlow<AgentState>(AgentState.Idle)
    val state: StateFlow<AgentState> = _state.asStateFlow()

    private val _events = MutableStateFlow<List<AgentEvent>>(emptyList())
    val events: StateFlow<List<AgentEvent>> = _events.asStateFlow()

    var systemPrompt: String = DEFAULT_SYSTEM_PROMPT
    var preferredModelId: String? = null

    private var _activeSpaceId: String? = null
    val activeSpaceName: String?
        get() = _activeSpaceId?.let { id ->
            spaceManager?.getSpaces()?.find { it.id == id }?.let { "${it.emoji} ${it.name}" }
        }

    fun setActiveSpace(spaceId: String?) {
        _activeSpaceId = spaceId
    }

    private fun emit(event: AgentEvent) {
        _events.value = _events.value + event
    }

    private fun buildSystemPrompt(): String {
        val base = systemPrompt
        val spaceId = _activeSpaceId ?: return base

        val space = spaceManager?.getSpaces()?.find { it.id == spaceId } ?: return base
        val knowledge = spaceManager?.getKnowledgeBase(spaceId) ?: ""

        return buildString {
            appendLine(base)
            appendLine()
            appendLine("## Active Space: ${space.emoji} ${space.name}")
            if (space.description.isNotBlank()) {
                appendLine("Description: ${space.description}")
            }
            space.systemPrompt?.let {
                appendLine()
                appendLine("### Space-specific instructions:")
                appendLine(it)
            }
            if (knowledge.isNotBlank()) {
                appendLine()
                appendLine("### Knowledge base for this space:")
                appendLine(knowledge)
            }
            appendLine()
            appendLine("Files for this space are in workspace/spaces/${space.id}/files/")
            appendLine("Use that directory when the user asks to create or find files for this project.")
        }
    }

    suspend fun sendMessage(
        text: String,
        media: List<ContentPart> = emptyList(),
    ) {
        if (_state.value is AgentState.Running) return

        _state.value = AgentState.Running

        conversationManager.addUserMessage(text, media)
        emit(AgentEvent.UserMessage(text, media))

        val hasImages = media.any { it.type == "image_base64" }
        val hasAudio = media.any { it.type == "audio_base64" }

        val selection = modelRouter.selectBestModel(preferredModelId, hasImages, hasAudio)
        if (selection == null) {
            val errorMsg = "No LLM provider configured. Please add an API key in Settings."
            emit(AgentEvent.AssistantMessage(errorMsg))
            conversationManager.addAssistantMessage(errorMsg)
            _state.value = AgentState.Idle
            return
        }

        emit(AgentEvent.ModelSelected(selection.modelInfo.displayName))

        val fullSystemPrompt = buildSystemPrompt()

        var iterations = 0
        while (iterations < MAX_TOOL_ITERATIONS) {
            iterations++

            val request = ChatRequest(
                model = selection.modelId,
                messages = conversationManager.getMessagesForRequest(),
                tools = if (selection.modelInfo.supportsToolUse) toolRegistry.getDefinitions() else null,
                systemPrompt = fullSystemPrompt,
            )

            var responseText = ""
            var responseToolCalls = listOf<ToolCallRequest>()
            var error: Exception? = null

            _state.value = AgentState.Running

            val streamBuffer = StringBuilder()
            emit(AgentEvent.StreamStart)

            selection.provider.chatCompletion(
                request = request,
                onChunk = { chunk ->
                    streamBuffer.append(chunk)
                    emit(AgentEvent.StreamChunk(chunk, streamBuffer.toString()))
                },
                onToolCall = { /* collected in onDone */ },
                onDone = { response ->
                    responseText = response.content
                    responseToolCalls = response.toolCalls
                    response.usage?.let { emit(AgentEvent.TokenUsage(it)) }
                },
                onError = { e -> error = e },
            )

            emit(AgentEvent.StreamEnd)

            if (error != null) {
                val errorMsg = "Error: ${error!!.message}"
                emit(AgentEvent.Error(errorMsg))
                conversationManager.addAssistantMessage(errorMsg)
                break
            }

            if (responseToolCalls.isEmpty()) {
                conversationManager.addAssistantMessage(responseText)
                emit(AgentEvent.AssistantMessage(responseText))
                break
            }

            conversationManager.addAssistantMessage(responseText, responseToolCalls)
            if (responseText.isNotBlank()) {
                emit(AgentEvent.AssistantMessage(responseText))
            }

            for (toolCall in responseToolCalls) {
                emit(AgentEvent.ToolCallStart(toolCall.name, toolCall.arguments.toString()))
                _state.value = AgentState.ExecutingTool(toolCall.name)

                val tool = toolRegistry.get(toolCall.name)
                val result = if (tool != null) {
                    try {
                        tool.execute(toolCall.arguments)
                    } catch (e: Exception) {
                        ToolResult.error("Tool execution failed: ${e.message}")
                    }
                } else {
                    ToolResult.error("Unknown tool: ${toolCall.name}")
                }

                conversationManager.addToolResult(toolCall.id, toolCall.name, result.output)
                emit(AgentEvent.ToolCallResult(toolCall.name, result.output, result.isError))
            }
        }

        if (iterations >= MAX_TOOL_ITERATIONS) {
            val msg = "Reached maximum tool iterations ($MAX_TOOL_ITERATIONS). Stopping."
            emit(AgentEvent.Error(msg))
            conversationManager.addAssistantMessage(msg)
        }

        _state.value = AgentState.Idle
    }

    fun clearConversation() {
        conversationManager.clear()
        _events.value = emptyList()
    }
}

sealed class AgentState {
    data object Idle : AgentState()
    data object Running : AgentState()
    data class ExecutingTool(val toolName: String) : AgentState()
}

sealed class AgentEvent {
    data class UserMessage(val text: String, val media: List<ContentPart> = emptyList()) : AgentEvent()
    data class AssistantMessage(val text: String) : AgentEvent()
    data class ModelSelected(val modelName: String) : AgentEvent()
    data object StreamStart : AgentEvent()
    data class StreamChunk(val chunk: String, val fullText: String) : AgentEvent()
    data object StreamEnd : AgentEvent()
    data class ToolCallStart(val toolName: String, val arguments: String) : AgentEvent()
    data class ToolCallResult(val toolName: String, val result: String, val isError: Boolean) : AgentEvent()
    data class TokenUsage(val usage: com.openclaw.android.llm.TokenUsage) : AgentEvent()
    data class Error(val message: String) : AgentEvent()
}

const val DEFAULT_SYSTEM_PROMPT = """You are OpenClaw, a personal AI assistant running on Android.

You have access to a sandboxed workspace folder where you can create, read, edit, and organize files.
Users can share media (images, audio, video, documents) with you from other apps.

## Your capabilities:
- Read and write files in workspace/ (supports .txt, .md, .html, .json, .csv and more)
- Read shared media from shared/ (shared by the user from other apps)
- Copy files from shared/ to workspace/ for organizing
- Search files by name or content
- Search the web for current information
- Share files from workspace with other apps
- Export conversations as documents
- Fetch web URLs and extract content

## Rules:
- You can ONLY operate within workspace/ (write) and shared/ (read)
- You cannot execute code, install software, or access the internet beyond web_search and fetch_url
- Be concise and helpful
- When the user shares an image, describe what you see and ask how you can help
- When the user shares a URL or link, use the web_search tool to fetch and analyze its content
- When working with files, always confirm actions before deleting
- Create files in formats the user requests (.md, .html, .txt, etc.)

## File paths:
- workspace/ — your working directory (full read/write)
- shared/ — media shared by the user (read-only, can copy to workspace)"""
