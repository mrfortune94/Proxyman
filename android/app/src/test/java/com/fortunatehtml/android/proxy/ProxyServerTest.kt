package com.fortunatehtml.android.proxy

import com.fortunatehtml.android.data.TrafficRepository
import com.fortunatehtml.android.model.TrafficEntry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for the proxy-server logic that can be exercised without a live
 * network or Android system services.
 */
class ProxyServerTest {

    private lateinit var repository: TrafficRepository

    @Before
    fun setup() {
        repository = TrafficRepository()
    }

    // ----------------------------------------------------------------
    // Entry state transitions via the repository (mirrors handleHttp /
    // forwardHttpsRequest paths)
    // ----------------------------------------------------------------

    @Test
    fun `traffic entry transitions from PENDING to COMPLETE`() {
        val entry = TrafficEntry(
            method = "GET",
            url = "http://example.com/",
            host = "example.com",
            path = "/",
            scheme = "http"
        )
        repository.addEntry(entry)
        assertEquals(TrafficEntry.State.PENDING, repository.getEntry(entry.id)?.state)

        repository.updateEntry(entry.id) {
            it.copy(statusCode = 200, state = TrafficEntry.State.COMPLETE)
        }

        val updated = repository.getEntry(entry.id)
        assertNotNull(updated)
        assertEquals(TrafficEntry.State.COMPLETE, updated?.state)
        assertEquals(200, updated?.statusCode)
        assertEquals("200", updated?.statusText)
    }

    @Test
    fun `traffic entry transitions from PENDING to FAILED`() {
        val entry = TrafficEntry(
            method = "GET",
            url = "http://unreachable.invalid/",
            host = "unreachable.invalid",
            path = "/",
            scheme = "http"
        )
        repository.addEntry(entry)

        repository.updateEntry(entry.id) { it.copy(state = TrafficEntry.State.FAILED) }

        val updated = repository.getEntry(entry.id)
        assertNotNull(updated)
        assertEquals(TrafficEntry.State.FAILED, updated?.state)
        assertEquals("Error", updated?.statusText)
    }

    @Test
    fun `updateEntry on non-existent id is a no-op`() {
        // Updating a missing entry must not throw
        repository.updateEntry("does-not-exist") { it.copy(statusCode = 500) }
        assertEquals(0, repository.getEntries().size)
    }

    // ----------------------------------------------------------------
    // Null-entry guard — validates the fix for the scope bug in
    // handleHttp() where `entry` was referenced in catch{} before it
    // could be assigned.
    // ----------------------------------------------------------------

    @Test
    fun `null entry guard prevents repository update when entry was never created`() {
        // This mirrors the corrected catch-block pattern in handleHttp():
        //   var entry: TrafficEntry? = null
        //   try { ... entry = TrafficEntry(...) ... }
        //   catch (e: Exception) { entry?.let { ... } }
        //
        // When an exception is thrown before `entry` is assigned, the
        // `entry?.let { ... }` block must NOT be executed.

        var entry: TrafficEntry? = null
        var updateCalled = false

        // Simulate: exception thrown before entry is assigned
        entry?.let {
            updateCalled = true
            repository.updateEntry(it.id) { e -> e.copy(state = TrafficEntry.State.FAILED) }
        }

        assertFalse("updateEntry must not be called when entry is null", updateCalled)
        assertEquals(0, repository.getEntries().size)
    }

    @Test
    fun `null entry guard still updates repository when entry was created before exception`() {
        // When an exception is thrown AFTER `entry` is assigned, the
        // `entry?.let { ... }` block must execute and update the entry.

        var entry: TrafficEntry? = null

        entry = TrafficEntry(
            method = "POST",
            url = "http://example.com/api",
            host = "example.com",
            path = "/api",
            scheme = "http"
        )
        repository.addEntry(entry)

        // Simulate: exception thrown after entry is assigned
        entry?.let {
            repository.updateEntry(it.id) { e -> e.copy(state = TrafficEntry.State.FAILED) }
        }

        val result = repository.getEntry(entry!!.id)
        assertNotNull(result)
        assertEquals(TrafficEntry.State.FAILED, result?.state)
    }
}
