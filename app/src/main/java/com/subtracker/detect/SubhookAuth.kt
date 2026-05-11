package com.subtracker.detect

import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object SubhookAuth {
    fun sign(secret: String, tsMillis: Long, body: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val raw = "$tsMillis.$body".toByteArray(Charsets.UTF_8)
        val sig = Base64.getEncoder().encodeToString(mac.doFinal(raw))
        return "HMAC-SHA256 ts=$tsMillis,sig=$sig"
    }
}
