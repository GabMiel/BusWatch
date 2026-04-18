package com.example.buswatch

import android.content.Intent
import android.net.Uri
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
import com.example.buswatch.common.R as CommonR
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

class Signup3 : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    
    private var selectedCountryCode1 = "+63"
    private var selectedCountryCode2 = "+63"
    private var maxPhoneDigits1 = 10 
    private var maxPhoneDigits2 = 10
    private var isEmergencyPhoneFormatting1 = false
    private var isEmergencyPhoneFormatting2 = false

    private lateinit var etContact1Name: EditText
    private lateinit var etEmergencyPhone1: EditText
    private lateinit var etContact1Relation: EditText
    private lateinit var etContact2Name: EditText
    private lateinit var etEmergencyPhone2: EditText
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

        val childFirstName = intent.getStringExtra("childFirstName") ?: "Child"
        val childLastName = intent.getStringExtra("childLastName") ?: "Name"
        findViewById<TextView>(R.id.tvChildNameDisplay).text = getString(CommonR.string.medical_information_for_child_s_name, childFirstName, childLastName)

        etContact1Name = findViewById(R.id.editTextText17)
        etEmergencyPhone1 = findViewById(R.id.etEmergencyPhone1)
        etContact1Relation = findViewById(R.id.editTextText19)
        
        etContact2Name = findViewById(R.id.editTextText20)
        etEmergencyPhone2 = findViewById(R.id.etEmergencyPhone2)
        etContact2Relation = findViewById(R.id.editTextText21)
        
        tvCountryCode1 = findViewById(R.id.tvSignup3EmergencyCountryCode1)
        tvCountryCode2 = findViewById(R.id.tvSignup3EmergencyCountryCode2)
        
        tvContact1NameWarning = findViewById(R.id.tvContact1NameWarning)
        tvContact2NameWarning = findViewById(R.id.tvContact2NameWarning)

        val backButton = findViewById<Button>(R.id.btnSignup3Back)
        val registerButton = findViewById<Button>(R.id.btnSignup3Register)

        setupNameWatcher(etContact1Name, tvContact1NameWarning)
        setupNameWatcher(etContact2Name, tvContact2NameWarning)

        etContact1Name.filters = arrayOf(InputFilter.LengthFilter(50))
        etContact2Name.filters = arrayOf(InputFilter.LengthFilter(50))
        
        selectedCountryCode1 = intent.getStringExtra("c1Code") ?: "+63"
        selectedCountryCode2 = intent.getStringExtra("c2Code") ?: "+63"
        tvCountryCode1.text = selectedCountryCode1
        tvCountryCode2.text = selectedCountryCode2
        
        maxPhoneDigits1 = getPhoneLengthForCode(selectedCountryCode1)
        maxPhoneDigits2 = getPhoneLengthForCode(selectedCountryCode2)

        updateEmergencyPhoneFilter1()
        updateEmergencyPhoneFilter2()

        setupEmergencyPhoneFormatting()

        etContact1Name.setText(intent.getStringExtra("c1Name") ?: "")
        etEmergencyPhone1.setText(intent.getStringExtra("c1Phone") ?: "")
        etContact1Relation.setText(intent.getStringExtra("c1Rel") ?: "")
        etContact2Name.setText(intent.getStringExtra("c2Name") ?: "")
        etEmergencyPhone2.setText(intent.getStringExtra("c2Phone") ?: "")
        etContact2Relation.setText(intent.getStringExtra("c2Rel") ?: "")

        val countries = arrayOf("Philippines (+63)", "USA/Canada (+1)", "UK (+44)", "Australia (+61)", "New Zealand (+64)", "Singapore (+65)", "Ireland (+353)")
        val codesOnly = arrayOf("+63", "+1", "+44", "+61", "+64", "+65", "+353")
        val lengths = arrayOf(10, 10, 10, 9, 10, 8, 9)

        findViewById<FrameLayout>(R.id.btnSignup3EmergencyCountryCode1).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Select Country Code")
                .setItems(countries) { _, which ->
                    selectedCountryCode1 = codesOnly[which]
                    maxPhoneDigits1 = lengths[which]
                    tvCountryCode1.text = selectedCountryCode1
                    etEmergencyPhone1.text.clear()
                    updateEmergencyPhoneFilter1()
                }
                .show()
        }

        findViewById<FrameLayout>(R.id.btnSignup3EmergencyCountryCode2).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Select Country Code")
                .setItems(countries) { _, which ->
                    selectedCountryCode2 = codesOnly[which]
                    maxPhoneDigits2 = lengths[which]
                    tvCountryCode2.text = selectedCountryCode2
                    etEmergencyPhone2.text.clear()
                    updateEmergencyPhoneFilter2()
                }
                .show()
        }

        backButton.setOnClickListener { goBack() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { goBack() }
        })

        registerButton.setOnClickListener {
            val contact1Name = etContact1Name.text.toString().trim()
            val contact1PhoneRaw = etEmergencyPhone1.text.toString().replace(" ", "")
            val contact1Relation = etContact1Relation.text.toString().trim()

            if (contact1Name.isEmpty() || contact1PhoneRaw.isEmpty() || contact1Relation.isEmpty()) {
                Toast.makeText(this, "Please fill in all required fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (contact1PhoneRaw.length != maxPhoneDigits1) {
                Toast.makeText(this, "Please enter a valid $maxPhoneDigits1-digit phone number for Contact 1", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            val contact2Name = etContact2Name.text.toString().trim()
            val contact2PhoneRaw = etEmergencyPhone2.text.toString().replace(" ", "")
            if (contact2PhoneRaw.isNotEmpty() && contact2PhoneRaw.length != maxPhoneDigits2) {
                Toast.makeText(this, "Please enter a valid $maxPhoneDigits2-digit phone number for Contact 2", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (isInvalidName(contact1Name) || (contact2Name.isNotEmpty() && isInvalidName(contact2Name))) {
                Toast.makeText(this, "Please correct the errors in the name fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            registerButton.isEnabled = false
            
            val fullPhone1 = "$selectedCountryCode1 $contact1PhoneRaw"
            val fullPhone2 = if (contact2PhoneRaw.isNotEmpty()) "$selectedCountryCode2 $contact2PhoneRaw" else ""

            registerUser(
                contact1Name, fullPhone1, contact1Relation,
                contact2Name,
                fullPhone2,
                etContact2Relation.text.toString().trim()
            )
        }
    }

    private fun getPhoneLengthForCode(code: String): Int {
        return when(code) {
            "+61", "+353" -> 9
            "+65" -> 8
            else -> 10
        }
    }

    private fun setupNameWatcher(editText: EditText, warningView: TextView) {
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val input = s?.toString() ?: ""
                warningView.visibility = if (isInvalidName(input)) View.VISIBLE else View.GONE
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun isInvalidName(name: String): Boolean {
        if (name.isEmpty()) return false
        val regex = Regex("^[a-zA-Z\\s.-]*$")
        return !regex.matches(name)
    }

    private fun setupEmergencyPhoneFormatting() {
        etEmergencyPhone1.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isEmergencyPhoneFormatting1 || s == null) return
                isEmergencyPhoneFormatting1 = true
                applyEmergencyPhoneFormatting(etEmergencyPhone1, s, selectedCountryCode1)
                isEmergencyPhoneFormatting1 = false
            }
        })

        etEmergencyPhone2.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isEmergencyPhoneFormatting2 || s == null) return
                isEmergencyPhoneFormatting2 = true
                applyEmergencyPhoneFormatting(etEmergencyPhone2, s, selectedCountryCode2)
                isEmergencyPhoneFormatting2 = false
            }
        })
    }

    private fun applyEmergencyPhoneFormatting(editText: EditText, s: Editable, countryCode: String) {
        val digits = s.toString().replace(" ", "")
        val formatted = StringBuilder()
        
        when (countryCode) {
            "+63", "+1", "+64" -> { 
                for (i in digits.indices) {
                    formatted.append(digits[i])
                    if ((i == 2 || i == 5) && i != digits.length - 1) formatted.append(" ")
                }
            }
            "+44" -> { 
                for (i in digits.indices) {
                    formatted.append(digits[i])
                    if (i == 4 && i != digits.length - 1) formatted.append(" ")
                }
            }
            "+61" -> { 
                for (i in digits.indices) {
                    formatted.append(digits[i])
                    if ((i == 2 || i == 5) && i != digits.length - 1) formatted.append(" ")
                }
            }
            "+65" -> { 
                for (i in digits.indices) {
                    formatted.append(digits[i])
                    if (i == 3 && i != digits.length - 1) formatted.append(" ")
                }
            }
            "+353" -> { 
                for (i in digits.indices) {
                    formatted.append(digits[i])
                    if ((i == 1 || i == 4) && i != digits.length - 1) formatted.append(" ")
                }
            }
            else -> formatted.append(digits)
        }

        if (formatted.toString() != s.toString()) {
            val selection = editText.selectionStart
            val oldLength = s.length
            s.replace(0, s.length, formatted.toString())
            val newLength = formatted.length
            val newSelection = (selection + (newLength - oldLength)).coerceIn(0, newLength)
            editText.setSelection(newSelection)
        }
    }

    private fun updateEmergencyPhoneFilter1() {
        val spaces = when (selectedCountryCode1) {
            "+63", "+1", "+61", "+64", "+353" -> 2
            "+44", "+65" -> 1
            else -> 0
        }
        etEmergencyPhone1.filters = arrayOf(InputFilter.LengthFilter(maxPhoneDigits1 + spaces))
    }

    private fun updateEmergencyPhoneFilter2() {
        val spaces = when (selectedCountryCode2) {
            "+63", "+1", "+61", "+64", "+353" -> 2
            "+44", "+65" -> 1
            else -> 0
        }
        etEmergencyPhone2.filters = arrayOf(InputFilter.LengthFilter(maxPhoneDigits2 + spaces))
    }

    private fun goBack() {
        val resultIntent = Intent().apply {
            putExtras(intent)
            putExtra("c1Name", etContact1Name.text.toString().trim())
            putExtra("c1Phone", etEmergencyPhone1.text.toString().replace(" ", ""))
            putExtra("c1Rel", etContact1Relation.text.toString().trim())
            putExtra("c1Code", selectedCountryCode1)
            putExtra("c2Name", etContact2Name.text.toString().trim())
            putExtra("c2Phone", etEmergencyPhone2.text.toString().replace(" ", ""))
            putExtra("c2Rel", etContact2Relation.text.toString().trim())
            putExtra("c2Code", selectedCountryCode2)
        }
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    private fun registerUser(
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
                        uploadImagesAndSaveData(uid, c1Name, c1Phone, c1Relation, c2Name, c2Phone, c2Relation)
                    }
                } else {
                    findViewById<Button>(R.id.btnSignup3Register).isEnabled = true
                    Toast.makeText(this, "Registration failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun uploadImagesAndSaveData(
        uid: String,
        c1Name: String, c1Phone: String, c1Relation: String,
        c2Name: String, c2Phone: String, c2Relation: String
    ) {
        val primaryAvatarUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("childAvatarUri", Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("childAvatarUri")
        }
        
        val parentAvatarUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("parentAvatarUri", Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("parentAvatarUri")
        }
        
        @Suppress("UNCHECKED_CAST")
        val additionalChildren = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra("additionalChildren", ArrayList::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra("additionalChildren")
        } as? ArrayList<HashMap<String, Any?>> ?: arrayListOf()

        val uploadTasks = mutableListOf<Pair<String, Uri>>()
        val uploadedUrls = mutableMapOf<String, String>()

        if (parentAvatarUri != null) uploadTasks.add("parentAvatar" to parentAvatarUri)
        if (primaryAvatarUri != null) uploadTasks.add("primaryAvatar" to primaryAvatarUri)
        
        additionalChildren.forEachIndexed { index, child ->
            val uri = child["childAvatarUri"] as? Uri
            if (uri != null) uploadTasks.add("child_${index}_avatar" to uri)
        }

        if (uploadTasks.isEmpty()) {
            saveUserData(uid, c1Name, c1Phone, c1Relation, c2Name, c2Phone, c2Relation, emptyMap())
            return
        }

        var completedCount = 0
        Toast.makeText(this, "Uploading photos...", Toast.LENGTH_SHORT).show()

        uploadTasks.forEach { (key, uri) ->
            val path = "parents/$uid/avatars/$key.jpg"
            val ref = storage.reference.child(path)
            
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
                        saveUserData(uid, c1Name, c1Phone, c1Relation, c2Name, c2Phone, c2Relation, uploadedUrls)
                    }
                }
        }
    }

    private fun saveUserData(
        uid: String,
        c1Name: String, c1Phone: String, c1Relation: String,
        c2Name: String, c2Phone: String, c2Relation: String,
        uploadedUrls: kotlin.collections.Map<String, String>
    ) {
        val profileData = hashMapOf<String, Any?>(
            "firstName" to (intent.getStringExtra("firstName") ?: ""),
            "lastName" to (intent.getStringExtra("lastName") ?: ""),
            "middleName" to (intent.getStringExtra("middleName") ?: ""),
            "suffix" to (intent.getStringExtra("suffix") ?: ""),
            "email" to (intent.getStringExtra("email") ?: ""),
            "phone" to (intent.getStringExtra("phone") ?: ""),
            "preferredLanguage" to (intent.getStringExtra("preferredLanguage") ?: "English"),
            "parentAvatarUrl" to (uploadedUrls["parentAvatar"] ?: "")
        )

        val userData = hashMapOf<String, Any>(
            "role" to "parent",
            "status" to "pending",
            "profile" to profileData,
            "child" to hashMapOf(
                "firstName" to (intent.getStringExtra("childFirstName") ?: ""),
                "lastName" to (intent.getStringExtra("childLastName") ?: ""),
                "middleName" to (intent.getStringExtra("childMiddleName") ?: ""),
                "suffix" to (intent.getStringExtra("childSuffix") ?: ""),
                "age" to (intent.getStringExtra("childAge") ?: ""),
                "class" to (intent.getStringExtra("childSection") ?: ""),
                "grade" to (intent.getStringExtra("childGrade") ?: ""),
                "school" to (intent.getStringExtra("childSchool") ?: ""),
                "address" to (intent.getStringExtra("childAddress") ?: ""),
                "latitude" to intent.getDoubleExtra("childLatitude", 0.0),
                "longitude" to intent.getDoubleExtra("childLongitude", 0.0),
                "childAvatarUrl" to (uploadedUrls["primaryAvatar"] ?: ""),
                "status" to "AT HOME",
                "stop" to ""
            ),
            "emergencyContacts" to listOf(
                hashMapOf("name" to c1Name, "phone" to c1Phone, "relationship" to c1Relation),
                hashMapOf("name" to c2Name, "phone" to c2Phone, "relationship" to c2Relation)
            )
        )

        @Suppress("UNCHECKED_CAST")
        val additionalChildren = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra("additionalChildren", ArrayList::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra("additionalChildren")
        } as? ArrayList<HashMap<String, Any?>>

        if (additionalChildren != null && additionalChildren.isNotEmpty()) {
            val updatedChildren = additionalChildren.mapIndexed { index, child ->
                val newChild = HashMap(child)
                newChild["childAvatarUrl"] = uploadedUrls["child_${index}_avatar"] ?: ""
                newChild["status"] = "AT HOME"
                if (newChild.containsKey("section")) newChild["class"] = newChild["section"]
                newChild
            }
            userData["children"] = updatedChildren
        }

        db.collection("parents").document(uid).set(userData)
            .addOnSuccessListener {
                Toast.makeText(this, "Registration Successful!", Toast.LENGTH_LONG).show()
                val mainIntent = Intent(this, ParentMainActivity::class.java)
                mainIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(mainIntent)
                finish()
            }
            .addOnFailureListener { e ->
                findViewById<Button>(R.id.btnSignup3Register).isEnabled = true
                Toast.makeText(this, "Error saving data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
