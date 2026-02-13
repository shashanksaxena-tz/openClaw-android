package com.openclaw.android.tools

import com.openclaw.android.llm.ToolDefinition

/**
 * Registry of all tools available to the agent.
 * Tools are registered at startup and their definitions are sent to the LLM.
 */
class ToolRegistry {

    private val tools = mutableMapOf<String, Tool>()

    fun register(tool: Tool) {
        tools[tool.name] = tool
    }

    fun get(name: String): Tool? = tools[name]

    fun getAll(): List<Tool> = tools.values.toList()

    fun getDefinitions(): List<ToolDefinition> = tools.values.map { it.toDefinition() }
}
