package com.openclaw.android

import android.app.Application
import android.util.Log
import com.openclaw.android.agent.AgentRuntime
import com.openclaw.android.agent.ConversationManager
import com.openclaw.android.agent.NotificationHelper
import com.openclaw.android.agent.SmartNotificationManager
import com.openclaw.android.data.ConversationExpiry
import com.openclaw.android.data.MemorySystem
import com.openclaw.android.data.PrivacyAudit
import com.openclaw.android.data.SettingsRepository
import com.openclaw.android.data.SpaceManager
import com.openclaw.android.data.db.AppDatabase
import com.openclaw.android.llm.*
import com.openclaw.android.llm.ModelDownloadManager
import com.openclaw.android.sandbox.SandboxedFileSystem
import com.openclaw.android.tools.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class OpenClawApp : Application() {

    /** Set by MainActivity so ScreenCaptureTool can access the current window. */
    var currentWindow: android.view.Window? = null

    lateinit var settings: SettingsRepository
        private set

    lateinit var sandboxedFileSystem: SandboxedFileSystem
        private set

    lateinit var modelRouter: ModelRouter
        private set

    lateinit var toolRegistry: ToolRegistry
        private set

    lateinit var agentRuntime: AgentRuntime
        private set

    lateinit var spaceManager: SpaceManager
        private set

    lateinit var conversationManager: ConversationManager
        private set

    lateinit var memorySystem: MemorySystem
        private set

    lateinit var privacyAudit: PrivacyAudit
        private set

    lateinit var conversationExpiry: ConversationExpiry
        private set

    lateinit var smartNotificationManager: SmartNotificationManager
        private set

    lateinit var permissionManager: PermissionManager
        private set

    lateinit var modelDownloadManager: ModelDownloadManager
        private set

    override fun onCreate() {
        super.onCreate()

        // Install a crash handler so native/OOM crashes get saved for next launch.
        // This lets us show the user what went wrong instead of silently exiting.
        installCrashHandler()

        // Notification channels
        NotificationHelper.createChannel(this)

        // Settings
        settings = SettingsRepository(this)

        // File system
        sandboxedFileSystem = SandboxedFileSystem(this)

        // Space manager
        spaceManager = SpaceManager(this)

        // Database
        val database = AppDatabase.getInstance(this)
        val dao = database.conversationDao()
        val memoryDao = database.memoryDao()

        // Conversation manager (Room-backed)
        conversationManager = ConversationManager(dao)

        // Phase 2: Memory system
        memorySystem = MemorySystem(memoryDao)

        // Phase 2: Privacy audit
        privacyAudit = PrivacyAudit(this)

        // Phase 2: Conversation expiry
        conversationExpiry = ConversationExpiry(this, database)

        // Phase 2: Smart notifications
        smartNotificationManager = SmartNotificationManager(this)

        // Permission manager (bridges tool execution ↔ UI permission dialogs)
        permissionManager = PermissionManager(this)

        // Model download manager for local GGUF models
        modelDownloadManager = ModelDownloadManager(this)

        // LLM providers (including local llama.cpp model)
        val llamaProvider = LlamaProvider(
            downloadManager = modelDownloadManager,
            getActiveModelId = { settings.getActiveLocalModelId().ifBlank { null } },
        )
        val providers = mutableMapOf<String, LlmProvider>(
            "gemini" to GeminiProvider(apiKeyProvider = { settings.getGeminiKey() }),
            "groq" to GroqProvider(apiKeyProvider = { settings.getGroqKey() }),
            "cerebras" to CerebrasProvider(apiKeyProvider = { settings.getCerebrasKey() }),
        )
        // Always register the local provider if enabled OR if models are downloaded.
        // This ensures offline mode works even if the user toggled the setting off and
        // back on, or if the app was restarted after downloading a model.
        if (settings.getLocalModelEnabled() || modelDownloadManager.getDownloadedModels().isNotEmpty()) {
            providers["local-llama"] = llamaProvider
        }
        modelRouter = ModelRouter(providers)

        // Tools — original
        toolRegistry = ToolRegistry().apply {
            register(ReadFileTool(sandboxedFileSystem))
            register(WriteFileTool(sandboxedFileSystem))
            register(ListFilesTool(sandboxedFileSystem))
            register(DeleteFileTool(sandboxedFileSystem))
            register(SearchFilesTool(sandboxedFileSystem))
            register(MoveFileTool(sandboxedFileSystem))
            register(CreateDirectoryTool(sandboxedFileSystem))
            register(FetchUrlTool())
            register(WebSearchTool())
            register(ShareFileTool(this@OpenClawApp, sandboxedFileSystem))
            register(ExportChatTool(sandboxedFileSystem) {
                conversationManager.getConversationAsText()
            })

            // Phase 2: System integration tools
            register(CalendarTool(this@OpenClawApp))
            register(ContactsTool(this@OpenClawApp))
            register(SmsTool(this@OpenClawApp))
            register(CallLogTool(this@OpenClawApp))
            register(SettingsControlTool(this@OpenClawApp))
            register(AppLauncherTool(this@OpenClawApp))
            register(ClipboardTool(this@OpenClawApp))
            register(EmailTool(this@OpenClawApp))

            // Screen capture (uses window reference set by MainActivity)
            register(ScreenCaptureTool(this@OpenClawApp, sandboxedFileSystem) { currentWindow })

            // Phase 2: AI features
            register(MemoryTool(memorySystem))
            register(NotificationTool(smartNotificationManager))
            register(HabitTrackerTool(this@OpenClawApp))
            register(PrivacyAuditTool(privacyAudit))

            // Phase 3: Executive Assistant tools
            register(SmartNoteTool(this@OpenClawApp))
            register(TaskManagerTool(this@OpenClawApp))
            register(TravelManagerTool(this@OpenClawApp))
            register(TeamManagerTool(this@OpenClawApp))
            register(ProductivityInsightsTool(this@OpenClawApp))
            register(DailyBriefingTool(this@OpenClawApp))
            register(DecisionLogTool(this@OpenClawApp))
        }

        // Agent runtime
        agentRuntime = AgentRuntime(
            modelRouter = modelRouter,
            toolRegistry = toolRegistry,
            conversationManager = conversationManager,
            spaceManager = spaceManager,
            sandboxedFileSystem = sandboxedFileSystem,
            onBackgroundResponse = { preview ->
                NotificationHelper.showResponseReady(this@OpenClawApp, preview)
            },
            permissionManager = permissionManager,
        ).apply {
            systemPrompt = settings.getSystemPrompt()
            preferredModelId = settings.getDefaultModel().ifBlank { null }
        }

        // Run conversation expiry on startup
        CoroutineScope(Dispatchers.IO).launch {
            try {
                conversationExpiry.purgeExpired()
            } catch (e: Exception) {
                android.util.Log.e("OpenClawApp", "Conversation expiry failed", e)
            }
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        agentRuntime.destroy()
    }

    /**
     * Saves crash info to a file so the next app launch can show the user what went wrong.
     * This is critical for native crashes (llama.cpp OOM, SIGSEGV) where the process dies
     * without any visible error.
     */
    private fun installCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val crashFile = java.io.File(filesDir, "last_crash.txt")
                val timestamp = java.text.SimpleDateFormat(
                    "yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()
                ).format(java.util.Date())
                val info = buildString {
                    appendLine("Crash at $timestamp")
                    appendLine("Thread: ${thread.name}")
                    appendLine("Error: ${throwable::class.java.simpleName}: ${throwable.message}")
                    appendLine()
                    appendLine(throwable.stackTraceToString().take(2000))
                }
                crashFile.writeText(info)
                Log.e("OpenClawApp", "Crash saved to last_crash.txt", throwable)
            } catch (_: Exception) {
                // Don't crash in the crash handler
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    /**
     * Check if the app crashed last time (e.g. native OOM) and return the crash message.
     * Called from the chat screen to show an inline error on restart.
     */
    fun consumeLastCrash(): String? {
        val crashFile = java.io.File(filesDir, "last_crash.txt")
        if (!crashFile.exists()) return null
        return try {
            val info = crashFile.readText()
            crashFile.delete()
            if (info.contains("OutOfMemory", ignoreCase = true) ||
                info.contains("llama", ignoreCase = true) ||
                info.contains("native", ignoreCase = true)
            ) {
                "The app crashed last time, likely because the local AI model ran out of memory. " +
                    "Try using a smaller model, or close other apps before loading the model."
            } else {
                "The app crashed unexpectedly last time. If this keeps happening, try clearing app data in Settings."
            }
        } catch (_: Exception) {
            crashFile.delete()
            null
        }
    }
}
