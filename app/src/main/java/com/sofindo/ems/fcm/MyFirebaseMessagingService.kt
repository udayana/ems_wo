package com.sofindo.ems.fcm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.sofindo.ems.R
import com.sofindo.ems.api.RetrofitClient
import com.sofindo.ems.services.UserService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.media.RingtoneManager
import android.net.Uri
import android.Manifest

class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val CHANNEL_ID = "ems_default_channel"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        sendTokenToServer(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.notification?.title ?: message.data["title"] ?: "EMS WO"
        val body = message.notification?.body ?: message.data["body"] ?: "New message"
        val woId = message.data["woId"] ?: message.data["workorder_id"]
        val clickAction = message.notification?.clickAction ?: message.data["click_action"]
        showNotification(title, body, woId, clickAction)
    }

    private fun showNotification(title: String, body: String, woId: String? = null, clickAction: String? = null) {
        // Check notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // Permission not granted, skip showing notification
                return
            }
        }

        createChannel()

        // Create PendingIntent for notification click (deep linking)
        val intent = android.content.Intent(this, com.sofindo.ems.MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                    android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP

            if (!clickAction.isNullOrEmpty()) {
                action = clickAction
            } else if (!woId.isNullOrEmpty()) {
                action = com.sofindo.ems.MainActivity.ACTION_OPEN_WORK_ORDER
            }

            if (!woId.isNullOrEmpty()) {
                putExtra(com.sofindo.ems.MainActivity.EXTRA_TARGET_WO_ID, woId)
                putExtra("workorder_id", woId)
                putExtra("woId", woId)
                data = android.net.Uri.parse("emswo://workorder/$woId")
            }
        }

        val pendingIntent = android.app.PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_task)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setVibrate(longArrayOf(0, 500, 300, 500))
            .setContentIntent(pendingIntent)  // ✅ Deep linking intent
            .build()

        try {
            with(NotificationManagerCompat.from(this)) {
                notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
            }
        } catch (e: SecurityException) {
            // Handle potential SecurityException
            android.util.Log.w("FCM", "Failed to show notification due to permission", e)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "EMS Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for EMS WO"
                enableLights(true)
                lightColor = Color.GREEN
                enableVibration(true)
                val attrs = android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                // Default notification sound; can be changed to raw/ringtone if available
                val defaultSound: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                setSound(defaultSound, attrs)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun sendTokenToServer(token: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val user = UserService.getCurrentUser() ?: return@launch
                val email = user.email
                val dept = user.dept ?: ""
                RetrofitClient.apiService.saveFcmToken(
                    token = token,
                    email = email,
                    dept = dept
                )
            } catch (_: Exception) {
                // Ignore silently
            }
        }
    }
}


