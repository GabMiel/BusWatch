package com.example.buswatch

import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import com.example.buswatch.common.R as CommonR

class Notification : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.notification)

        val backButton = findViewById<ImageButton>(R.id.btnNotificationBack)
        backButton.setOnClickListener {
            finish()
            overridePendingTransition(CommonR.anim.stay, CommonR.anim.slide_out_bottom)
        }
    }
}