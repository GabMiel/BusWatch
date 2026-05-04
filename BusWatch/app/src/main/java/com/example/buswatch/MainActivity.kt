package com.example.buswatch

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.startup)

        // After 2 seconds, move to Load1 or Login
        Handler(Looper.getMainLooper()).postDelayed({
            val sharedPref = getSharedPreferences("onboarding", Context.MODE_PRIVATE)
            val isFirstRun = sharedPref.getBoolean("isFirstRun", true)

            if (isFirstRun) {
                val intent = Intent(this, Load1::class.java)
                startActivity(intent)
            } else {
                val intent = Intent(this, Login::class.java)
                startActivity(intent)
            }
            finish()
        }, 2000)
    }
}