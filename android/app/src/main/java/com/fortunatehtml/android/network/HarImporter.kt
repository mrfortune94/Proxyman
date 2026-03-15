package com.fortunatehtml.android.network

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
     * Parse a HAR JSON stream and return a list of traffic entries.
     * Returns an empty list on parse errors rather than throwing.
     */
    fun import(stream: InputStream, projectId: String? = null): List<TrafficEntryEntity> {
        return runCatching {
            val json = stream.bufferedReader().readText()
            val root = gson.fromJson(json, JsonObject::class.java)
            val entries = root
                ?.getAsJsonObject("log")
                ?.getAsJsonArray("entries")
                ?: return emptyList()

            val result = mutableListOf<TrafficEntryEntity>()
            for (elem in entries) {
                val obj      = elem.asJsonObject
                val request  = obj.getAsJsonObject("request")  ?: continue
                val response = obj.getAsJsonObject("response") ?: continue

                val url    = request.get("url")?.asString ?: continue
                val method = request.get("method")?.asString ?: "GET"
                val uri    = runCatching { URI(url) }.getOrNull() ?: continue

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

                result += TrafficEntryEntity(
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
            result
        }.getOrElse { emptyList() }
    }

    private fun parseHeaders(obj: JsonObject): Map<String, String> {
        val arr = obj.getAsJsonArray("headers") ?: return emptyMap()
        return arr.associate { h ->
            val header = h.asJsonObject
            (header.get("name")?.asString ?: "") to (header.get("value")?.asString ?: "")
        }
    }
}
