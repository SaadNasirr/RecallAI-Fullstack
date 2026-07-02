package com.example.recallai.data

import com.example.recallai.data.local.MemoryDao
import com.example.recallai.data.local.MemoryEntity
import com.example.recallai.data.local.MemoryMediaEntity
import com.example.recallai.data.local.MemorySyncStatus
import com.example.recallai.data.remote.RemoteMemoryDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import java.time.OffsetDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryRepository @Inject constructor(
    private val dao: MemoryDao,
    private val remoteMemoryRepo: RemoteMemoryRepository,
    private val careRepository: CareRepository
) {

    private fun isCaregiver(): Boolean =
        AuthManager.userRole.equals("caregiver", ignoreCase = true)

    /** Patient = self; caregiver = selected patient on watchlist. */
    fun resolveScopeUserId(): String? =
        if (isCaregiver()) {
            careRepository.selectedPatientId.value?.trim()?.takeIf { it.isNotBlank() }
        } else {
            AuthManager.userId?.trim()?.takeIf { it.isNotBlank() }
        }

    private fun includeLegacyRows(): Boolean = !isCaregiver()

    /**
     * Pull cloud memories into Room (merge by serverId). Best-effort upload of pending local rows.
     */
    suspend fun syncFromServer(): Boolean = withContext(Dispatchers.IO) {
        val scope = resolveScopeUserId() ?: return@withContext false
        try {
            val remote = remoteMemoryRepo.getMemories()
            for (dto in remote) {
                upsertRemoteDto(dto, scope)
            }
            uploadPendingMemories(scope)
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun clearAllMemories() = withContext(Dispatchers.IO) {
        dao.deleteAllMemories()
    }

    suspend fun saveTextMemory(
        text: String,
        title: String? = null,
        type: String = "TEXT",
        mood: String? = null,
        tags: List<String> = emptyList(),
        userId: String? = null
    ): Long = withContext(Dispatchers.IO) {
        val scope = userId?.trim()?.takeIf { it.isNotBlank() } ?: resolveScopeUserId() ?: AuthManager.userId
        // Client-side dedup: if exact same text was saved in the last 5 minutes, return the existing id.
        // This prevents duplicate rows when the user taps a button multiple times or the sync worker
        // re-uploads a PENDING row that was already uploaded but whose serverId wasn't written back.
        val dedupWindow = System.currentTimeMillis() - 5 * 60 * 1000L
        val recent = dao.findRecentDuplicate(text, dedupWindow)
        if (recent != null) return@withContext recent.id

        val memory = MemoryEntity(
            userId = scope,
            createdAt = System.currentTimeMillis(),
            title = title,
            text = text,
            type = type,
            mood = mood,
            tags = if (tags.isEmpty()) null else tags.joinToString(","),
            syncStatus = MemorySyncStatus.PENDING
        )
        val id = dao.insertMemory(memory)
        uploadRow(memory.copy(id = id))
        id
    }

    suspend fun saveMemoryWithMedia(
        text: String,
        media: List<MemoryMediaEntity>,
        title: String? = null,
        type: String,
        mood: String? = null,
        tags: List<String> = emptyList(),
        userId: String? = null
    ): Long = withContext(Dispatchers.IO) {
        val scope = userId?.trim()?.takeIf { it.isNotBlank() } ?: resolveScopeUserId() ?: AuthManager.userId
        // Same 5-minute dedup guard as saveTextMemory — prevents duplicate object-detection saves
        // when the camera fires multiple times for the same frame or the user re-opens the locator.
        val dedupWindow = System.currentTimeMillis() - 5 * 60 * 1000L
        val recent = dao.findRecentDuplicate(text, dedupWindow)
        if (recent != null) return@withContext recent.id

        val memory = MemoryEntity(
            userId = scope,
            createdAt = System.currentTimeMillis(),
            title = title,
            text = text,
            type = type,
            mood = mood,
            tags = if (tags.isEmpty()) null else tags.joinToString(","),
            syncStatus = MemorySyncStatus.PENDING
        )
        val id = dao.insertMemoryWithMedia(memory, media)
        uploadRow(memory.copy(id = id))
        id
    }

    suspend fun getRecentMemories(limit: Int = 50): List<MemoryEntity> =
        withContext(Dispatchers.IO) {
            val scope = resolveScopeUserId() ?: return@withContext emptyList()
            dao.getRecentMemoriesForScope(scope, includeLegacyRows(), limit)
        }

    fun observeRecentMemories(limit: Int = 50): Flow<List<MemoryEntity>> {
        val scope = resolveScopeUserId() ?: return flowOf(emptyList())
        return dao.observeRecentMemoriesForScope(scope, includeLegacyRows(), limit)
    }

    suspend fun searchMemories(query: String, limit: Int = 50): List<MemoryEntity> =
        withContext(Dispatchers.IO) {
            val scope = resolveScopeUserId() ?: return@withContext emptyList()
            dao.searchMemoriesForScope(scope, includeLegacyRows(), query, limit)
        }

    suspend fun countAllMemories(): Int = withContext(Dispatchers.IO) {
        val scope = resolveScopeUserId() ?: return@withContext 0
        dao.countAllMemoriesForScope(scope, includeLegacyRows())
    }

    suspend fun countByType(type: String): Int = withContext(Dispatchers.IO) {
        val scope = resolveScopeUserId() ?: return@withContext 0
        dao.countMemoriesByTypeForScope(scope, includeLegacyRows(), type)
    }

    suspend fun getLatestObjectDetectionMemory(): MemoryEntity? = withContext(Dispatchers.IO) {
        val scope = resolveScopeUserId() ?: return@withContext null
        dao.getLatestObjectDetectionMemoryForScope(scope, includeLegacyRows())
    }

    suspend fun getRecentObjectDetectionMemories(limit: Int = 6): List<MemoryEntity> =
        withContext(Dispatchers.IO) {
            val scope = resolveScopeUserId() ?: return@withContext emptyList()
            dao.getRecentObjectDetectionMemoriesForScope(scope, includeLegacyRows(), limit)
        }

    suspend fun getLatestRecallMemory(): MemoryEntity? = withContext(Dispatchers.IO) {
        val scope = resolveScopeUserId() ?: return@withContext null
        dao.getLatestRecallMemoryForScope(scope, includeLegacyRows())
    }

    suspend fun getRecentRecallMemories(limit: Int = 6): List<MemoryEntity> = withContext(Dispatchers.IO) {
        val scope = resolveScopeUserId() ?: return@withContext emptyList()
        dao.getRecentRecallMemoriesForScope(scope, includeLegacyRows(), limit)
    }

    suspend fun getRecentFaceMemories(limit: Int = 8): List<MemoryEntity> = withContext(Dispatchers.IO) {
        val scope = resolveScopeUserId() ?: return@withContext emptyList()
        dao.getRecentFaceMemoriesForScope(scope, includeLegacyRows(), limit)
    }

    fun observeRecentFaceMemories(limit: Int = 8): Flow<List<MemoryEntity>> {
        val scope = resolveScopeUserId() ?: return flowOf(emptyList())
        return dao.observeRecentFaceMemoriesForScope(scope, includeLegacyRows(), limit)
    }

    suspend fun getLatestImageUriForMemory(memoryId: Long): String? = withContext(Dispatchers.IO) {
        dao.getLatestImageUriForMemory(memoryId)
    }

    private suspend fun upsertRemoteDto(dto: RemoteMemoryDto, scopeUserId: String) {
        val serverId = dto._id?.trim()?.takeIf { it.isNotBlank() } ?: return
        val mapped = mapRemoteToEntity(dto, scopeUserId, serverId)
        val existing = dao.findByServerId(serverId)
        dao.insertMemory(
            if (existing != null) mapped.copy(id = existing.id) else mapped
        )
    }

    private fun mapRemoteToEntity(
        dto: RemoteMemoryDto,
        scopeUserId: String,
        serverId: String
    ): MemoryEntity {
        val parsedCreatedAt = runCatching {
            dto.createdAt?.let { OffsetDateTime.parse(it).toInstant().toEpochMilli() }
        }.getOrNull() ?: System.currentTimeMillis()
        val body = dto.rawText?.takeIf { it.isNotBlank() }
            ?: dto.text?.takeIf { it.isNotBlank() }
            ?: "Memory entry"
        val type = dto.type?.trim()?.takeIf { it.isNotBlank() } ?: "REMOTE"
        return MemoryEntity(
            serverId = serverId,
            userId = scopeUserId,
            createdAt = parsedCreatedAt,
            title = type.replace('_', ' ').replaceFirstChar { it.uppercase() },
            text = body,
            type = type.uppercase(),
            mood = null,
            tags = dto.tags?.joinToString(","),
            syncStatus = MemorySyncStatus.SYNCED
        )
    }

    private suspend fun uploadPendingMemories(scopeUserId: String) {
        val pending = dao.getMemoriesNeedingUpload()
        for (row in pending) {
            val scoped = if (row.userId.isNullOrBlank()) row.copy(userId = scopeUserId) else row
            uploadRow(scoped)
        }
    }

    private suspend fun uploadRow(row: MemoryEntity) {
        if (row.serverId != null && row.syncStatus == MemorySyncStatus.SYNCED) return
        try {
            val resp = remoteMemoryRepo.saveMemory(text = row.text)
            val serverId = resp._id?.trim()?.takeIf { it.isNotBlank() }
            if (serverId != null) {
                val existing = dao.findByServerId(serverId)
                val synced = row.copy(
                    id = existing?.id ?: row.id,
                    serverId = serverId,
                    userId = row.userId ?: resolveScopeUserId(),
                    syncStatus = MemorySyncStatus.SYNCED,
                    type = resp.type?.uppercase() ?: row.type
                )
                dao.insertMemory(synced)
            } else {
                dao.updateMemory(row.copy(syncStatus = MemorySyncStatus.FAILED))
            }
        } catch (_: Exception) {
            if (row.id != 0L) {
                dao.updateMemory(row.copy(syncStatus = MemorySyncStatus.FAILED))
            }
        }
    }
}
