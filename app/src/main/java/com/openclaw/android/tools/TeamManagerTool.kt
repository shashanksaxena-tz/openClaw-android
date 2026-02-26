package com.openclaw.android.tools

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Team Management & Leadership Dashboard tool.
 * Track team members, their strengths/weaknesses, performance,
 * delegation, coaching notes, and interaction history.
 */
class TeamManagerTool(context: Context) : Tool {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("team_manager", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class TeamMember(
        val id: String,
        val name: String,
        val role: String,
        val strengths: List<String> = emptyList(),
        val weaknesses: List<String> = emptyList(),
        val goals: List<String> = emptyList(),
        val addedAt: Long,
    )

    @Serializable
    data class PerformanceNote(
        val id: String,
        val memberId: String,
        val type: String,
        val content: String,
        val date: Long,
    )

    @Serializable
    data class Delegation(
        val id: String,
        val memberId: String,
        val task: String,
        val deadline: Long? = null,
        val status: String = "assigned",
        val assignedAt: Long,
        val completedAt: Long? = null,
    )

    override val name = "team_manager"
    override val description = "Manage team members, track performance, delegate tasks, and maintain coaching notes. " +
            "Actions: 'add_member' (add team member with role/strengths), 'list_team' (view all members), " +
            "'view_member' (detailed member profile), 'add_note' (performance/coaching note), " +
            "'delegate' (assign task to member), 'complete_delegation' (mark delegated task done), " +
            "'update_member' (update strengths/goals), 'remove_member' (remove from team), " +
            "'dashboard' (leadership overview), 'check_in_due' (who needs a check-in)."

    override val parameterSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                put("enum", buildJsonArray {
                    add("add_member"); add("list_team"); add("view_member")
                    add("add_note"); add("delegate"); add("complete_delegation")
                    add("update_member"); add("remove_member")
                    add("dashboard"); add("check_in_due")
                })
            }
            putJsonObject("member_id") { put("type", "string"); put("description", "Team member ID") }
            putJsonObject("name") { put("type", "string"); put("description", "Member's name") }
            putJsonObject("role") { put("type", "string"); put("description", "Member's role/title") }
            putJsonObject("strengths") { put("type", "string"); put("description", "Comma-separated strengths") }
            putJsonObject("weaknesses") { put("type", "string"); put("description", "Comma-separated areas for improvement") }
            putJsonObject("goals") { put("type", "string"); put("description", "Comma-separated goals") }
            putJsonObject("note_type") {
                put("type", "string")
                put("enum", buildJsonArray {
                    add("performance"); add("coaching"); add("feedback")
                    add("observation"); add("praise"); add("concern")
                })
            }
            putJsonObject("content") { put("type", "string"); put("description", "Note content or task description") }
            putJsonObject("task") { put("type", "string"); put("description", "Delegated task description") }
            putJsonObject("deadline") { put("type", "string"); put("description", "Deadline (ISO 8601)") }
            putJsonObject("delegation_id") { put("type", "string"); put("description", "Delegation ID") }
        }
        putJsonArray("required") { add("action") }
    }

    override suspend fun execute(arguments: JsonElement): ToolResult {
        val args = arguments.jsonObject
        val action = args["action"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("Missing 'action' parameter")

        return try {
            when (action) {
                "add_member" -> addMember(args)
                "list_team" -> listTeam()
                "view_member" -> viewMember(args)
                "add_note" -> addNote(args)
                "delegate" -> delegateTask(args)
                "complete_delegation" -> completeDelegation(args)
                "update_member" -> updateMember(args)
                "remove_member" -> removeMember(args)
                "dashboard" -> dashboard()
                "check_in_due" -> checkInDue()
                else -> ToolResult.error("Unknown action: $action")
            }
        } catch (e: Exception) {
            ToolResult.error("Team manager error: ${e.message}")
        }
    }

    private fun addMember(args: JsonObject): ToolResult {
        val name = args["name"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("Missing 'name'")
        val role = args["role"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("Missing 'role'")

        val id = UUID.randomUUID().toString().take(8)
        val member = TeamMember(
            id = id, name = name, role = role,
            strengths = args["strengths"]?.jsonPrimitive?.contentOrNull
                ?.split(",")?.map { it.trim() } ?: emptyList(),
            weaknesses = args["weaknesses"]?.jsonPrimitive?.contentOrNull
                ?.split(",")?.map { it.trim() } ?: emptyList(),
            goals = args["goals"]?.jsonPrimitive?.contentOrNull
                ?.split(",")?.map { it.trim() } ?: emptyList(),
            addedAt = System.currentTimeMillis(),
        )

        val members = getAllMembers().toMutableList()
        members.add(member)
        saveMembers(members)

        return ToolResult.success(
            "Team member added (ID: $id)\n" +
            "Name: $name | Role: $role\n" +
            if (member.strengths.isNotEmpty()) "Strengths: ${member.strengths.joinToString(", ")}\n" else ""
        )
    }

    private fun listTeam(): ToolResult {
        val members = getAllMembers()
        if (members.isEmpty()) return ToolResult.success("No team members added yet.")

        val sb = StringBuilder("Team (${members.size} members):\n\n")
        for (m in members) {
            val delegations = getAllDelegations().count { it.memberId == m.id && it.status == "assigned" }
            sb.append("- **${m.name}** — ${m.role} (ID: ${m.id})\n")
            if (m.strengths.isNotEmpty()) sb.append("  Strengths: ${m.strengths.joinToString(", ")}\n")
            if (delegations > 0) sb.append("  Active tasks: $delegations\n")
            sb.append("\n")
        }
        return ToolResult.success(sb.toString())
    }

    private fun viewMember(args: JsonObject): ToolResult {
        val memberId = args["member_id"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("Missing 'member_id'")

        val member = getAllMembers().find { it.id == memberId }
            ?: return ToolResult.error("Member '$memberId' not found.")

        val notes = getAllNotes().filter { it.memberId == memberId }.sortedByDescending { it.date }
        val delegations = getAllDelegations().filter { it.memberId == memberId }
        val df = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

        val sb = StringBuilder("## ${member.name}\n")
        sb.append("Role: ${member.role}\n\n")

        if (member.strengths.isNotEmpty()) sb.append("**Strengths**: ${member.strengths.joinToString(", ")}\n")
        if (member.weaknesses.isNotEmpty()) sb.append("**Areas for improvement**: ${member.weaknesses.joinToString(", ")}\n")
        if (member.goals.isNotEmpty()) sb.append("**Goals**: ${member.goals.joinToString(", ")}\n")
        sb.append("\n")

        val activeDelegations = delegations.filter { it.status == "assigned" }
        val completedDelegations = delegations.filter { it.status == "done" }
        sb.append("**Delegations**: ${activeDelegations.size} active, ${completedDelegations.size} completed\n")
        for (d in activeDelegations) {
            sb.append("  - ${d.task}")
            if (d.deadline != null) sb.append(" (due: ${df.format(Date(d.deadline))})")
            sb.append("\n")
        }
        sb.append("\n")

        if (notes.isNotEmpty()) {
            sb.append("**Recent Notes** (${notes.size} total):\n")
            for (n in notes.take(10)) {
                sb.append("  [${n.type}] ${df.format(Date(n.date))}: ${n.content.take(80)}\n")
            }
        }

        return ToolResult.success(sb.toString())
    }

    private fun addNote(args: JsonObject): ToolResult {
        val memberId = args["member_id"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("Missing 'member_id'")
        val type = args["note_type"]?.jsonPrimitive?.contentOrNull ?: "observation"
        val content = args["content"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("Missing 'content'")

        val member = getAllMembers().find { it.id == memberId }
            ?: return ToolResult.error("Member '$memberId' not found.")

        val id = UUID.randomUUID().toString().take(8)
        val note = PerformanceNote(
            id = id, memberId = memberId, type = type,
            content = content, date = System.currentTimeMillis(),
        )

        val notes = getAllNotes().toMutableList()
        notes.add(note)
        saveNotes(notes)

        return ToolResult.success("[$type] note added for ${member.name}: $content")
    }

    private fun delegateTask(args: JsonObject): ToolResult {
        val memberId = args["member_id"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("Missing 'member_id'")
        val task = args["task"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("Missing 'task'")

        val member = getAllMembers().find { it.id == memberId }
            ?: return ToolResult.error("Member '$memberId' not found.")

        val id = UUID.randomUUID().toString().take(8)
        val delegation = Delegation(
            id = id, memberId = memberId, task = task,
            deadline = args["deadline"]?.jsonPrimitive?.contentOrNull?.let { parseDate(it) },
            assignedAt = System.currentTimeMillis(),
        )

        val delegations = getAllDelegations().toMutableList()
        delegations.add(delegation)
        saveDelegations(delegations)

        val df = SimpleDateFormat("MMM d", Locale.getDefault())
        val deadlineStr = if (delegation.deadline != null)
            " — due ${df.format(Date(delegation.deadline))}" else ""
        return ToolResult.success("Delegated to ${member.name}: $task$deadlineStr (ID: $id)")
    }

    private fun completeDelegation(args: JsonObject): ToolResult {
        val delegationId = args["delegation_id"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("Missing 'delegation_id'")

        val delegations = getAllDelegations().toMutableList()
        val index = delegations.indexOfFirst { it.id == delegationId }
        if (index == -1) return ToolResult.error("Delegation '$delegationId' not found.")

        delegations[index] = delegations[index].copy(
            status = "done",
            completedAt = System.currentTimeMillis(),
        )
        saveDelegations(delegations)

        val member = getAllMembers().find { it.id == delegations[index].memberId }
        return ToolResult.success("Delegation '${delegations[index].task}' by ${member?.name ?: "unknown"} marked complete.")
    }

    private fun updateMember(args: JsonObject): ToolResult {
        val memberId = args["member_id"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("Missing 'member_id'")

        val members = getAllMembers().toMutableList()
        val index = members.indexOfFirst { it.id == memberId }
        if (index == -1) return ToolResult.error("Member '$memberId' not found.")

        val m = members[index]
        members[index] = m.copy(
            name = args["name"]?.jsonPrimitive?.contentOrNull ?: m.name,
            role = args["role"]?.jsonPrimitive?.contentOrNull ?: m.role,
            strengths = args["strengths"]?.jsonPrimitive?.contentOrNull
                ?.split(",")?.map { it.trim() } ?: m.strengths,
            weaknesses = args["weaknesses"]?.jsonPrimitive?.contentOrNull
                ?.split(",")?.map { it.trim() } ?: m.weaknesses,
            goals = args["goals"]?.jsonPrimitive?.contentOrNull
                ?.split(",")?.map { it.trim() } ?: m.goals,
        )
        saveMembers(members)
        return ToolResult.success("Member '${members[index].name}' updated.")
    }

    private fun removeMember(args: JsonObject): ToolResult {
        val memberId = args["member_id"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("Missing 'member_id'")

        val members = getAllMembers().toMutableList()
        val removed = members.removeAll { it.id == memberId }
        if (!removed) return ToolResult.error("Member '$memberId' not found.")
        saveMembers(members)
        return ToolResult.success("Member '$memberId' removed from team.")
    }

    private fun dashboard(): ToolResult {
        val members = getAllMembers()
        val delegations = getAllDelegations()
        val notes = getAllNotes()
        val now = System.currentTimeMillis()
        val weekAgo = now - 7L * 24 * 60 * 60 * 1000

        val active = delegations.count { it.status == "assigned" }
        val overdue = delegations.count {
            it.status == "assigned" && it.deadline != null && it.deadline < now
        }
        val completedThisWeek = delegations.count {
            it.status == "done" && (it.completedAt ?: 0) > weekAgo
        }
        val notesThisWeek = notes.count { it.date > weekAgo }

        val sb = StringBuilder("## Leadership Dashboard\n\n")
        sb.append("Team size: ${members.size}\n")
        sb.append("Active delegations: $active | Overdue: $overdue\n")
        sb.append("Completed this week: $completedThisWeek\n")
        sb.append("Notes added this week: $notesThisWeek\n\n")

        if (overdue > 0) {
            sb.append("### Overdue Delegations\n")
            val df = SimpleDateFormat("MMM d", Locale.getDefault())
            for (d in delegations.filter { it.status == "assigned" && it.deadline != null && it.deadline < now }) {
                val member = members.find { it.id == d.memberId }
                sb.append("- ${member?.name}: ${d.task} (due ${df.format(Date(d.deadline!!))})\n")
            }
            sb.append("\n")
        }

        sb.append("### Team Workload\n")
        for (m in members) {
            val memberActive = delegations.count { it.memberId == m.id && it.status == "assigned" }
            sb.append("- ${m.name}: $memberActive active tasks\n")
        }

        return ToolResult.success(sb.toString())
    }

    private fun checkInDue(): ToolResult {
        val members = getAllMembers()
        val notes = getAllNotes()
        val now = System.currentTimeMillis()
        val weekAgo = now - 7L * 24 * 60 * 60 * 1000

        val needsCheckIn = members.filter { m ->
            val lastNote = notes.filter { it.memberId == m.id }.maxByOrNull { it.date }
            lastNote == null || lastNote.date < weekAgo
        }

        if (needsCheckIn.isEmpty()) {
            return ToolResult.success("All team members have been checked in with recently!")
        }

        val sb = StringBuilder("Team members due for a check-in:\n\n")
        for (m in needsCheckIn) {
            val lastNote = notes.filter { it.memberId == m.id }.maxByOrNull { it.date }
            val df = SimpleDateFormat("MMM d", Locale.getDefault())
            val lastContact = if (lastNote != null) "last note: ${df.format(Date(lastNote.date))}" else "no notes yet"
            sb.append("- **${m.name}** (${m.role}) — $lastContact\n")
        }
        return ToolResult.success(sb.toString())
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

    private fun getAllMembers(): List<TeamMember> {
        val raw = prefs.getString("members_data", "[]") ?: "[]"
        return try { json.decodeFromString(raw) } catch (_: Exception) { emptyList() }
    }
    private fun saveMembers(members: List<TeamMember>) {
        prefs.edit().putString("members_data", json.encodeToString(members)).apply()
    }
    private fun getAllNotes(): List<PerformanceNote> {
        val raw = prefs.getString("notes_data", "[]") ?: "[]"
        return try { json.decodeFromString(raw) } catch (_: Exception) { emptyList() }
    }
    private fun saveNotes(notes: List<PerformanceNote>) {
        prefs.edit().putString("notes_data", json.encodeToString(notes)).apply()
    }
    private fun getAllDelegations(): List<Delegation> {
        val raw = prefs.getString("delegations_data", "[]") ?: "[]"
        return try { json.decodeFromString(raw) } catch (_: Exception) { emptyList() }
    }
    private fun saveDelegations(delegations: List<Delegation>) {
        prefs.edit().putString("delegations_data", json.encodeToString(delegations)).apply()
    }
}
