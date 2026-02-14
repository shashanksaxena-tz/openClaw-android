package com.openclaw.android.agent

import com.openclaw.android.data.SpaceManager
import com.openclaw.android.llm.*
import com.openclaw.android.sandbox.SandboxedFileSystem
import com.openclaw.android.tools.ToolRegistry
import com.openclaw.android.tools.ToolResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class AgentRuntime(
    private val modelRouter: ModelRouter,
    private val toolRegistry: ToolRegistry,
    private val conversationManager: ConversationManager,
    private val spaceManager: SpaceManager? = null,
    private val sandboxedFileSystem: SandboxedFileSystem? = null,
    private val onBackgroundResponse: ((String) -> Unit)? = null,
) {
    companion object {
        const val MAX_TOOL_ITERATIONS = 10
        const val TOOL_TIMEOUT_MS = 30_000L
        private val BLOCKED_TOOLS = setOf(
            "run_code", "execute_code", "execute", "run_command", "shell",
            "bash", "python", "execute_python", "run_script", "terminal",
            "code_execution", "run_program",
        )
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

    val activeModelName: String?
        get() = preferredModelId?.let { id ->
            modelRouter.resolveModel(id)?.modelInfo?.displayName
        }

    // Cancel support
    private var currentJob: Job? = null
    private var _isCancelled = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun setActiveSpace(spaceId: String?) {
        _activeSpaceId = spaceId
        sandboxedFileSystem?.setActiveSpace(spaceId)
        // Launch in the agent's own scope to avoid runBlocking deadlocks
        scope.launch { conversationManager.setActiveSpace(spaceId) }
    }

    fun cancel() {
        _isCancelled = true
        currentJob?.cancel()
        _state.value = AgentState.Idle
    }

    fun destroy() {
        scope.cancel()
    }

    private fun emit(event: AgentEvent) {
        _events.update { it + event }
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
            if (space.description.isNotBlank()) appendLine("Description: ${space.description}")
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
            appendLine("All file operations now automatically route to this space's directory.")
        }
    }

    private var lastUserText: String = ""
    private var lastUserMedia: List<ContentPart> = emptyList()

    suspend fun retryLastMessage() {
        if (lastUserText.isNotBlank() || lastUserMedia.isNotEmpty()) {
            // Remove the last error event
            _events.value = _events.value.dropLastWhile { it is AgentEvent.Error }
            sendMessage(lastUserText, lastUserMedia, isRetry = true)
        }
    }

    suspend fun sendMessage(
        text: String,
        media: List<ContentPart> = emptyList(),
        isRetry: Boolean = false,
    ) {
        if (_state.value is AgentState.Running) return

        _isCancelled = false
        _state.value = AgentState.Running
        lastUserText = text
        lastUserMedia = media

        if (!isRetry) {
            conversationManager.addUserMessage(text, media)
            emit(AgentEvent.UserMessage(text, media))
        }

        val hasImages = media.any { it.type == "image_base64" }
        val hasAudio = media.any { it.type == "audio_base64" }

        val selection = modelRouter.selectBestModel(preferredModelId, hasImages, hasAudio)
        if (selection == null) {
            val error = ErrorHandler.UserError(
                title = "No AI configured",
                message = "Add an API key in Settings to start chatting.",
                action = ErrorHandler.ErrorAction.OpenSettings,
            )
            emit(AgentEvent.Error(ErrorHandler.formatForChat(error)))
            conversationManager.addAssistantMessage(ErrorHandler.formatForChat(error))
            _state.value = AgentState.Idle
            return
        }

        emit(AgentEvent.ModelSelected(selection.modelInfo.displayName))

        val fullSystemPrompt = buildSystemPrompt()
        var iterations = 0

        currentJob = scope.launch {
            try {
                while (iterations < MAX_TOOL_ITERATIONS && !_isCancelled) {
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

                    val streamBuffer = StringBuilder()
                    emit(AgentEvent.StreamStart)

                    selection.provider.chatCompletion(
                        request = request,
                        onChunk = { chunk ->
                            if (_isCancelled) return@chatCompletion
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

                    if (_isCancelled) {
                        if (streamBuffer.isNotEmpty()) {
                            val partial = streamBuffer.toString()
                            conversationManager.addAssistantMessage("$partial\n\n[Generation stopped]")
                            emit(AgentEvent.AssistantMessage("$partial\n\n*[Generation stopped]*"))
                        }
                        break
                    }

                    if (error != null) {
                        val userError = ErrorHandler.mapError(error!!)
                        val errorMsg = ErrorHandler.formatForChat(userError)
                        emit(AgentEvent.Error(errorMsg))
                        conversationManager.addAssistantMessage(errorMsg)
                        break
                    }

                    if (responseToolCalls.isEmpty()) {
                        conversationManager.addAssistantMessage(responseText)
                        emit(AgentEvent.AssistantMessage(responseText))
                        onBackgroundResponse?.invoke(responseText)
                        break
                    }

                    conversationManager.addAssistantMessage(responseText, responseToolCalls)
                    if (responseText.isNotBlank()) emit(AgentEvent.AssistantMessage(responseText))

                    for (toolCall in responseToolCalls) {
                        if (_isCancelled) break
                        emit(AgentEvent.ToolCallStart(toolCall.name, toolCall.arguments.toString()))
                        _state.value = AgentState.ExecutingTool(toolCall.name)

                        // Block code execution tool calls
                        val result = if (toolCall.name.lowercase() in BLOCKED_TOOLS) {
                            ToolResult.error("Code execution is not available. I can only work with files, web search, and other safe tools.")
                        } else {
                            val tool = toolRegistry.get(toolCall.name)
                            if (tool != null) {
                                try {
                                    withTimeoutOrNull(TOOL_TIMEOUT_MS) {
                                        tool.execute(toolCall.arguments)
                                    } ?: ToolResult.error("Tool '${toolCall.name}' timed out after ${TOOL_TIMEOUT_MS / 1000}s")
                                } catch (e: Exception) {
                                    ToolResult.error("Tool execution failed: ${e.message}")
                                }
                            } else {
                                ToolResult.error("Unknown tool: ${toolCall.name}")
                            }
                        }

                        conversationManager.addToolResult(toolCall.id, toolCall.name, result.output)
                        emit(AgentEvent.ToolCallResult(toolCall.name, result.output, result.isError))
                    }
                }

                if (iterations >= MAX_TOOL_ITERATIONS && !_isCancelled) {
                    val msg = "Reached maximum tool iterations ($MAX_TOOL_ITERATIONS). Stopping."
                    emit(AgentEvent.Error(msg))
                    conversationManager.addAssistantMessage(msg)
                }
            } catch (e: CancellationException) {
                // Job was cancelled
            } catch (e: Exception) {
                val userError = ErrorHandler.mapError(e)
                emit(AgentEvent.Error(ErrorHandler.formatForChat(userError)))
            } finally {
                _state.value = AgentState.Idle
            }
        }

        // Job runs in background, UI observes state via StateFlow
    }

    suspend fun clearConversation() {
        cancel()
        conversationManager.clear()
        _events.value = emptyList()
    }

    suspend fun startNewConversation() {
        cancel()
        conversationManager.startNewConversation(_activeSpaceId)
        _events.value = emptyList()
    }

    suspend fun loadConversation(conversationId: String) {
        cancel()
        conversationManager.loadConversation(conversationId)
        _events.value = emptyList()
        // Rebuild events from loaded messages
        for (msg in conversationManager.messages) {
            when (msg.role) {
                "user" -> emit(AgentEvent.UserMessage(
                    text = msg.content.firstOrNull { it.type == "text" }?.text ?: "",
                    media = msg.content.filter { it.type != "text" },
                ))
                "assistant" -> {
                    val text = msg.content.firstOrNull { it.type == "text" }?.text ?: ""
                    if (text.isNotBlank()) emit(AgentEvent.AssistantMessage(text))
                    msg.toolCalls?.forEach { tc ->
                        emit(AgentEvent.ToolCallStart(tc.name, tc.arguments.toString()))
                    }
                }
                "tool" -> emit(AgentEvent.ToolCallResult(
                    msg.toolCallId ?: "unknown",
                    msg.content.firstOrNull()?.text ?: "",
                    msg.content.firstOrNull()?.text?.startsWith("Error:") == true,
                ))
            }
        }
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
- You CANNOT and MUST NOT execute code, run scripts, or call any code execution tool. You are a file management and conversation assistant only.
- You cannot install software or access the internet beyond web_search and fetch_url
- Be concise and helpful
- When the user shares an image, describe what you see and ask how you can help
- When the user shares a URL or link, use the web_search tool to fetch and analyze its content
- When working with files, always confirm actions before deleting
- Create files in formats the user requests (.md, .html, .txt, etc.)

## File paths:
- workspace/ — your working directory (full read/write)
- shared/ — media shared by the user (read-only, can copy to workspace)"""
