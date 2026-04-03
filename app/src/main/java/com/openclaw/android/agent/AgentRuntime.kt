package com.openclaw.android.agent

import com.openclaw.android.PermissionManager
import com.openclaw.android.data.SpaceManager
import com.openclaw.android.llm.*
import com.openclaw.android.llm.LlamaProvider
import com.openclaw.android.sandbox.SandboxedFileSystem
import com.openclaw.android.tools.ToolRegistry
import com.openclaw.android.tools.ToolResult
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

class AgentRuntime(
    private val modelRouter: ModelRouter,
    private val toolRegistry: ToolRegistry,
    private val conversationManager: ConversationManager,
    private val spaceManager: SpaceManager? = null,
    private val sandboxedFileSystem: SandboxedFileSystem? = null,
    private val onBackgroundResponse: ((String) -> Unit)? = null,
    val permissionManager: PermissionManager? = null,
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
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e("AgentRuntime", "Uncaught coroutine exception", throwable)
        val ex = if (throwable is Exception) throwable else RuntimeException("Internal error: ${throwable.message}", throwable)
        val userError = ErrorHandler.mapError(ex)
        emit(AgentEvent.Error(ErrorHandler.formatForChat(userError)))
        _state.value = AgentState.Idle
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + exceptionHandler)

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
        _events.update { events ->
            // Cap event list to prevent OOM in long sessions.
            // Keep the last 500 events — enough for ~50 tool-call rounds.
            val list = events + event
            if (list.size > 500) list.drop(list.size - 500) else list
        }
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

        try {
            if (!isRetry) {
                conversationManager.addUserMessage(text, media)
                emit(AgentEvent.UserMessage(text, media))
            }
        } catch (e: Exception) {
            Log.e("AgentRuntime", "Failed to save user message", e)
            // Still show the message in UI even if DB save failed
            if (!isRetry) emit(AgentEvent.UserMessage(text, media))
        }

        val hasImages = media.any { it.type == "image_base64" }
        val hasAudio = media.any { it.type == "audio_base64" }

        val selection: ModelRouter.ModelSelection?
        try {
            selection = modelRouter.selectBestModel(preferredModelId, hasImages, hasAudio)
        } catch (e: Exception) {
            Log.e("AgentRuntime", "Model selection failed", e)
            emit(AgentEvent.Error("Something went wrong: ${e.message ?: "model selection failed"}"))
            _state.value = AgentState.Idle
            return
        }

        if (selection == null) {
            val error = ErrorHandler.UserError(
                title = "No AI available",
                message = "No model is available. Download a local model in Settings for offline use, or add a cloud API key.",
                action = ErrorHandler.ErrorAction.OpenSettings,
            )
            emit(AgentEvent.Error(ErrorHandler.formatForChat(error)))
            try { conversationManager.addAssistantMessage(ErrorHandler.formatForChat(error)) } catch (_: Exception) {}
            _state.value = AgentState.Idle
            return
        }

        emit(AgentEvent.ModelSelected(selection.modelInfo.displayName))

        val fullSystemPrompt = buildSystemPrompt()
        var iterations = 0
        // Track the active provider — may switch from local to cloud on escalation
        var activeSelection: ModelRouter.ModelSelection = selection

        currentJob = scope.launch {
            try {
                while (iterations < MAX_TOOL_ITERATIONS && !_isCancelled) {
                    iterations++

                    val request = ChatRequest(
                        model = activeSelection.modelId,
                        messages = conversationManager.getMessagesForRequest(),
                        tools = if (activeSelection.modelInfo.supportsToolUse) toolRegistry.getDefinitions() else null,
                        systemPrompt = fullSystemPrompt,
                    )

                    var responseText = ""
                    var responseToolCalls = listOf<ToolCallRequest>()
                    var error: Exception? = null

                    val streamBuffer = StringBuilder()
                    val chunkBatch = StringBuilder()
                    val lastEmitTime = AtomicLong(System.currentTimeMillis())
                    emit(AgentEvent.StreamStart)

                    activeSelection.provider.chatCompletion(
                        request = request,
                        onChunk = { chunk ->
                            if (_isCancelled) return@chatCompletion
                            streamBuffer.append(chunk)
                            chunkBatch.append(chunk)
                            // Throttle UI updates: batch chunks and emit at most every 50ms
                            // to prevent OOM from rapid token-by-token emissions (local models).
                            // Emits the accumulated batch so no tokens are lost from streaming display.
                            val now = System.currentTimeMillis()
                            if (now - lastEmitTime.get() >= 50 || chunk.contains("\n")) {
                                lastEmitTime.set(now)
                                emit(AgentEvent.StreamChunk(chunkBatch.toString()))
                                chunkBatch.setLength(0)
                            }
                        },
                        onToolCall = { /* collected in onDone */ },
                        onDone = { response ->
                            responseText = response.content
                            responseToolCalls = response.toolCalls
                            response.usage?.let { emit(AgentEvent.TokenUsage(it)) }
                        },
                        onError = { e -> error = e },
                    )

                    // Flush any remaining batched chunks
                    if (chunkBatch.isNotEmpty()) {
                        emit(AgentEvent.StreamChunk(chunkBatch.toString()))
                        chunkBatch.setLength(0)
                    }
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
                        // If a cloud model failed due to network, fall back to local model
                        val isNetworkError = error is java.net.UnknownHostException ||
                            error is java.net.SocketTimeoutException ||
                            error is java.net.ConnectException ||
                            error?.message?.contains("Unable to resolve host", ignoreCase = true) == true ||
                            error?.message?.contains("timeout", ignoreCase = true) == true
                        if (isNetworkError && !activeSelection.isLocal) {
                            val localFallback = modelRouter.selectBestModel(null, hasImages = false, hasAudio = false)
                            if (localFallback != null && localFallback.isLocal) {
                                emit(AgentEvent.Escalation(
                                    from = activeSelection.modelInfo.displayName,
                                    to = "${localFallback.modelInfo.displayName} (offline)",
                                ))
                                activeSelection = localFallback
                                emit(AgentEvent.ModelSelected(localFallback.modelInfo.displayName))
                                continue // Retry with local model
                            }
                        }
                        val userError = ErrorHandler.mapError(error!!)
                        val errorMsg = ErrorHandler.formatForChat(userError)
                        emit(AgentEvent.Error(errorMsg))
                        conversationManager.addAssistantMessage(errorMsg)
                        break
                    }

                    // ── Escalation: local model says it can't handle this ────
                    if (activeSelection.isLocal &&
                        responseText.contains(LlamaProvider.ESCALATION_MARKER)
                    ) {
                        val cloudSelection = modelRouter.selectCloudModel(hasImages, hasAudio)
                        if (cloudSelection != null) {
                            emit(AgentEvent.Escalation(
                                from = activeSelection.modelInfo.displayName,
                                to = cloudSelection.modelInfo.displayName,
                            ))
                            // Clear the escalation response from conversation
                            // so cloud model gets a clean slate
                            activeSelection = cloudSelection
                            emit(AgentEvent.ModelSelected(cloudSelection.modelInfo.displayName))
                            // Loop back to re-send with cloud model
                            continue
                        }
                        // No cloud model available — show what we got
                        val cleaned = responseText
                            .replace(LlamaProvider.ESCALATION_MARKER, "")
                            .trim()
                            .ifBlank { "I need a cloud model for this task, but none is configured. Add an API key in Settings." }
                        try { conversationManager.addAssistantMessage(cleaned) } catch (e: Exception) { Log.e("AgentRuntime", "DB save failed", e) }
                        emit(AgentEvent.AssistantMessage(cleaned))
                        break
                    }

                    if (responseToolCalls.isEmpty()) {
                        try { conversationManager.addAssistantMessage(responseText) } catch (e: Exception) { Log.e("AgentRuntime", "DB save failed", e) }
                        emit(AgentEvent.AssistantMessage(responseText))
                        onBackgroundResponse?.invoke(responseText)
                        break
                    }

                    try { conversationManager.addAssistantMessage(responseText, responseToolCalls) } catch (e: Exception) { Log.e("AgentRuntime", "DB save failed", e) }
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
                                // Check runtime permissions before executing
                                val perms = tool.requiredPermissions
                                val permGranted = if (perms.isNotEmpty() && permissionManager != null) {
                                    permissionManager.ensurePermissions(perms, tool.name)
                                } else true

                                if (!permGranted) {
                                    ToolResult.error("Permission denied. The ${tool.name} tool needs access that was not granted. Please allow the requested permissions and try again.")
                                } else {
                                    try {
                                        withTimeoutOrNull(TOOL_TIMEOUT_MS) {
                                            tool.execute(toolCall.arguments)
                                        } ?: ToolResult.error("Tool '${toolCall.name}' timed out after ${TOOL_TIMEOUT_MS / 1000}s")
                                    } catch (e: Exception) {
                                        ToolResult.error("Tool execution failed: ${e.message}")
                                    }
                                }
                            } else {
                                ToolResult.error("Unknown tool: ${toolCall.name}")
                            }
                        }

                        try { conversationManager.addToolResult(toolCall.id, toolCall.name, result.output) } catch (e: Exception) { Log.e("AgentRuntime", "DB save failed", e) }
                        emit(AgentEvent.ToolCallResult(toolCall.name, result.output, result.isError))
                    }
                }

                if (iterations >= MAX_TOOL_ITERATIONS && !_isCancelled) {
                    val msg = "Reached maximum tool iterations ($MAX_TOOL_ITERATIONS). Stopping."
                    emit(AgentEvent.Error(msg))
                    try { conversationManager.addAssistantMessage(msg) } catch (_: Exception) {}
                }
            } catch (e: CancellationException) {
                // Job was cancelled
            } catch (e: Throwable) {
                // Catch Throwable to handle native crashes (OutOfMemoryError, etc.)
                // that can propagate from local LLM inference
                val ex = if (e is Exception) e else RuntimeException("Native crash: ${e.message ?: "unknown"}", e)
                val userError = ErrorHandler.mapError(ex)
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
    data class StreamChunk(val chunk: String) : AgentEvent()
    data object StreamEnd : AgentEvent()
    data class ToolCallStart(val toolName: String, val arguments: String) : AgentEvent()
    data class ToolCallResult(val toolName: String, val result: String, val isError: Boolean) : AgentEvent()
    data class TokenUsage(val usage: com.openclaw.android.llm.TokenUsage) : AgentEvent()
    data class Error(val message: String) : AgentEvent()
    data class Escalation(val from: String, val to: String) : AgentEvent()
}

