package com.subtracker.detect

import com.subtracker.Subscription
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DetectionReconcilerTest {
    private val payload = DetectionPayload(
        email_id = "e1", provider = "Spotify", amount = 99.0, currency = "TRY",
        date_iso = "2026-05-11", cycle = "monthly", confidence = 0.95, raw_subject = "Receipt"
    )

    @Test
    fun `no matching subscription returns new_sub`() {
        val pd = DetectionReconciler.reconcile(payload, subscriptions = emptyList(), now = 1_700_000_000_000L)
        assertEquals("new_sub", pd.kind)
        assertNull(pd.targetSubId)
        assertEquals("Spotify", pd.provider)
        assertEquals(99.0, pd.amount, 0.001)
    }

    @Test
    fun `match by name with same amount returns new_payment`() {
        val sub = Subscription(
            id = 5, name = "Spotify Premium", amount = 99.0, currency = "TRY",
            cycle = "monthly", nextBilling = 1_700_000_000_000L, category = "Yayın"
        )
        val pd = DetectionReconciler.reconcile(payload, subscriptions = listOf(sub), now = 1_700_000_000_000L)
        assertEquals("new_payment", pd.kind)
        assertEquals(5L, pd.targetSubId)
    }

    @Test
    fun `match different amount returns amount_change`() {
        val sub = Subscription(
            id = 5, name = "Spotify", amount = 79.0, currency = "TRY",
            cycle = "monthly", nextBilling = 1_700_000_000_000L, category = "Yayın"
        )
        val pd = DetectionReconciler.reconcile(payload, subscriptions = listOf(sub), now = 1_700_000_000_000L)
        assertEquals("amount_change", pd.kind)
        assertEquals(5L, pd.targetSubId)
    }

    @Test
    fun `match different currency returns amount_change`() {
        val sub = Subscription(
            id = 5, name = "Spotify", amount = 99.0, currency = "USD",
            cycle = "monthly", nextBilling = 1_700_000_000_000L, category = "Yayın"
        )
        val pd = DetectionReconciler.reconcile(payload, subscriptions = listOf(sub), now = 1_700_000_000_000L)
        assertEquals("amount_change", pd.kind)
        assertEquals(5L, pd.targetSubId)
    }

    @Test
    fun `normalize strips corporate suffixes`() {
        assertEquals("dropbox", DetectionReconciler.normalize("Dropbox, Inc."))
        assertEquals("anthropic", DetectionReconciler.normalize("Anthropic, PBC"))
        assertEquals("reddit", DetectionReconciler.normalize("Reddit, Inc."))
        assertEquals("tp-link", DetectionReconciler.normalize("TP-Link Systems Inc."))
    }

    @Test
    fun `dropbox inc matches existing dropbox subscription`() {
        val sub = Subscription(
            id = 5, name = "Dropbox", amount = 80.99, currency = "TRY",
            cycle = "monthly", nextBilling = 1_700_000_000_000L, category = "Yazılım"
        )
        val p = payload.copy(provider = "Dropbox, Inc.", amount = 80.99)
        val pd = DetectionReconciler.reconcile(p, subscriptions = listOf(sub), now = 1_700_000_000_000L)
        assertEquals("new_payment", pd.kind)
        assertEquals(5L, pd.targetSubId)
    }

    @Test
    fun `reddit and reddit inc collapse to same sub`() {
        val sub = Subscription(
            id = 5, name = "Reddit", amount = 6.99, currency = "USD",
            cycle = "monthly", nextBilling = 1_700_000_000_000L, category = "Yayın"
        )
        val p = payload.copy(provider = "Reddit, Inc.", amount = 6.99, currency = "USD")
        val pd = DetectionReconciler.reconcile(p, subscriptions = listOf(sub), now = 1_700_000_000_000L)
        assertEquals("new_payment", pd.kind)
        assertEquals(5L, pd.targetSubId)
    }
}
