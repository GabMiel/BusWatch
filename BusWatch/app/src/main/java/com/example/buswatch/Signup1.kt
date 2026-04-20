package com.example.buswatch

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.util.Patterns
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import com.bumptech.glide.Glide
import com.example.buswatch.common.R as CommonR
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class Signup1 : AppCompatActivity() {
    private var selectedLanguage: String? = null
    private var selectedSuffix: String? = null
    private var isPasswordVisible = false
    private var isConfirmPasswordVisible = false
    private var selectedCountryCode = "+63"
    private var maxPhoneDigits = 10 
    private var isParentPhoneFormatting = false
    private var parentAvatarUri: Uri? = null
    
    private lateinit var etFirstName: EditText
    private lateinit var etLastName: EditText
    private lateinit var etMiddleName: EditText
    private lateinit var tvSelectedSuffix: TextView
    private lateinit var etEmail: EditText
    private lateinit var etParentPhone: EditText
    private lateinit var etPassword: EditText
    private lateinit var etConfirmPassword: EditText
    private lateinit var tvSelectedLanguage: TextView
    private lateinit var tvCountryCode: TextView
    private lateinit var ivParentAvatar: ImageView
    
    private lateinit var tvFirstNameWarning: TextView
    private lateinit var tvLastNameWarning: TextView
    private lateinit var tvMiddleNameWarning: TextView
    private lateinit var tvEmailWarning: TextView

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private var savedSignupData: Bundle? = null
    private var emailValidationJob: Job? = null
    private var isEmailValid = false

    private val pickAvatarLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { 
            parentAvatarUri = it
            Glide.with(this).load(it).circleCrop().into(ivParentAvatar)
        }
    }

    private val signup2Launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            data?.let {
                savedSignupData = it.extras
                etFirstName.setText(it.getStringExtra("firstName"))
                etLastName.setText(it.getStringExtra("lastName"))
                etMiddleName.setText(it.getStringExtra("middleName"))
                
                selectedSuffix = it.getStringExtra("suffix")
                if (!selectedSuffix.isNullOrEmpty()) {
                    tvSelectedSuffix.text = selectedSuffix
                    tvSelectedSuffix.setTextColor(Color.BLACK)
                } else {
                    tvSelectedSuffix.text = getString(CommonR.string.suffix)
                    tvSelectedSuffix.setTextColor("#888888".toColorInt())
                }

                etEmail.setText(it.getStringExtra("email"))
                
                val fullPhone = it.getStringExtra("phone") ?: ""
                if (fullPhone.contains(" ")) {
                    selectedCountryCode = fullPhone.substringBefore(" ")
                    tvCountryCode.text = selectedCountryCode
                    etParentPhone.setText(fullPhone.substringAfter(" "))
                    updateParentPhoneFilter()
                }

                etPassword.setText(it.getStringExtra("password"))
                etConfirmPassword.setText(it.getStringExtra("password"))
                
                selectedLanguage = it.getStringExtra("preferredLanguage")
                if (selectedLanguage != null) {
                    tvSelectedLanguage.text = selectedLanguage
                    tvSelectedLanguage.setTextColor(Color.BLACK)
                }

                @Suppress("DEPRECATION")
                parentAvatarUri = it.getParcelableExtra("parentAvatarUri")
                if (parentAvatarUri != null) {
                    Glide.with(this).load(parentAvatarUri).circleCrop().into(ivParentAvatar)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.signup1)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        etFirstName = findViewById(R.id.editTextText3)
        etLastName = findViewById(R.id.editTextText4)
        etMiddleName = findViewById(R.id.editTextText5)
        
        tvFirstNameWarning = findViewById(R.id.tvFirstNameWarning)
        tvLastNameWarning = findViewById(R.id.tvLastNameWarning)
        tvMiddleNameWarning = findViewById(R.id.tvMiddleNameWarning)
        
        val suffixSelector = findViewById<FrameLayout>(R.id.btnSignup1Suffix)
        tvSelectedSuffix = findViewById(R.id.tvSignup1SelectedSuffix)
        
        etEmail = findViewById(R.id.etSignup1Email)
        tvEmailWarning = findViewById(R.id.tvEmailWarning)
        etParentPhone = findViewById(R.id.etParentPhone)
        etPassword = findViewById(R.id.editTextTextPassword6)
        etConfirmPassword = findViewById(R.id.editTextTextPassword7)
        
        val countryCodeSelector = findViewById<FrameLayout>(R.id.btnSignup1ParentCountryCode)
        tvCountryCode = findViewById(R.id.tvSignup1ParentCountryCode)
        
        val viewPasswordButton = findViewById<ImageButton>(R.id.btnSignup1ViewPassword)
        val viewConfirmPasswordButton = findViewById<ImageButton>(R.id.btnSignup1ViewConfirmPassword)
        
        val languageSelector = findViewById<FrameLayout>(R.id.btnSignup1Language)
        tvSelectedLanguage = findViewById(R.id.tvSignup1SelectedLanguage)
        
        ivParentAvatar = findViewById(R.id.ivParentAvatar)
        val btnAddPhoto = findViewById<Button>(R.id.btnSignup1AddPhoto)
        
        btnAddPhoto.setOnClickListener { pickAvatarLauncher.launch("image/*") }

        tvSelectedLanguage.text = getString(CommonR.string.select_language)
        tvSelectedLanguage.setTextColor("#888888".toColorInt())
        tvSelectedSuffix.text = getString(CommonR.string.suffix)
        tvSelectedSuffix.setTextColor("#888888".toColorInt())

        etPassword.transformationMethod = PasswordTransformationMethod.getInstance()
        etConfirmPassword.transformationMethod = PasswordTransformationMethod.getInstance()

        val nextButton = findViewById<Button>(R.id.btnSignup1Next)
        val signInButton = findViewById<Button>(R.id.btnSignup1Signin)

        setupNameWatcher(etFirstName, tvFirstNameWarning)
        setupNameWatcher(etLastName, tvLastNameWarning)
        setupNameWatcher(etMiddleName, tvMiddleNameWarning)
        setupEmailWatcher()

        etFirstName.filters = arrayOf(InputFilter.LengthFilter(50))
        etLastName.filters = arrayOf(InputFilter.LengthFilter(50))
        etMiddleName.filters = arrayOf(InputFilter.LengthFilter(20))
        updateParentPhoneFilter()

        setupParentPhoneFormatting()

        viewPasswordButton.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            etPassword.transformationMethod = if (isPasswordVisible) HideReturnsTransformationMethod.getInstance() else PasswordTransformationMethod.getInstance()
            viewPasswordButton.setImageResource(if (isPasswordVisible) CommonR.drawable.ic_eye else CommonR.drawable.ic_eye_off)
            etPassword.setSelection(etPassword.text.length)
        }

        viewConfirmPasswordButton.setOnClickListener {
            isConfirmPasswordVisible = !isConfirmPasswordVisible
            etConfirmPassword.transformationMethod = if (isConfirmPasswordVisible) HideReturnsTransformationMethod.getInstance() else PasswordTransformationMethod.getInstance()
            viewConfirmPasswordButton.setImageResource(if (isConfirmPasswordVisible) CommonR.drawable.ic_eye else CommonR.drawable.ic_eye_off)
            etConfirmPassword.setSelection(etConfirmPassword.text.length)
        }

        countryCodeSelector.setOnClickListener {
            val countries = arrayOf("Philippines (+63)", "USA/Canada (+1)", "UK (+44)", "Australia (+61)", "New Zealand (+64)", "Singapore (+65)", "Ireland (+353)")
            val codes = arrayOf("+63", "+1", "+44", "+61", "+64", "+65", "+353")
            val lengths = arrayOf(10, 10, 10, 9, 10, 8, 9)
            
            AlertDialog.Builder(this)
                .setTitle("Select Country Code")
                .setItems(countries) { _, which ->
                    selectedCountryCode = codes[which]
                    maxPhoneDigits = lengths[which]
                    tvCountryCode.text = selectedCountryCode
                    etParentPhone.text.clear() 
                    updateParentPhoneFilter()
                }
                .show()
        }

        suffixSelector.setOnClickListener {
            val suffixes = arrayOf("None", "Jr.", "Sr.", "II", "III", "IV", "V")
            AlertDialog.Builder(this)
                .setTitle("Select Suffix")
                .setItems(suffixes) { _, pos ->
                    selectedSuffix = if (pos == 0) "" else suffixes[pos]
                    tvSelectedSuffix.text = if (pos == 0) getString(CommonR.string.suffix) else suffixes[pos]
                    tvSelectedSuffix.setTextColor(if (pos == 0) "#888888".toColorInt() else Color.BLACK)
                }
                .show()
        }

        languageSelector.setOnClickListener {
            val languages = arrayOf("English", "Filipino")
            val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, languages) {
                override fun isEnabled(position: Int) = position == 0
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = super.getView(position, convertView, parent)
                    val tv = view.findViewById<TextView>(android.R.id.text1)
                    tv.setTextColor(if (position == 1) Color.LTGRAY else Color.BLACK)
                    return view
                }
            }

            AlertDialog.Builder(this).setTitle(getString(CommonR.string.select_language)).setAdapter(adapter) { _, which ->
                if (which == 0) {
                    selectedLanguage = languages[which]
                    tvSelectedLanguage.text = selectedLanguage
                    tvSelectedLanguage.setTextColor(Color.BLACK)
                }}.show()
        }

        nextButton.setOnClickListener {
            val firstName = etFirstName.text.toString().trim()
            val lastName = etLastName.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val phone = etParentPhone.text.toString().replace(" ", "")
            val password = etPassword.text.toString().trim()
            val confirmPassword = etConfirmPassword.text.toString().trim()

            if (firstName.isEmpty() || lastName.isEmpty() || email.isEmpty() || phone.isEmpty() || password.isEmpty() || selectedLanguage == null) {
                Toast.makeText(this, "Please fill in all required fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (phone.length != maxPhoneDigits) {
                Toast.makeText(this, "Please enter a valid $maxPhoneDigits-digit phone number", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (isInvalidName(firstName) || isInvalidName(lastName) || isInvalidName(etMiddleName.text.toString())) {
                Toast.makeText(this, "Please correct the errors in the name fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!isEmailValid) {
                Toast.makeText(this, "Please enter a valid and unique email address", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password != confirmPassword) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            proceedToSignup2(firstName, lastName, email, phone, password)
        }

        signInButton.setOnClickListener {
            startActivity(Intent(this, Login::class.java))
            finish()
        }
    }

    private fun setupEmailWatcher() {
        etEmail.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                emailValidationJob?.cancel()
                val email = s?.toString()?.trim() ?: ""
                
                if (email.isEmpty()) {
                    tvEmailWarning.visibility = View.GONE
                    isEmailValid = false
                    return
                }

                if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    tvEmailWarning.text = getString(CommonR.string.invalid_email_format)
                    tvEmailWarning.setTextColor(Color.RED)
                    tvEmailWarning.visibility = View.VISIBLE
                    isEmailValid = false
                } else {
                    tvEmailWarning.text = getString(CommonR.string.checking_availability)
                    tvEmailWarning.setTextColor(Color.GRAY)
                    tvEmailWarning.visibility = View.VISIBLE
                    
                    emailValidationJob = CoroutineScope(Dispatchers.Main).launch {
                        delay(600) // Debounce
                        checkEmailAvailability(email)
                    }
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun checkEmailAvailability(email: String) {
        // First check Auth (real-time availability in Firebase Auth)
        auth.fetchSignInMethodsForEmail(email)
            .addOnCompleteListener { authTask ->
                if (authTask.isSuccessful) {
                    val isAuthUsed = authTask.result?.signInMethods?.isNotEmpty() ?: false
                    if (isAuthUsed) {
                        tvEmailWarning.text = getString(CommonR.string.email_already_registered)
                        tvEmailWarning.setTextColor(Color.RED)
                        isEmailValid = false
                    } else {
                        // Then check Firestore for "pending" or "approved" parents
                        db.collection("parents")
                            .whereEqualTo("profile.email", email)
                            .get()
                            .addOnSuccessListener { documents ->
                                if (!documents.isEmpty) {
                                    tvEmailWarning.text = getString(CommonR.string.email_already_registered)
                                    tvEmailWarning.setTextColor(Color.RED)
                                    isEmailValid = false
                                } else {
                                    tvEmailWarning.text = getString(CommonR.string.email_available)
                                    tvEmailWarning.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
                                    isEmailValid = true
                                }
                            }
                            .addOnFailureListener {
                                // If Firestore check fails, but Auth is clear, we cautiously allow
                                tvEmailWarning.visibility = View.GONE
                                isEmailValid = true
                            }
                    }
                } else {
                    // Fallback to Firestore only if fetchSignInMethods fails
                    db.collection("parents")
                        .whereEqualTo("profile.email", email)
                        .get()
                        .addOnSuccessListener { docs ->
                            if (!docs.isEmpty) {
                                tvEmailWarning.text = getString(CommonR.string.email_already_registered)
                                tvEmailWarning.setTextColor(Color.RED)
                                isEmailValid = false
                            } else {
                                tvEmailWarning.visibility = View.GONE
                                isEmailValid = true
                            }
                        }
                }
            }
    }

    private fun proceedToSignup2(firstName: String, lastName: String, email: String, phone: String, password: String) {
        val fullPhone = "$selectedCountryCode $phone"
        val intent = Intent(this, Signup2::class.java).apply {
            savedSignupData?.let { putExtras(it) }
            putExtra("firstName", firstName)
            putExtra("lastName", lastName)
            putExtra("middleName", etMiddleName.text.toString().trim())
            putExtra("suffix", selectedSuffix ?: "")
            putExtra("email", email)
            putExtra("phone", fullPhone)
            putExtra("password", password)
            putExtra("preferredLanguage", selectedLanguage)
            putExtra("parentAvatarUri", parentAvatarUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        signup2Launcher.launch(intent)
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
        return !Regex("^[a-zA-Z\\s.-]*$").matches(name)
    }

    private fun setupParentPhoneFormatting() {
        etParentPhone.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isParentPhoneFormatting || s == null) return
                isParentPhoneFormatting = true
                applyParentPhoneFormatting(s, selectedCountryCode)
                isParentPhoneFormatting = false
            }
        })
    }

    private fun applyParentPhoneFormatting(s: Editable, countryCode: String) {
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
            val selection = etParentPhone.selectionStart
            val oldLength = s.length
            s.replace(0, s.length, formatted.toString())
            val newLength = formatted.length
            val newSelection = (selection + (newLength - oldLength)).coerceIn(0, newLength)
            etParentPhone.setSelection(newSelection)
        }
    }

    private fun updateParentPhoneFilter() {
        val spaces = when (selectedCountryCode) {
            "+63", "+1", "+61", "+64", "+353" -> 2
            "+44", "+65" -> 1
            else -> 0
        }
        etParentPhone.filters = arrayOf(InputFilter.LengthFilter(maxPhoneDigits + spaces))
    }
}
