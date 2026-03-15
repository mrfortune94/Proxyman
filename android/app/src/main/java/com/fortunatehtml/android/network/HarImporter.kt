package com.fortunatehtml.android.network

import android.util.Log
import com.fortunatehtml.android.data.db.entity.TrafficEntryEntity
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.InputStream
import java.net.URI

/**
 * Imports HTTP Archive (HAR) files and converts them into [TrafficEntryEntity] records.
 *
 * HAR files are the standard export format of browser DevTools, Charles Proxy,
 * and many other tools. Importing a HAR allows analysing traffic that was captured
 * externally (e.g. via browser devtools on a system I control) without requiring
 * any live interception.
 */
class HarImporter {

    private val gson = Gson()
    // Reuse a single formatter instance across all entries (SimpleDateFormat is not thread-safe
    // but HarImporter is used from a single coroutine at a time)
    private val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        java.util.Locale.US)

    /**
     * Result of a HAR import operation.
     */
    data class ImportResult(
        val entries: List<TrafficEntryEntity>,
        val errors: List<String>,
        val success: Boolean
    ) {
        companion object {
            fun success(entries: List<TrafficEntryEntity>) = ImportResult(entries, emptyList(), true)
            fun error(errors: List<String>) = ImportResult(emptyList(), errors, false)
            fun partial(entries: List<TrafficEntryEntity>, errors: List<String>) = ImportResult(entries, errors, entries.isNotEmpty())
        }
    }

    /**
     * Parse a HAR JSON stream and return a list of traffic entries.
     * Returns an empty list on parse errors rather than throwing.
     * 
     * @deprecated Use [importWithResult] for detailed error reporting
     * @see importWithResult
     */
    @Deprecated("Use importWithResult() for detailed error reporting", ReplaceWith("importWithResult(stream, projectId).entries"))
    fun import(stream: InputStream, projectId: String? = null): List<TrafficEntryEntity> {
        return importWithResult(stream, projectId).entries
    }

    /**
     * Parse a HAR JSON stream and return detailed import results including any errors.
     */
    fun importWithResult(stream: InputStream, projectId: String? = null): ImportResult {
        val errors = mutableListOf<String>()
        
        return runCatching {
            val json = stream.bufferedReader().readText()
            if (json.isBlank()) {
                return ImportResult.error(listOf("HAR file is empty"))
            }
            
            val root = runCatching { 
                gson.fromJson(json, JsonObject::class.java) 
            }.getOrElse { e ->
                Log.e(TAG, "Failed to parse HAR JSON", e)
                return ImportResult.error(listOf("Invalid JSON format: ${e.message}"))
            }
            
            if (root == null) {
                return ImportResult.error(listOf("HAR file contains null root object"))
            }

            val log = root.getAsJsonObject("log")
            if (log == null) {
                return ImportResult.error(listOf("Invalid HAR format: missing 'log' object"))
            }

            val entries = log.getAsJsonArray("entries")
            if (entries == null) {
                return ImportResult.error(listOf("Invalid HAR format: missing 'entries' array"))
            }

            if (entries.size() == 0) {
                return ImportResult.error(listOf("HAR file contains no traffic entries"))
            }

            val result = mutableListOf<TrafficEntryEntity>()
            for ((index, elem) in entries.withIndex()) {
                val entryResult = parseEntry(elem.asJsonObject, projectId, index)
                if (entryResult.isSuccess) {
                    result.add(entryResult.getOrThrow())
                } else {
                    val errorMsg = entryResult.exceptionOrNull()?.message ?: "Unknown error"
                    errors.add("Entry $index: $errorMsg")
                    Log.w(TAG, "Failed to parse entry $index: $errorMsg")
                }
            }
            
            if (result.isEmpty() && errors.isNotEmpty()) {
                ImportResult.error(errors)
            } else if (errors.isNotEmpty()) {
                ImportResult.partial(result, errors)
            } else {
                ImportResult.success(result)
            }
        }.getOrElse { e ->
            Log.e(TAG, "HAR import failed", e)
            ImportResult.error(listOf("Import failed: ${e.message}"))
        }
    }

    private fun parseEntry(obj: JsonObject, projectId: String?, @Suppress("UNUSED_PARAMETER") index: Int): Result<TrafficEntryEntity> {
        return runCatching {
            val request  = obj.getAsJsonObject("request")
                ?: throw IllegalArgumentException("Missing 'request' object")
            val response = obj.getAsJsonObject("response")
                ?: throw IllegalArgumentException("Missing 'response' object")

            val url = request.get("url")?.asString
                ?: throw IllegalArgumentException("Missing 'url' in request")
            val method = request.get("method")?.asString ?: "GET"
            val uri = URI(url)

            val reqHeaders  = parseHeaders(request)
            val respHeaders = parseHeaders(response)

            val postData   = request.getAsJsonObject("postData")
            val reqBody    = postData?.get("text")?.asString

            val content     = response.getAsJsonObject("content")
            val respBody    = content?.get("text")?.asString
            val respSize    = content?.get("size")?.asLong
            val contentType = content?.get("mimeType")?.asString

            val statusCode = response.get("status")?.asInt
            val duration   = obj.getAsJsonObject("timings")
                ?.let { t ->
                    listOf("send", "wait", "receive")
                        .sumOf { k -> t.get(k)?.asLong ?: 0L }
                }

            val startedDateTime = obj.get("startedDateTime")?.asString
            val timestamp = runCatching {
                dateFormat.parse(startedDateTime ?: "")?.time
            }.getOrNull() ?: System.currentTimeMillis()

            TrafficEntryEntity(
                projectId       = projectId,
                method          = method,
                url             = url,
                host            = uri.host ?: "",
                path            = uri.path ?: "/",
                scheme          = uri.scheme ?: "https",
                queryParams     = uri.query ?: "",
                requestHeaders  = reqHeaders,
                requestBody     = reqBody,
                statusCode      = statusCode,
                responseHeaders = respHeaders,
                responseBody    = respBody,
                timestamp       = timestamp,
                duration        = duration,
                responseSize    = respSize,
                isHttps         = uri.scheme == "https",
                contentType     = contentType,
                source          = "har",
                state           = if (statusCode != null) "COMPLETE" else "PENDING"
            )
        }
    }

    private fun parseHeaders(obj: JsonObject): Map<String, String> {
        val arr = obj.getAsJsonArray("headers") ?: return emptyMap()
        return arr.associate { h ->
            val header = h.asJsonObject
            (header.get("name")?.asString ?: "") to (header.get("value")?.asString ?: "")
        }
    }

    companion object {
        private const val TAG = "HarImporter"
    }
}
