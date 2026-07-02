package com.example.recallai.data.local

import kotlinx.coroutines.flow.Flow
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

@Dao
interface MemoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: MemoryEntity): Long

    @Update
    suspend fun updateMemory(memory: MemoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedia(media: List<MemoryMediaEntity>)

    @Transaction
    suspend fun insertMemoryWithMedia(
        memory: MemoryEntity,
        media: List<MemoryMediaEntity>
    ): Long {
        val id = insertMemory(memory)
        if (media.isNotEmpty()) {
            insertMedia(media.map { it.copy(memoryId = id) })
        }
        return id
    }

    @Query("SELECT * FROM memories WHERE serverId = :serverId LIMIT 1")
    suspend fun findByServerId(serverId: String): MemoryEntity?

    /** Client-side dedup: returns the most recent row with the same text saved after [since]. */
    @Query("SELECT * FROM memories WHERE text = :text AND createdAt >= :since ORDER BY createdAt DESC LIMIT 1")
    suspend fun findRecentDuplicate(text: String, since: Long): MemoryEntity?

    @Query("DELETE FROM memories")
    suspend fun deleteAllMemories()

    @Query(
        """
        SELECT * FROM memories
        WHERE userId = :scopeUserId
           OR (:includeLegacy = 1 AND userId IS NULL)
        ORDER BY createdAt DESC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun getRecentMemoriesForScope(
        scopeUserId: String,
        includeLegacy: Boolean,
        limit: Int,
        offset: Int = 0
    ): List<MemoryEntity>

    @Query(
        """
        SELECT * FROM memories
        WHERE userId = :scopeUserId
           OR (:includeLegacy = 1 AND userId IS NULL)
        ORDER BY createdAt DESC
        LIMIT :limit OFFSET :offset
        """
    )
    fun observeRecentMemoriesForScope(
        scopeUserId: String,
        includeLegacy: Boolean,
        limit: Int,
        offset: Int = 0
    ): Flow<List<MemoryEntity>>

    @Query(
        """
        SELECT * FROM memories
        WHERE (userId = :scopeUserId OR (:includeLegacy = 1 AND userId IS NULL))
          AND (:type IS NULL OR type = :type)
        ORDER BY createdAt DESC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun getMemoriesByTypeForScope(
        scopeUserId: String,
        includeLegacy: Boolean,
        type: String?,
        limit: Int,
        offset: Int = 0
    ): List<MemoryEntity>

    @Query(
        """
        SELECT * FROM memories
        WHERE (userId = :scopeUserId OR (:includeLegacy = 1 AND userId IS NULL))
          AND (text LIKE '%' || :q || '%'
            OR (tags IS NOT NULL AND tags LIKE '%' || :q || '%'))
        ORDER BY createdAt DESC
        LIMIT :limit
        """
    )
    suspend fun searchMemoriesForScope(
        scopeUserId: String,
        includeLegacy: Boolean,
        q: String,
        limit: Int = 50
    ): List<MemoryEntity>

    @Query(
        """
        SELECT COUNT(*) FROM memories
        WHERE userId = :scopeUserId OR (:includeLegacy = 1 AND userId IS NULL)
        """
    )
    suspend fun countAllMemoriesForScope(scopeUserId: String, includeLegacy: Boolean): Int

    @Query(
        """
        SELECT COUNT(*) FROM memories
        WHERE (userId = :scopeUserId OR (:includeLegacy = 1 AND userId IS NULL))
          AND type = :type
        """
    )
    suspend fun countMemoriesByTypeForScope(
        scopeUserId: String,
        includeLegacy: Boolean,
        type: String
    ): Int

    @Query(
        """
        SELECT * FROM memories
        WHERE (userId = :scopeUserId OR (:includeLegacy = 1 AND userId IS NULL))
          AND type = 'OBJECT_DETECTION'
        ORDER BY createdAt DESC LIMIT 1
        """
    )
    suspend fun getLatestObjectDetectionMemoryForScope(
        scopeUserId: String,
        includeLegacy: Boolean
    ): MemoryEntity?

    @Query(
        """
        SELECT * FROM memories
        WHERE (userId = :scopeUserId OR (:includeLegacy = 1 AND userId IS NULL))
          AND type = 'OBJECT_DETECTION'
        ORDER BY createdAt DESC LIMIT :limit
        """
    )
    suspend fun getRecentObjectDetectionMemoriesForScope(
        scopeUserId: String,
        includeLegacy: Boolean,
        limit: Int
    ): List<MemoryEntity>

    @Query(
        """
        SELECT * FROM memories
        WHERE (userId = :scopeUserId OR (:includeLegacy = 1 AND userId IS NULL))
          AND type IN ('RECALL_ASSISTANT','RECALL_NOTE')
        ORDER BY createdAt DESC LIMIT 1
        """
    )
    suspend fun getLatestRecallMemoryForScope(
        scopeUserId: String,
        includeLegacy: Boolean
    ): MemoryEntity?

    @Query(
        """
        SELECT * FROM memories
        WHERE (userId = :scopeUserId OR (:includeLegacy = 1 AND userId IS NULL))
          AND type IN ('RECALL_ASSISTANT','RECALL_NOTE')
        ORDER BY createdAt DESC LIMIT :limit
        """
    )
    suspend fun getRecentRecallMemoriesForScope(
        scopeUserId: String,
        includeLegacy: Boolean,
        limit: Int
    ): List<MemoryEntity>

    @Query(
        """
        SELECT * FROM memories
        WHERE (userId = :scopeUserId OR (:includeLegacy = 1 AND userId IS NULL))
          AND type = 'FACE_ANALYSIS'
        ORDER BY createdAt DESC LIMIT :limit
        """
    )
    suspend fun getRecentFaceMemoriesForScope(
        scopeUserId: String,
        includeLegacy: Boolean,
        limit: Int
    ): List<MemoryEntity>

    @Query(
        """
        SELECT * FROM memories
        WHERE (userId = :scopeUserId OR (:includeLegacy = 1 AND userId IS NULL))
          AND type = 'FACE_ANALYSIS'
        ORDER BY createdAt DESC LIMIT :limit
        """
    )
    fun observeRecentFaceMemoriesForScope(
        scopeUserId: String,
        includeLegacy: Boolean,
        limit: Int
    ): Flow<List<MemoryEntity>>

    @Query(
        "SELECT uri FROM memory_media WHERE memoryId = :memoryId AND type = 'image' " +
            "ORDER BY id DESC LIMIT 1"
    )
    suspend fun getLatestImageUriForMemory(memoryId: Long): String?

    @Query(
        """
        SELECT * FROM memories
        WHERE syncStatus = 'PENDING'
           OR (syncStatus = 'LEGACY' AND serverId IS NULL)
        ORDER BY createdAt ASC
        LIMIT :limit
        """
    )
    suspend fun getMemoriesNeedingUpload(limit: Int = 40): List<MemoryEntity>
}
