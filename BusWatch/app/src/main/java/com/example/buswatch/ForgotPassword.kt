package com.example.buswatch

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class ForgotPassword : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.forgot_password)

        auth = FirebaseAuth.getInstance()

        val btnBack = findViewById<ImageButton>(R.id.btnForgotBack)
        val etEmail = findViewById<EditText>(R.id.etForgotEmail)
        val btnSubmit = findViewById<Button>(R.id.btnForgotSubmit)

        btnBack.setOnClickListener {
            finish()
        }

        btnSubmit.setOnClickListener {
            val email = etEmail.text.toString().trim()

            if (email.isEmpty()) {
                Toast.makeText(this, "Please enter your email address", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnSubmit.isEnabled = false
            auth.sendPasswordResetEmail(email)
                .addOnCompleteListener { task ->
                    btnSubmit.isEnabled = true
                    if (task.isSuccessful) {
                        Toast.makeText(this, "Reset link sent to your email!", Toast.LENGTH_LONG).show()
                        finish()
                    } else {
                        Toast.makeText(this, "Error: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }
}
