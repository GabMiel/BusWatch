package com.example.buswatch.admin

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Redirect to AdminHome
        val intent = Intent(this, AdminHome::class.java)
        startActivity(intent)
        finish()
    }
}
