package com.openclaw.android.tools

import com.openclaw.android.sandbox.SandboxedFileSystem
import kotlinx.serialization.json.*

class ReadFileTool(
    private val fs: SandboxedFileSystem,
) : Tool {

    override val name = "read_file"
    override val description = "Read the contents of a file from the workspace or shared directory. " +
            "Use 'workspace/' prefix for workspace files or 'shared/' prefix for shared media files."

    override val parameterSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("path") {
                put("type", "string")
                put("description", "File path relative to workspace/ or shared/ directory")
            }
        }
        putJsonArray("required") { add("path") }
    }

    override suspend fun execute(arguments: JsonElement): ToolResult {
        val path = arguments.jsonObject["path"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("Missing 'path' parameter")

        val file = when {
            path.startsWith("shared/") -> fs.resolveShared(path.removePrefix("shared/"))
            path.startsWith("workspace/") -> fs.resolve(path.removePrefix("workspace/"))
            else -> fs.resolve(path)
        }

        return file.fold(
            onSuccess = { f ->
                if (!f.exists()) return ToolResult.error("File not found: $path")
                if (f.isDirectory) return ToolResult.error("Path is a directory, not a file: $path")
                if (f.length() > 1_000_000) return ToolResult.error("File too large (>${f.length()} bytes). Max 1MB for text read.")

                // Check if it's a binary file
                val extension = f.extension.lowercase()
                if (extension in BINARY_EXTENSIONS) {
                    return ToolResult.success("Binary file: ${f.name} (${f.length()} bytes, type: $extension)")
                }

                try {
                    ToolResult.success(f.readText())
                } catch (e: Exception) {
                    ToolResult.error("Failed to read file: ${e.message}")
                }
            },
            onFailure = { ToolResult.error(it.message ?: "Path not allowed") }
        )
    }

    companion object {
        val BINARY_EXTENSIONS = setOf(
            "jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "heif",
            "mp4", "mkv", "avi", "mov", "webm",
            "mp3", "wav", "ogg", "m4a", "aac", "flac",
            "zip", "tar", "gz", "7z", "rar",
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "apk", "exe", "so", "dll",
        )
    }
}
