package com.subtracker

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException

class ReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result =
        try {
            Notifier.runReminderCheck(applicationContext)
            Result.success()
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            // Don't retry indefinitely on permanent errors (schema, code bugs).
            // WorkManager will run again on next periodic interval anyway.
            Result.success()
        }
}

object Notifier {
    const val CHANNEL_ID = "subscription_reminders"
    const val DETECTION_CHANNEL_ID = "subscription_detections"
    const val WORK_NAME = "reminder_check"

    private const val PREFS = "subscription_reminders"
    private const val DAY_MILLIS = 86_400_000L

    internal var dbForTest: AppDb? = null
    internal var nowMillisForTest: Long? = null
    internal var notificationHookForTest: ((Context, Subscription) -> Unit)? = null

    internal fun resetForTest() {
        dbForTest = null
        nowMillisForTest = null
        notificationHookForTest = null
    }

    suspend fun runReminderCheck(context: Context): Int {
        val db = dbForTest ?: (context.applicationContext as App).db
        val now = nowMillisForTest ?: System.currentTimeMillis()
        val allSubs = db.dao().all()

        // Clean up dedup keys for subscriptions that no longer exist
        clearOrphanedSentKeys(context, allSubs.map { it.id }.toSet())

        val due = allSubs
            .filter { shouldConsiderForReminder(it, now) }
            .filterNot { wasAlreadySent(context, it) }

        var firedCount = 0
        due.forEach { sub ->
            if (fire(context, sub)) {
                markSent(context, sub)
                firedCount += 1
            }
        }
        return firedCount
    }

    internal fun shouldConsiderForReminder(sub: Subscription, nowMillis: Long): Boolean {
        if (!sub.reminderOn) return false
        val windowMillis = sub.reminderDays.toLong().coerceAtLeast(0L) * DAY_MILLIS
        val delta = sub.nextBilling - nowMillis
        return delta in 0..windowMillis
    }

    private fun wasAlreadySent(context: Context, sub: Subscription): Boolean =
        prefs(context).getLong(sentKey(sub), Long.MIN_VALUE) == sub.nextBilling

    private fun markSent(context: Context, sub: Subscription) {
        prefs(context).edit().putLong(sentKey(sub), sub.nextBilling).apply()
    }

    private fun clearOrphanedSentKeys(context: Context, liveIds: Set<Long>) {
        val p = prefs(context)
        val toRemove = p.all.keys.filter { key ->
            if (!key.startsWith("last_sent_")) return@filter false
            val id = key.removePrefix("last_sent_").toLongOrNull() ?: return@filter true
            id !in liveIds
        }
        if (toRemove.isNotEmpty()) {
            p.edit().also { editor -> toRemove.forEach { editor.remove(it) } }.apply()
        }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun sentKey(sub: Subscription): String =
        "last_sent_${sub.id}"

    private fun fire(context: Context, sub: Subscription): Boolean {
        notificationHookForTest?.let {
            it(context, sub)
            return true
        }

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            sub.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val daysOut = ((sub.nextBilling - System.currentTimeMillis()) / DAY_MILLIS).coerceAtLeast(0L)
        val whenText = when (daysOut) {
            0L -> "bugün"
            1L -> "yarın"
            else -> "$daysOut gün sonra"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("${sub.name} ödemesi")
            .setContentText("${formatMoney(sub.amount, sub.currency)} $whenText düşecek")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setWhen(sub.nextBilling)
            .setShowWhen(true)
            .build()

        val nm = ContextCompat.getSystemService(context, NotificationManager::class.java) ?: return false
        nm.notify(sub.id.toInt(), notification)
        return true
    }
}
