package com.openclaw.android.tools

import com.openclaw.android.sandbox.SandboxedFileSystem
import kotlinx.serialization.json.*

class ListFilesTool(
    private val fs: SandboxedFileSystem,
) : Tool {

    override val name = "list_files"
    override val description = "List files and directories. Use 'workspace/' to list workspace files, " +
            "'shared/' to list shared media, or a subdirectory path."

    override val parameterSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("path") {
                put("type", "string")
                put("description", "Directory path to list. Default: workspace root. Use 'shared/' for shared media.")
            }
        }
    }

    override suspend fun execute(arguments: JsonElement): ToolResult {
        val path = arguments.jsonObject["path"]?.jsonPrimitive?.contentOrNull ?: ""

        val files = when {
            path == "shared" || path == "shared/" || path.startsWith("shared/") -> {
                fs.listShared()
            }
            else -> {
                val cleanPath = path.removePrefix("workspace/").removePrefix("/")
                fs.listWorkspace(cleanPath).getOrElse {
                    return ToolResult.error(it.message ?: "Failed to list directory")
                }
            }
        }

        if (files.isEmpty()) {
            return ToolResult.success("(empty directory)")
        }

        val listing = files.joinToString("\n") { info ->
            val icon = if (info.isDirectory) "[dir]" else "[file]"
            val size = if (info.isDirectory) "" else " (${formatSize(info.size)})"
            "$icon ${info.path}$size"
        }

        return ToolResult.success(listing)
    }

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${"%.1f".format(bytes.toDouble() / (1024 * 1024))} MB"
    }
}
