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

    companion object {
        private const val ADMIN_EMAIL = "admin@buswatch.com"
        private const val ADMIN_PASSWORD = "admin123"
        private const val DRIVER_EMAIL = "driver@buswatch.com"
        private const val DRIVER_PASSWORD = "driver123"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.login)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        createAdminAccount()
        createDriverAccount()

        val emailEditText = findViewById<EditText>(R.id.etLoginEmail)
        val passwordEditText = findViewById<EditText>(R.id.etLoginPassword)
        val viewPasswordButton = findViewById<ImageButton>(R.id.btnLoginViewPassword)
        val loginButton = findViewById<Button>(R.id.btnLoginLogin)
        val signupButton = findViewById<Button>(R.id.btnLoginSignup)
        val forgotPasswordButton = findViewById<Button>(R.id.btnLoginForgotPassword)

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

            when (emailInput) {
                "admin" -> {
                    emailInput = ADMIN_EMAIL
                    if (passwordInput == "admin") passwordInput = ADMIN_PASSWORD
                }
                "driver" -> {
                    emailInput = DRIVER_EMAIL
                    if (passwordInput == "driver") passwordInput = DRIVER_PASSWORD
                }
            }

            if (emailInput.isEmpty() || passwordInput.isEmpty()) {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            loginButton.isEnabled = false

            auth.signInWithEmailAndPassword(emailInput, passwordInput)
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

        forgotPasswordButton.setOnClickListener {
            startActivity(Intent(this, ForgotPassword::class.java))
        }
    }

    private fun createAdminAccount() {
        db.collection("admin").whereEqualTo("role", "admin").get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    auth.createUserWithEmailAndPassword(ADMIN_EMAIL, ADMIN_PASSWORD)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                val uid = auth.currentUser?.uid
                                if (uid != null) {
                                    val adminData = hashMapOf(
                                        "role" to "admin",
                                        "firstName" to "System",
                                        "lastName" to "Admin",
                                        "email" to ADMIN_EMAIL,
                                        "status" to "approved"
                                    )
                                    db.collection("admin").document(uid).set(adminData)
                                }
                            }
                        }
                }
            }
    }

    private fun createDriverAccount() {
        db.collection("drivers").whereEqualTo("role", "driver").get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    auth.signInWithEmailAndPassword(DRIVER_EMAIL, DRIVER_PASSWORD)
                        .addOnCompleteListener { signInTask ->
                            if (signInTask.isSuccessful) {
                                saveDriverData(auth.currentUser?.uid)
                            } else {
                                auth.createUserWithEmailAndPassword(DRIVER_EMAIL, DRIVER_PASSWORD)
                                    .addOnCompleteListener { createItemTask ->
                                        if (createItemTask.isSuccessful) {
                                            saveDriverData(auth.currentUser?.uid)
                                        }
                                    }
                            }
                        }
                }
            }
    }

    private fun saveDriverData(uid: String?) {
        if (uid == null) return
        val driverData = hashMapOf(
            "role" to "driver",
            "firstName" to "Test",
            "lastName" to "Driver",
            "email" to DRIVER_EMAIL,
            "status" to "active",
            "licenseNumber" to "DL123456"
        )
        db.collection("drivers").document(uid).set(driverData)
    }

    private fun checkUserRole(uid: String) {
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
                                        findViewById<Button>(R.id.btnLoginLogin).isEnabled = true
                                    }
                            }
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "Error fetching parent role", Toast.LENGTH_SHORT).show()
                            findViewById<Button>(R.id.btnLoginLogin).isEnabled = true
                        }
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error fetching admin role", Toast.LENGTH_SHORT).show()
                findViewById<Button>(R.id.btnLoginLogin).isEnabled = true
            }
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
