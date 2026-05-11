package com.subtracker.detect

import kotlinx.serialization.Serializable

@Serializable
data class DetectionPayload(
    val email_id: String,
    val provider: String,
    val amount: Double,
    val currency: String,
    val date_iso: String,
    val cycle: String,
    val confidence: Double,
    val raw_subject: String = ""
)
