package com.subtracker.fcm

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.subtracker.App
import com.subtracker.MainActivity
import com.subtracker.Notifier
import com.subtracker.detect.DetectionPayload
import com.subtracker.detect.DetectionReconciler
import com.subtracker.formatMoney
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

class MailDetectionWorker(
    private val ctx: Context, params: WorkerParameters
) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val emailId = inputData.getString("email_id") ?: return Result.failure()
        return try {
            val payload = DetectionPayload(
                email_id = emailId,
                provider = inputData.getString("provider").orEmpty(),
                amount = inputData.getString("amount")?.toDoubleOrNull() ?: 0.0,
                currency = inputData.getString("currency").orEmpty(),
                date_iso = inputData.getString("date_iso").orEmpty(),
                cycle = inputData.getString("cycle").orEmpty(),
                confidence = inputData.getString("confidence")?.toDoubleOrNull() ?: 0.0,
                raw_subject = inputData.getString("raw_subject").orEmpty()
            )
            val app = ctx.applicationContext as App
            val pendingDao = app.db.pendingDao()
            if (pendingDao.byEmailId(payload.email_id) != null) {
                Result.success()
            } else {
                val subs = app.db.dao().getAll().first()
                val pd = DetectionReconciler.reconcile(payload, subs, System.currentTimeMillis())
                val newId = pendingDao.insert(pd)
                if (newId > 0) notify(payload, newId.toInt())
                Result.success()
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            t.printStackTrace()
            Result.success()
        }
    }

    private fun notify(payload: DetectionPayload, id: Int) {
        val intent = Intent(ctx, MainActivity::class.java).apply {
            putExtra("open_screen", "pending_detections")
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(
            ctx, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val body = "${formatMoney(payload.amount, payload.currency)} algılandı — onayla?"
        val n = NotificationCompat.Builder(ctx, Notifier.DETECTION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("${payload.provider} ödemesi")
            .setContentText(body)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        val nm = ContextCompat.getSystemService(ctx, android.app.NotificationManager::class.java) ?: return
        nm.notify(2_000_000 + id, n)
    }
}
