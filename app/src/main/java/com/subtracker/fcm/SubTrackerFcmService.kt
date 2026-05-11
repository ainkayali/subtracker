package com.subtracker.fcm

import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class SubTrackerFcmService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        WorkManager.getInstance(applicationContext).enqueue(
            OneTimeWorkRequestBuilder<FcmTokenRegisterWorker>()
                .setInputData(Data.Builder().putString("token", token).build())
                .build()
        )
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        if (data["type"] != "detection") return
        val builder = Data.Builder()
        for ((k, v) in data) builder.putString(k, v)
        WorkManager.getInstance(applicationContext).enqueue(
            OneTimeWorkRequestBuilder<MailDetectionWorker>().setInputData(builder.build()).build()
        )
    }
}
