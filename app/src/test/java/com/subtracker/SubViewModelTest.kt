package com.subtracker

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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

    private fun millis(date: LocalDate): Long =
        date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun date(millis: Long): LocalDate =
        java.time.Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
}
