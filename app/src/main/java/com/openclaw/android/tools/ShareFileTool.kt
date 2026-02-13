package com.openclaw.android.tools

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.openclaw.android.sandbox.SandboxedFileSystem
import kotlinx.serialization.json.*

/**
 * Allows the agent to share files from workspace with other apps
 * via Android's share intent system.
 */
class ShareFileTool(
    private val context: Context,
    private val fs: SandboxedFileSystem,
) : Tool {

    override val name = "share_file"
    override val description = "Share a file from the workspace with another app on the device. " +
            "This opens the Android share sheet so the user can pick where to send the file."

    override val parameterSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("path") {
                put("type", "string")
                put("description", "File path relative to workspace/")
            }
        }
        putJsonArray("required") { add("path") }
    }

    override suspend fun execute(arguments: JsonElement): ToolResult {
        val path = arguments.jsonObject["path"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("Missing 'path' parameter")

        val cleanPath = path.removePrefix("workspace/")
        val file = fs.resolve(cleanPath).getOrElse {
            return ToolResult.error(it.message ?: "Invalid path")
        }

        if (!file.exists()) return ToolResult.error("File not found: $cleanPath")
        if (file.isDirectory) return ToolResult.error("Cannot share a directory")

        return try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )

            val mimeType = getMimeType(file.extension)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(Intent.createChooser(shareIntent, "Share ${file.name}").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })

            ToolResult.success("Share sheet opened for: ${file.name}")
        } catch (e: Exception) {
            ToolResult.error("Failed to share: ${e.message}")
        }
    }

    private fun getMimeType(extension: String): String = when (extension.lowercase()) {
        "txt" -> "text/plain"
        "md" -> "text/markdown"
        "html", "htm" -> "text/html"
        "csv" -> "text/csv"
        "json" -> "application/json"
        "pdf" -> "application/pdf"
        "doc" -> "application/msword"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "mp4" -> "video/mp4"
        "mp3" -> "audio/mpeg"
        else -> "application/octet-stream"
    }
}
