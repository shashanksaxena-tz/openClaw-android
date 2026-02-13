package com.openclaw.android.tools

import com.openclaw.android.sandbox.SandboxedFileSystem
import kotlinx.serialization.json.*

class WriteFileTool(
    private val fs: SandboxedFileSystem,
) : Tool {

    override val name = "write_file"
    override val description = "Write content to a file in the workspace directory. " +
            "Creates parent directories if needed. Cannot write to the shared/ directory."

    override val parameterSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("path") {
                put("type", "string")
                put("description", "File path relative to the workspace directory")
            }
            putJsonObject("content") {
                put("type", "string")
                put("description", "The content to write to the file")
            }
            putJsonObject("append") {
                put("type", "boolean")
                put("description", "If true, append to existing file instead of overwriting. Default: false")
            }
        }
        putJsonArray("required") { add("path"); add("content") }
    }

    override suspend fun execute(arguments: JsonElement): ToolResult {
        val args = arguments.jsonObject
        val path = args["path"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("Missing 'path' parameter")
        val content = args["content"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("Missing 'content' parameter")
        val append = args["append"]?.jsonPrimitive?.booleanOrNull ?: false

        // Strip workspace/ prefix if present
        val cleanPath = path.removePrefix("workspace/")

        return fs.resolve(cleanPath).fold(
            onSuccess = { file ->
                if (!fs.isWritable(file)) {
                    return ToolResult.error("Cannot write to this location. Only workspace/ is writable.")
                }

                // Check available disk space
                val freeSpace = file.parentFile?.usableSpace ?: 0
                if (freeSpace < content.length * 2) {
                    return ToolResult.error("Not enough disk space. Available: ${freeSpace / 1024}KB, needed: ~${content.length / 1024}KB")
                }

                try {
                    file.parentFile?.mkdirs()
                    if (append) {
                        file.appendText(content)
                    } else {
                        file.writeText(content)
                    }
                    ToolResult.success("Written ${content.length} chars to $cleanPath")
                } catch (e: Exception) {
                    ToolResult.error("Failed to write: ${e.message}")
                }
            },
            onFailure = { ToolResult.error(it.message ?: "Path not allowed") }
        )
    }
}
