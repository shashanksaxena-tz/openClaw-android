package com.openclaw.android.tools

import com.openclaw.android.data.MemorySystem
import kotlinx.serialization.json.*

class MemoryTool(private val memory: MemorySystem) : Tool {

    override val name = "memory"
    override val description = "Store and recall persistent facts about the user across conversations. " +
            "Use this to remember user preferences, important facts, people, habits, and notes. " +
            "Actions: 'remember' (store a fact), 'recall' (search memories), 'list' (list all), " +
            "'forget' (delete a memory), 'stats' (memory statistics)."

    override val parameterSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                put("enum", buildJsonArray { add("remember"); add("recall"); add("list"); add("forget"); add("stats") })
                put("description", "Memory action to perform")
            }
            putJsonObject("category") {
                put("type", "string")
                put("enum", buildJsonArray { add("preference"); add("fact"); add("person"); add("habit"); add("note") })
                put("description", "Category of the memory")
            }
            putJsonObject("key") {
                put("type", "string")
                put("description", "Short identifier for the memory (e.g. 'favorite_color', 'wife_name', 'dietary_restriction')")
            }
            putJsonObject("value") {
                put("type", "string")
                put("description", "The content to remember")
            }
            putJsonObject("query") {
                put("type", "string")
                put("description", "Search query to recall memories")
            }
        }
        putJsonArray("required") { add("action") }
    }

    override suspend fun execute(arguments: JsonElement): ToolResult {
        val args = arguments.jsonObject
        val action = args["action"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("Missing 'action' parameter")

        return try {
            when (action) {
                "remember" -> {
                    val category = args["category"]?.jsonPrimitive?.contentOrNull ?: "note"
                    val key = args["key"]?.jsonPrimitive?.contentOrNull
                        ?: return ToolResult.error("Missing 'key'")
                    val value = args["value"]?.jsonPrimitive?.contentOrNull
                        ?: return ToolResult.error("Missing 'value'")
                    val entity = memory.remember(category, key, value)
                    ToolResult.success("Remembered [$category] $key: $value")
                }
                "recall" -> {
                    val query = args["query"]?.jsonPrimitive?.contentOrNull
                        ?: args["key"]?.jsonPrimitive?.contentOrNull
                        ?: return ToolResult.error("Missing 'query' or 'key'")
                    val results = memory.recall(query)
                    if (results.isEmpty()) {
                        ToolResult.success("No memories matching '$query'.")
                    } else {
                        val sb = StringBuilder("Found ${results.size} memories:\n\n")
                        results.forEach { m ->
                            sb.append("- [${m.category}] **${m.key}**: ${m.value}\n")
                            sb.append("  (learned: ${m.source}, accessed ${m.accessCount} times)\n")
                        }
                        ToolResult.success(sb.toString())
                    }
                }
                "list" -> {
                    val category = args["category"]?.jsonPrimitive?.contentOrNull
                    val memories = if (category != null) memory.recallByCategory(category) else memory.getAll()
                    if (memories.isEmpty()) {
                        ToolResult.success("No memories stored${if (category != null) " in '$category'" else ""}.")
                    } else {
                        val sb = StringBuilder("All memories${if (category != null) " ($category)" else ""} (${memories.size}):\n\n")
                        val grouped = memories.groupBy { it.category }
                        grouped.forEach { (cat, items) ->
                            sb.append("**$cat** (${items.size}):\n")
                            items.forEach { sb.append("  - ${it.key}: ${it.value}\n") }
                            sb.append("\n")
                        }
                        ToolResult.success(sb.toString())
                    }
                }
                "forget" -> {
                    val key = args["key"]?.jsonPrimitive?.contentOrNull
                        ?: return ToolResult.error("Missing 'key' to forget")
                    memory.forget(key)
                    ToolResult.success("Forgotten: $key")
                }
                "stats" -> {
                    val count = memory.count()
                    val all = memory.getAll()
                    val categories = all.groupBy { it.category }.mapValues { it.value.size }
                    val sb = StringBuilder("Memory statistics:\n")
                    sb.append("- Total memories: $count\n")
                    categories.forEach { (cat, num) -> sb.append("- $cat: $num\n") }
                    ToolResult.success(sb.toString())
                }
                else -> ToolResult.error("Unknown action: $action")
            }
        } catch (e: Exception) {
            ToolResult.error("Memory error: ${e.message}")
        }
    }
}
