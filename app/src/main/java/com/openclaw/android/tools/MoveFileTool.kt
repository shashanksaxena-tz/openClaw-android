package com.openclaw.android.tools

import com.openclaw.android.sandbox.SandboxedFileSystem
import kotlinx.serialization.json.*

class MoveFileTool(
    private val fs: SandboxedFileSystem,
) : Tool {

    override val name = "move_file"
    override val description = "Move or rename a file within the workspace. " +
            "Can also copy files from shared/ into workspace/."

    override val parameterSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("source") {
                put("type", "string")
                put("description", "Source file path (workspace/ or shared/ prefix)")
            }
            putJsonObject("destination") {
                put("type", "string")
                put("description", "Destination path in workspace/")
            }
            putJsonObject("copy") {
                put("type", "boolean")
                put("description", "If true, copy instead of move. Default: false")
            }
        }
        putJsonArray("required") { add("source"); add("destination") }
    }

    override suspend fun execute(arguments: JsonElement): ToolResult {
        val args = arguments.jsonObject
        val sourcePath = args["source"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("Missing 'source' parameter")
        val destPath = args["destination"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("Missing 'destination' parameter")
        val isCopy = args["copy"]?.jsonPrimitive?.booleanOrNull ?: false

        // Resolve source
        val sourceFile = when {
            sourcePath.startsWith("shared/") -> fs.resolveShared(sourcePath.removePrefix("shared/"))
            else -> fs.resolve(sourcePath.removePrefix("workspace/"))
        }.getOrElse { return ToolResult.error(it.message ?: "Invalid source path") }

        if (!sourceFile.exists()) return ToolResult.error("Source not found: $sourcePath")

        // Resolve destination (must be in workspace)
        val destFile = fs.resolve(destPath.removePrefix("workspace/"))
            .getOrElse { return ToolResult.error(it.message ?: "Invalid destination path") }

        if (!fs.isWritable(destFile)) {
            return ToolResult.error("Destination must be in workspace/")
        }

        return try {
            destFile.parentFile?.mkdirs()

            if (isCopy || !fs.isWritable(sourceFile)) {
                // Copy (always copy from shared/, can't move from there)
                sourceFile.copyTo(destFile, overwrite = true)
                ToolResult.success("Copied ${sourceFile.name} to ${destFile.relativeTo(fs.workspaceDir).path}")
            } else {
                // Move within workspace
                sourceFile.renameTo(destFile)
                ToolResult.success("Moved to ${destFile.relativeTo(fs.workspaceDir).path}")
            }
        } catch (e: Exception) {
            ToolResult.error("Failed: ${e.message}")
        }
    }
}
