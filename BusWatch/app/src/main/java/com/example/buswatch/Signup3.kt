package com.example.buswatch

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
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
    private var selectedCountryCode1 = "+63"
    private var selectedCountryCode2 = "+63"
    private var maxPhoneDigits1 = 11
    private var maxPhoneDigits2 = 11

    private lateinit var etAllergies: EditText
    private lateinit var etMedications: EditText
    private lateinit var etConditions: EditText
    private lateinit var etContact1Name: EditText
    private lateinit var etContact1Phone: EditText
    private lateinit var etContact1Relation: EditText
    private lateinit var etContact2Name: EditText
    private lateinit var etContact2Phone: EditText
    private lateinit var etContact2Relation: EditText
    
    private lateinit var tvCountryCode1: TextView
    private lateinit var tvCountryCode2: TextView
    
    private lateinit var tvContact1NameWarning: TextView
    private lateinit var tvContact2NameWarning: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.signup3)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()

        // Display child's name
        val childFirstName = intent.getStringExtra("childFirstName") ?: "Child"
        val childLastName = intent.getStringExtra("childLastName") ?: "Name"
        findViewById<TextView>(R.id.tvChildNameDisplay).text = getString(CommonR.string.medical_information_for_child_s_name, childFirstName, childLastName)

        val bloodTypeSelector = findViewById<FrameLayout>(R.id.btnSignup3BloodType)
        val tvSelectedBloodType = findViewById<TextView>(R.id.tvSignup3SelectedBloodType)
        
        etAllergies = findViewById(R.id.editTextText13)
        etMedications = findViewById(R.id.editTextText14)
        etConditions = findViewById(R.id.editTextText15)
        
        etContact1Name = findViewById(R.id.editTextText17)
        etContact1Phone = findViewById(R.id.editTextText18)
        etContact1Relation = findViewById(R.id.editTextText19)
        
        etContact2Name = findViewById(R.id.editTextText20)
        etContact2Phone = findViewById(R.id.editTextText22)
        etContact2Relation = findViewById(R.id.editTextText21)
        
        tvCountryCode1 = findViewById(R.id.tvSignup3CountryCode1)
        tvCountryCode2 = findViewById(R.id.tvSignup3CountryCode2)
        
        tvContact1NameWarning = findViewById(R.id.tvContact1NameWarning)
        tvContact2NameWarning = findViewById(R.id.tvContact2NameWarning)

        val backButton = findViewById<Button>(R.id.btnSignup3Back)
        val registerButton = findViewById<Button>(R.id.btnSignup3Register)

        // Real-time validation
        setupNameWatcher(etContact1Name, tvContact1NameWarning)
        setupNameWatcher(etContact2Name, tvContact2NameWarning)
        setupPhoneFormatting(etContact1Phone, 1)
        setupPhoneFormatting(etContact2Phone, 2)

        // Character limits for names
        etContact1Name.filters = arrayOf(InputFilter.LengthFilter(50))
        etContact2Name.filters = arrayOf(InputFilter.LengthFilter(50))

        // Pre-fill if returning
        intent.getStringExtra("bloodType")?.let {
            if (it.isNotEmpty()) {
                selectedBloodType = it
                tvSelectedBloodType.text = selectedBloodType
                tvSelectedBloodType.setTextColor(Color.BLACK)
            }
        }
        
        // English-speaking Country codes Logic (Consistend with Signup1)
        val countries = arrayOf("Philippines (+63)", "USA/Canada (+1)", "UK (+44)", "Australia (+61)", "New Zealand (+64)", "Singapore (+65)", "Ireland (+353)")
        val codesOnly = arrayOf("+63", "+1", "+44", "+61", "+64", "+65", "+353")
        val lengths = arrayOf(11, 10, 10, 9, 10, 8, 9)

        findViewById<FrameLayout>(R.id.btnSignup3CountryCode1).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Select Country Code")
                .setItems(countries) { _, which ->
                    selectedCountryCode1 = codesOnly[which]
                    maxPhoneDigits1 = lengths[which]
                    tvCountryCode1.text = selectedCountryCode1
                    etContact1Phone.setText(etContact1Phone.text.toString())
                }
                .show()
        }

        findViewById<FrameLayout>(R.id.btnSignup3CountryCode2).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Select Country Code")
                .setItems(countries) { _, which ->
                    selectedCountryCode2 = codesOnly[which]
                    maxPhoneDigits2 = lengths[which]
                    tvCountryCode2.text = selectedCountryCode2
                    etContact2Phone.setText(etContact2Phone.text.toString())
                }
                .show()
        }

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

        backButton.setOnClickListener { goBack() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { goBack() }
        })

        registerButton.setOnClickListener {
            val contact1Name = etContact1Name.text.toString().trim()
            val contact1Phone = etContact1Phone.text.toString().replace(" ", "")
            val contact1Relation = etContact1Relation.text.toString().trim()

            if (contact1Name.isEmpty() || contact1Phone.isEmpty() || contact1Relation.isEmpty()) {
                Toast.makeText(this, "Please fill in all required fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (isInvalidName(contact1Name) || isInvalidName(etContact2Name.text.toString())) {
                Toast.makeText(this, "Please correct the errors in the name fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            registerButton.isEnabled = false
            
            val fullPhone1 = "$selectedCountryCode1 $contact1Phone"
            val rawPhone2 = etContact2Phone.text.toString().replace(" ", "")
            val fullPhone2 = if (rawPhone2.isNotEmpty()) "$selectedCountryCode2 $rawPhone2" else ""

            registerUser(
                if (selectedBloodType == "Select blood type") "" else selectedBloodType,
                etAllergies.text.toString().trim(),
                etMedications.text.toString().trim(),
                etConditions.text.toString().trim(),
                contact1Name, fullPhone1, contact1Relation,
                etContact2Name.text.toString().trim(),
                fullPhone2,
                etContact2Relation.text.toString().trim()
            )
        }
    }

    private fun setupPhoneFormatting(editText: EditText, contactNum: Int) {
        editText.addTextChangedListener(object : TextWatcher {
            private var isUpdating = false
            private var lastCursorPosition = 0
            
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                lastCursorPosition = editText.selectionStart
            }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isUpdating) return
                isUpdating = true
                
                val currentMax = if (contactNum == 1) maxPhoneDigits1 else maxPhoneDigits2
                val currentCC = if (contactNum == 1) selectedCountryCode1 else selectedCountryCode2
                
                val rawString = s.toString()
                val digits = rawString.replace(" ", "")
                
                val truncatedDigits = if (digits.length > currentMax) {
                    digits.substring(0, currentMax)
                } else {
                    digits
                }
                
                val formatted = formatByCountry(truncatedDigits, currentCC)
                
                s?.replace(0, s.length, formatted)
                
                try {
                    val newPos = lastCursorPosition.coerceIn(0, formatted.length)
                    editText.setSelection(newPos)
                } catch (_: Exception) {
                    editText.setSelection(formatted.length)
                }
                
                isUpdating = false
            }
        })
    }

    private fun formatByCountry(digits: String, countryCode: String): String {
        val sb = StringBuilder()
        when (countryCode) {
            "+63" -> {
                for (i in digits.indices) {
                    if (i == 4 || i == 7) sb.append(" ")
                    sb.append(digits[i])
                }
            }
            "+1", "+44", "+64" -> {
                for (i in digits.indices) {
                    if (i == 3 || i == 6) sb.append(" ")
                    sb.append(digits[i])
                }
            }
            "+61", "+353" -> {
                for (i in digits.indices) {
                    if (i == 3 || i == 6) sb.append(" ")
                    sb.append(digits[i])
                }
            }
            "+65" -> {
                for (i in digits.indices) {
                    if (i == 4) sb.append(" ")
                    sb.append(digits[i])
                }
            }
            else -> {
                for (i in digits.indices) {
                    if (i > 0 && i % 3 == 0) sb.append(" ")
                    sb.append(digits[i])
                }
            }
        }
        return sb.toString()
    }

    private fun setupNameWatcher(editText: EditText, warningView: TextView) {
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val input = s?.toString() ?: ""
                if (isInvalidName(input)) {
                    warningView.visibility = View.VISIBLE
                } else {
                    warningView.visibility = View.GONE
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun isInvalidName(name: String): Boolean {
        if (name.isEmpty()) return false
        val regex = Regex("^[a-zA-Z\\s.-]*$")
        return !regex.matches(name)
    }

    private fun goBack() {
        val resultIntent = Intent().apply {
            putExtras(intent)
            putExtra("bloodType", if (selectedBloodType == "Select blood type") "" else selectedBloodType)
            putExtra("c1Name", etContact1Name.text.toString().trim())
            putExtra("c1Phone", etContact1Phone.text.toString().replace(" ", ""))
        }
        setResult(RESULT_OK, resultIntent)
        finish()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, CommonR.anim.stay, CommonR.anim.stay)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(CommonR.anim.stay, CommonR.anim.stay)
        }
    }

    private fun registerUser(
        bloodType: String, allergies: String, medications: String, conditions: String,
        c1Name: String, c1Phone: String, c1Relation: String,
        c2Name: String, c2Phone: String, c2Relation: String
    ) {
        val email = intent.getStringExtra("email") ?: ""
        val password = intent.getStringExtra("password") ?: ""

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
        val additionalChildren = IntentCompat.getSerializableExtra(intent, "additionalChildren", ArrayList::class.java) as? ArrayList<HashMap<String, Any?>> ?: arrayListOf()

        val uploadTasks = mutableListOf<Pair<String, android.net.Uri>>()
        
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
                c1Name, c1Phone, c1Relation, c2Name, c2Phone, c2Relation, emptyMap())
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
        val medicalData = hashMapOf(
            "bloodType" to bloodType,
            "allergies" to allergies,
            "medications" to medications,
            "conditions" to conditions
        )

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
                "enrollmentFormUrl" to (uploadedUrls["primaryEnrollment"] ?: intent.getStringExtra("enrollmentFormUrl") ?: ""),
                "medical" to medicalData
            ),
            "emergencyContacts" to listOf(
                hashMapOf("name" to c1Name, "phone" to c1Phone, "relationship" to c1Relation),
                hashMapOf("name" to c2Name, "phone" to c2Phone, "relationship" to c2Relation)
            )
        )

        @Suppress("UNCHECKED_CAST")
        val additionalChildren = IntentCompat.getSerializableExtra(intent, "additionalChildren", ArrayList::class.java) as? ArrayList<HashMap<String, Any?>>
        if (additionalChildren != null) {
            val updatedChildren = additionalChildren.mapIndexed { index, child ->
                val newChild = HashMap(child)
                newChild["avatarUrl"] = uploadedUrls["child_${index}_avatar"] ?: child["avatarUrl"] ?: ""
                newChild["enrollmentFormUrl"] = uploadedUrls["child_${index}_enrollment"] ?: child["enrollmentFormUrl"] ?: ""
                // For now, additional children share the same medical info entered in Signup3
                newChild["medical"] = medicalData
                newChild
            }
            userData["children"] = updatedChildren
        }

        db.collection("parents").document(uid).set(userData)
            .addOnSuccessListener {
                Toast.makeText(this, "Registration Successful!", Toast.LENGTH_LONG).show()
                val homeIntent = Intent(this, Home::class.java)
                homeIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(homeIntent)
                finish()
            }
            .addOnFailureListener { e ->
                findViewById<Button>(R.id.btnSignup3Register).isEnabled = true
                Toast.makeText(this, "Error saving data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
