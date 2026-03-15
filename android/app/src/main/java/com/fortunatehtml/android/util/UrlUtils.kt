package com.fortunatehtml.android.util

/**
 * URL sanitisation helpers shared across WebView navigation and the Repeater.
 */
object UrlUtils {

    /**
     * Validates and normalises a user-typed URL string for use in WebView or OkHttp.
     *
     * Only `http://` and `https://` schemes are accepted. Dangerous schemes that could
     * be used for XSS or local file access (`file://`, `javascript:`, `data:`, `blob:`,
     * `content:`) are rejected by returning `null`.
     *
     * If the input has no recognisable scheme, `https://` is prepended.
     *
     * @param input Raw user-typed URL string.
     * @return A normalised URL string, or `null` if the scheme is not allowed.
     */
    fun sanitise(input: String): String? {
        val trimmed = input.trim()
        return when {
            trimmed.startsWith("https://") || trimmed.startsWith("http://") -> trimmed
            // Dangerous schemes – reject silently so no navigation occurs.
            trimmed.startsWith("file://")      ||
            trimmed.startsWith("javascript:")  ||
            trimmed.startsWith("data:")        ||
            trimmed.startsWith("blob:")        ||
            trimmed.startsWith("content:")     -> null
            // Unknown scheme – assume HTTPS.
            else -> "https://$trimmed"
        }
    }
}
