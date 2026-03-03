package com.nsstaff.downdetectapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import com.google.firebase.messaging.RemoteMessage

class FirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "Message received from: ${message.from}")

        message.notification?.let { notification ->
            showNotification(
                title = notification.title ?: "DownDetect",
                body = notification.body ?: ""
            )
        } ?: run {
            message.data.let { data ->
                showNotification(
                    title = data["title"] ?: "DownDetect",
                    body = data["body"] ?: ""
                )
            }
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token: ${token.take(20)}...")
        sendTokenToServer(token)
    }

    private fun showNotification(title: String, body: String) {
        val channelId = "downdetect_alerts"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "DownDetect Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when app status is degraded or unhealthy"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun sendTokenToServer(token: String) {
        val apiBaseUrl = try {
            val inputStream = resources.openRawResource(R.raw.config)
            val properties = java.util.Properties()
            properties.load(inputStream)
            inputStream.close()
            properties.getProperty("api_base_url", "")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading config", e)
            ""
        }
        if (apiBaseUrl.isBlank()) return

        Thread {
            try {
                val url = "$apiBaseUrl/api/register-push"
                val client = okhttp3.OkHttpClient()
                val body = """{"token":"$token"}""".toRequestBody("application/json".toMediaType())
                val request = okhttp3.Request.Builder()
                    .url(url)
                    .post(body)
                    .build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    Log.d(TAG, "Token registered successfully")
                } else {
                    Log.w(TAG, "Token registration failed: ${response.code}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error registering token", e)
            }
        }.start()
    }

    companion object {
        private const val TAG = "DownDetectFCM"
        private const val NOTIFICATION_ID = 1001
    }
}
