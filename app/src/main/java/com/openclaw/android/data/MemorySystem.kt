package com.openclaw.android.data

import com.openclaw.android.data.db.MemoryDao
import com.openclaw.android.data.db.MemoryEntity

/**
 * Personal memory system — the AI's "second brain".
 * Stores persistent facts, preferences, and context that the AI can recall
 * across conversations to provide a personalized experience.
 */
class MemorySystem(private val dao: MemoryDao) {

    suspend fun remember(category: String, key: String, value: String, source: String = "user_told"): MemoryEntity {
        val existing = dao.getByKey(key)
        val entity = if (existing != null) {
            existing.copy(
                value = value,
                category = category,
                source = source,
                updatedAt = System.currentTimeMillis(),
            )
        } else {
            MemoryEntity(
                category = category,
                key = key,
                value = value,
                source = source,
            )
        }
        val id = dao.upsert(entity)
        return entity.copy(id = if (entity.id == 0L) id else entity.id)
    }

    suspend fun recall(query: String): List<MemoryEntity> {
        val results = dao.search(query)
        results.forEach { dao.incrementAccessCount(it.id) }
        return results
    }

    suspend fun recallByCategory(category: String): List<MemoryEntity> {
        return dao.getByCategory(category)
    }

    suspend fun forget(key: String): Boolean {
        dao.deleteByKey(key)
        return true
    }

    suspend fun getAll(): List<MemoryEntity> = dao.getAll()

    suspend fun getContextSummary(): String {
        val memories = dao.getMostAccessed(20)
        if (memories.isEmpty()) return ""

        val sb = StringBuilder("\n--- User Memory Context ---\n")
        val grouped = memories.groupBy { it.category }
        grouped.forEach { (category, items) ->
            sb.append("[$category]\n")
            items.forEach { sb.append("- ${it.key}: ${it.value}\n") }
        }
        sb.append("--- End Memory ---\n")
        return sb.toString()
    }

    suspend fun count(): Int = dao.count()
}
