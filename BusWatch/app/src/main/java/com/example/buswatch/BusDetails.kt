package com.example.buswatch

import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity

class BusDetails : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.busdetails)

        val backButton = findViewById<ImageButton>(R.id.btnBusBack)
        backButton.setOnClickListener {
            finish()
            overridePendingTransition(android.R.anim.fade_in, R.anim.slide_out_bottom)
        }
    }
}