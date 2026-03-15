package com.fortunatehtml.android.util

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [UrlUtils.sanitise] – the shared URL scheme validation used
 * by the WebView browser and the Repeater to prevent dangerous scheme navigation.
 */
class UrlUtilsTest {

    // ----------------------------------------------------------------
    // Allowed schemes – must pass through unchanged
    // ----------------------------------------------------------------

    @Test
    fun `https URL returned as-is`() {
        assertEquals("https://example.com/api", UrlUtils.sanitise("https://example.com/api"))
    }

    @Test
    fun `http URL returned as-is`() {
        assertEquals("http://localhost:8080/health", UrlUtils.sanitise("http://localhost:8080/health"))
    }

    @Test
    fun `bare hostname prefixed with https`() {
        assertEquals("https://api.example.com", UrlUtils.sanitise("api.example.com"))
    }

    @Test
    fun `leading and trailing whitespace is trimmed`() {
        assertEquals("https://example.com", UrlUtils.sanitise("  https://example.com  "))
    }

    // ----------------------------------------------------------------
    // Blocked schemes – must return null
    // ----------------------------------------------------------------

    @Test
    fun `file scheme is blocked`() {
        assertNull(UrlUtils.sanitise("file:///etc/passwd"))
    }

    @Test
    fun `javascript scheme is blocked`() {
        assertNull(UrlUtils.sanitise("javascript:alert(1)"))
    }

    @Test
    fun `data scheme is blocked`() {
        assertNull(UrlUtils.sanitise("data:text/html,<script>alert(1)</script>"))
    }

    @Test
    fun `blob scheme is blocked`() {
        assertNull(UrlUtils.sanitise("blob:https://example.com/some-id"))
    }

    @Test
    fun `content scheme is blocked`() {
        assertNull(UrlUtils.sanitise("content://com.example.provider/data"))
    }

    // ----------------------------------------------------------------
    // Edge cases
    // ----------------------------------------------------------------

    @Test
    fun `empty string prefixed with https`() {
        assertEquals("https://", UrlUtils.sanitise(""))
    }

    @Test
    fun `whitespace-only string prefixed with https after trim`() {
        assertEquals("https://", UrlUtils.sanitise("   "))
    }
}
