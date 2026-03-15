package com.fortunatehtml.android.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/** A named workspace grouping related traffic entries and notes. */
@Entity(tableName = "projects")
data class Project(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false,
    val tags: List<String> = emptyList(),
    val notes: String = ""
)
