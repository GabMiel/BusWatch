package com.example.buswatch

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity

class Login : AppCompatActivity() {
    private var isPasswordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.login)

        val emailEditText = findViewById<EditText>(R.id.editTextText)
        val passwordEditText = findViewById<EditText>(R.id.editTextTextPassword)
        val viewPasswordButton = findViewById<ImageButton>(R.id.btnLoginViewPassword)
        val loginButton = findViewById<Button>(R.id.btnLoginLogin)
        val signupButton = findViewById<Button>(R.id.btnLoginSignup)

        viewPasswordButton.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            if (isPasswordVisible) {
                passwordEditText.inputType = InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                viewPasswordButton.setImageResource(R.drawable.view)
            } else {
                passwordEditText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                viewPasswordButton.setImageResource(R.drawable.view)
            }
            passwordEditText.setSelection(passwordEditText.text.length)
        }

        loginButton.setOnClickListener {
            // Navigate to Home
            val intent = Intent(this, Home::class.java)
            startActivity(intent)
        }

        signupButton.setOnClickListener {
            // Navigate to Signup1
            val intent = Intent(this, Signup1::class.java)
            startActivity(intent)
        }
    }
}