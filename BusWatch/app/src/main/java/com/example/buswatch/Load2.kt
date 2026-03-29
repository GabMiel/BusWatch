package com.example.buswatch

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity

class Load2 : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.load2)

        val nextButton = findViewById<ImageButton>(R.id.btnLoad2Next)
        nextButton.setOnClickListener {
            val intent = Intent(this, Load3::class.java)
            startActivity(intent)
            finish()
        }
    }
}