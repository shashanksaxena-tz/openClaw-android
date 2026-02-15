package com.openclaw.android

import android.app.Application
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

    override fun onCreate() {
        super.onCreate()

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

        // LLM providers (including local model)
        val localProvider = LocalModelProvider(this)
        val providers = mapOf(
            "gemini" to GeminiProvider(apiKeyProvider = { settings.getGeminiKey() }),
            "groq" to GroqProvider(apiKeyProvider = { settings.getGroqKey() }),
            "cerebras" to CerebrasProvider(apiKeyProvider = { settings.getCerebrasKey() }),
            "local" to localProvider,
        )
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
}
