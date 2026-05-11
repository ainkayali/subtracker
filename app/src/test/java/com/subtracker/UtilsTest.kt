package com.subtracker

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class UtilsTest {
    @Test
    fun `monthlyAmount weekly multiplies by 52 over 12`() {
        val sub = Subscription(name = "Weekly", amount = 12.0, currency = "TRY", cycle = "weekly", nextBilling = 0L, category = "Diger")

        assertEquals(52.0, monthlyAmount(sub), 0.0001)
    }

    @Test
    fun `monthlyAmount monthly returns amount`() {
        val sub = Subscription(name = "Monthly", amount = 99.0, currency = "TRY", cycle = "monthly", nextBilling = 0L, category = "Diger")

        assertEquals(99.0, monthlyAmount(sub), 0.0001)
    }

    @Test
    fun `monthlyAmount yearly divides by 12`() {
        val sub = Subscription(name = "Yearly", amount = 1200.0, currency = "TRY", cycle = "yearly", nextBilling = 0L, category = "Diger")

        assertEquals(100.0, monthlyAmount(sub), 0.0001)
    }

    @Test
    fun `addCycle monthly handles end of January`() {
        val next = addCycle(millis(LocalDate.of(2026, 1, 31)), "monthly")

        assertEquals(LocalDate.of(2026, 2, 28), date(next))
    }

    @Test
    fun `addCycle weekly adds seven days`() {
        val next = addCycle(millis(LocalDate.of(2026, 5, 1)), "weekly")

        assertEquals(LocalDate.of(2026, 5, 8), date(next))
    }

    @Test
    fun `addCycle yearly adds one year`() {
        val next = addCycle(millis(LocalDate.of(2026, 5, 1)), "yearly")

        assertEquals(LocalDate.of(2027, 5, 1), date(next))
    }

    @Test
    fun `relativeLabel handles key boundaries`() {
        val today = LocalDate.now()

        assertEquals("Bugun", relativeLabel(millis(today)).withoutTurkishChars())
        assertEquals("Yarin", relativeLabel(millis(today.plusDays(1))).withoutTurkishChars())
        assertEquals("6 gun sonra", relativeLabel(millis(today.plusDays(6))).withoutTurkishChars())
        assertEquals("1 hafta sonra", relativeLabel(millis(today.plusDays(7))).withoutTurkishChars())
        assertEquals("4 hafta sonra", relativeLabel(millis(today.plusDays(29))).withoutTurkishChars())
        assertEquals("1 ay sonra", relativeLabel(millis(today.plusDays(30))).withoutTurkishChars())
        assertEquals("12 ay sonra", relativeLabel(millis(today.plusDays(364))).withoutTurkishChars())
        assertEquals("1 yil sonra", relativeLabel(millis(today.plusDays(365))).withoutTurkishChars())
    }

    private fun millis(date: LocalDate): Long =
        date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun date(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

    private fun String.withoutTurkishChars(): String =
        replace('ü', 'u').replace('Ü', 'U')
            .replace('ı', 'i').replace('İ', 'I')
            .replace('ğ', 'g').replace('Ğ', 'G')
            .replace('ş', 's').replace('Ş', 'S')
}
