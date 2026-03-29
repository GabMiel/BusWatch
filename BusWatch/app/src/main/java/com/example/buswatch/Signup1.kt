package com.example.buswatch

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class Signup1 : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.signup1)

        val nextButton = findViewById<Button>(R.id.btnSignup1Next)
        val signinButton = findViewById<Button>(R.id.btnSignup1Signin)

        nextButton.setOnClickListener {
            val intent = Intent(this, Signup2::class.java)
            startActivity(intent)
        }

        signinButton.setOnClickListener {
            val intent = Intent(this, Login::class.java)
            startActivity(intent)
            finish()
        }
    }
}