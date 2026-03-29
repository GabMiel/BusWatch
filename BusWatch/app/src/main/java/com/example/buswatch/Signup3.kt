package com.example.buswatch

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class Signup3 : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.signup3)

        val backButton = findViewById<Button>(R.id.btnSignup3Back)
        val registerButton = findViewById<Button>(R.id.btnSignup3Register)

        backButton.setOnClickListener {
            val intent = Intent(this, Signup2::class.java)
            startActivity(intent)
            finish()
        }

        registerButton.setOnClickListener {
            // After registration, go back to Login
            val intent = Intent(this, Login::class.java)
            startActivity(intent)
            finishAffinity() // Clear activity stack so user can't go back into signup
        }
    }
}