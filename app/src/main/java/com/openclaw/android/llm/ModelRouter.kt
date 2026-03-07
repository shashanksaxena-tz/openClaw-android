package com.openclaw.android.llm

/**
 * Routes requests to the appropriate LLM provider based on the selected model
 * and message content.
 *
 * Supports a **local-first** strategy: when a local model is available and the
 * user hasn't pinned a specific cloud model, requests go to the on-device LLM
 * first. The local model self-determines whether it can handle the task or
 * needs to escalate to cloud — see [LlamaProvider.ESCALATION_MARKER].
 */
class ModelRouter(
    private val providers: Map<String, LlmProvider>,
) {
    data class ModelSelection(
        val provider: LlmProvider,
        val modelId: String,
        val modelInfo: ModelInfo,
        val isLocal: Boolean = false,
    )

    /** Get all available models across all configured providers. */
    fun getAvailableModels(): List<Pair<LlmProvider, ModelInfo>> =
        providers.values
            .filter { it.isConfigured() }
            .flatMap { provider -> provider.availableModels.map { provider to it } }

    /** Find the right provider and model for a given model ID. */
    fun resolveModel(modelId: String): ModelSelection? {
        for ((_, provider) in providers) {
            val model = provider.availableModels.find { it.id == modelId }
            if (model != null) {
                return ModelSelection(
                    provider, modelId, model,
                    isLocal = provider.providerId == "local-llama",
                )
            }
        }
        return null
    }

    /**
     * Smart model selection: picks the best model based on content and preferences.
     *
     * **Local-first strategy:**
     * When a local model is configured and no specific cloud model is pinned,
     * the local model is selected. It will self-escalate to cloud if needed.
     *
     * **Explicit model selection:**
     * When the user has pinned a specific model (including a local one), that
     * model is always used first.
     */
    fun selectBestModel(
        preferredModelId: String?,
        hasImages: Boolean,
        hasAudio: Boolean,
    ): ModelSelection? {
        // Try preferred model first
        if (preferredModelId != null) {
            val selection = resolveModel(preferredModelId)
            if (selection != null) {
                // If we need vision but the preferred model doesn't support it, find one that does
                if (hasImages && !selection.modelInfo.supportsVision) {
                    return findVisionModel() ?: selection
                }
                return selection
            }
        }

        // Images/audio always need cloud (local can't do vision)
        if (hasImages || hasAudio) {
            return findVisionModel() ?: findAnyModel()
        }

        // Local-first: if a local model is available, use it
        val localModel = findLocalModel()
        if (localModel != null) {
            return localModel
        }

        // Fall back to cloud
        return findFastTextModel() ?: findAnyModel()
    }

    /**
     * Find the best cloud model for escalation.
     * Called by AgentRuntime when the local model requests escalation.
     */
    fun selectCloudModel(
        hasImages: Boolean = false,
        hasAudio: Boolean = false,
    ): ModelSelection? {
        if (hasImages || hasAudio) {
            return findVisionModel() ?: findCloudTextModel()
        }
        return findCloudTextModel() ?: findAnyModel()
    }

    /** Find an available local model. */
    private fun findLocalModel(): ModelSelection? {
        val local = providers["local-llama"]
        if (local != null && local.isConfigured()) {
            val model = local.availableModels.firstOrNull()
            if (model != null) {
                return ModelSelection(local, model.id, model, isLocal = true)
            }
        }
        return null
    }

    /** Find a vision-capable model, preferring Gemini (free + best vision). */
    private fun findVisionModel(): ModelSelection? {
        // Prefer Gemini for vision (free tier, native multimodal)
        val gemini = providers["gemini"]
        if (gemini != null && gemini.isConfigured()) {
            val model = gemini.availableModels.find { it.supportsVision }
            if (model != null) return ModelSelection(gemini, model.id, model)
        }

        // Fallback to any vision model
        for ((_, provider) in providers) {
            if (!provider.isConfigured() || provider.providerId == "local-llama") continue
            val model = provider.availableModels.find { it.supportsVision }
            if (model != null) return ModelSelection(provider, model.id, model)
        }
        return null
    }

    /** Find a fast cloud text model, preferring Groq/Cerebras for speed. */
    private fun findFastTextModel(): ModelSelection? {
        // Groq is usually fastest
        for (id in listOf("groq", "cerebras", "gemini")) {
            val provider = providers[id]
            if (provider != null && provider.isConfigured()) {
                val model = provider.availableModels.firstOrNull()
                if (model != null) return ModelSelection(provider, model.id, model)
            }
        }
        return null
    }

    /** Find any cloud model (excludes local). */
    private fun findCloudTextModel(): ModelSelection? {
        for (id in listOf("groq", "cerebras", "gemini")) {
            val provider = providers[id]
            if (provider != null && provider.isConfigured()) {
                val model = provider.availableModels.firstOrNull()
                if (model != null) return ModelSelection(provider, model.id, model)
            }
        }
        return null
    }

    private fun findAnyModel(): ModelSelection? {
        for ((_, provider) in providers) {
            if (!provider.isConfigured()) continue
            val model = provider.availableModels.firstOrNull()
            if (model != null) {
                return ModelSelection(
                    provider, model.id, model,
                    isLocal = provider.providerId == "local-llama",
                )
            }
        }
        return null
    }
}
