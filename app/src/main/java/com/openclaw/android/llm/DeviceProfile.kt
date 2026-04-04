package com.openclaw.android.llm

/**
 * Device capability profile for local inference configuration.
 * All decisions about context size, GPU layers, threads, etc. are centralized here.
 * Aligned with off-grid-mobile-ai's device tiering.
 */
data class DeviceProfile(
    val totalMemMb: Long,
    val availableMemMb: Long,
    val cpuCores: Int,
) {
    val totalGb: Double get() = totalMemMb / 1024.0
    val availGb: Double get() = availableMemMb / 1024.0

    /** Max context tokens this device can safely handle. */
    val maxContext: Int get() {
        val base = when {
            totalGb <= 4 -> 1024
            totalGb <= 6 -> 2048
            totalGb <= 8 -> 4096
            totalGb <= 12 -> 8192
            else -> 16384
        }
        // Downgrade if available memory is tight
        return if (availGb < 1.5 && base > 2048) base / 2 else base
    }

    /**
     * GPU layers: DISABLED on Android by default.
     * Adreno OpenCL backend loads models fine but hangs during llama_decode().
     * Confirmed on SM-F966B (Fold 6, Adreno 750, 11GB RAM) — model loads with
     * gpu=24 in 8s but inference never produces a token.
     * Off-grid also defaults to GPU=0 on Android for the same reason.
     */
    val gpuLayers: Int get() = 0

    /** Thread count: up to 6 performance cores. */
    val threads: Int get() = minOf(maxOf(1, cpuCores - 2), 6)

    /** Flash attention: enabled on 8GB+ (always CPU-only since GPU disabled on Android). */
    val flashAttention: Boolean get() = totalGb >= 8

    /** Whether this device has enough RAM to attempt model loading. */
    val canLoadModel: Boolean get() = availableMemMb >= MIN_RAM_MB

    companion object {
        const val MIN_RAM_MB = 300L

        fun detect(): DeviceProfile = DeviceProfile(
            totalMemMb = LlamaBridge.getTotalMemoryMb(),
            availableMemMb = LlamaBridge.getAvailableMemoryMb(),
            cpuCores = Runtime.getRuntime().availableProcessors(),
        )
    }
}
