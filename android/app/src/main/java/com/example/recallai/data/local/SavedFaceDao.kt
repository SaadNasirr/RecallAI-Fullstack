package com.example.recallai.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface SavedFaceDao {

    @Query("SELECT * FROM saved_faces ORDER BY createdAt DESC")
    suspend fun getAll(): List<SavedFaceEntity>

    @Query("SELECT COUNT(*) FROM saved_faces")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SavedFaceEntity)

    @Update
    suspend fun update(entity: SavedFaceEntity)

    @Query("DELETE FROM saved_faces WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM saved_faces")
    suspend fun deleteAll()
}
