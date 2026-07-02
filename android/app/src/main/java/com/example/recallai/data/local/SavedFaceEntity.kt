package com.example.recallai.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_faces")
data class SavedFaceEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** JSON array of floats — L2-normalized descriptor at save time. */
    val embeddingJson: String,
    val createdAt: Long
)
