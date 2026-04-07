package com.example.buswatch

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import com.example.buswatch.common.R as CommonR

class Notification : AppCompatActivity() {
    private lateinit var btnAll: Button
    private lateinit var btnUnread: Button
    private var isFilterAll = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.notification)

        btnAll = findViewById(R.id.btnNotificationAll)
        btnUnread = findViewById(R.id.btnNotificationUnread)

        val backButton = findViewById<ImageButton>(R.id.btnNotificationBack)
        backButton.setOnClickListener {
            finish()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, CommonR.anim.stay, CommonR.anim.slide_out_bottom)
            } else {
                @Suppress("DEPRECATION")
                overridePendingTransition(CommonR.anim.stay, CommonR.anim.slide_out_bottom)
            }
        }

        btnAll.setOnClickListener {
            if (!isFilterAll) {
                isFilterAll = true
                updateFilterUI()
            }
        }

        btnUnread.setOnClickListener {
            if (isFilterAll) {
                isFilterAll = false
                updateFilterUI()
            }
        }
    }

    private fun updateFilterUI() {
        if (isFilterAll) {
            btnAll.setBackgroundResource(CommonR.drawable.rectangle_shape)
            btnAll.backgroundTintList = ContextCompat.getColorStateList(this, CommonR.color.yellow_primary)
            btnAll.setTextColor(Color.BLACK)

            btnUnread.setBackgroundColor(Color.TRANSPARENT)
            btnUnread.setTextColor("#666666".toColorInt())
        } else {
            btnAll.setBackgroundColor(Color.TRANSPARENT)
            btnAll.setTextColor("#666666".toColorInt())

            btnUnread.setBackgroundResource(CommonR.drawable.rectangle_shape)
            btnUnread.backgroundTintList = ContextCompat.getColorStateList(this, CommonR.color.yellow_primary)
            btnUnread.setTextColor(Color.BLACK)
        }
    }
}
