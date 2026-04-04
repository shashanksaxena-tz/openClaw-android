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

    /** Max context tokens. Conservative like off-grid: always at least 2048. */
    val maxContext: Int get() = when {
        totalGb <= 6 -> 2048
        totalGb <= 12 -> 2048
        else -> 4096
    }

    /**
     * GPU layers: DISABLED on Android by default.
     * Adreno OpenCL backend loads models fine but hangs during llama_decode().
     * Confirmed on SM-F966B (Fold 6, Adreno 750, 11GB RAM) — model loads with
     * gpu=24 in 8s but inference never produces a token.
     * Off-grid also defaults to GPU=0 on Android for the same reason.
     */
    val gpuLayers: Int get() = 0

    /** Thread count: 4 max (off-grid default). Over-threading onto efficiency cores hurts. */
    val threads: Int get() = minOf(maxOf(1, cpuCores - 2), 4)

    /**
     * Flash attention: DISABLED by default.
     * Was causing hangs on SM-F966B even with cpu-only + q8_0 KV.
     * Off-grid enables it but they use llama.rn which may handle it differently.
     * Can be re-enabled once confirmed working on real devices.
     */
    val flashAttention: Boolean get() = false

    /** Whether this device has enough RAM to attempt model loading.
     *  Off-grid only warns, never blocks. We use 150MB absolute floor. */
    val canLoadModel: Boolean get() = availableMemMb >= MIN_RAM_MB

    companion object {
        const val MIN_RAM_MB = 150L

        fun detect(): DeviceProfile = DeviceProfile(
            totalMemMb = LlamaBridge.getTotalMemoryMb(),
            availableMemMb = LlamaBridge.getAvailableMemoryMb(),
            cpuCores = Runtime.getRuntime().availableProcessors(),
        )
    }
}
