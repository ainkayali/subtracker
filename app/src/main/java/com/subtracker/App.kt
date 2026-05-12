package com.subtracker

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.room.Room
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.messaging.FirebaseMessaging
import com.subtracker.fcm.FcmTokenRegisterWorker
import java.util.concurrent.TimeUnit

class App : Application() {
    val db by lazy {
        Room.databaseBuilder(this, AppDb::class.java, "subs.db")
            .addMigrations(AppDb.MIGRATION_1_2, AppDb.MIGRATION_2_3)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        installCrashHandler()
        createNotificationChannels()
        if (!isRobolectric()) {
            runCatching { scheduleReminderWorker() }
            runCatching { registerFcmTokenOnStart() }
        }
    }

    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val sw = java.io.StringWriter()
                throwable.printStackTrace(java.io.PrintWriter(sw))
                val ts = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                    .format(java.util.Date())
                java.io.File(filesDir, "last_crash.txt").writeText(
                    "$ts thread=${thread.name}\n${sw}"
                )
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun isRobolectric(): Boolean =
        Build.FINGERPRINT.equals("robolectric", ignoreCase = true)

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(
            Notifier.CHANNEL_ID,
            "Abonelik hatirlaticilari",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "Yaklasan abonelik odemeleri icin hatirlaticilar" })
        nm.createNotificationChannel(NotificationChannel(
            Notifier.DETECTION_CHANNEL_ID,
            "Mail tabanli tespitler",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "Maillerden tespit edilen abonelikler" })
    }

    private fun scheduleReminderWorker() {
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(
            24, TimeUnit.HOURS, 1, TimeUnit.HOURS
        )
            .setInitialDelay(1, TimeUnit.HOURS)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            Notifier.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private fun registerFcmTokenOnStart() {
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            WorkManager.getInstance(this).enqueue(
                OneTimeWorkRequestBuilder<FcmTokenRegisterWorker>()
                    .setInputData(Data.Builder().putString("token", token).build())
                    .build()
            )
        }
    }
}
