package com.openclaw.android.share

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.openclaw.android.OpenClawApp
import com.openclaw.android.ui.screens.ChatScreen
import com.openclaw.android.ui.theme.OpenClawTheme

/**
 * Receives shared content from other apps (Telegram, Instagram, Threads, etc.)
 * via Android's share sheet.
 *
 * Handles:
 * - Media files (images, videos, audio)
 * - Text content
 * - URLs (from social media shares like reels, threads posts)
 * - Multiple items at once
 */
class ShareReceiverActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val (sharedText, sharedMedia) = extractSharedContent(intent)

        val app = application as OpenClawApp
        val savedMedia = sharedMedia.map { (mimeType, uri) ->
            val savedUri = saveToSharedFolder(app.sandboxedFileSystem, mimeType, uri)
            mimeType to (savedUri ?: uri)
        }

        val messageText = buildShareMessage(sharedText, sharedMedia.isEmpty())

        setContent {
            OpenClawTheme {
                ChatScreen(
                    runtime = app.agentRuntime,
                    onNavigateToSettings = { },
                    initialMessage = messageText,
                    initialMedia = savedMedia.ifEmpty { null },
                )
            }
        }
    }

    private fun extractSharedContent(intent: Intent): Pair<String?, List<Pair<String, Uri>>> {
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)

        val combinedText = listOfNotNull(subject, text).joinToString("\n").ifBlank { null }

        val media = mutableListOf<Pair<String, Uri>>()

        when (intent.action) {
            Intent.ACTION_SEND -> {
                val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                if (uri != null) {
                    val mimeType = intent.type ?: contentResolver.getType(uri) ?: "application/octet-stream"
                    media.add(mimeType to uri)
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                uris?.forEach { uri ->
                    val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
                    media.add(mimeType to uri)
                }
            }
        }

        return combinedText to media
    }

    private fun buildShareMessage(text: String?, hasNoMedia: Boolean): String? {
        if (text == null) return null

        val urlPattern = Regex("""https?://[^\s<>"{}|\\^`\[\]]+""")
        val urls = urlPattern.findAll(text).map { it.value }.toList()

        if (urls.isEmpty()) return text

        val nonUrlText = urlPattern.replace(text, "").trim()
        val isSocialMediaShare = urls.any { url ->
            SOCIAL_MEDIA_DOMAINS.any { domain -> url.contains(domain) }
        }

        return if (isSocialMediaShare && (nonUrlText.isBlank() || nonUrlText.length < 50)) {
            buildString {
                if (nonUrlText.isNotBlank()) {
                    appendLine(nonUrlText)
                    appendLine()
                }
                appendLine("I shared this link with you. Please use the web_search tool to fetch the content from the URL and tell me about it:")
                for (url in urls) {
                    appendLine(url)
                }
            }.trim()
        } else {
            text
        }
    }

    private fun saveToSharedFolder(
        fs: com.openclaw.android.sandbox.SandboxedFileSystem,
        mimeType: String,
        uri: Uri,
    ): Uri? {
        return try {
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val extension = mimeTypeToExtension(mimeType)
            val fileName = "shared_${System.currentTimeMillis()}$extension"
            val destFile = java.io.File(fs.sharedDir, fileName)

            destFile.outputStream().use { out ->
                inputStream.copyTo(out)
            }
            inputStream.close()

            Uri.fromFile(destFile)
        } catch (_: Exception) {
            null
        }
    }

    private fun mimeTypeToExtension(mimeType: String): String = when {
        mimeType.contains("jpeg") || mimeType.contains("jpg") -> ".jpg"
        mimeType.contains("png") -> ".png"
        mimeType.contains("gif") -> ".gif"
        mimeType.contains("webp") -> ".webp"
        mimeType.contains("heic") || mimeType.contains("heif") -> ".heic"
        mimeType.contains("mp4") -> ".mp4"
        mimeType.contains("webm") -> ".webm"
        mimeType.contains("mp3") -> ".mp3"
        mimeType.contains("wav") -> ".wav"
        mimeType.contains("ogg") -> ".ogg"
        mimeType.contains("m4a") -> ".m4a"
        mimeType.contains("pdf") -> ".pdf"
        mimeType.contains("text") -> ".txt"
        mimeType.contains("json") -> ".json"
        else -> ".bin"
    }

    companion object {
        private val SOCIAL_MEDIA_DOMAINS = listOf(
            "instagram.com", "threads.net", "twitter.com", "x.com",
            "tiktok.com", "youtube.com", "youtu.be", "reddit.com",
            "facebook.com", "fb.watch", "t.me", "telegram.me",
        )
    }
}
