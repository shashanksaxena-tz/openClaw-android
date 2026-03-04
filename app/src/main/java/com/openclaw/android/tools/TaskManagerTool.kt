package com.openclaw.android.tools

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Task & Action Management tool.
 * Converts notes into tasks with priorities, deadlines, dependencies,
 * and grouping by project or person.
 */
class TaskManagerTool(context: Context) : Tool {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("task_manager", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class Task(
        val id: String,
        val title: String,
        val description: String = "",
        val priority: String = "medium",
        val status: String = "pending",
        val project: String = "general",
        val assignedTo: String = "",
        val createdAt: Long,
        val deadline: Long? = null,
        val completedAt: Long? = null,
        val dependencies: List<String> = emptyList(),
        val tags: List<String> = emptyList(),
    )

    override val name = "task_manager"
    override val description = "Manage tasks with priorities, deadlines, dependencies, and project grouping. " +
            "Actions: 'create' (new task), 'list' (list tasks with filters), 'complete' (mark done), " +
            "'update' (modify task), 'delete' (remove task), 'prioritize' (show prioritized view), " +
            "'by_project' (group by project), 'by_person' (group by assigned person), " +
            "'overdue' (show overdue tasks), 'stats' (task statistics)."

    override val parameterSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                put("enum", buildJsonArray {
                    add("create"); add("list"); add("complete"); add("update")
                    add("delete"); add("prioritize"); add("by_project")
                    add("by_person"); add("overdue"); add("stats")
                })
                put("description", "Task action to perform")
            }
            putJsonObject("title") {
                put("type", "string")
                put("description", "Task title")
            }
            putJsonObject("description") {
                put("type", "string")
                put("description", "Task description/details")
            }
            putJsonObject("priority") {
                put("type", "string")
                put("enum", buildJsonArray { add("high"); add("medium"); add("low") })
                put("description", "Task priority")
            }
            putJsonObject("project") {
                put("type", "string")
                put("description", "Project name for grouping")
            }
            putJsonObject("assigned_to") {
                put("type", "string")
                put("description", "Person assigned to this task")
            }
            putJsonObject("deadline") {
                put("type", "string")
                put("description", "Deadline in ISO 8601: 2024-03-15 or 2024-03-15T14:00")
            }
            putJsonObject("task_id") {
                put("type", "string")
                put("description", "Task ID for update/complete/delete")
            }
            putJsonObject("dependencies") {
                put("type", "string")
                put("description", "Comma-separated task IDs this depends on")
            }
            putJsonObject("tags") {
                put("type", "string")
                put("description", "Comma-separated tags")
            }
            putJsonObject("status") {
                put("type", "string")
                put("enum", buildJsonArray { add("pending"); add("in_progress"); add("done"); add("blocked") })
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
                "create" -> createTask(args)
                "list" -> listTasks(args)
                "complete" -> completeTask(args)
                "update" -> updateTask(args)
                "delete" -> deleteTask(args)
                "prioritize" -> prioritizedView()
                "by_project" -> byProject()
                "by_person" -> byPerson()
                "overdue" -> overdueView()
                "stats" -> taskStats()
                else -> ToolResult.error("Unknown action: $action")
            }
        } catch (e: Exception) {
            ToolResult.error("Task manager error: ${e.message}")
        }
    }

    private fun createTask(args: JsonObject): ToolResult {
        val title = args["title"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("Missing 'title'")

        val id = UUID.randomUUID().toString().take(8)
        val task = Task(
            id = id,
            title = title,
            description = args["description"]?.jsonPrimitive?.contentOrNull ?: "",
            priority = args["priority"]?.jsonPrimitive?.contentOrNull ?: "medium",
            project = args["project"]?.jsonPrimitive?.contentOrNull ?: "general",
            assignedTo = args["assigned_to"]?.jsonPrimitive?.contentOrNull ?: "",
            createdAt = System.currentTimeMillis(),
            deadline = args["deadline"]?.jsonPrimitive?.contentOrNull?.let { parseDate(it) },
            dependencies = args["dependencies"]?.jsonPrimitive?.contentOrNull
                ?.split(",")?.map { it.trim() } ?: emptyList(),
            tags = args["tags"]?.jsonPrimitive?.contentOrNull
                ?.split(",")?.map { it.trim() } ?: emptyList(),
        )

        val tasks = getAllTasks().toMutableList()
        tasks.add(task)
        saveAllTasks(tasks)

        val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())
        val sb = StringBuilder("Task created (ID: $id)\n")
        sb.append("Title: $title\n")
        sb.append("Priority: ${task.priority} | Project: ${task.project}\n")
        if (task.deadline != null) sb.append("Deadline: ${dateFormat.format(Date(task.deadline))}\n")
        if (task.assignedTo.isNotBlank()) sb.append("Assigned to: ${task.assignedTo}\n")

        return ToolResult.success(sb.toString())
    }

    private fun listTasks(args: JsonObject): ToolResult {
        val status = args["status"]?.jsonPrimitive?.contentOrNull
        val project = args["project"]?.jsonPrimitive?.contentOrNull
        val priority = args["priority"]?.jsonPrimitive?.contentOrNull

        var tasks = getAllTasks()
        if (status != null) tasks = tasks.filter { it.status == status }
        if (project != null) tasks = tasks.filter { it.project.equals(project, ignoreCase = true) }
        if (priority != null) tasks = tasks.filter { it.priority == priority }

        tasks = tasks.sortedWith(compareBy<Task> {
            when (it.priority) { "high" -> 0; "medium" -> 1; else -> 2 }
        }.thenBy { it.deadline ?: Long.MAX_VALUE })

        if (tasks.isEmpty()) return ToolResult.success("No tasks found.")

        return ToolResult.success(formatTaskList(tasks, "Tasks"))
    }

    private fun completeTask(args: JsonObject): ToolResult {
        val taskId = args["task_id"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("Missing 'task_id'")

        val tasks = getAllTasks().toMutableList()
        val index = tasks.indexOfFirst { it.id == taskId }
        if (index == -1) return ToolResult.error("Task '$taskId' not found.")

        tasks[index] = tasks[index].copy(status = "done", completedAt = System.currentTimeMillis())
        saveAllTasks(tasks)
        return ToolResult.success("Task '${tasks[index].title}' marked as complete!")
    }

    private fun updateTask(args: JsonObject): ToolResult {
        val taskId = args["task_id"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("Missing 'task_id'")

        val tasks = getAllTasks().toMutableList()
        val index = tasks.indexOfFirst { it.id == taskId }
        if (index == -1) return ToolResult.error("Task '$taskId' not found.")

        val t = tasks[index]
        tasks[index] = t.copy(
            title = args["title"]?.jsonPrimitive?.contentOrNull ?: t.title,
            description = args["description"]?.jsonPrimitive?.contentOrNull ?: t.description,
            priority = args["priority"]?.jsonPrimitive?.contentOrNull ?: t.priority,
            project = args["project"]?.jsonPrimitive?.contentOrNull ?: t.project,
            assignedTo = args["assigned_to"]?.jsonPrimitive?.contentOrNull ?: t.assignedTo,
            status = args["status"]?.jsonPrimitive?.contentOrNull ?: t.status,
            deadline = args["deadline"]?.jsonPrimitive?.contentOrNull?.let { parseDate(it) } ?: t.deadline,
        )

        saveAllTasks(tasks)
        return ToolResult.success("Task '$taskId' updated.")
    }

    private fun deleteTask(args: JsonObject): ToolResult {
        val taskId = args["task_id"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("Missing 'task_id'")

        val tasks = getAllTasks().toMutableList()
        val removed = tasks.removeAll { it.id == taskId }
        if (!removed) return ToolResult.error("Task '$taskId' not found.")

        saveAllTasks(tasks)
        return ToolResult.success("Task '$taskId' deleted.")
    }

    private fun prioritizedView(): ToolResult {
        val tasks = getAllTasks().filter { it.status != "done" }
            .sortedWith(compareBy<Task> {
                when (it.priority) { "high" -> 0; "medium" -> 1; else -> 2 }
            }.thenBy { it.deadline ?: Long.MAX_VALUE })

        if (tasks.isEmpty()) return ToolResult.success("No pending tasks! All caught up.")
        return ToolResult.success(formatTaskList(tasks, "Prioritized Tasks"))
    }

    private fun byProject(): ToolResult {
        val tasks = getAllTasks().filter { it.status != "done" }
        val grouped = tasks.groupBy { it.project }

        if (grouped.isEmpty()) return ToolResult.success("No pending tasks.")

        val sb = StringBuilder("Tasks by Project:\n\n")
        for ((project, projectTasks) in grouped) {
            sb.append("### $project (${projectTasks.size} tasks)\n")
            for (t in projectTasks.sortedBy { it.deadline ?: Long.MAX_VALUE }) {
                val icon = priorityIcon(t.priority)
                sb.append("  $icon ${t.title} (${t.status})\n")
            }
            sb.append("\n")
        }
        return ToolResult.success(sb.toString())
    }

    private fun byPerson(): ToolResult {
        val tasks = getAllTasks().filter { it.status != "done" && it.assignedTo.isNotBlank() }
        val grouped = tasks.groupBy { it.assignedTo }

        if (grouped.isEmpty()) return ToolResult.success("No tasks assigned to anyone.")

        val sb = StringBuilder("Tasks by Person:\n\n")
        for ((person, personTasks) in grouped) {
            sb.append("### $person (${personTasks.size} tasks)\n")
            for (t in personTasks) {
                val icon = priorityIcon(t.priority)
                sb.append("  $icon ${t.title} [${t.project}]\n")
            }
            sb.append("\n")
        }
        return ToolResult.success(sb.toString())
    }

    private fun overdueView(): ToolResult {
        val now = System.currentTimeMillis()
        val tasks = getAllTasks().filter {
            it.status != "done" && it.deadline != null && it.deadline < now
        }.sortedBy { it.deadline }

        if (tasks.isEmpty()) return ToolResult.success("No overdue tasks!")
        return ToolResult.success(formatTaskList(tasks, "OVERDUE Tasks"))
    }

    private fun taskStats(): ToolResult {
        val tasks = getAllTasks()
        val pending = tasks.count { it.status == "pending" }
        val inProgress = tasks.count { it.status == "in_progress" }
        val done = tasks.count { it.status == "done" }
        val blocked = tasks.count { it.status == "blocked" }
        val overdue = tasks.count {
            it.status != "done" && it.deadline != null && it.deadline < System.currentTimeMillis()
        }
        val high = tasks.count { it.status != "done" && it.priority == "high" }

        val completionRate = if (tasks.isNotEmpty()) (done * 100 / tasks.size) else 0

        // Tasks completed this week
        val weekAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        val completedThisWeek = tasks.count {
            it.status == "done" && (it.completedAt ?: 0) > weekAgo
        }

        val sb = StringBuilder("Task Statistics:\n\n")
        sb.append("Total: ${tasks.size} | Completion rate: $completionRate%\n")
        sb.append("Pending: $pending | In Progress: $inProgress | Done: $done | Blocked: $blocked\n")
        sb.append("High priority (open): $high | Overdue: $overdue\n")
        sb.append("Completed this week: $completedThisWeek\n\n")

        sb.append("Projects: ${tasks.map { it.project }.distinct().size}\n")
        sb.append("People involved: ${tasks.filter { it.assignedTo.isNotBlank() }.map { it.assignedTo }.distinct().size}\n")

        return ToolResult.success(sb.toString())
    }

    private fun formatTaskList(tasks: List<Task>, header: String): String {
        val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())
        val sb = StringBuilder("$header (${tasks.size}):\n\n")

        for (t in tasks.take(30)) {
            val icon = priorityIcon(t.priority)
            val status = when (t.status) {
                "done" -> "[DONE]"
                "in_progress" -> "[IN PROGRESS]"
                "blocked" -> "[BLOCKED]"
                else -> ""
            }
            sb.append("$icon **${t.title}** ${status} (ID: ${t.id})\n")
            if (t.description.isNotBlank()) sb.append("  ${t.description.take(80)}\n")
            sb.append("  Project: ${t.project}")
            if (t.assignedTo.isNotBlank()) sb.append(" | Assigned: ${t.assignedTo}")
            if (t.deadline != null) sb.append(" | Due: ${dateFormat.format(Date(t.deadline))}")
            sb.append("\n\n")
        }

        return sb.toString()
    }

    private fun priorityIcon(priority: String) = when (priority) {
        "high" -> "!!!"
        "low" -> " - "
        else -> " * "
    }

    private fun parseDate(dateStr: String): Long? {
        val formats = listOf(
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault()),
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()),
        )
        for (fmt in formats) {
            try { return fmt.parse(dateStr)?.time } catch (_: Exception) {}
        }
        return null
    }

    private fun getAllTasks(): List<Task> {
        val raw = prefs.getString("tasks_data", "[]") ?: "[]"
        return try {
            json.decodeFromString<List<Task>>(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveAllTasks(tasks: List<Task>) {
        prefs.edit().putString("tasks_data", json.encodeToString(tasks)).apply()
    }
}
