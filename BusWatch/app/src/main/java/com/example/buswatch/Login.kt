package com.example.buswatch

import android.content.Intent
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.buswatch.common.R as CommonR

class Login : AppCompatActivity() {
    private var isPasswordVisible = false
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var progressBar: ProgressBar

    companion object {
        private const val ADMIN_EMAIL = "admin@buswatch.com"
        private const val ADMIN_PASSWORD = "Admin123"
        private const val DRIVER_EMAIL = "driver@buswatch.com"
        private const val DRIVER_PASSWORD = "Driver123"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.login)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        
        val emailEditText = findViewById<EditText>(R.id.etLoginEmail)
        val passwordEditText = findViewById<EditText>(R.id.etLoginPassword)
        val viewPasswordButton = findViewById<ImageButton>(R.id.btnLoginViewPassword)
        val loginButton = findViewById<Button>(R.id.btnLoginLogin)
        val signupButton = findViewById<Button>(R.id.btnLoginSignup)
        val forgotPasswordButton = findViewById<Button>(R.id.btnLoginForgotPassword)
        progressBar = findViewById(R.id.progressBar)

        auth.currentUser?.let {
            checkUserRole(it.uid)
        }

        viewPasswordButton.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            if (isPasswordVisible) {
                passwordEditText.transformationMethod = HideReturnsTransformationMethod.getInstance()
                viewPasswordButton.setImageResource(CommonR.drawable.ic_eye)
            } else {
                passwordEditText.transformationMethod = PasswordTransformationMethod.getInstance()
                viewPasswordButton.setImageResource(CommonR.drawable.ic_eye_off)
            }
            passwordEditText.setSelection(passwordEditText.text.length)
        }

        loginButton.setOnClickListener {
            var emailInput = emailEditText.text.toString().trim()
            var passwordInput = passwordEditText.text.toString().trim()

            // Handle shortcuts for admin and driver
            if (emailInput.lowercase() == "admin") {
                emailInput = ADMIN_EMAIL
                if (passwordInput.lowercase() == "admin") {
                    passwordInput = ADMIN_PASSWORD
                }
            } else if (emailInput.lowercase() == "driver") {
                emailInput = DRIVER_EMAIL
                if (passwordInput.lowercase() == "driver") {
                    passwordInput = DRIVER_PASSWORD
                }
            }

            if (emailInput.isEmpty() || passwordInput.isEmpty()) {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            performFirebaseLogin(emailInput, passwordInput)
        }

        signupButton.setOnClickListener {
            startActivity(Intent(this, Signup1::class.java))
        }

        forgotPasswordButton.setOnClickListener {
            startActivity(Intent(this, ForgotPassword::class.java))
        }
    }

    private fun performFirebaseLogin(email: String, pass: String) {
        val loginButton = findViewById<Button>(R.id.btnLoginLogin)
        loginButton.isEnabled = false
        progressBar.visibility = View.VISIBLE

        auth.signInWithEmailAndPassword(email, pass)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val uid = auth.currentUser?.uid
                    if (uid != null) {
                        checkUserRole(uid)
                    }
                } else {
                    // If this was an admin login attempt that failed, try to create the account automatically
                    if (email == ADMIN_EMAIL && pass == ADMIN_PASSWORD) {
                        auth.createUserWithEmailAndPassword(ADMIN_EMAIL, ADMIN_PASSWORD)
                            .addOnCompleteListener { createTask ->
                                if (createTask.isSuccessful) {
                                    val uid = auth.currentUser?.uid
                                    if (uid != null) {
                                        createAdminFirestoreDoc(uid)
                                        navigateBasedOnRole("admin")
                                    }
                                } else {
                                    resetLoginState()
                                    Toast.makeText(this, "Admin Setup Failed: ${createTask.exception?.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                    } else {
                        resetLoginState()
                        Toast.makeText(this, "Login failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
    }

    private fun createAdminFirestoreDoc(uid: String) {
        val adminData = hashMapOf(
            "role" to "admin",
            "firstName" to "System",
            "lastName" to "Admin",
            "email" to ADMIN_EMAIL,
            "status" to "approved"
        )
        // Ensure we use the 'admin' collection as requested
        db.collection("admin").document(uid).set(adminData)
            .addOnSuccessListener {
                Log.d("Login", "Admin document created successfully in 'admin' collection")
            }
            .addOnFailureListener { e ->
                Log.e("Login", "Failed to create admin doc: ${e.message}")
            }
    }

    private fun checkUserRole(uid: String) {
        // Master admin bypass
        if (auth.currentUser?.email == ADMIN_EMAIL) {
            createAdminFirestoreDoc(uid) 
            navigateBasedOnRole("admin")
            return
        }

        // Check 'admin' collection
        db.collection("admin").document(uid).get()
            .addOnSuccessListener { adminDoc ->
                if (adminDoc != null && adminDoc.exists()) {
                    navigateBasedOnRole("admin")
                } else {
                    db.collection("parents").document(uid).get()
                        .addOnSuccessListener { parentDoc ->
                            if (parentDoc != null && parentDoc.exists()) {
                                val role = parentDoc.getString("role") ?: "parent"
                                navigateBasedOnRole(role)
                            } else {
                                db.collection("drivers").document(uid).get()
                                    .addOnSuccessListener { driverDoc ->
                                        if (driverDoc != null && driverDoc.exists()) {
                                            navigateBasedOnRole("driver")
                                        } else {
                                            navigateBasedOnRole("parent")
                                        }
                                    }
                                    .addOnFailureListener {
                                        Toast.makeText(this, "Error fetching driver role", Toast.LENGTH_SHORT).show()
                                        resetLoginState()
                                    }
                            }
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "Error fetching parent role", Toast.LENGTH_SHORT).show()
                            resetLoginState()
                        }
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error fetching admin role", Toast.LENGTH_SHORT).show()
                resetLoginState()
            }
    }

    private fun resetLoginState() {
        findViewById<Button>(R.id.btnLoginLogin).isEnabled = true
        progressBar.visibility = View.GONE
    }

    private fun navigateBasedOnRole(role: String?) {
        val intent = when (role?.lowercase()) {
            "admin" -> Intent(this, com.example.buswatch.admin.AdminHome::class.java)
            "driver" -> Intent(this, com.example.buswatch.driver.DriverHome::class.java)
            "parent" -> Intent(this, ParentMainActivity::class.java)
            else -> Intent(this, ParentMainActivity::class.java)
        }
        startActivity(intent)
        finish()
    }
}
