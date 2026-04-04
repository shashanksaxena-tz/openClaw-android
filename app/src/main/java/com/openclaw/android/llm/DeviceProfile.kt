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

    /** GPU layers: 0 for ≤6GB, 12 for ≤8GB, 24 for >8GB. */
    val gpuLayers: Int get() = when {
        totalGb <= 6 -> 0
        totalGb <= 8 -> 12
        else -> 24
    }

    /** Thread count: up to 6 performance cores. */
    val threads: Int get() = minOf(maxOf(1, cpuCores - 2), 6)

    /** Flash attention: safe on 8GB+ CPU-only (OpenCL backend can't handle it). */
    val flashAttention: Boolean get() = totalGb >= 8 && gpuLayers == 0

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
