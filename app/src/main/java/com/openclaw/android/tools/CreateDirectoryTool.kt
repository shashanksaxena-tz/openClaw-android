package com.openclaw.android.tools

import com.openclaw.android.sandbox.SandboxedFileSystem
import kotlinx.serialization.json.*

class CreateDirectoryTool(
    private val fs: SandboxedFileSystem,
) : Tool {

    override val name = "create_directory"
    override val description = "Create a new directory in the workspace."

    override val parameterSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("path") {
                put("type", "string")
                put("description", "Directory path relative to workspace")
            }
        }
        putJsonArray("required") { add("path") }
    }

    override suspend fun execute(arguments: JsonElement): ToolResult {
        val path = arguments.jsonObject["path"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("Missing 'path' parameter")

        val cleanPath = path.removePrefix("workspace/")

        return fs.resolve(cleanPath).fold(
            onSuccess = { dir ->
                if (!fs.isWritable(dir)) {
                    return ToolResult.error("Cannot create directories outside workspace")
                }
                if (dir.exists()) {
                    return ToolResult.success("Directory already exists: $cleanPath")
                }

                try {
                    dir.mkdirs()
                    ToolResult.success("Created directory: $cleanPath")
                } catch (e: Exception) {
                    ToolResult.error("Failed to create directory: ${e.message}")
                }
            },
            onFailure = { ToolResult.error(it.message ?: "Path not allowed") }
        )
    }
}
