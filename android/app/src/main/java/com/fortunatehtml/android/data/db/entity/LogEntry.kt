package com.fortunatehtml.android.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/** An app event log entry (startup, agreement, capture, import/export, errors, etc.). */
@Entity(tableName = "log_entries")
data class LogEntry(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    /** One of: startup, agreement, capture, replay, import, export, error, webview, proxy */
    val category: String,
    val message: String,
    val detail: String = ""
)
