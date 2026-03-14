package com.fortunatehtml.android.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Persisted representation of a single captured HTTP request/response pair.
 *
 * Sources:
 *   webview  – navigation or resource load observed in the embedded WebView
 *   okhttp   – captured by OkHttpInspector within this app
 *   sdk      – forwarded by the companion debug SDK from another app I own
 *   har      – imported from a HAR or JSON session file
 *   proxy    – relayed through a reverse-proxy endpoint I control
 */
@Entity(tableName = "traffic_entries")
data class TrafficEntryEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val projectId: String? = null,
    val method: String,
    val url: String,
    val host: String,
    val path: String,
    val scheme: String,
    val queryParams: String = "",
    val requestHeaders: Map<String, String> = emptyMap(),
    val requestBody: String? = null,
    val statusCode: Int? = null,
    val responseHeaders: Map<String, String> = emptyMap(),
    val responseBody: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val duration: Long? = null,
    val responseSize: Long? = null,
    val isHttps: Boolean = false,
    /** One of: webview, okhttp, sdk, har, proxy */
    val source: String = "okhttp",
    val state: String = "PENDING",
    val contentType: String? = null,
    val notes: String = "",
    val tags: List<String> = emptyList()
)
