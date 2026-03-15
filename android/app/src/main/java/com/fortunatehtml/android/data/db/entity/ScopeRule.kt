package com.fortunatehtml.android.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * A scope rule defining which host/domain belongs to which environment.
 * Capture, replay, and export actions respect scope to prevent
 * accidental out-of-scope requests.
 */
@Entity(tableName = "scope_rules")
data class ScopeRule(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val host: String,                // e.g. "api.myapp.com" or "*.myapp.com"
    /** One of: local, development, staging, production */
    val environment: String = "development",
    val isEnabled: Boolean = true,
    val notes: String = ""
)
