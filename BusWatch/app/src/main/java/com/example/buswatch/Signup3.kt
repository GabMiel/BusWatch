package com.example.buswatch

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import com.example.buswatch.common.R as CommonR
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

class Signup3 : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private var selectedBloodType = "Select blood type"

    private lateinit var etAllergies: EditText
    private lateinit var etMedications: EditText
    private lateinit var etConditions: EditText
    private lateinit var etContact1Name: EditText
    private lateinit var etContact1Phone: EditText
    private lateinit var etContact1Relation: EditText
    private lateinit var etContact2Name: EditText
    private lateinit var etContact2Phone: EditText
    private lateinit var etContact2Relation: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.signup3)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()

        // Display child's name from Signup2
        val childFirstName = intent.getStringExtra("childFirstName") ?: "Child"
        val childLastName = intent.getStringExtra("childLastName") ?: "Name"
        findViewById<TextView>(R.id.tvChildNameDisplay).text = getString(CommonR.string.medical_information_for_child_s_name, childFirstName, childLastName)

        val bloodTypeSelector = findViewById<FrameLayout>(R.id.btnSignup3BloodType)
        val tvSelectedBloodType = bloodTypeSelector.getChildAt(0) as TextView
        
        etAllergies = findViewById(R.id.editTextText13)
        etMedications = findViewById(R.id.editTextText14)
        etConditions = findViewById(R.id.editTextText15)
        
        etContact1Name = findViewById(R.id.editTextText17)
        etContact1Phone = findViewById(R.id.editTextText18)
        etContact1Relation = findViewById(R.id.editTextText19)
        
        etContact2Name = findViewById(R.id.editTextText20)
        etContact2Phone = findViewById(R.id.editTextText22)
        etContact2Relation = findViewById(R.id.editTextText21)

        val backButton = findViewById<Button>(R.id.btnSignup3Back)
        val registerButton = findViewById<Button>(R.id.btnSignup3Register)

        // Pre-fill if returning
        intent.getStringExtra("bloodType")?.let {
            if (it.isNotEmpty()) {
                selectedBloodType = it
                tvSelectedBloodType.text = selectedBloodType
                tvSelectedBloodType.setTextColor(Color.BLACK)
            }
        }
        intent.getStringExtra("allergies")?.let { etAllergies.setText(it) }
        intent.getStringExtra("medications")?.let { etMedications.setText(it) }
        intent.getStringExtra("conditions")?.let { etConditions.setText(it) }
        intent.getStringExtra("c1Name")?.let { etContact1Name.setText(it) }
        intent.getStringExtra("c1Phone")?.let { etContact1Phone.setText(it) }
        intent.getStringExtra("c1Relation")?.let { etContact1Relation.setText(it) }
        intent.getStringExtra("c2Name")?.let { etContact2Name.setText(it) }
        intent.getStringExtra("c2Phone")?.let { etContact2Phone.setText(it) }
        intent.getStringExtra("c2Relation")?.let { etContact2Relation.setText(it) }

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
            goBack()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                goBack()
            }
        })

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

    private fun goBack() {
        val resultIntent = Intent().apply {
            putExtras(intent)
            putExtra("bloodType", if (selectedBloodType == "Select blood type") "" else selectedBloodType)
            putExtra("allergies", etAllergies.text.toString().trim())
            putExtra("medications", etMedications.text.toString().trim())
            putExtra("conditions", etConditions.text.toString().trim())
            putExtra("c1Name", etContact1Name.text.toString().trim())
            putExtra("c1Phone", etContact1Phone.text.toString().trim())
            putExtra("c1Relation", etContact1Relation.text.toString().trim())
            putExtra("c2Name", etContact2Name.text.toString().trim())
            putExtra("c2Phone", etContact2Phone.text.toString().trim())
            putExtra("c2Relation", etContact2Relation.text.toString().trim())
        }
        setResult(RESULT_OK, resultIntent)
        finish()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, CommonR.anim.stay, CommonR.anim.slide_out_right)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(CommonR.anim.stay, CommonR.anim.slide_out_right)
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
                        uploadImagesAndSaveData(uid, bloodType, allergies, medications, conditions, 
                            c1Name, c1Phone, c1Relation, c2Name, c2Phone, c2Relation)
                    }
                } else {
                    findViewById<Button>(R.id.btnSignup3Register).isEnabled = true
                    Toast.makeText(this, "Registration failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun uploadImagesAndSaveData(
        uid: String, bloodType: String, allergies: String, medications: String, conditions: String,
        c1Name: String, c1Phone: String, c1Relation: String,
        c2Name: String, c2Phone: String, c2Relation: String
    ) {
        val primaryAvatarUri = intent.getStringExtra("childAvatarUrl")?.toUri()
        val primaryEnrollmentUri = intent.getStringExtra("enrollmentFormUrl")?.toUri()
        
        @Suppress("UNCHECKED_CAST")
        val additionalChildren = intent.getSerializableExtra("additionalChildren") as? ArrayList<HashMap<String, Any?>> ?: arrayListOf()

        val uploadTasks = mutableListOf<Pair<String, Uri>>()
        
        if (primaryAvatarUri != null && primaryAvatarUri.scheme == "content") {
            uploadTasks.add("primaryAvatar" to primaryAvatarUri)
        }
        if (primaryEnrollmentUri != null && primaryEnrollmentUri.scheme == "content") {
            uploadTasks.add("primaryEnrollment" to primaryEnrollmentUri)
        }
        
        additionalChildren.forEachIndexed { index, child ->
            val avatarUri = (child["avatarUrl"] as? String)?.toUri()
            val enrollmentUri = (child["enrollmentFormUrl"] as? String)?.toUri()
            if (avatarUri != null && avatarUri.scheme == "content") {
                uploadTasks.add("child_${index}_avatar" to avatarUri)
            }
            if (enrollmentUri != null && enrollmentUri.scheme == "content") {
                uploadTasks.add("child_${index}_enrollment" to enrollmentUri)
            }
        }

        if (uploadTasks.isEmpty()) {
            saveUserData(uid, bloodType, allergies, medications, conditions, 
                c1Name, c1Phone, c1Relation, c2Name, c2Phone, c2Relation, kotlin.collections.emptyMap())
            return
        }

        val uploadedUrls = mutableMapOf<String, String>()
        var completedCount = 0

        uploadTasks.forEach { (key, uri) ->
            val ref = storage.reference.child("parents/$uid/$key.jpg")
            ref.putFile(uri)
                .continueWithTask { task ->
                    if (!task.isSuccessful) task.exception?.let { throw it }
                    ref.downloadUrl
                }
                .addOnCompleteListener { task ->
                    completedCount++
                    if (task.isSuccessful) {
                        uploadedUrls[key] = task.result.toString()
                    }
                    
                    if (completedCount == uploadTasks.size) {
                        saveUserData(uid, bloodType, allergies, medications, conditions, 
                            c1Name, c1Phone, c1Relation, c2Name, c2Phone, c2Relation, uploadedUrls)
                    }
                }
        }
    }

    private fun saveUserData(
        uid: String, bloodType: String, allergies: String, medications: String, conditions: String,
        c1Name: String, c1Phone: String, c1Relation: String,
        c2Name: String, c2Phone: String, c2Relation: String,
        uploadedUrls: kotlin.collections.Map<String, String>
    ) {
        val userData = hashMapOf<String, Any>(
            "role" to "parent",
            "firstName" to (intent.getStringExtra("firstName") ?: ""),
            "lastName" to (intent.getStringExtra("lastName") ?: ""),
            "middleName" to (intent.getStringExtra("middleName") ?: ""),
            "suffix" to (intent.getStringExtra("suffix") ?: ""),
            "email" to (intent.getStringExtra("email") ?: ""),
            "phone" to (intent.getStringExtra("phone") ?: ""),
            "preferredLanguage" to (intent.getStringExtra("preferredLanguage") ?: "English"),
            "status" to "pending",
            
            "child" to hashMapOf(
                "firstName" to (intent.getStringExtra("childFirstName") ?: ""),
                "lastName" to (intent.getStringExtra("childLastName") ?: ""),
                "middleName" to (intent.getStringExtra("childMiddleName") ?: ""),
                "suffix" to (intent.getStringExtra("childSuffix") ?: ""),
                "age" to (intent.getStringExtra("childAge") ?: ""),
                "grade" to (intent.getStringExtra("childGrade") ?: ""),
                "school" to (intent.getStringExtra("childSchool") ?: ""),
                "avatarUrl" to (uploadedUrls["primaryAvatar"] ?: intent.getStringExtra("childAvatarUrl") ?: ""),
                "enrollmentFormUrl" to (uploadedUrls["primaryEnrollment"] ?: intent.getStringExtra("enrollmentFormUrl") ?: "")
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

        @Suppress("UNCHECKED_CAST")
        val additionalChildren = intent.getSerializableExtra("additionalChildren") as? ArrayList<HashMap<String, Any?>>
        if (additionalChildren != null) {
            val updatedChildren = additionalChildren.mapIndexed { index, child ->
                val newChild = HashMap(child)
                newChild["avatarUrl"] = uploadedUrls["child_${index}_avatar"] ?: child["avatarUrl"] ?: ""
                newChild["enrollmentFormUrl"] = uploadedUrls["child_${index}_enrollment"] ?: child["enrollmentFormUrl"] ?: ""
                newChild
            }
            userData["children"] = updatedChildren
        }

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
