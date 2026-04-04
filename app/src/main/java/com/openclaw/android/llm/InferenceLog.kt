package com.openclaw.android.llm

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Diagnostic logging system for local model inference.
 * Writes to a ring-buffer log file (last 200 lines) that users can
 * extract from Settings to debug issues.
 */
object InferenceLog {

    private const val LOG_FILE_NAME = "inference.log"
    private const val MAX_LINES = 200

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    private var logFile: File? = null

    /**
     * Sets up the log file path. Must be called once before any other method.
     */
    fun init(context: Context) {
        synchronized(this) {
            logFile = File(context.filesDir, LOG_FILE_NAME)
        }
    }

    /**
     * Appends a timestamped line to the log, trimming to [MAX_LINES].
     */
    fun log(tag: String, message: String) {
        synchronized(this) {
            val file = logFile ?: return
            val timestamp = dateFormat.format(Date())
            val line = "[$timestamp] [$tag] $message"
            appendAndTruncate(file, line)
        }
    }

    /**
     * Logs a model load event with all configuration parameters.
     */
    fun logModelLoad(modelPath: String, config: Map<String, Any>) {
        val configStr = config.entries.joinToString(", ") { "${it.key}=${it.value}" }
        log("MODEL_LOAD", "path=$modelPath config={$configStr}")
    }

    /**
     * Logs the start of an inference request.
     */
    fun logInferenceStart(promptTokens: Int, maxGenTokens: Int, contextSize: Int) {
        log(
            "INFERENCE_START",
            "promptTokens=$promptTokens maxGenTokens=$maxGenTokens contextSize=$contextSize"
        )
    }

    /**
     * Logs the time to first token.
     */
    fun logFirstToken(timeMs: Long) {
        log("FIRST_TOKEN", "timeToFirstToken=${timeMs}ms")
    }

    /**
     * Logs final inference statistics.
     */
    fun logInferenceEnd(totalTokens: Int, durationMs: Long, tokensPerSec: Double) {
        log(
            "INFERENCE_END",
            "totalTokens=$totalTokens duration=${durationMs}ms tokensPerSec=${"%.2f".format(tokensPerSec)}"
        )
    }

    /**
     * Logs an error.
     */
    fun logError(tag: String, error: String) {
        log("ERROR:$tag", error)
    }

    /**
     * Returns the full log contents as a string (for Settings export).
     */
    fun getLogText(): String {
        synchronized(this) {
            val file = logFile ?: return ""
            if (!file.exists()) return ""
            return file.readText()
        }
    }

    /**
     * Clears the log file.
     */
    fun clear() {
        synchronized(this) {
            val file = logFile ?: return
            if (file.exists()) {
                file.writeText("")
            }
        }
    }

    /**
     * Appends [line] to [file] and keeps only the last [MAX_LINES] lines.
     */
    private fun appendAndTruncate(file: File, line: String) {
        val existing = if (file.exists()) {
            file.readLines()
        } else {
            emptyList()
        }
        val updated = existing + line
        val trimmed = if (updated.size > MAX_LINES) {
            updated.takeLast(MAX_LINES)
        } else {
            updated
        }
        file.writeText(trimmed.joinToString("\n") + "\n")
    }
}
