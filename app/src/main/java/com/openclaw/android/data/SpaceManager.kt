package com.openclaw.android.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Manages Spaces (projects). Each space has:
 * - Its own files directory
 * - Its own conversation history
 * - An optional custom system prompt
 * - A knowledge base (auto-generated from conversation digests)
 */
class SpaceManager(private val context: Context) {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val spacesRoot: File
        get() = File(context.getExternalFilesDir(null), "spaces").apply { mkdirs() }

    fun getSpaces(): List<Space> {
        val metaFile = File(spacesRoot, "spaces.json")
        if (!metaFile.exists()) {
            // Create default space
            val defaultSpace = Space(
                id = "default",
                name = "General",
                emoji = "💬",
                description = "Default workspace",
            )
            saveSpaces(listOf(defaultSpace))
            getSpaceDir(defaultSpace.id) // create dirs
            return listOf(defaultSpace)
        }
        return try {
            json.decodeFromString<List<Space>>(metaFile.readText())
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun createSpace(name: String, emoji: String = "📁", description: String = "", systemPrompt: String? = null): Space {
        val id = name.lowercase().replace(Regex("[^a-z0-9]"), "-").take(30) +
                "-${System.currentTimeMillis() % 10000}"
        val space = Space(id = id, name = name, emoji = emoji, description = description, systemPrompt = systemPrompt)
        val spaces = getSpaces() + space
        saveSpaces(spaces)
        getSpaceDir(id) // create dirs
        return space
    }

    fun deleteSpace(spaceId: String) {
        if (spaceId == "default") return // can't delete default
        val spaces = getSpaces().filter { it.id != spaceId }
        saveSpaces(spaces)
        File(spacesRoot, spaceId).deleteRecursively()
    }

    fun getSpaceDir(spaceId: String): File {
        val dir = File(spacesRoot, spaceId)
        File(dir, "files").mkdirs()
        File(dir, "conversations").mkdirs()
        File(dir, "knowledge").mkdirs()
        return dir
    }

    fun getSpaceFilesDir(spaceId: String): File =
        File(getSpaceDir(spaceId), "files").apply { mkdirs() }

    fun saveConversation(spaceId: String, conversationId: String, messages: String) {
        val file = File(File(getSpaceDir(spaceId), "conversations"), "$conversationId.json")
        file.writeText(messages)
    }

    fun saveDigest(spaceId: String, digest: String) {
        val file = File(File(getSpaceDir(spaceId), "knowledge"), "digest-${System.currentTimeMillis()}.md")
        file.writeText(digest)
    }

    fun getKnowledgeBase(spaceId: String): String {
        val knowledgeDir = File(getSpaceDir(spaceId), "knowledge")
        if (!knowledgeDir.exists()) return ""
        return knowledgeDir.listFiles()
            ?.filter { it.extension == "md" }
            ?.sortedByDescending { it.lastModified() }
            ?.take(5) // Last 5 digests
            ?.joinToString("\n\n---\n\n") { it.readText() }
            ?: ""
    }

    private fun saveSpaces(spaces: List<Space>) {
        File(spacesRoot, "spaces.json").writeText(json.encodeToString(spaces))
    }
}

@Serializable
data class Space(
    val id: String,
    val name: String,
    val emoji: String = "📁",
    val description: String = "",
    val systemPrompt: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
