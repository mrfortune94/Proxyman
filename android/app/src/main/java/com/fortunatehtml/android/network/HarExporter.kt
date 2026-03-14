package com.fortunatehtml.android.network

import com.fortunatehtml.android.data.db.entity.TrafficEntryEntity
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Exports traffic entries to HAR (HTTP Archive) JSON format.
 *
 * The resulting JSON can be opened in browser DevTools, Postman, Charles Proxy,
 * or any other tool that accepts the HAR 1.2 format.
 */
class HarExporter {

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun export(entries: List<TrafficEntryEntity>): String {
        val log = JsonObject().apply {
            addProperty("version", "1.2")
            add("creator", JsonObject().apply {
                addProperty("name", "Pocket API Inspector")
                addProperty("version", "2.0")
            })
            add("entries", JsonArray().also { arr ->
                entries.forEach { e -> arr.add(toHarEntry(e)) }
            })
        }
        return gson.toJson(JsonObject().apply { add("log", log) })
    }

    private fun toHarEntry(e: TrafficEntryEntity): JsonObject = JsonObject().apply {
        addProperty("startedDateTime", dateFormat.format(Date(e.timestamp)))
        addProperty("time", (e.duration ?: 0).toDouble())
        add("request", JsonObject().apply {
            addProperty("method", e.method)
            addProperty("url", e.url)
            addProperty("httpVersion", "HTTP/1.1")
            add("headers", headersToArray(e.requestHeaders))
            add("queryString", JsonArray())
            add("cookies", JsonArray())
            addProperty("headersSize", -1)
            addProperty("bodySize", e.requestBody?.length ?: 0)
            if (e.requestBody != null) {
                add("postData", JsonObject().apply {
                    addProperty("mimeType", e.requestHeaders["Content-Type"] ?: "")
                    addProperty("text", e.requestBody)
                })
            }
        })
        add("response", JsonObject().apply {
            addProperty("status", e.statusCode ?: 0)
            addProperty("statusText", "")
            addProperty("httpVersion", "HTTP/1.1")
            add("headers", headersToArray(e.responseHeaders))
            add("cookies", JsonArray())
            add("content", JsonObject().apply {
                addProperty("size", e.responseSize ?: 0)
                addProperty("mimeType", e.contentType ?: "")
                if (e.responseBody != null) addProperty("text", e.responseBody)
            })
            addProperty("redirectURL", "")
            addProperty("headersSize", -1)
            addProperty("bodySize", e.responseSize ?: 0)
        })
        add("cache", JsonObject())
        add("timings", JsonObject().apply {
            addProperty("send", 0)
            addProperty("wait", (e.duration ?: 0).toDouble())
            addProperty("receive", 0)
        })
    }

    private fun headersToArray(headers: Map<String, String>): JsonArray =
        JsonArray().also { arr ->
            headers.forEach { (k, v) ->
                arr.add(JsonObject().apply {
                    addProperty("name", k)
                    addProperty("value", v)
                })
            }
        }
}
