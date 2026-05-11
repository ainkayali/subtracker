package com.subtracker

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.room.Room
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class App : Application() {
    val db by lazy {
        Room.databaseBuilder(this, AppDb::class.java, "subs.db")
            .addMigrations(AppDb.MIGRATION_1_2)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        createReminderChannel()
        // Skip WorkManager scheduling under Robolectric — tests init their own.
        if (!isRobolectric()) {
            runCatching { scheduleReminderWorker() }
        }
    }

    private fun isRobolectric(): Boolean =
        Build.FINGERPRINT.equals("robolectric", ignoreCase = true)

    private fun createReminderChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            Notifier.CHANNEL_ID,
            "Abonelik hatirlaticilari",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Yaklasan abonelik odemeleri icin hatirlaticilar"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun scheduleReminderWorker() {
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(
            24,
            TimeUnit.HOURS,
            1,
            TimeUnit.HOURS
        )
            .setInitialDelay(1, TimeUnit.HOURS)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            Notifier.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
