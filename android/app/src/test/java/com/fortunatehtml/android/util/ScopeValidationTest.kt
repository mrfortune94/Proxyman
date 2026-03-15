package com.fortunatehtml.android.util

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for scope validation logic used in RepeaterFragment.
 * Tests the wildcard matching algorithm for scope rules.
 */
class ScopeValidationTest {

    /**
     * Test exact host matching.
     */
    @Test
    fun `exact host match returns true`() {
        val scopeHosts = setOf("api.example.com", "test.example.org")
        assertTrue(isHostInScope("api.example.com", scopeHosts))
        assertTrue(isHostInScope("test.example.org", scopeHosts))
    }

    /**
     * Test non-matching hosts return false.
     */
    @Test
    fun `non-matching host returns false`() {
        val scopeHosts = setOf("api.example.com")
        assertFalse(isHostInScope("other.example.com", scopeHosts))
        assertFalse(isHostInScope("api.other.com", scopeHosts))
    }

    /**
     * Test wildcard matching for subdomains.
     */
    @Test
    fun `wildcard matches subdomains`() {
        val scopeHosts = setOf("*.example.com")
        assertTrue(isHostInScope("api.example.com", scopeHosts))
        assertTrue(isHostInScope("test.example.com", scopeHosts))
        assertTrue(isHostInScope("sub.api.example.com", scopeHosts))
    }

    /**
     * Test wildcard also matches the base domain.
     */
    @Test
    fun `wildcard matches base domain`() {
        val scopeHosts = setOf("*.example.com")
        assertTrue(isHostInScope("example.com", scopeHosts))
    }

    /**
     * Test wildcard doesn't match unrelated domains.
     */
    @Test
    fun `wildcard does not match unrelated domain`() {
        val scopeHosts = setOf("*.example.com")
        assertFalse(isHostInScope("example.org", scopeHosts))
        assertFalse(isHostInScope("notexample.com", scopeHosts))
    }

    /**
     * Test empty scope set allows all hosts.
     */
    @Test
    fun `empty scope allows all hosts`() {
        val scopeHosts = emptySet<String>()
        assertTrue(isHostInScope("any.domain.com", scopeHosts))
        assertTrue(isHostInScope("localhost", scopeHosts))
    }

    /**
     * Test case insensitivity.
     */
    @Test
    fun `matching is case insensitive`() {
        val scopeHosts = setOf("api.example.com", "*.test.org")
        assertTrue(isHostInScope("API.EXAMPLE.COM", scopeHosts))
        assertTrue(isHostInScope("Sub.Test.Org", scopeHosts))
    }

    /**
     * Test localhost and IP addresses.
     */
    @Test
    fun `localhost and IPs can be in scope`() {
        val scopeHosts = setOf("localhost", "127.0.0.1", "192.168.1.100")
        assertTrue(isHostInScope("localhost", scopeHosts))
        assertTrue(isHostInScope("127.0.0.1", scopeHosts))
        assertTrue(isHostInScope("192.168.1.100", scopeHosts))
    }

    // Helper function mirroring RepeaterFragment logic
    private fun isHostInScope(host: String, scopeHosts: Set<String>): Boolean {
        if (scopeHosts.isEmpty()) return true

        val lowerHost = host.lowercase()
        for (scopeHost in scopeHosts) {
            val lowerScopeHost = scopeHost.lowercase()
            if (lowerScopeHost.startsWith("*.")) {
                val domain = lowerScopeHost.substring(2)
                if (lowerHost == domain || lowerHost.endsWith(".$domain")) {
                    return true
                }
            } else if (lowerHost == lowerScopeHost) {
                return true
            }
        }
        return false
    }
}
