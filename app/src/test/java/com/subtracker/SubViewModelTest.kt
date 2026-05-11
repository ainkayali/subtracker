package com.subtracker

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.subtracker.detect.DetectionPayload
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class SubViewModelTest {
    private lateinit var db: AppDb

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDb::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `rollForwardPastDueSubscriptions advances next billing and writes one log per elapsed cycle`() = runBlocking {
        val subId = db.dao().insert(
            Subscription(
                name = "Netflix",
                amount = 149.99,
                currency = "TRY",
                cycle = "monthly",
                nextBilling = millis(LocalDate.of(2026, 1, 1)),
                category = "Yayin"
            )
        )

        rollForwardPastDueSubscriptions(db, millis(LocalDate.of(2026, 3, 15)))

        val updated = db.dao().byId(subId)!!
        val logs = db.paymentDao().forSub(subId).first()
        assertEquals(LocalDate.of(2026, 4, 1), date(updated.nextBilling))
        assertEquals(3, logs.size)
        assertEquals(listOf(
            LocalDate.of(2026, 3, 1),
            LocalDate.of(2026, 2, 1),
            LocalDate.of(2026, 1, 1)
        ), logs.map { date(it.paidAt) })
        assertTrue(logs.all { it.amount == 149.99 && it.currency == "TRY" && it.cycleAtPayment == "monthly" })
    }

    @Test
    fun `rollForwardPastDueSubscriptions leaves future subscriptions untouched`() = runBlocking {
        val subId = db.dao().insert(
            Subscription(
                name = "Future",
                amount = 50.0,
                currency = "TRY",
                cycle = "monthly",
                nextBilling = millis(LocalDate.of(2026, 6, 1)),
                category = "Diger"
            )
        )

        rollForwardPastDueSubscriptions(db, millis(LocalDate.of(2026, 5, 11)))

        assertEquals(LocalDate.of(2026, 6, 1), date(db.dao().byId(subId)!!.nextBilling))
        assertTrue(db.paymentDao().forSub(subId).first().isEmpty())
    }

    @Test
    fun `byId returns null for missing subscription`() = runBlocking {
        assertNull(db.dao().byId(999L))
    }

    @Test
    fun `deleting subscription cascades payment logs`() = runBlocking {
        val subId = db.dao().insert(
            Subscription(
                name = "Cascade",
                amount = 10.0,
                currency = "TRY",
                cycle = "monthly",
                nextBilling = millis(LocalDate.of(2026, 5, 1)),
                category = "Diger"
            )
        )
        val sub = db.dao().byId(subId)!!
        db.paymentDao().insert(
            PaymentLog(
                subscriptionId = subId,
                paidAt = sub.nextBilling,
                amount = sub.amount,
                currency = sub.currency,
                cycleAtPayment = sub.cycle
            )
        )

        db.dao().delete(sub)

        assertTrue(db.paymentDao().forSub(subId).first().isEmpty())
    }

    @Test
    fun `applying new payment advances next billing from paid date`() = runBlocking {
        val subId = db.dao().insert(
            Subscription(
                name = "Spotify",
                amount = 99.0,
                currency = "TRY",
                cycle = "monthly",
                nextBilling = millis(LocalDate.of(2026, 5, 1)),
                category = "Muzik"
            )
        )
        val detection = PendingDetection(
            emailId = "mail-spotify-may",
            kind = "new_payment",
            targetSubId = subId,
            provider = "Spotify",
            amount = 99.0,
            currency = "TRY",
            dateIso = "2026-05-11",
            cycle = "monthly",
            confidence = 0.96,
            rawSubject = "Spotify receipt",
            createdAt = millis(LocalDate.of(2026, 5, 11))
        )

        db.withTransaction {
            applyDetectionInTransaction(db, detection)
        }

        val updated = db.dao().byId(subId)!!
        val logs = db.paymentDao().forSub(subId).first()
        assertEquals(LocalDate.of(2026, 6, 11), date(updated.nextBilling))
        assertEquals(1, logs.size)
        assertEquals(LocalDate.of(2026, 5, 11), date(logs.single().paidAt))
    }

    @Test
    fun `backfill auto apply reuses newly created subscription for later same provider payments`() = runBlocking {
        val detections = listOf(
            DetectionPayload(
                email_id = "spotify-apr",
                provider = "Spotify",
                amount = 99.0,
                currency = "TRY",
                date_iso = "2026-04-11",
                cycle = "monthly",
                confidence = 0.97,
                raw_subject = "Spotify April receipt"
            ),
            DetectionPayload(
                email_id = "spotify-may",
                provider = "Spotify",
                amount = 99.0,
                currency = "TRY",
                date_iso = "2026-05-11",
                cycle = "monthly",
                confidence = 0.97,
                raw_subject = "Spotify May receipt"
            )
        )

        val result = applyBackfillDetectionsInTransaction(
            db = db,
            detections = detections,
            now = millis(LocalDate.of(2026, 5, 12))
        )

        val subscriptions = db.dao().getAll().first()
        val logs = db.paymentDao().recent(10).first()
        assertEquals(2, result.inserted)
        assertEquals(2, result.autoApplied)
        assertEquals(0, result.pending)
        assertEquals(1, subscriptions.size)
        assertEquals(LocalDate.of(2026, 6, 11), date(subscriptions.single().nextBilling))
        assertEquals(2, logs.size)
        assertEquals(
            listOf(LocalDate.of(2026, 5, 11), LocalDate.of(2026, 4, 11)),
            logs.map { date(it.paidAt) }
        )
    }

    @Test
    fun `clearAllSubscriptionsInTransaction clears detection history so backfill can rebuild`() = runBlocking {
        val subId = db.dao().insert(
            Subscription(
                name = "Spotify",
                amount = 99.0,
                currency = "TRY",
                cycle = "monthly",
                nextBilling = millis(LocalDate.of(2026, 6, 11)),
                category = "Muzik"
            )
        )
        db.paymentDao().insert(
            PaymentLog(
                subscriptionId = subId,
                paidAt = millis(LocalDate.of(2026, 5, 11)),
                amount = 99.0,
                currency = "TRY",
                cycleAtPayment = "monthly"
            )
        )
        db.pendingDao().insert(
            PendingDetection(
                emailId = "spotify-may",
                kind = "new_sub",
                targetSubId = null,
                provider = "Spotify",
                amount = 99.0,
                currency = "TRY",
                dateIso = "2026-05-11",
                cycle = "monthly",
                confidence = 0.97,
                rawSubject = "Spotify May receipt",
                createdAt = millis(LocalDate.of(2026, 5, 12)),
                status = "accepted"
            )
        )

        clearAllSubscriptionsInTransaction(db)

        assertTrue(db.dao().all().isEmpty())
        assertTrue(db.paymentDao().recent(10).first().isEmpty())
        assertNull(db.pendingDao().byEmailId("spotify-may"))
    }

    @Test
    fun `backfill replays accepted detections when matching subscription was deleted`() = runBlocking {
        db.pendingDao().insert(
            PendingDetection(
                emailId = "spotify-may",
                kind = "new_sub",
                targetSubId = null,
                provider = "Spotify",
                amount = 99.0,
                currency = "TRY",
                dateIso = "2026-05-11",
                cycle = "monthly",
                confidence = 0.97,
                rawSubject = "Spotify May receipt",
                createdAt = millis(LocalDate.of(2026, 5, 12)),
                status = "accepted"
            )
        )

        val result = applyBackfillDetectionsInTransaction(
            db = db,
            detections = listOf(
                DetectionPayload(
                    email_id = "spotify-may",
                    provider = "Spotify",
                    amount = 99.0,
                    currency = "TRY",
                    date_iso = "2026-05-11",
                    cycle = "monthly",
                    confidence = 0.97,
                    raw_subject = "Spotify May receipt"
                )
            ),
            now = millis(LocalDate.of(2026, 5, 12))
        )

        assertEquals(1, result.autoApplied)
        assertEquals(1, db.dao().getAll().first().size)
    }

    private fun millis(date: LocalDate): Long =
        date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun date(millis: Long): LocalDate =
        java.time.Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
}
