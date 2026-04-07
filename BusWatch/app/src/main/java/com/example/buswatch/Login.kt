package com.example.buswatch

import android.content.Intent
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.buswatch.common.R as CommonR

class Login : AppCompatActivity() {
    private var isPasswordVisible = false
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.login)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Automatically try to create the admin and driver accounts on startup for convenience
        createAdminAccount()
        createDriverAccount()

        val emailEditText = findViewById<EditText>(R.id.editTextText)
        val passwordEditText = findViewById<EditText>(R.id.editTextTextPassword)
        val viewPasswordButton = findViewById<ImageButton>(R.id.btnLoginViewPassword)
        val loginButton = findViewById<Button>(R.id.btnLoginLogin)
        val signupButton = findViewById<Button>(R.id.btnLoginSignup)
        
        auth.currentUser?.let {
            checkUserRole(it.uid)
        }

        viewPasswordButton.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            if (isPasswordVisible) {
                // Use transformationMethod instead of inputType to preserve font/typeface
                passwordEditText.transformationMethod = HideReturnsTransformationMethod.getInstance()
                viewPasswordButton.setImageResource(CommonR.drawable.ic_eye) 
            } else {
                passwordEditText.transformationMethod = PasswordTransformationMethod.getInstance()
                viewPasswordButton.setImageResource(CommonR.drawable.ic_eye_off)
            }
            passwordEditText.setSelection(passwordEditText.text.length)
        }

        loginButton.setOnClickListener {
            var email = emailEditText.text.toString().trim()
            var password = passwordEditText.text.toString().trim()

            // Handle "admin" shorthand
            if (email == "admin") {
                email = "admin@buswatch.com"
            }
            if (password == "admin") {
                password = "admin123"
            }

            // Handle "driver" shorthand
            if (email == "driver") {
                email = "driver@buswatch.com"
            }
            if (password == "driver") {
                password = "driver123"
            }

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

    private fun createAdminAccount() {
        val adminEmail = "admin@buswatch.com"
        val adminPassword = "admin123"

        db.collection("parents").whereEqualTo("role", "admin").get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    auth.createUserWithEmailAndPassword(adminEmail, adminPassword)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                val uid = auth.currentUser?.uid
                                if (uid != null) {
                                    val adminData = hashMapOf(
                                        "role" to "admin",
                                        "firstName" to "System",
                                        "lastName" to "Admin",
                                        "email" to adminEmail,
                                        "status" to "approved"
                                    )
                                    db.collection("parents").document(uid).set(adminData)
                                }
                            }
                        }
                }
            }
    }

    private fun createDriverAccount() {
        val driverEmail = "driver@buswatch.com"
        val driverPassword = "driver123"

        db.collection("parents").whereEqualTo("role", "driver").get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    auth.createUserWithEmailAndPassword(driverEmail, driverPassword)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                val uid = auth.currentUser?.uid
                                if (uid != null) {
                                    val driverData = hashMapOf(
                                        "role" to "driver",
                                        "firstName" to "Test",
                                        "lastName" to "Driver",
                                        "email" to driverEmail,
                                        "status" to "approved"
                                    )
                                    db.collection("parents").document(uid).set(driverData)
                                }
                            }
                        }
                }
            }
    }

    private fun checkUserRole(uid: String) {
        db.collection("parents").document(uid).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val role = document.getString("role")
                    navigateBasedOnRole(role)
                } else {
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
            else -> Intent(this, Home::class.java)
        }
        startActivity(intent)
        finish()
    }
}
