package com.subtracker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExchangeRateRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        context.getSharedPreferences("exchange_rates", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `parseTcmbXml reads ForexSelling rates and source date`() {
        val input = javaClass.getResourceAsStream("/tcmb_today_sample.xml")!!

        val rates = parseTcmbXml(input)

        assertEquals("11.05.2026", rates.sourceDate)
        assertEquals(1.0, rates.ratesToTry["TRY"]!!, 0.0001)
        assertEquals(45.2700, rates.ratesToTry["USD"]!!, 0.0001)
        assertEquals(48.9600, rates.ratesToTry["EUR"]!!, 0.0001)
    }

    @Test
    fun `loadCached returns null when preferences are empty or corrupt`() {
        val repo = ExchangeRateRepository(context)

        assertNull(repo.loadCached())

        context.getSharedPreferences("exchange_rates", Context.MODE_PRIVATE)
            .edit()
            .putString("rates", "USD:not-a-number")
            .commit()

        assertNull(repo.loadCached())
    }

    @Test
    fun `save and loadCached round trip exchange rates`() {
        val repo = ExchangeRateRepository(context)
        val expected = ExchangeRates(
            ratesToTry = mapOf("TRY" to 1.0, "USD" to 45.27),
            sourceDate = "11.05.2026",
            source = "TCMB satis"
        )

        repo.save(expected)

        assertEquals(expected, repo.loadCached())
    }
}
