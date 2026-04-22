package com.subtracker

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Currency
import java.util.Date
import java.util.Locale

fun formatMoney(amount: Double, currency: String): String = try {
    NumberFormat.getCurrencyInstance(Locale.getDefault()).apply {
        setCurrency(Currency.getInstance(currency))
    }.format(amount)
} catch (_: Exception) {
    "%.2f $currency".format(amount)
}

fun monthlyAmount(sub: Subscription): Double = when (sub.cycle) {
    "weekly" -> sub.amount * 52.0 / 12.0
    "yearly" -> sub.amount / 12.0
    else -> sub.amount
}

fun relativeLabel(millis: Long): String {
    val days = ChronoUnit.DAYS.between(
        LocalDate.now(),
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
    )
    return when {
        days < 0 -> "Gecikmiş"
        days == 0L -> "Bugün"
        days == 1L -> "Yarın"
        days < 7 -> "${days} gün sonra"
        days < 30 -> "${days / 7} hafta sonra"
        days < 365 -> "${days / 30} ay sonra"
        else -> "${days / 365} yıl sonra"
    }
}

fun formatDate(millis: Long): String =
    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(millis))

fun addCycle(millis: Long, cycle: String): Long {
    val date = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
    val next = when (cycle) {
        "weekly" -> date.plusWeeks(1)
        "yearly" -> date.plusYears(1)
        else -> date.plusMonths(1)
    }
    return next.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
