package com.example.buswatch

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import com.example.buswatch.common.R as CommonR

class Settings : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings)

        val backButton = findViewById<ImageButton>(R.id.btnSettingsSettings)
        backButton.setOnClickListener {
            finish()
            overridePendingTransition(android.R.anim.fade_in, CommonR.anim.zoom_out)
        }

        val logoutButton = findViewById<Button>(R.id.btnSettingsLogout)
        logoutButton.setOnClickListener {
            val intent = Intent(this, Login::class.java)
            // Clear the activity stack so the user can't go back to settings after logging out
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
}