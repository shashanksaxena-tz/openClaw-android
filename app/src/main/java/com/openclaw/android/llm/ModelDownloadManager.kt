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
 * Manages downloading, storing, and tracking .litertlm models for local inference.
 *
 * Models are stored in the app's internal storage under `models/`.
 * Supports resumable downloads and provides progress updates via StateFlow.
 *
 * Uses .litertlm files from the HuggingFace LiteRT Community.
 * These are pre-quantized, pre-optimized model bundles that include tokenizer,
 * model weights, and metadata — no separate prompt templates needed.
 */
class ModelDownloadManager(private val context: Context) {

    companion object {
        private const val TAG = "ModelDownloadManager"
        private const val MODELS_DIR = "models"
        private const val BUFFER_SIZE = 8 * 1024
    }

    /**
     * Curated list of .litertlm models optimized for Android.
     *
     * From the official HuggingFace LiteRT Community and Google repos.
     * All models are 4-bit quantized for on-device efficiency.
     *
     * Key advantage over GGUF: LiteRT-LM models are specifically optimized for
     * Android hardware (ARM NEON, GPU via OpenCL/Vulkan, NPU via QNN).
     * The 557MB Gemma 4-1B runs faster here than a 2GB GGUF via llama.cpp.
     */
    val availableModels: List<DownloadableModel> = listOf(
        // ── Small (2-3GB RAM, runs on any modern phone) ─────────────────
        DownloadableModel(
            id = "gemma4-e2b-litert",
            name = "Gemma 4 E2B",
            description = "Google's ultra-efficient mobile model. Fast, smart, and tiny.",
            sizeBytes = 1_420_000_000L,
            ramRequired = "2 GB",
            downloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
            fileName = "gemma-4-E2B-it.litertlm",
            contextWindow = 8192,
            supportsToolUse = true,
            tier = ModelTier.SMALL,
        ),
        DownloadableModel(
            id = "qwen25-1.5b-litert",
            name = "Qwen 2.5 1.5B",
            description = "Alibaba's efficient model. Good reasoning for its size.",
            sizeBytes = 1_524_000_000L,
            ramRequired = "3 GB",
            downloadUrl = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
            fileName = "Qwen2.5-1.5B-Instruct_q8.litertlm",
            contextWindow = 4096,
            supportsToolUse = true,
            tier = ModelTier.SMALL,
        ),
        // ── Medium (4-6GB RAM, recommended for most phones) ─────────────
        DownloadableModel(
            id = "gemma4-e4b-litert",
            name = "Gemma 4 E4B",
            description = "Google's balanced 4B model. Excellent performance/quality ratio.",
            sizeBytes = 2_850_000_000L,
            ramRequired = "4 GB",
            downloadUrl = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm",
            fileName = "gemma-4-E4B-it.litertlm",
            contextWindow = 8192,
            supportsToolUse = true,
            tier = ModelTier.MEDIUM,
        ),
        DownloadableModel(
            id = "phi4-mini-litert",
            name = "Phi 4 Mini (3.8B)",
            description = "Microsoft's compact model. Strong reasoning, 8-bit quantized.",
            sizeBytes = 3_728_000_000L,
            ramRequired = "4 GB",
            downloadUrl = "https://huggingface.co/litert-community/Phi-4-mini-instruct/resolve/main/Phi-4-mini-instruct_multi-prefill-seq_q8_ekv4096.litertlm",
            fileName = "Phi-4-mini-instruct_q8.litertlm",
            contextWindow = 4096,
            supportsToolUse = true,
            tier = ModelTier.MEDIUM,
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
                    isComplete = file.length() >= model.sizeBytes * 0.95,
                )
            } else null
        }
    }

    fun isModelDownloaded(modelId: String): Boolean {
        val model = availableModels.find { it.id == modelId } ?: return false
        val file = File(modelsDir, model.fileName)
        return file.exists() && file.length() >= model.sizeBytes * 0.95
    }

    fun getModelPath(modelId: String): String? {
        val model = availableModels.find { it.id == modelId } ?: return null
        val file = File(modelsDir, model.fileName)
        return if (file.exists() && file.length() >= model.sizeBytes * 0.95) file.absolutePath else null
    }

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
                .addHeader("User-Agent", "OpenClaw-Android/2.0")

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
                    raf.seek(existingBytes)
                } else {
                    raf.setLength(0)
                }

                val buffer = ByteArray(BUFFER_SIZE)
                var bytesWritten = if (response.code == 206) existingBytes else 0L
                var lastProgressUpdate = System.currentTimeMillis()
                var speedSampleStart = System.currentTimeMillis()
                var speedSampleBytes = 0L
                var currentSpeed = 0L

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
                        speedSampleBytes += read

                        val now = System.currentTimeMillis()
                        if (now - lastProgressUpdate > 500) {
                            val sampleElapsed = now - speedSampleStart
                            if (sampleElapsed > 0) {
                                currentSpeed = (speedSampleBytes * 1000) / sampleElapsed
                            }
                            if (sampleElapsed > 2000) {
                                speedSampleStart = now
                                speedSampleBytes = 0L
                            }
                            val remaining = totalBytes - bytesWritten
                            val eta = if (currentSpeed > 0) remaining / currentSpeed else -1L

                            _downloadState.value = DownloadState.Downloading(
                                modelId = modelId,
                                modelName = model.name,
                                progress = if (totalBytes > 0) bytesWritten.toFloat() / totalBytes else 0f,
                                downloadedBytes = bytesWritten,
                                totalBytes = totalBytes,
                                speedBytesPerSec = currentSpeed,
                                etaSeconds = eta,
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

    fun cancelDownload() {
        cancelRequested = true
    }

    fun deleteModel(modelId: String): Boolean {
        val model = availableModels.find { it.id == modelId } ?: return false
        val file = File(modelsDir, model.fileName)
        return if (file.exists()) {
            if (LiteRTBridge.getLoadedModelPath() == file.absolutePath) {
                LiteRTBridge.unloadModel()
            }
            val deleted = file.delete()
            Log.i(TAG, "Deleted model ${model.name}: $deleted")
            deleted
        } else false
    }

    fun getTotalDiskUsage(): Long =
        modelsDir.listFiles()?.sumOf { it.length() } ?: 0L

    fun resetState() {
        _downloadState.value = DownloadState.Idle
    }

    /** Remove any old .gguf files from the previous llama.cpp-based version. */
    fun cleanupLegacyModels() {
        val dir = modelsDir
        if (!dir.exists()) return
        dir.listFiles()?.filter { it.extension == "gguf" }?.forEach { file ->
            Log.i(TAG, "Removing legacy GGUF model: ${file.name}")
            file.delete()
        }
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
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val speedBytesPerSec: Long = 0L,
        val etaSeconds: Long = -1L,
    ) : DownloadState() {
        val progressPercent: Int get() = (progress * 100).toInt()
        val downloadedDisplay: String get() = formatBytes(downloadedBytes)
        val totalDisplay: String get() = formatBytes(totalBytes)
        val speedDisplay: String get() = if (speedBytesPerSec > 0) "${formatBytes(speedBytesPerSec)}/s" else ""
        val etaDisplay: String get() = when {
            etaSeconds < 0 -> "Calculating..."
            etaSeconds < 60 -> "${etaSeconds}s remaining"
            etaSeconds < 3600 -> "${etaSeconds / 60}m ${etaSeconds % 60}s remaining"
            else -> "${etaSeconds / 3600}h ${(etaSeconds % 3600) / 60}m remaining"
        }

        private fun formatBytes(bytes: Long): String = when {
            bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0)
            bytes >= 1_000_000 -> "${bytes / 1_000_000} MB"
            bytes >= 1_000 -> "${bytes / 1_000} KB"
            else -> "$bytes B"
        }
    }
    data class Complete(val modelId: String, val modelName: String, val filePath: String) : DownloadState()
    data class Error(val modelId: String, val modelName: String, val message: String) : DownloadState()
}
