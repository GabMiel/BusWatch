package com.example.buswatch

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity

class Load1 : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.load1)

        val nextButton = findViewById<ImageButton>(R.id.btnNext)
        nextButton.setOnClickListener {
            val intent = Intent(this, Load2::class.java)
            startActivity(intent)
            finish()
        }
    }
}