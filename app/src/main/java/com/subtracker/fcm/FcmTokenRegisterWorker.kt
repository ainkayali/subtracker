package com.subtracker.fcm

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.subtracker.BuildConfig
import com.subtracker.detect.SubhookClient
import kotlinx.coroutines.CancellationException

class FcmTokenRegisterWorker(
    ctx: Context, params: WorkerParameters
) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val token = inputData.getString("token") ?: return Result.failure()
        return try {
            val client = SubhookClient(BuildConfig.SUBHOOK_BASE_URL, BuildConfig.SUBHOOK_HMAC_SECRET)
            client.registerFcmToken(token, android.os.Build.MODEL)
            Result.success()
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            t.printStackTrace()
            Result.retry()
        }
    }
}
