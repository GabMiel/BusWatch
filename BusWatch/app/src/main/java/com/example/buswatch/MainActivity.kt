package com.example.buswatch

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.startup)

        // After 2 seconds, move to Load1Activity
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, Load1::class.java)
            startActivity(intent)
            finish()
        }, 2000)
    }
}