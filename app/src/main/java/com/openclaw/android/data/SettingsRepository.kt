package com.openclaw.android.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.openclaw.android.agent.DEFAULT_SYSTEM_PROMPT

/**
 * Encrypted storage for API keys and app settings.
 * Uses Android's EncryptedSharedPreferences — keys never leave the device unencrypted.
 */
class SettingsRepository(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "openclaw_settings",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    // API Keys
    fun getGeminiKey(): String = prefs.getString(KEY_GEMINI, "") ?: ""
    fun setGeminiKey(key: String) { prefs.edit().putString(KEY_GEMINI, key).apply() }

    fun getGroqKey(): String = prefs.getString(KEY_GROQ, "") ?: ""
    fun setGroqKey(key: String) { prefs.edit().putString(KEY_GROQ, key).apply() }

    fun getCerebrasKey(): String = prefs.getString(KEY_CEREBRAS, "") ?: ""
    fun setCerebrasKey(key: String) { prefs.edit().putString(KEY_CEREBRAS, key).apply() }

    // Model preference
    fun getDefaultModel(): String = prefs.getString(KEY_DEFAULT_MODEL, "") ?: ""
    fun setDefaultModel(modelId: String) { prefs.edit().putString(KEY_DEFAULT_MODEL, modelId).apply() }

    // System prompt
    fun getSystemPrompt(): String = prefs.getString(KEY_SYSTEM_PROMPT, DEFAULT_SYSTEM_PROMPT) ?: DEFAULT_SYSTEM_PROMPT
    fun setSystemPrompt(prompt: String) { prefs.edit().putString(KEY_SYSTEM_PROMPT, prompt).apply() }

    // Onboarding
    fun getOnboardingComplete(): Boolean = prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false)
    fun setOnboardingComplete(complete: Boolean) { prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETE, complete).apply() }

    // Local model settings
    fun getActiveLocalModelId(): String = prefs.getString(KEY_LOCAL_MODEL_ID, "") ?: ""
    fun setActiveLocalModelId(modelId: String) { prefs.edit().putString(KEY_LOCAL_MODEL_ID, modelId).apply() }

    fun getLocalModelEnabled(): Boolean = prefs.getBoolean(KEY_LOCAL_MODEL_ENABLED, false)
    fun setLocalModelEnabled(enabled: Boolean) { prefs.edit().putBoolean(KEY_LOCAL_MODEL_ENABLED, enabled).apply() }

    // Theme preference: "system", "light", or "dark". Default is "light" (white theme).
    fun getThemeMode(): String = prefs.getString(KEY_THEME_MODE, THEME_LIGHT) ?: THEME_LIGHT
    fun setThemeMode(mode: String) { prefs.edit().putString(KEY_THEME_MODE, mode).apply() }

    // Check if any cloud provider is configured
    fun hasAnyApiKey(): Boolean = getGeminiKey().isNotBlank() ||
            getGroqKey().isNotBlank() ||
            getCerebrasKey().isNotBlank()

    /** True when any AI provider is available — cloud API keys OR a local model enabled. */
    fun hasAnyProvider(): Boolean = hasAnyApiKey() ||
            (getLocalModelEnabled() && getActiveLocalModelId().isNotBlank())

    companion object {
        private const val KEY_GEMINI = "api_key_gemini"
        private const val KEY_GROQ = "api_key_groq"
        private const val KEY_CEREBRAS = "api_key_cerebras"
        private const val KEY_DEFAULT_MODEL = "default_model"
        private const val KEY_SYSTEM_PROMPT = "system_prompt"
        private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
        private const val KEY_LOCAL_MODEL_ID = "local_model_id"
        private const val KEY_LOCAL_MODEL_ENABLED = "local_model_enabled"
        private const val KEY_THEME_MODE = "theme_mode"

        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
        const val THEME_SYSTEM = "system"
    }
}
