package com.openclaw.android.llm

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit

/**
 * Manages downloading, storing, and tracking GGUF models for local inference.
 *
 * Models are stored in the app's internal storage under `models/`.
 * Supports resumable downloads and provides progress updates via StateFlow.
 */
class ModelDownloadManager(private val context: Context) {

    companion object {
        private const val TAG = "ModelDownloadManager"
        private const val MODELS_DIR = "models"
        private const val BUFFER_SIZE = 8 * 1024 // 8KB chunks
    }

    /** Curated list of models tested on Android devices. */
    val availableModels: List<DownloadableModel> = listOf(
        // ── Small (1-2GB RAM, runs on any modern phone) ──────────────────
        DownloadableModel(
            id = "qwen2.5-1.5b-q4",
            name = "Qwen 2.5 1.5B",
            description = "Ultra-light model. Fast responses, basic tool calling.",
            sizeBytes = 1_100_000_000L,
            ramRequired = "2 GB",
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
            fileName = "qwen2.5-1.5b-instruct-q4_k_m.gguf",
            contextWindow = 32768,
            supportsToolUse = true,
            tier = ModelTier.SMALL,
        ),
        DownloadableModel(
            id = "phi-3.5-mini-q4",
            name = "Phi 3.5 Mini (3.8B)",
            description = "Microsoft's compact model. Strong reasoning for its size.",
            sizeBytes = 2_300_000_000L,
            ramRequired = "3 GB",
            downloadUrl = "https://huggingface.co/bartowski/Phi-3.5-mini-instruct-GGUF/resolve/main/Phi-3.5-mini-instruct-Q4_K_M.gguf",
            fileName = "Phi-3.5-mini-instruct-Q4_K_M.gguf",
            contextWindow = 131072,
            supportsToolUse = true,
            tier = ModelTier.SMALL,
        ),
        // ── Medium (3-4GB RAM, recommended for most flagship phones) ─────
        DownloadableModel(
            id = "llama-3.2-3b-q4",
            name = "Llama 3.2 3B",
            description = "Meta's efficient model. Good balance of speed and quality.",
            sizeBytes = 2_000_000_000L,
            ramRequired = "3 GB",
            downloadUrl = "https://huggingface.co/bartowski/Llama-3.2-3B-Instruct-GGUF/resolve/main/Llama-3.2-3B-Instruct-Q4_K_M.gguf",
            fileName = "Llama-3.2-3B-Instruct-Q4_K_M.gguf",
            contextWindow = 131072,
            supportsToolUse = true,
            tier = ModelTier.MEDIUM,
        ),
        DownloadableModel(
            id = "qwen2.5-3b-q4",
            name = "Qwen 2.5 3B",
            description = "Best tool-calling at this size. Recommended for most devices.",
            sizeBytes = 2_100_000_000L,
            ramRequired = "3 GB",
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-3B-Instruct-GGUF/resolve/main/qwen2.5-3b-instruct-q4_k_m.gguf",
            fileName = "qwen2.5-3b-instruct-q4_k_m.gguf",
            contextWindow = 32768,
            supportsToolUse = true,
            tier = ModelTier.MEDIUM,
        ),
        // ── Large (5-8GB RAM, for high-end devices like Fold 7) ──────────
        DownloadableModel(
            id = "qwen2.5-7b-q4",
            name = "Qwen 2.5 7B",
            description = "Best local model for tool calling and orchestration. Recommended for 12GB+ RAM.",
            sizeBytes = 4_700_000_000L,
            ramRequired = "6 GB",
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-7B-Instruct-GGUF/resolve/main/qwen2.5-7b-instruct-q4_k_m.gguf",
            fileName = "qwen2.5-7b-instruct-q4_k_m.gguf",
            contextWindow = 32768,
            supportsToolUse = true,
            tier = ModelTier.LARGE,
        ),
        DownloadableModel(
            id = "llama-3.1-8b-q4",
            name = "Llama 3.1 8B",
            description = "Meta's capable model. Strong general reasoning.",
            sizeBytes = 4_900_000_000L,
            ramRequired = "6 GB",
            downloadUrl = "https://huggingface.co/bartowski/Meta-Llama-3.1-8B-Instruct-GGUF/resolve/main/Meta-Llama-3.1-8B-Instruct-Q4_K_M.gguf",
            fileName = "Meta-Llama-3.1-8B-Instruct-Q4_K_M.gguf",
            contextWindow = 131072,
            supportsToolUse = true,
            tier = ModelTier.LARGE,
        ),
    )