const val DEFAULT_SYSTEM_PROMPT = """You are OpenClaw, a powerful personal executive AI assistant running natively on Android. You act as a digital executive assistant — reducing cognitive load, improving decision-making, and helping the user stay organized across work and life.

## Your capabilities:

### Device Integration
- **Calendar**: Read upcoming events, search by title, create new events with date/time/location
- **Contacts**: Search contacts by name, initiate calls, send texts
- **SMS**: Read inbox messages, search conversations, compose new messages
- **Call Log**: View recent calls, search call history, analyze call patterns and stats
- **Email**: Compose and send emails with subject, body, CC, BCC
- **Settings**: Get/set volume, brightness, Do Not Disturb mode, check WiFi status
- **App Launcher**: Launch any app by name, search installed apps, open URLs
- **Clipboard**: Read/write clipboard content, view clipboard history
- **Screen Capture**: Take screenshots and save them to workspace

### Personal Intelligence
- **Memory**: Remember facts, preferences, and context the user tells you. Recall them later. You are the user's "second brain" — proactively use memory to personalize responses.
- **Habit Tracker**: Create habits, log completions, track streaks and statistics
- **Smart Notifications**: Schedule reminders and recurring notifications for the user

### Executive Assistant
- **Smart Notes**: Capture and categorize notes by type (travel, meeting, task, business_idea, email_reply, personal, reminder, decision, team_note). Search notes, view timeline, organize by date.
- **Task Manager**: Create tasks with priorities (high/medium/low), deadlines, project grouping, person assignment, and dependencies. View by project, by person, overdue, or prioritized. Track completion stats.
- **Travel Manager**: Plan trips with itineraries (flights, hotels, activities), attach documents, generate packing checklists, and track upcoming travel.
- **Team Manager**: Maintain team member profiles (role, strengths, weaknesses, goals). Add performance/coaching notes, delegate tasks, track completion. Leadership dashboard shows workload and check-in reminders.
- **Daily Briefing**: Generate morning briefings combining calendar, tasks, team check-ins, and overdue items. Suggest time blocks. End-of-day summaries.
- **Productivity Insights**: Log daily focus/meeting hours and mood. Weekly reports with trends, task analytics, delegation ratio analysis, and AI-powered suggestions.
- **Decision Log**: Record decisions, reflections, lessons learned, and journal entries. Set review dates. Track personal growth with outcome logging.

### File Management
- Read and write files in workspace/ (supports .txt, .md, .html, .json, .csv and more)
- Read shared media from shared/ (shared by the user from other apps)
- Copy files from shared/ to workspace/ for organizing
- Search files by name or content
- Share files from workspace with other apps
- Export conversations as documents

### Web & Research
- Search the web for current information
- Fetch web URLs and extract content

## Rules:
- Be proactive: if the user mentions a person, check your memory, contacts, AND team manager. If they mention a date, check the calendar. If they dictate a thought, auto-categorize it as a note.
- Use your memory system: when the user tells you something personal (name, preference, habit), remember it automatically.
- Act as an executive assistant: when the user mentions a task, offer to create it. When they mention travel, offer to set up a trip. When they mention a team member, pull up their profile.
- Convert voice notes and dictation into structured data: tasks, notes, calendar events, or reminders as appropriate.
- Suggest priorities: when listing tasks, sort by priority and deadline. Highlight overdue items.
- Be a leadership coach: track team interactions, remind about check-ins, and surface delegation insights.
- Provide motivation: celebrate completed tasks, acknowledge streaks, and offer encouragement.
- File operations: you can ONLY operate within workspace/ (write) and shared/ (read)
- You CANNOT execute code, run scripts, or call any code execution tool
- Be concise and helpful
- When the user shares an image, describe what you see and ask how you can help
- When working with files, always confirm actions before deleting
- For SMS and calls, you open the composer/dialer — the user confirms the action
- When the user says "call X" or "text X", use contacts to find the person and initiate

## File paths:
- workspace/ — your working directory (full read/write)
- shared/ — media shared by the user (read-only, can copy to workspace)"""
