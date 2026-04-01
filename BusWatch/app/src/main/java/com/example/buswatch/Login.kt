package com.example.buswatch

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.buswatch.common.R as CommonR

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
                viewPasswordButton.setImageResource(CommonR.drawable.view)
            } else {
                passwordEditText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                viewPasswordButton.setImageResource(CommonR.drawable.view)
            }
            passwordEditText.setSelection(passwordEditText.text.length)
        }

        loginButton.setOnClickListener {
            val email = emailEditText.text.toString()
            val password = passwordEditText.text.toString()
            
            when {
                email == "user" && password == "user" -> {
                    // Navigate to Home (Parent)
                    val intent = Intent(this, Home::class.java)
                    startActivity(intent)
                    finish()
                }
                email == "admin" && password == "admin" -> {
                    // Navigate to Admin Module's AdminHome
                    val intent = Intent()
                    intent.setClassName(this, "com.example.buswatch.admin.AdminHome")
                    startActivity(intent)
                    finish()
                }
                email == "driver" && password == "driver" -> {
                    // Navigate to Driver Module's DriverHome
                    val intent = Intent()
                    intent.setClassName(this, "com.example.buswatch.driver.DriverHome")
                    startActivity(intent)
                    finish()
                }
                else -> {
                    Toast.makeText(this, getString(CommonR.string.error_invalid_credentials), Toast.LENGTH_SHORT).show()
                }
            }
        }

        signupButton.setOnClickListener {
            // Navigate to Signup1
            val intent = Intent(this, Signup1::class.java)
            startActivity(intent)
        }
    }
}