    private val modelsDir: File
        get() = File(context.filesDir, MODELS_DIR).also { it.mkdirs() }

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .writeTimeout(5, TimeUnit.MINUTES)
        .followRedirects(true)
        .build()

    @Volatile
    private var cancelRequested = false

    /** Get list of models already downloaded on device. */
    fun getDownloadedModels(): List<DownloadedModel> {
        val dir = modelsDir
        if (!dir.exists()) return emptyList()

        return availableModels.mapNotNull { model ->
            val file = File(dir, model.fileName)
            if (file.exists() && file.length() > 0) {
                DownloadedModel(
                    model = model,
                    filePath = file.absolutePath,
                    fileSizeBytes = file.length(),
                    isComplete = file.length() >= model.sizeBytes * 0.95, // allow 5% variance from estimate
                )
            } else null
        }
    }

    /** Check if a specific model is downloaded. */
    fun isModelDownloaded(modelId: String): Boolean {
        val model = availableModels.find { it.id == modelId } ?: return false
        val file = File(modelsDir, model.fileName)
        return file.exists() && file.length() >= model.sizeBytes * 0.95
    }

    /** Get the file path of a downloaded model. */
    fun getModelPath(modelId: String): String? {
        val model = availableModels.find { it.id == modelId } ?: return null
        val file = File(modelsDir, model.fileName)
        return if (file.exists() && file.length() > 0) file.absolutePath else null
    }

    /** Download a model with progress updates. Supports resume. */
    suspend fun downloadModel(modelId: String) = withContext(Dispatchers.IO) {
        val model = availableModels.find { it.id == modelId }
            ?: throw IllegalArgumentException("Unknown model: $modelId")

        cancelRequested = false
        val file = File(modelsDir, model.fileName)
        val existingBytes = if (file.exists()) file.length() else 0L

        _downloadState.value = DownloadState.Downloading(
            modelId = modelId,
            modelName = model.name,
            progress = if (model.sizeBytes > 0) existingBytes.toFloat() / model.sizeBytes else 0f,
            downloadedBytes = existingBytes,
            totalBytes = model.sizeBytes,
        )

        try {
            val requestBuilder = Request.Builder()
                .url(model.downloadUrl)
                .addHeader("User-Agent", "OpenClaw-Android/0.5")

            // Resume support
            if (existingBytes > 0) {
                requestBuilder.addHeader("Range", "bytes=$existingBytes-")
                Log.i(TAG, "Resuming download from byte $existingBytes")
            }

            val response = client.newCall(requestBuilder.build()).execute()

            response.use {
            if (!response.isSuccessful && response.code != 206) {
                throw RuntimeException("Download failed: HTTP ${response.code}")
            }

            val totalBytes = if (response.code == 206) {
                // Partial content - total is existing + remaining
                val contentLength = response.body?.contentLength() ?: 0L
                existingBytes + contentLength
            } else {
                response.body?.contentLength() ?: model.sizeBytes
            }

            val inputStream = response.body?.byteStream()
                ?: throw RuntimeException("Empty response body")

            val raf = RandomAccessFile(file, "rw")
            try {
                if (response.code == 206) {
                    raf.seek(existingBytes) // Resume at end
                } else {
                    raf.setLength(0) // Fresh start
                }

                val buffer = ByteArray(BUFFER_SIZE)
                var bytesWritten = if (response.code == 206) existingBytes else 0L
                var lastProgressUpdate = System.currentTimeMillis()

                inputStream.use { stream ->
                    while (true) {
                        if (cancelRequested) {
                            _downloadState.value = DownloadState.Idle
                            Log.i(TAG, "Download cancelled. ${bytesWritten / (1024 * 1024)}MB saved for resume.")
                            return@withContext
                        }

                        val read = stream.read(buffer)
                        if (read == -1) break

                        raf.write(buffer, 0, read)
                        bytesWritten += read

                        // Update progress at most every 200ms to avoid UI thrashing
                        val now = System.currentTimeMillis()
                        if (now - lastProgressUpdate > 200) {
                            _downloadState.value = DownloadState.Downloading(
                                modelId = modelId,
                                modelName = model.name,
                                progress = if (totalBytes > 0) bytesWritten.toFloat() / totalBytes else 0f,
                                downloadedBytes = bytesWritten,
                                totalBytes = totalBytes,
                            )
                            lastProgressUpdate = now
                        }
                    }
                }

                Log.i(TAG, "Download complete: ${model.name} (${bytesWritten / (1024 * 1024)}MB)")
                _downloadState.value = DownloadState.Complete(modelId, model.name, file.absolutePath)
            } finally {
                raf.close()
            }
            } // response.use

        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            _downloadState.value = DownloadState.Error(modelId, model.name, e.message ?: "Unknown error")
        }
    }

