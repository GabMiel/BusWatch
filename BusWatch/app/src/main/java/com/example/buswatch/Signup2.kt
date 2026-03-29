package com.example.buswatch

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class Signup2 : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.signup2)

        val backButton = findViewById<Button>(R.id.btnSignup2Back)
        val nextButton = findViewById<Button>(R.id.btnSignup2Next)

        backButton.setOnClickListener {
            val intent = Intent(this, Signup1::class.java)
            startActivity(intent)
            finish()
        }

        nextButton.setOnClickListener {
            val intent = Intent(this, Signup3::class.java)
            startActivity(intent)
        }
    }
}