package com.openclaw.android.tools

import com.openclaw.android.llm.ToolDefinition
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Base interface for all tools available to the agent.
 * Each tool defines its schema (for the LLM) and execution logic.
 */
interface Tool {
    val name: String
    val description: String
    val parameterSchema: JsonObject

    /** Android permissions this tool needs at runtime (e.g. Manifest.permission.READ_CALENDAR). */
    val requiredPermissions: List<String> get() = emptyList()

    /** Execute the tool with the given arguments. Returns the result as a string. */
    suspend fun execute(arguments: JsonElement): ToolResult

    /** Convert to the LLM tool definition format. */
    fun toDefinition(): ToolDefinition = ToolDefinition(
        name = name,
        description = description,
        parameters = parameterSchema,
    )
}

data class ToolResult(
    val output: String,
    val isError: Boolean = false,
) {
    companion object {
        fun success(output: String) = ToolResult(output, isError = false)
        fun error(message: String) = ToolResult("Error: $message", isError = true)
    }
}
