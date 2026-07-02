package com.example.recallai.memories

import com.example.recallai.data.local.MemoryEntity
import javax.inject.Inject
import javax.inject.Singleton

data class MemoryOpenPayload(
    val memoryId: Long,
    val type: String,
    val title: String?,
    val text: String,
    val tags: String?,
    val createdAt: Long
) {
    fun contextLabel(): String =
        title?.trim()?.takeIf { it.isNotBlank() }
            ?: text.lineSequence().firstOrNull()?.trim()?.take(120)?.takeIf { it.isNotBlank() }
            ?: "Memory"

    companion object {
        fun from(entity: MemoryEntity) = MemoryOpenPayload(
            memoryId = entity.id,
            type = entity.type,
            title = entity.title,
            text = entity.text,
            tags = entity.tags,
            createdAt = entity.createdAt
        )
    }
}

@Singleton
class MemoryOpenCoordinator @Inject constructor() {
    @Volatile
    private var pending: MemoryOpenPayload? = null

    fun open(memory: MemoryEntity) {
        pending = MemoryOpenPayload.from(memory)
    }

    fun open(payload: MemoryOpenPayload) {
        pending = payload
    }

    fun consume(): MemoryOpenPayload? {
        val p = pending
        pending = null
        return p
    }
}
