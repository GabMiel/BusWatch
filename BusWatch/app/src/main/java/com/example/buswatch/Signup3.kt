package com.example.buswatch

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.buswatch.common.R as CommonR
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class Signup3 : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var selectedBloodType = "Select blood type"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.signup3)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Display child's name from Signup2
        val childFirstName = intent.getStringExtra("childFirstName") ?: "Child"
        val childLastName = intent.getStringExtra("childLastName") ?: "Name"
        findViewById<TextView>(R.id.tvChildNameDisplay).text = getString(CommonR.string.medical_information_for_child_s_name, childFirstName, childLastName)

        val bloodTypeSelector = findViewById<FrameLayout>(R.id.btnSignup3BloodType)
        val tvSelectedBloodType = bloodTypeSelector.getChildAt(0) as TextView
        
        val etAllergies = findViewById<EditText>(R.id.editTextText13)
        val etMedications = findViewById<EditText>(R.id.editTextText14)
        val etConditions = findViewById<EditText>(R.id.editTextText15)
        
        val etContact1Name = findViewById<EditText>(R.id.editTextText17)
        val etContact1Phone = findViewById<EditText>(R.id.editTextText18)
        val etContact1Relation = findViewById<EditText>(R.id.editTextText19)
        
        val etContact2Name = findViewById<EditText>(R.id.editTextText20)
        val etContact2Phone = findViewById<EditText>(R.id.editTextText22)
        val etContact2Relation = findViewById<EditText>(R.id.editTextText21)

        val backButton = findViewById<Button>(R.id.btnSignup3Back)
        val registerButton = findViewById<Button>(R.id.btnSignup3Register)

        bloodTypeSelector.setOnClickListener {
            val bloodTypes = arrayOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-", "Unknown")
            AlertDialog.Builder(this)
                .setTitle("Select Blood Type")
                .setItems(bloodTypes) { _, which ->
                    selectedBloodType = bloodTypes[which]
                    tvSelectedBloodType.text = selectedBloodType
                    tvSelectedBloodType.setTextColor(Color.BLACK)
                }
                .show()
        }

        backButton.setOnClickListener {
            finish()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, CommonR.anim.stay, CommonR.anim.slide_out_right)
            } else {
                @Suppress("DEPRECATION")
                overridePendingTransition(CommonR.anim.stay, CommonR.anim.slide_out_right)
            }
        }

        registerButton.setOnClickListener {
            val contact1Name = etContact1Name.text.toString().trim()
            val contact1Phone = etContact1Phone.text.toString().trim()
            val contact1Relation = etContact1Relation.text.toString().trim()

            if (contact1Name.isEmpty() || contact1Phone.isEmpty() || contact1Relation.isEmpty()) {
                Toast.makeText(this, "Please fill in Emergency Contact 1", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            registerButton.isEnabled = false
            registerUser(
                if (selectedBloodType == "Select blood type") "" else selectedBloodType,
                etAllergies.text.toString().trim(),
                etMedications.text.toString().trim(),
                etConditions.text.toString().trim(),
                contact1Name, contact1Phone, contact1Relation,
                etContact2Name.text.toString().trim(),
                etContact2Phone.text.toString().trim(),
                etContact2Relation.text.toString().trim()
            )
        }
    }

    private fun registerUser(
        bloodType: String, allergies: String, medications: String, conditions: String,
        c1Name: String, c1Phone: String, c1Relation: String,
        c2Name: String, c2Phone: String, c2Relation: String
    ) {
        val email = intent.getStringExtra("email") ?: ""
        val password = intent.getStringExtra("password") ?: ""

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Error: Missing credentials", Toast.LENGTH_SHORT).show()
            findViewById<Button>(R.id.btnSignup3Register).isEnabled = true
            return
        }

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val uid = auth.currentUser?.uid
                    if (uid != null) {
                        saveUserData(uid, bloodType, allergies, medications, conditions, 
                            c1Name, c1Phone, c1Relation, c2Name, c2Phone, c2Relation)
                    }
                } else {
                    findViewById<Button>(R.id.btnSignup3Register).isEnabled = true
                    Toast.makeText(this, "Registration failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun saveUserData(
        uid: String, bloodType: String, allergies: String, medications: String, conditions: String,
        c1Name: String, c1Phone: String, c1Relation: String,
        c2Name: String, c2Phone: String, c2Relation: String
    ) {
        val userData = hashMapOf(
            "role" to "parent",
            "firstName" to intent.getStringExtra("firstName"),
            "lastName" to intent.getStringExtra("lastName"),
            "middleName" to intent.getStringExtra("middleName"),
            "suffix" to intent.getStringExtra("suffix"),
            "email" to intent.getStringExtra("email"),
            "phone" to intent.getStringExtra("phone"),
            "preferredLanguage" to intent.getStringExtra("preferredLanguage"),
            
            "child" to hashMapOf(
                "firstName" to intent.getStringExtra("childFirstName"),
                "lastName" to intent.getStringExtra("childLastName"),
                "middleName" to intent.getStringExtra("childMiddleName"),
                "suffix" to intent.getStringExtra("childSuffix"),
                "age" to intent.getStringExtra("childAge"),
                "grade" to intent.getStringExtra("childGrade"),
                "avatarUrl" to intent.getStringExtra("childAvatarUrl"),
                "enrollmentFormUrl" to intent.getStringExtra("enrollmentFormUrl")
            ),
            
            "medical" to hashMapOf(
                "bloodType" to bloodType,
                "allergies" to allergies,
                "medications" to medications,
                "conditions" to conditions
            ),
            
            "emergencyContacts" to listOf(
                hashMapOf("name" to c1Name, "phone" to c1Phone, "relationship" to c1Relation),
                hashMapOf("name" to c2Name, "phone" to c2Phone, "relationship" to c2Relation)
            )
        )

        db.collection("parents").document(uid).set(userData)
            .addOnSuccessListener {
                Toast.makeText(this, "Registration Successful!", Toast.LENGTH_LONG).show()
                val intent = Intent(this, Home::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .addOnFailureListener { e ->
                findViewById<Button>(R.id.btnSignup3Register).isEnabled = true
                Toast.makeText(this, "Error saving data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
