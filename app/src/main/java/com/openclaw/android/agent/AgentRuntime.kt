package com.openclaw.android.agent

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
 * Max tool iterations prevents runaway loops.
 */
class AgentRuntime(
    private val modelRouter: ModelRouter,
    private val toolRegistry: ToolRegistry,
    private val conversationManager: ConversationManager,
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

    private fun emit(event: AgentEvent) {
        _events.value = _events.value + event
    }

    suspend fun sendMessage(
        text: String,
        media: List<ContentPart> = emptyList(),
    ) {
        if (_state.value is AgentState.Running) return

        _state.value = AgentState.Running

        // Add user message to conversation
        conversationManager.addUserMessage(text, media)
        emit(AgentEvent.UserMessage(text, media))

        // Determine if we need vision
        val hasImages = media.any { it.type == "image_base64" }
        val hasAudio = media.any { it.type == "audio_base64" }

        // Select model
        val selection = modelRouter.selectBestModel(preferredModelId, hasImages, hasAudio)
        if (selection == null) {
            val errorMsg = "No LLM provider configured. Please add an API key in Settings."
            emit(AgentEvent.AssistantMessage(errorMsg))
            conversationManager.addAssistantMessage(errorMsg)
            _state.value = AgentState.Idle
            return
        }

        emit(AgentEvent.ModelSelected(selection.modelInfo.displayName))

        // Tool calling loop
        var iterations = 0
        while (iterations < MAX_TOOL_ITERATIONS) {
            iterations++

            val request = ChatRequest(
                model = selection.modelId,
                messages = conversationManager.getMessagesForRequest(),
                tools = if (selection.modelInfo.supportsToolUse) toolRegistry.getDefinitions() else null,
                systemPrompt = systemPrompt,
            )

            var responseText = ""
            var responseToolCalls = listOf<ToolCallRequest>()
            var error: Exception? = null

            _state.value = AgentState.Running

            // Stream the response
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

            // Handle error
            if (error != null) {
                val errorMsg = "Error: ${error!!.message}"
                emit(AgentEvent.Error(errorMsg))
                conversationManager.addAssistantMessage(errorMsg)
                break
            }

            // No tool calls — final response
            if (responseToolCalls.isEmpty()) {
                conversationManager.addAssistantMessage(responseText)
                emit(AgentEvent.AssistantMessage(responseText))
                break
            }

            // Has tool calls — execute them
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

            // Continue loop — send tool results back to LLM
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
- Read and write files in workspace/
- Read shared media from shared/ (shared by the user from other apps)
- Copy files from shared/ to workspace/ for organizing
- Search files by name or content
- Fetch web URLs
- Create directories and organize content

## Rules:
- You can ONLY operate within workspace/ (write) and shared/ (read)
- You cannot execute code, install software, or access the internet beyond fetch_url
- Be concise and helpful
- When the user shares an image, describe what you see and ask how you can help
- When working with files, always confirm actions before deleting

## File paths:
- workspace/ — your working directory (full read/write)
- shared/ — media shared by the user (read-only, can copy to workspace)"""
