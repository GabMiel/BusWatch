package com.example.buswatch

import android.app.Application
import com.cloudinary.android.MediaManager

class BusWatchApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        val config = mapOf(
            "cloud_name" to "djtqqmjsp",
            "api_key" to "414199764998612",
            "api_secret" to "6zbS91G0UDbk0IOOYP90nI-q4sY"
        )
        
        MediaManager.init(this, config)
    }
}
