package com.fortunatehtml.android.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [BrowserActivity]'s SSL trust-check logic.
 *
 * The core trust predicate is extracted to [BrowserActivity.isTrustedByProxyCa]
 * (the two-parameter overload) so that it can be exercised here without requiring
 * Android's [android.net.http.SslError] stubs or Robolectric.
 */
class BrowserActivitySslTrustTest {

    // SslError.SSL_UNTRUSTED constant value
    private val SSL_UNTRUSTED = 3
    private val SSL_EXPIRED = 1
    private val SSL_IDMISMATCH = 2
    private val SSL_NOTYETVALID = 0

    private val proxyCaCn = BrowserActivity.PROXY_CA_CN

    // ----------------------------------------------------------------
    // Cases that SHOULD be trusted (handler.proceed())
    // ----------------------------------------------------------------

    @Test
    fun `trusted when error is SSL_UNTRUSTED and issuer CN matches proxy CA`() {
        assertTrue(BrowserActivity.isTrustedByProxyCa(SSL_UNTRUSTED, proxyCaCn))
    }

    // ----------------------------------------------------------------
    // Cases that MUST NOT be trusted (handler.cancel())
    // ----------------------------------------------------------------

    @Test
    fun `not trusted when issuer CN does not match proxy CA`() {
        assertFalse(BrowserActivity.isTrustedByProxyCa(SSL_UNTRUSTED, "Some Other CA"))
    }

    @Test
    fun `not trusted when issuer CN is null`() {
        assertFalse(BrowserActivity.isTrustedByProxyCa(SSL_UNTRUSTED, null))
    }

    @Test
    fun `not trusted when error is SSL_EXPIRED even with correct CN`() {
        assertFalse(BrowserActivity.isTrustedByProxyCa(SSL_EXPIRED, proxyCaCn))
    }

    @Test
    fun `not trusted when error is SSL_IDMISMATCH even with correct CN`() {
        assertFalse(BrowserActivity.isTrustedByProxyCa(SSL_IDMISMATCH, proxyCaCn))
    }

    @Test
    fun `not trusted when error is SSL_NOTYETVALID even with correct CN`() {
        assertFalse(BrowserActivity.isTrustedByProxyCa(SSL_NOTYETVALID, proxyCaCn))
    }

    @Test
    fun `not trusted when error is SSL_EXPIRED and issuer CN does not match`() {
        assertFalse(BrowserActivity.isTrustedByProxyCa(SSL_EXPIRED, "Evil CA"))
    }

    @Test
    fun `not trusted when issuer CN is empty string`() {
        assertFalse(BrowserActivity.isTrustedByProxyCa(SSL_UNTRUSTED, ""))
    }
}
