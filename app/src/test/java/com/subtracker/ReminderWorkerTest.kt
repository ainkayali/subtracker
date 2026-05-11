package com.subtracker

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class ReminderWorkerTest {
    private lateinit var context: Context
    private lateinit var db: AppDb

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("subscription_reminders", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        db = Room.inMemoryDatabaseBuilder(context, AppDb::class.java)
            .allowMainThreadQueries()
            .build()
        Notifier.resetForTest()
        Notifier.dbForTest = db
    }

    @After
    fun tearDown() {
        db.close()
        Notifier.resetForTest()
    }

    @Test
    fun `worker fires notification once for due reminder then deduplicates same billing date`() = runBlocking {
        val now = millis(LocalDate.of(2026, 5, 11))
        Notifier.nowMillisForTest = now
        val fired = mutableListOf<Subscription>()
        Notifier.notificationHookForTest = { _, sub -> fired.add(sub) }
        db.dao().insert(
            Subscription(
                name = "Spotify",
                amount = 59.99,
                currency = "TRY",
                cycle = "monthly",
                nextBilling = millis(LocalDate.of(2026, 5, 12)),
                category = "Yayin",
                reminderOn = true,
                reminderDays = 1
            )
        )

        assertSuccess(TestListenableWorkerBuilder<ReminderWorker>(context).build().doWork())
        assertEquals(listOf("Spotify"), fired.map { it.name })

        fired.clear()
        assertSuccess(TestListenableWorkerBuilder<ReminderWorker>(context).build().doWork())
        assertTrue(fired.isEmpty())
    }

    @Test
    fun `worker skips disabled reminders`() = runBlocking {
        Notifier.nowMillisForTest = millis(LocalDate.of(2026, 5, 11))
        val fired = mutableListOf<Subscription>()
        Notifier.notificationHookForTest = { _, sub -> fired.add(sub) }
        db.dao().insert(
            Subscription(
                name = "Muted",
                amount = 10.0,
                currency = "TRY",
                cycle = "monthly",
                nextBilling = millis(LocalDate.of(2026, 5, 11)),
                category = "Diger",
                reminderOn = false,
                reminderDays = 0
            )
        )

        assertSuccess(TestListenableWorkerBuilder<ReminderWorker>(context).build().doWork())
        assertTrue(fired.isEmpty())
    }

    @Test
    fun `worker skips subscriptions outside reminder window`() = runBlocking {
        Notifier.nowMillisForTest = millis(LocalDate.of(2026, 5, 11))
        val fired = mutableListOf<Subscription>()
        Notifier.notificationHookForTest = { _, sub -> fired.add(sub) }
        db.dao().insert(
            Subscription(
                name = "Later",
                amount = 10.0,
                currency = "TRY",
                cycle = "monthly",
                nextBilling = millis(LocalDate.of(2026, 5, 20)),
                category = "Diger",
                reminderOn = true,
                reminderDays = 1
            )
        )

        assertSuccess(TestListenableWorkerBuilder<ReminderWorker>(context).build().doWork())
        assertTrue(fired.isEmpty())
    }

    private fun millis(date: LocalDate): Long =
        date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun assertSuccess(result: ListenableWorker.Result) {
        assertEquals(ListenableWorker.Result.success().javaClass, result.javaClass)
    }
}
