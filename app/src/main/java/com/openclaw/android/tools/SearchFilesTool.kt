package com.openclaw.android.tools

import com.openclaw.android.sandbox.SandboxedFileSystem
import kotlinx.serialization.json.*

class SearchFilesTool(
    private val fs: SandboxedFileSystem,
) : Tool {

    override val name = "search_files"
    override val description = "Search for files by name pattern or search within file contents in the workspace."

    override val parameterSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("query") {
                put("type", "string")
                put("description", "Search query — file name pattern or text to search within files")
            }
            putJsonObject("type") {
                put("type", "string")
                put("enum", JsonArray(listOf(JsonPrimitive("name"), JsonPrimitive("content"))))
                put("description", "Search type: 'name' to match file names, 'content' to search inside files. Default: 'name'")
            }
            putJsonObject("path") {
                put("type", "string")
                put("description", "Subdirectory to search within. Default: entire workspace")
            }
        }
        putJsonArray("required") { add("query") }
    }

    override suspend fun execute(arguments: JsonElement): ToolResult {
        val args = arguments.jsonObject
        val query = args["query"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("Missing 'query' parameter")
        val searchType = args["type"]?.jsonPrimitive?.contentOrNull ?: "name"
        val subPath = args["path"]?.jsonPrimitive?.contentOrNull ?: ""

        val baseDir = fs.resolve(subPath).getOrElse {
            return ToolResult.error(it.message ?: "Invalid path")
        }

        if (!baseDir.exists() || !baseDir.isDirectory) {
            return ToolResult.error("Directory not found: $subPath")
        }

        return when (searchType) {
            "name" -> searchByName(baseDir, query)
            "content" -> searchByContent(baseDir, query)
            else -> ToolResult.error("Invalid search type: $searchType (use 'name' or 'content')")
        }
    }

    private fun searchByName(baseDir: java.io.File, pattern: String): ToolResult {
        val matches = baseDir.walkTopDown()
            .filter { it.name.contains(pattern, ignoreCase = true) }
            .take(50)
            .map { it.relativeTo(fs.workspaceDir).path }
            .toList()

        return if (matches.isEmpty()) {
            ToolResult.success("No files matching '$pattern'")
        } else {
            ToolResult.success("Found ${matches.size} match(es):\n${matches.joinToString("\n")}")
        }
    }

    private fun searchByContent(baseDir: java.io.File, query: String): ToolResult {
        val results = mutableListOf<String>()

        baseDir.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() !in ReadFileTool.BINARY_EXTENSIONS }
            .filter { it.length() < 500_000 } // Skip files > 500KB
            .forEach { file ->
                try {
                    val lines = file.readLines()
                    lines.forEachIndexed { index, line ->
                        if (line.contains(query, ignoreCase = true)) {
                            val path = file.relativeTo(fs.workspaceDir).path
                            results.add("$path:${index + 1}: ${line.trim().take(120)}")
                        }
                    }
                } catch (_: Exception) {
                    // Skip unreadable files
                }

                if (results.size >= 50) return@forEach
            }

        return if (results.isEmpty()) {
            ToolResult.success("No files containing '$query'")
        } else {
            ToolResult.success("Found ${results.size} match(es):\n${results.joinToString("\n")}")
        }
    }
}
