package com.openclaw.android.llm

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.delay

/**
 * Network utilities: connectivity check, retry with exponential backoff.
 */
object NetworkHelper {

    fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /**
     * Retry a suspend block with exponential backoff.
     * @param maxRetries Maximum number of retry attempts
     * @param initialDelayMs Initial delay before first retry
     * @param maxDelayMs Maximum delay cap
     * @param shouldRetry Predicate to determine if error is retryable
     */
    suspend fun <T> withRetry(
        maxRetries: Int = 3,
        initialDelayMs: Long = 1000,
        maxDelayMs: Long = 16000,
        shouldRetry: (Exception) -> Boolean = { isRetryable(it) },
        block: suspend () -> T,
    ): T {
        var lastException: Exception? = null
        var delayMs = initialDelayMs

        repeat(maxRetries + 1) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                lastException = e
                if (attempt < maxRetries && shouldRetry(e)) {
                    delay(delayMs)
                    delayMs = (delayMs * 2).coerceAtMost(maxDelayMs)
                } else {
                    throw e
                }
            }
        }
        throw lastException ?: Exception("Retry failed")
    }

    /** Check if an error is worth retrying (transient network/server issues). */
    fun isRetryable(e: Exception): Boolean {
        val msg = e.message ?: return false
        return msg.contains("timeout", ignoreCase = true) ||
                msg.contains("Unable to resolve host", ignoreCase = true) ||
                msg.contains("Connection reset", ignoreCase = true) ||
                msg.contains("502") || msg.contains("503") || msg.contains("429") ||
                e is java.net.SocketTimeoutException ||
                e is java.net.UnknownHostException ||
                e is java.io.IOException
    }
}
