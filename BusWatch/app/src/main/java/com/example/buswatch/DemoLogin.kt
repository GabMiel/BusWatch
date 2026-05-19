package com.example.buswatch

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.onesignal.OneSignal
import com.google.android.material.materialswitch.MaterialSwitch

class DemoLogin : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.demo_login)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val roleSpinner = findViewById<Spinner>(R.id.spDemoRole)
        val loginButton = findViewById<Button>(R.id.btnDemoLogin)
        val backButton = findViewById<ImageButton>(R.id.btnBack)
        val swLoginMode = findViewById<MaterialSwitch>(R.id.swLoginMode)
        progressBar = findViewById(R.id.progressBar)

        backButton.setOnClickListener { finish() }
        
        // Ensure the toggle starts as ON as per requirements
        swLoginMode.isChecked = true
        
        swLoginMode.setOnCheckedChangeListener { _, isChecked ->
            // When toggled off, go back to the regular login screen
            if (!isChecked) {
                finish()
            }
        }

        loginButton.setOnClickListener {
            val selectedRole = roleSpinner.selectedItem.toString()
            val (email, password) = when (selectedRole) {
                "Parent" -> "test1@gmail.com" to "User1234"
                "Driver" -> "driver2@gmail.com" to "Driver1234"
                "Conductor" -> "conductor@gmail.com" to "Conductor1234"
                else -> "" to ""
            }

            if (email.isNotEmpty()) {
                performDemoLogin(email, password)
            }
        }
    }

    private fun performDemoLogin(email: String, pass: String) {
        val loginButton = findViewById<Button>(R.id.btnDemoLogin)
        loginButton.isEnabled = false
        progressBar.visibility = View.VISIBLE

        auth.signInWithEmailAndPassword(email, pass)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val uid = auth.currentUser?.uid
                    if (uid != null) {
                        // Mark as demo session
                        val prefs = getSharedPreferences("BusWatchPrefs", Context.MODE_PRIVATE)
                        prefs.edit().putBoolean("is_demo", true).apply()

                        OneSignal.login(uid)
                        checkUserRole(uid)
                    }
                } else {
                    loginButton.isEnabled = true
                    progressBar.visibility = View.GONE
                    Toast.makeText(this, "Demo Login Failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun checkUserRole(uid: String) {
        db.collection("admin").document(uid).get().addOnSuccessListener { adminDoc ->
            if (adminDoc.exists()) {
                navigateBasedOnRole("admin")
            } else {
                db.collection("parents").document(uid).get().addOnSuccessListener { parentDoc ->
                    if (parentDoc.exists()) {
                        navigateBasedOnRole("parent")
                    } else {
                        db.collection("drivers").document(uid).get().addOnSuccessListener { driverDoc ->
                            if (driverDoc.exists()) {
                                navigateBasedOnRole("driver")
                            } else {
                                db.collection("conductors").document(uid).get().addOnSuccessListener { conductorDoc ->
                                    if (conductorDoc.exists()) {
                                        navigateBasedOnRole("conductor")
                                    } else {
                                        navigateBasedOnRole("parent")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }.addOnFailureListener { 
            findViewById<Button>(R.id.btnDemoLogin).isEnabled = true
            progressBar.visibility = View.GONE
        }
    }

    private fun navigateBasedOnRole(role: String) {
        val intent = when (role.lowercase()) {
            "admin" -> Intent(this, com.example.buswatch.admin.AdminHome::class.java)
            "driver", "conductor" -> Intent(this, com.example.buswatch.driver.DriverHome::class.java)
            else -> Intent(this, ParentMainActivity::class.java)
        }
        startActivity(intent)
        finishAffinity()
    }
}
