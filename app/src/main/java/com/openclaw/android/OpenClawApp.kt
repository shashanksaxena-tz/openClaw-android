package com.openclaw.android

import android.app.Application
import com.openclaw.android.agent.AgentRuntime
import com.openclaw.android.agent.ConversationManager
import com.openclaw.android.data.SettingsRepository
import com.openclaw.android.llm.*
import com.openclaw.android.sandbox.SandboxedFileSystem
import com.openclaw.android.tools.*

/**
 * Application singleton. Wires up all components:
 * - Settings (encrypted API key storage)
 * - Sandboxed file system
 * - LLM providers (Gemini, Groq, Cerebras)
 * - Tool registry
 * - Agent runtime
 */
class OpenClawApp : Application() {

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

    override fun onCreate() {
        super.onCreate()

        // Settings
        settings = SettingsRepository(this)

        // File system
        sandboxedFileSystem = SandboxedFileSystem(this)

        // LLM providers
        val providers = mapOf(
            "gemini" to GeminiProvider(apiKeyProvider = { settings.getGeminiKey() }),
            "groq" to GroqProvider(apiKeyProvider = { settings.getGroqKey() }),
            "cerebras" to CerebrasProvider(apiKeyProvider = { settings.getCerebrasKey() }),
        )
        modelRouter = ModelRouter(providers)

        // Tools
        toolRegistry = ToolRegistry().apply {
            register(ReadFileTool(sandboxedFileSystem))
            register(WriteFileTool(sandboxedFileSystem))
            register(ListFilesTool(sandboxedFileSystem))
            register(DeleteFileTool(sandboxedFileSystem))
            register(SearchFilesTool(sandboxedFileSystem))
            register(MoveFileTool(sandboxedFileSystem))
            register(CreateDirectoryTool(sandboxedFileSystem))
            register(FetchUrlTool())
        }

        // Conversation
        val conversationManager = ConversationManager()

        // Agent runtime
        agentRuntime = AgentRuntime(
            modelRouter = modelRouter,
            toolRegistry = toolRegistry,
            conversationManager = conversationManager,
        ).apply {
            systemPrompt = settings.getSystemPrompt()
            preferredModelId = settings.getDefaultModel().ifBlank { null }
        }
    }
}
