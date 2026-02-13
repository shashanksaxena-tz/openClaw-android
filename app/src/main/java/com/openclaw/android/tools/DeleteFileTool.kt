package com.openclaw.android.tools

import com.openclaw.android.sandbox.SandboxedFileSystem
import kotlinx.serialization.json.*

class DeleteFileTool(
    private val fs: SandboxedFileSystem,
) : Tool {

    override val name = "delete_file"
    override val description = "Delete a file or empty directory from the workspace. Cannot delete from shared/."

    override val parameterSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("path") {
                put("type", "string")
                put("description", "File or directory path relative to workspace")
            }
        }
        putJsonArray("required") { add("path") }
    }

    override suspend fun execute(arguments: JsonElement): ToolResult {
        val path = arguments.jsonObject["path"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("Missing 'path' parameter")

        val cleanPath = path.removePrefix("workspace/")

        return fs.resolve(cleanPath).fold(
            onSuccess = { file ->
                if (!fs.isWritable(file)) {
                    return ToolResult.error("Cannot delete from this location")
                }
                if (!file.exists()) {
                    return ToolResult.error("File not found: $cleanPath")
                }

                try {
                    if (file.isDirectory) {
                        if (file.list()?.isNotEmpty() == true) {
                            return ToolResult.error("Directory is not empty. Delete contents first.")
                        }
                        file.delete()
                    } else {
                        file.delete()
                    }
                    ToolResult.success("Deleted: $cleanPath")
                } catch (e: Exception) {
                    ToolResult.error("Failed to delete: ${e.message}")
                }
            },
            onFailure = { ToolResult.error(it.message ?: "Path not allowed") }
        )
    }
}
