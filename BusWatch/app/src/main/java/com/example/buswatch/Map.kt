package com.example.buswatch

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity

class Map : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.map)

        val btnMapBusDetails = findViewById<View>(R.id.btnMapBusDetails)
        btnMapBusDetails.setOnClickListener {
            val intent = Intent(this, BusDetails::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_bottom, R.anim.stay)
        }
    }
}