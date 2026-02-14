package com.openclaw.android.agent

/**
 * Maps raw API/network errors to user-friendly messages with actions.
 */
object ErrorHandler {

    data class UserError(
        val title: String,
        val message: String,
        val action: ErrorAction? = null,
    )

    sealed class ErrorAction {
        data object Retry : ErrorAction()
        data object OpenSettings : ErrorAction()
        data object CheckNetwork : ErrorAction()
    }

    fun mapError(exception: Exception): UserError {
        val msg = exception.message ?: "Unknown error"

        return when {
            // Network errors
            msg.contains("Unable to resolve host", ignoreCase = true) ||
            msg.contains("No address associated", ignoreCase = true) ||
            msg.contains("Network is unreachable", ignoreCase = true) ||
            exception is java.net.UnknownHostException ->
                UserError(
                    title = "No internet connection",
                    message = "Check your Wi-Fi or mobile data and try again.",
                    action = ErrorAction.CheckNetwork,
                )

            msg.contains("timeout", ignoreCase = true) ||
            msg.contains("timed out", ignoreCase = true) ||
            exception is java.net.SocketTimeoutException ->
                UserError(
                    title = "Request timed out",
                    message = "The AI server took too long to respond. Try again or switch to a faster model.",
                    action = ErrorAction.Retry,
                )

            // Rate limiting
            msg.contains("429") || msg.contains("rate limit", ignoreCase = true) ||
            msg.contains("quota", ignoreCase = true) || msg.contains("RESOURCE_EXHAUSTED", ignoreCase = true) ->
                UserError(
                    title = "Rate limit reached",
                    message = "You've sent too many requests. Wait a minute and try again, or switch to a different model.",
                    action = ErrorAction.Retry,
                )

            // Auth errors
            msg.contains("401") || msg.contains("403") ||
            msg.contains("PERMISSION_DENIED", ignoreCase = true) ||
            msg.contains("API key not valid", ignoreCase = true) ->
                UserError(
                    title = "Invalid API key",
                    message = "Your API key was rejected. Check it in Settings and make sure it's correct.",
                    action = ErrorAction.OpenSettings,
                )

            msg.contains("API key not configured", ignoreCase = true) ->
                UserError(
                    title = "No API key",
                    message = "Add an API key in Settings to start chatting.",
                    action = ErrorAction.OpenSettings,
                )

            // Model errors
            msg.contains("404") || msg.contains("not found", ignoreCase = true) ->
                UserError(
                    title = "Model not available",
                    message = "The selected model couldn't be reached. Try a different model in Settings.",
                    action = ErrorAction.OpenSettings,
                )

            // Server errors
            msg.contains("500") || msg.contains("502") || msg.contains("503") ||
            msg.contains("INTERNAL", ignoreCase = true) ->
                UserError(
                    title = "Server error",
                    message = "The AI provider is having issues. Try again in a moment.",
                    action = ErrorAction.Retry,
                )

            // Content safety
            msg.contains("SAFETY", ignoreCase = true) || msg.contains("blocked", ignoreCase = true) ->
                UserError(
                    title = "Content blocked",
                    message = "The AI provider flagged this content. Try rephrasing your message.",
                    action = null,
                )

            // Context too long
            msg.contains("context", ignoreCase = true) && msg.contains("length", ignoreCase = true) ||
            msg.contains("too many tokens", ignoreCase = true) ->
                UserError(
                    title = "Conversation too long",
                    message = "This conversation has too much content. Start a new conversation to continue.",
                    action = null,
                )

            // Generic fallback — sanitize to avoid leaking API keys or paths
            else -> UserError(
                title = "Something went wrong",
                message = sanitizeErrorMessage(msg),
                action = ErrorAction.Retry,
            )
        }
    }

    /** Strip API keys, file paths, and connection strings from raw error text. */
    private fun sanitizeErrorMessage(raw: String): String {
        var msg = raw.take(200)
        // Mask anything that looks like an API key (long alphanumeric tokens)
        msg = msg.replace(Regex("[A-Za-z0-9_-]{30,}"), "***")
        // Mask absolute file paths
        msg = msg.replace(Regex("/data/[^\\s]+"), "***")
        msg = msg.replace(Regex("/storage/[^\\s]+"), "***")
        // Mask URLs with credentials
        msg = msg.replace(Regex("key=[^&\\s]+"), "key=***")
        return msg.ifBlank { "An unexpected error occurred. Please try again." }
    }

    fun formatForChat(error: UserError): String {
        return buildString {
            append(error.title)
            append(": ")
            append(error.message)
            when (error.action) {
                ErrorAction.Retry -> append("\n\nTap 'Retry' to try again.")
                ErrorAction.OpenSettings -> append("\n\nGo to Settings to fix this.")
                ErrorAction.CheckNetwork -> append("\n\nCheck your connection and try again.")
                null -> {}
            }
        }
    }
}
