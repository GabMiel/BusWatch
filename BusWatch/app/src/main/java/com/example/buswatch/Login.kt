package com.example.buswatch

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class Login : AppCompatActivity() {
    private var isPasswordVisible = false
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.login)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val emailEditText = findViewById<EditText>(R.id.editTextText)
        val passwordEditText = findViewById<EditText>(R.id.editTextTextPassword)
        val viewPasswordButton = findViewById<ImageButton>(R.id.btnLoginViewPassword)
        val loginButton = findViewById<Button>(R.id.btnLoginLogin)
        val signupButton = findViewById<Button>(R.id.btnLoginSignup)
        
        // Check if user is already logged in
        auth.currentUser?.let {
            checkUserRole(it.uid)
        }

        viewPasswordButton.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            if (isPasswordVisible) {
                passwordEditText.inputType = InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                passwordEditText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            passwordEditText.setSelection(passwordEditText.text.length)
        }

        loginButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            loginButton.isEnabled = false
            
            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val uid = auth.currentUser?.uid
                        if (uid != null) {
                            checkUserRole(uid)
                        }
                    } else {
                        loginButton.isEnabled = true
                        Toast.makeText(this, "Login failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
        }

        signupButton.setOnClickListener {
            startActivity(Intent(this, Signup1::class.java))
        }
    }

    private fun checkUserRole(uid: String) {
        // Renamed from "users" to "parents" as requested
        db.collection("parents").document(uid).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val role = document.getString("role")
                    navigateBasedOnRole(role)
                } else {
                    // Also check a general users collection if needed for non-parents
                    // but for now we follow the "parents" instruction
                    navigateBasedOnRole("parent")
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error fetching user role", Toast.LENGTH_SHORT).show()
                findViewById<Button>(R.id.btnLoginLogin).isEnabled = true
            }
    }

    private fun navigateBasedOnRole(role: String?) {
        val intent = when (role) {
            "admin" -> {
                Intent().apply {
                    setClassName(this@Login, "com.example.buswatch.admin.AdminHome")
                }
            }
            "driver" -> {
                Intent().apply {
                    setClassName(this@Login, "com.example.buswatch.driver.DriverHome")
                }
            }
            else -> Intent(this, Home::class.java) // Default to Parent Home
        }
        startActivity(intent)
        finish()
    }
}
