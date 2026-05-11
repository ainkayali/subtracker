package com.subtracker.detect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubhookAuthTest {
    private val secret = "a".repeat(64)

    @Test
    fun `sign produces header with parsable timestamp and signature`() {
        val ts = 1_700_000_000_000L
        val body = """{"hi":1}"""
        val header = SubhookAuth.sign(secret, ts, body)
        val match = Regex("""^HMAC-SHA256\s+ts=(\d+),sig=([A-Za-z0-9+/=]+)$""").matchEntire(header)
        assertTrue("header malformed: $header", match != null)
        assertEquals("1700000000000", match!!.groupValues[1])
        // Determinism
        assertEquals(header, SubhookAuth.sign(secret, ts, body))
    }

    @Test
    fun `different body produces different signature`() {
        val ts = 1_700_000_000_000L
        val a = SubhookAuth.sign(secret, ts, "x")
        val b = SubhookAuth.sign(secret, ts, "y")
        assertTrue("signatures should differ", a != b)
    }
}
