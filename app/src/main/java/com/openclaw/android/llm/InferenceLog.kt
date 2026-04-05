package com.openclaw.android.llm

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Diagnostic log for local model inference.
 *
 * Ring-buffer (last 300 lines) written to `inference.log`.
 * Users can extract from Settings and paste into a bug report.
 * Logs are detailed enough to diagnose: why model didn't respond,
 * what stage it got stuck at, memory/context/GPU configuration.
 *
 * Example output:
 * ```
 * [12:34:56] [DEVICE] Pixel 9 Pro | RAM: 12288MB total, 4521MB free | CPU: 8 cores | Android 15
 * [12:34:56] [MODEL_LOAD] gemma-3n-E2B-it-Q4_K_M.gguf (2048MB)
 * [12:34:56] [CONFIG] ctx=4096 gpu=12 threads=6 flash=false mmap=true kv=q8_0
 * [12:34:56] [LOAD_ATTEMPT] 1/2: gpu=12 ctx=4096 → SUCCESS (1423ms)
 * [12:34:58] [PROMPT] 487 tokens (max 3840) | stripped=none | template=gemma
 * [12:34:58] [GENERATE] maxTokens=2048 temp=0.7
 * [12:35:01] [FIRST_TOKEN] 2834ms (prefill: 487 tokens in 2834ms = 172 t/s)
 * [12:35:08] [DONE] 156 tokens in 7234ms (21.6 t/s)
 * ```
 */
object InferenceLog {

    private const val FILE_NAME = "inference.log"
    private const val MAX_LINES = 300
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)
    private var logFile: File? = null
    private var appContext: Context? = null

    fun init(context: Context) {
        synchronized(this) {
            logFile = File(context.filesDir, FILE_NAME)
            appContext = context.applicationContext
        }
        logDevice()
    }

    // ── Core ────────────────────────────────────────────────────────────────

    fun log(tag: String, msg: String) {
        synchronized(this) {
            val file = logFile ?: return
            val line = "[${fmt.format(Date())}] [$tag] $msg"
            android.util.Log.i("InferenceLog", line)
            appendAndTrim(file, line)
        }
    }

    fun logError(tag: String, msg: String) = log("ERROR:$tag", msg)

    // ── Structured events ───────────────────────────────────────────────────

    fun logDevice() {
        val profile = DeviceProfile.detect(appContext)
        // Also log the raw sysinfo value so we can see the discrepancy
        val sysinfoFreeMb = LlamaBridge.getAvailableMemoryMb()
        val cores = Runtime.getRuntime().availableProcessors()
        log("DEVICE", "${Build.MODEL} | RAM: ${profile.totalMemMb}MB total, ${profile.availableMemMb}MB avail (sysinfo: ${sysinfoFreeMb}MB) | CPU: $cores cores | Android ${Build.VERSION.RELEASE}")
        log("DEVICE", "llama.cpp loaded=${LlamaBridge.isLoaded} real=${LlamaBridge.isRealBuild}")
    }

    fun logModelLoad(modelPath: String, config: Map<String, Any>) {
        val fileName = modelPath.substringAfterLast("/")
        val sizeMb = try { java.io.File(modelPath).length() / (1024 * 1024) } catch (_: Exception) { -1L }
        log("MODEL_LOAD", "$fileName (${sizeMb}MB)")
        val cfgStr = config.entries.joinToString(" ") { "${it.key}=${it.value}" }
        log("CONFIG", cfgStr)
    }

    fun logLoadAttempt(attempt: Int, total: Int, label: String, success: Boolean, durationMs: Long) {
        val result = if (success) "SUCCESS (${durationMs}ms)" else "FAILED (${durationMs}ms)"
        log("LOAD_ATTEMPT", "$attempt/$total: $label → $result")
    }

    fun logPrompt(tokens: Int, maxTokens: Int, stripped: String, template: String) {
        log("PROMPT", "$tokens tokens (max $maxTokens) | stripped=$stripped | template=$template")
    }

    fun logInferenceStart(promptTokens: Int, maxGenTokens: Int, contextSize: Int) {
        log("GENERATE", "maxTokens=$maxGenTokens temp=0.7 prompt=$promptTokens ctx=$contextSize")
    }

    fun logFirstToken(timeMs: Long) {
        log("FIRST_TOKEN", "${timeMs}ms")
    }

    fun logInferenceEnd(totalTokens: Int, durationMs: Long, tokensPerSec: Double) {
        log("DONE", "$totalTokens tokens in ${durationMs}ms (${"%.1f".format(tokensPerSec)} t/s)")
    }

    // ── Export ───────────────────────────────────────────────────────────────

    fun getLogText(): String = synchronized(this) {
        val file = logFile ?: return ""
        if (!file.exists()) return ""
        return file.readText()
    }

    fun clear() = synchronized(this) {
        logFile?.takeIf { it.exists() }?.writeText("")
    }

    // ── Internal ────────────────────────────────────────────────────────────

    private fun appendAndTrim(file: File, line: String) {
        val lines = if (file.exists()) file.readLines().toMutableList() else mutableListOf()
        lines.add(line)
        if (lines.size > MAX_LINES) {
            val trimmed = lines.takeLast(MAX_LINES)
            file.writeText(trimmed.joinToString("\n") + "\n")
        } else {
            file.appendText(line + "\n")
        }
    }
}
