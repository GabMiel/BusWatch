package com.example.buswatch

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import androidx.preference.PreferenceManager
import com.cloudinary.android.MediaManager
import com.google.android.gms.security.ProviderInstaller
import com.google.firebase.auth.FirebaseAuth
import com.onesignal.OneSignal
import com.onesignal.debug.LogLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import java.io.File

class BusWatchApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // 1. Initialize Google Play Services Provider (Fixes Broker errors)
        try {
            ProviderInstaller.installIfNeeded(this)
        } catch (e: Exception) {
            Log.e("BusWatchApp", "Google Play Services Provider installation failed: ${e.message}")
        }

        // 2. Osmdroid Initialization
        val config = Configuration.getInstance()
        config.userAgentValue = "BusWatch/1.0 (${packageName}; support@buswatch.com)"
        
        val osmdroidDir = File(filesDir, "osmdroid")
        if (!osmdroidDir.exists()) osmdroidDir.mkdirs()
        config.osmdroidBasePath = osmdroidDir
        config.osmdroidTileCache = File(osmdroidDir, "tiles")
        
        config.load(this, PreferenceManager.getDefaultSharedPreferences(this))

        // 3. Cloudinary Initialization
        val cloudinaryConfig = mapOf(
            "cloud_name" to "djtqqmjsp",
            "api_key" to "414199764998612",
            "api_secret" to "6zbS91G0UDbk0IOOYP90nI-q4sY"
        )
        MediaManager.init(this, cloudinaryConfig)

        // 4. OneSignal Initialization
        OneSignal.Debug.logLevel = LogLevel.VERBOSE
        OneSignal.initWithContext(this, "7dff4619-d475-4e8b-9a8e-31f2e7f40c19")

        FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
            OneSignal.login(uid)
        }

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
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
