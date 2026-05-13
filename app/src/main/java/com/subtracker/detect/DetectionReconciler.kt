package com.subtracker.detect

import com.subtracker.PendingDetection
import com.subtracker.Subscription
import java.util.Locale

object DetectionReconciler {
    private const val AMOUNT_EPSILON = 0.01

    fun reconcile(
        payload: DetectionPayload,
        subscriptions: List<Subscription>,
        now: Long
    ): PendingDetection {
        val pName = payload.provider.lowercase(Locale.US)
        val match = subscriptions.firstOrNull {
            val n = it.name.lowercase(Locale.US)
            n.contains(pName) || pName.contains(n.substringBefore(' '))
        }
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