    /** Cancel an in-progress download. Partial file is kept for resume. */
    fun cancelDownload() {
        cancelRequested = true
    }

    /** Delete a downloaded model from disk. */
    fun deleteModel(modelId: String): Boolean {
        val model = availableModels.find { it.id == modelId } ?: return false
        val file = File(modelsDir, model.fileName)
        return if (file.exists()) {
            val deleted = file.delete()
            Log.i(TAG, "Deleted model ${model.name}: $deleted")
            deleted
        } else false
    }

    /** Total disk space used by downloaded models. */
    fun getTotalDiskUsage(): Long =
        modelsDir.listFiles()?.sumOf { it.length() } ?: 0L

    /** Reset download state to idle. */
    fun resetState() {
        _downloadState.value = DownloadState.Idle
    }
}

// ── Data classes ─────────────────────────────────────────────────────────────

enum class ModelTier(val label: String) {
    SMALL("Small"),
    MEDIUM("Medium"),
    LARGE("Large"),
}

@Serializable
data class DownloadableModel(
    val id: String,
    val name: String,
    val description: String,
    val sizeBytes: Long,
    val ramRequired: String,
    val downloadUrl: String,
    val fileName: String,
    val contextWindow: Int,
    val supportsToolUse: Boolean,
    val tier: ModelTier,
) {
    val sizeDisplay: String
        get() {
            val gb = sizeBytes / 1_000_000_000.0
            return if (gb >= 1.0) "%.1f GB".format(gb)
            else "${sizeBytes / 1_000_000} MB"
        }
}

data class DownloadedModel(
    val model: DownloadableModel,
    val filePath: String,
    val fileSizeBytes: Long,
    val isComplete: Boolean,
) {
    val sizeDisplay: String
        get() {
            val gb = fileSizeBytes / 1_000_000_000.0
            return if (gb >= 1.0) "%.1f GB".format(gb)
            else "${fileSizeBytes / 1_000_000} MB"
        }
}

sealed class DownloadState {
    data object Idle : DownloadState()
    data class Downloading(
        val modelId: String,
        val modelName: String,
        val progress: Float, // 0.0 to 1.0
        val downloadedBytes: Long,
        val totalBytes: Long,
    ) : DownloadState() {
        val progressPercent: Int get() = (progress * 100).toInt()
        val downloadedDisplay: String get() = "${downloadedBytes / (1024 * 1024)} MB"
        val totalDisplay: String get() = "${totalBytes / (1024 * 1024)} MB"
    }
    data class Complete(val modelId: String, val modelName: String, val filePath: String) : DownloadState()
    data class Error(val modelId: String, val modelName: String, val message: String) : DownloadState()
}
