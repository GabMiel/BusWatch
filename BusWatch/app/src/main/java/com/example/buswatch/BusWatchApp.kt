package com.example.buswatch

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.cloudinary.android.MediaManager
import com.google.firebase.auth.FirebaseAuth
import com.onesignal.OneSignal
import com.onesignal.debug.LogLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BusWatchApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Cloudinary Initialization
        val config = mapOf(
            "cloud_name" to "djtqqmjsp",
            "api_key" to "414199764998612",
            "api_secret" to "6zbS91G0UDbk0IOOYP90nI-q4sY"
        )
        MediaManager.init(this, config)

        // OneSignal Logging
        OneSignal.Debug.logLevel = LogLevel.VERBOSE

        // OneSignal Initialization
        OneSignal.initWithContext(this, "7dff4619-d475-4e8b-9a8e-31f2e7f40c19")

        // Link current user if already logged in
        FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
            OneSignal.login(uid)
        }

        // Request notification permission
        CoroutineScope(Dispatchers.IO).launch {
            OneSignal.Notifications.requestPermission(true)
        }

        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "BusWatch Notifications"
            val descriptionText = "Notifications for bus arrival and student boarding"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel("BUSWATCH_NOTIF", name, importance).apply {
                description = descriptionText
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
                setBypassDnd(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
