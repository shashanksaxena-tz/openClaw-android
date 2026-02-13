package com.openclaw.android.llm

/**
 * Routes requests to the appropriate LLM provider based on the selected model
 * and message content. Automatically selects vision-capable models when images
 * are present.
 */
class ModelRouter(
    private val providers: Map<String, LlmProvider>,
) {
    data class ModelSelection(
        val provider: LlmProvider,
        val modelId: String,
        val modelInfo: ModelInfo,
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
                return ModelSelection(provider, modelId, model)
            }
        }
        return null
    }

    /**
     * Smart model selection: picks the best model based on content.
     * - If images are present and current model doesn't support vision, upgrade.
     * - Prefers free-tier models (Gemini Flash for vision, Groq/Cerebras for text).
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

        // No preference or not found — pick the best available
        if (hasImages || hasAudio) {
            return findVisionModel() ?: findAnyModel()
        }

        return findFastTextModel() ?: findAnyModel()
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
            if (!provider.isConfigured()) continue
            val model = provider.availableModels.find { it.supportsVision }
            if (model != null) return ModelSelection(provider, model.id, model)
        }
        return null
    }

    /** Find a fast text model, preferring Groq/Cerebras for speed. */
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

    private fun findAnyModel(): ModelSelection? {
        for ((_, provider) in providers) {
            if (!provider.isConfigured()) continue
            val model = provider.availableModels.firstOrNull()
            if (model != null) return ModelSelection(provider, model.id, model)
        }
        return null
    }
}
