package com.openclaw.android.llm

import android.app.ActivityManager
import android.content.Context

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

    /** Max context tokens. Conservative like off-grid: 2048 default. */
    val maxContext: Int get() = when {
        totalGb <= 6 -> 2048
        totalGb <= 12 -> 2048
        else -> 4096
    }

    /** GPU layers: DISABLED on Android (Adreno OpenCL hangs during inference). */
    val gpuLayers: Int get() = 0

    /** Thread count: 4 max (off-grid default). */
    val threads: Int get() = minOf(maxOf(1, cpuCores - 2), 4)

    /** Flash attention: DISABLED (caused hangs on SM-F966B). */
    val flashAttention: Boolean get() = false

    /** Off-grid never hard-blocks. We use 150MB absolute floor. */
    val canLoadModel: Boolean get() = availableMemMb >= MIN_RAM_MB

    companion object {
        const val MIN_RAM_MB = 150L

        /**
         * Detect device profile using Android's MemoryInfo API.
         *
         * CRITICAL: Uses ActivityManager.MemoryInfo.availMem, NOT sysinfo().freeram.
         * sysinfo().freeram only reports truly unused pages (~800MB on a 12GB device)
         * while availMem includes reclaimable cache (~5.4GB on the same device).
         * This was the root cause of "not enough RAM" errors on capable devices.
         */
        fun detect(context: Context? = null): DeviceProfile {
            val am = context?.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            am?.getMemoryInfo(memInfo)

            val totalMb = if (memInfo.totalMem > 0) {
                memInfo.totalMem / (1024 * 1024)
            } else {
                LlamaBridge.getTotalMemoryMb()
            }

            val availMb = if (memInfo.availMem > 0) {
                memInfo.availMem / (1024 * 1024)
            } else {
                // Fallback to native (less accurate but better than 0)
                LlamaBridge.getAvailableMemoryMb()
            }

            return DeviceProfile(
                totalMemMb = totalMb,
                availableMemMb = availMb,
                cpuCores = Runtime.getRuntime().availableProcessors(),
            )
        }
    }
}
