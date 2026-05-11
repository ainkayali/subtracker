package com.subtracker.detect

import com.subtracker.PendingDetection
import com.subtracker.Subscription
import java.util.Locale

object DetectionReconciler {
    private const val AMOUNT_EPSILON = 0.01

    private val SUFFIX_NOISE = listOf(
        ", inc.", " inc.", ", inc", " inc",
        ", llc", " llc", ", ltd", " ltd",
        " corporation", " corp.", " corp",
        ", pbc", " pbc",
        ", co.", " co.",
        " systems", " technologies"
    )

    internal fun normalize(name: String): String {
        var s = name.lowercase(Locale.US).trim()
        for (suf in SUFFIX_NOISE) if (s.endsWith(suf)) s = s.removeSuffix(suf).trim()
        return s.replace(Regex("[\\s.,]+"), " ").trim()
    }

    internal fun providersMatch(a: String, b: String): Boolean {
        val na = normalize(a)
        val nb = normalize(b)
        if (na.isEmpty() || nb.isEmpty()) return false
        if (na == nb) return true
        if (na.contains(nb) || nb.contains(na)) return true
        // Token overlap: any first token equality
        val ta = na.split(' ').firstOrNull() ?: ""
        val tb = nb.split(' ').firstOrNull() ?: ""
        if (ta.length >= 3 && ta == tb) return true
        return false
    }

    fun reconcile(
        payload: DetectionPayload,
        subscriptions: List<Subscription>,
        now: Long
    ): PendingDetection {
        val match = subscriptions.firstOrNull { providersMatch(it.name, payload.provider) }
        val kind = when {
            match == null -> "new_sub"
            kotlin.math.abs(match.amount - payload.amount) > AMOUNT_EPSILON -> "amount_change"
            match.currency.uppercase(Locale.US) != payload.currency.uppercase(Locale.US) -> "amount_change"
            else -> "new_payment"
        }
        return PendingDetection(
            emailId = payload.email_id,
            kind = kind,
            targetSubId = match?.id,
            provider = payload.provider,
            amount = payload.amount,
            currency = payload.currency,
            dateIso = payload.date_iso,
            cycle = payload.cycle,
            confidence = payload.confidence,
            rawSubject = payload.raw_subject,
            createdAt = now,
            status = "pending"
        )
    }
}
