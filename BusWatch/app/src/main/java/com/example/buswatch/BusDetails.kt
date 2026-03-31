package com.example.buswatch

import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import com.example.buswatch.common.R as CommonR

class BusDetails : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.busdetails)

        val backButton = findViewById<ImageButton>(R.id.btnBusBack)
        backButton.setOnClickListener {
            finish()
            overridePendingTransition(android.R.anim.fade_in, CommonR.anim.slide_out_bottom)
        }
    }
}