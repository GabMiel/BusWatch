package com.example.buswatch

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import com.example.buswatch.common.R as CommonR

class Signup1 : AppCompatActivity() {
    private var selectedLanguage: String? = null
    private var selectedSuffix: String? = null
    private var isConfirmPasswordVisible = false
    private var selectedCountryCode = "+63"
    private var maxPhoneDigits = 11 // Default for Philippines
    private var isPhoneFormatting = false
    
    private lateinit var etFirstName: EditText
    private lateinit var etLastName: EditText
    private lateinit var etMiddleName: EditText
    private lateinit var tvSelectedSuffix: TextView
    private lateinit var etEmail: EditText
    private lateinit var etPhone: EditText
    private lateinit var etPassword: EditText
    private lateinit var etConfirmPassword: EditText
    private lateinit var tvSelectedLanguage: TextView
    private lateinit var tvCountryCode: TextView
    
    private lateinit var tvFirstNameWarning: TextView
    private lateinit var tvLastNameWarning: TextView
    private lateinit var tvMiddleNameWarning: TextView

    private var savedSignupData: Bundle? = null

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
                }

                etEmail.setText(it.getStringExtra("email"))
                etPhone.setText(it.getStringExtra("phone")?.substringAfter(" ") ?: "")
                etPassword.setText(it.getStringExtra("password"))
                etConfirmPassword.setText(it.getStringExtra("password"))
                
                selectedLanguage = it.getStringExtra("preferredLanguage")
                if (selectedLanguage != null) {
                    tvSelectedLanguage.text = selectedLanguage
                    tvSelectedLanguage.setTextColor(Color.BLACK)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.signup1)

        etFirstName = findViewById(R.id.editTextText3)
        etLastName = findViewById(R.id.editTextText4)
        etMiddleName = findViewById(R.id.editTextText5)
        
        tvFirstNameWarning = findViewById(R.id.tvFirstNameWarning)
        tvLastNameWarning = findViewById(R.id.tvLastNameWarning)
        tvMiddleNameWarning = findViewById(R.id.tvMiddleNameWarning)
        
        val suffixSelector = findViewById<FrameLayout>(R.id.btnSignup1Suffix)
        tvSelectedSuffix = suffixSelector.getChildAt(0) as TextView
        
        etEmail = findViewById(R.id.etSignup1Email)
        etPhone = findViewById(R.id.editTextText7)
        etPassword = findViewById(R.id.editTextTextPassword6)
        etConfirmPassword = findViewById(R.id.editTextTextPassword7)
        
        val countryCodeSelector = findViewById<FrameLayout>(R.id.btnSignup1CountryCode)
        tvCountryCode = findViewById(R.id.tvSignup1CountryCode)
        
        val viewConfirmPasswordButton = findViewById<ImageButton>(R.id.btnSignup1ViewConfirmPassword)
        
        val languageSelector = findViewById<FrameLayout>(R.id.btnSignup1Language)
        tvSelectedLanguage = languageSelector.getChildAt(0) as TextView
        
        // Initial state
        tvSelectedLanguage.text = getString(CommonR.string.select_language)
        tvSelectedLanguage.setTextColor("#888888".toColorInt())
        tvSelectedSuffix.text = getString(CommonR.string.suffix)
        tvSelectedSuffix.setTextColor("#888888".toColorInt())

        // Password shouldn't be hidden
        etPassword.transformationMethod = null

        val nextButton = findViewById<Button>(R.id.btnSignup1Next)
        val signInButton = findViewById<Button>(R.id.btnSignup1Signin)

        // Setup real-time name validation
        setupNameWatcher(etFirstName, tvFirstNameWarning)
        setupNameWatcher(etLastName, tvLastNameWarning)
        setupNameWatcher(etMiddleName, tvMiddleNameWarning)

        // Character limits
        etFirstName.filters = arrayOf(InputFilter.LengthFilter(50))
        etLastName.filters = arrayOf(InputFilter.LengthFilter(50))
        etMiddleName.filters = arrayOf(InputFilter.LengthFilter(20))
        updatePhoneFilter()

        setupPhoneFormatting()

        viewConfirmPasswordButton.setOnClickListener {
            isConfirmPasswordVisible = !isConfirmPasswordVisible
            if (isConfirmPasswordVisible) {
                etConfirmPassword.transformationMethod = HideReturnsTransformationMethod.getInstance()
                viewConfirmPasswordButton.setImageResource(CommonR.drawable.ic_eye)
            } else {
                etConfirmPassword.transformationMethod = PasswordTransformationMethod.getInstance()
                viewConfirmPasswordButton.setImageResource(CommonR.drawable.ic_eye_off)
            }
            etConfirmPassword.setSelection(etConfirmPassword.text.length)
        }

        countryCodeSelector.setOnClickListener {
            val countries = arrayOf("Philippines (+63)", "USA/Canada (+1)", "UK (+44)", "Australia (+61)", "New Zealand (+64)", "Singapore (+65)", "Ireland (+353)")
            val codes = arrayOf("+63", "+1", "+44", "+61", "+64", "+65", "+353")
            val lengths = arrayOf(11, 10, 10, 9, 10, 8, 9)
            
            AlertDialog.Builder(this)
                .setTitle("Select Country Code")
                .setItems(countries) { _, which ->
                    selectedCountryCode = codes[which]
                    maxPhoneDigits = lengths[which]
                    tvCountryCode.text = selectedCountryCode
                    // Update length filter
                    updatePhoneFilter()
                    // Re-apply formatting to existing text
                    val currentText = etPhone.text.toString().replace(" ", "")
                    etPhone.setText(currentText)
                }
                .show()
        }

        suffixSelector.setOnClickListener {
            val suffixes = arrayOf("None", "Jr.", "Sr.", "II", "III", "IV", "V")
            AlertDialog.Builder(this)
                .setTitle("Select Suffix")
                .setItems(suffixes) { _, which ->
                    selectedSuffix = if (which == 0) "" else suffixes[which]
                    tvSelectedSuffix.text = if (which == 0) getString(CommonR.string.suffix) else suffixes[which]
                    tvSelectedSuffix.setTextColor(if (which == 0) "#888888".toColorInt() else Color.BLACK)
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
                }
            }.show()
        }

        nextButton.setOnClickListener {
            val firstName = etFirstName.text.toString().trim()
            val lastName = etLastName.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val phone = etPhone.text.toString().replace(" ", "")
            val password = etPassword.text.toString().trim()
            val confirmPassword = etConfirmPassword.text.toString().trim()

            if (firstName.isEmpty() || lastName.isEmpty() || email.isEmpty() || phone.isEmpty() || password.isEmpty() || selectedLanguage == null) {
                Toast.makeText(this, "Please fill in all required fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (isInvalidName(firstName) || isInvalidName(lastName) || isInvalidName(etMiddleName.text.toString())) {
                Toast.makeText(this, "Please correct the errors in the name fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password != confirmPassword) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

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
            }
            signup2Launcher.launch(intent)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, CommonR.anim.slide_in_right, CommonR.anim.slide_out_left)
            } else {
                @Suppress("DEPRECATION")
                overridePendingTransition(CommonR.anim.stay, CommonR.anim.slide_out_left)
            }
        }

        signInButton.setOnClickListener {
            val intent = Intent(this, Login::class.java)
            startActivity(intent)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, CommonR.anim.stay, CommonR.anim.slide_out_right)
            } else {
                @Suppress("DEPRECATION")
                overridePendingTransition(CommonR.anim.stay, CommonR.anim.slide_out_right)
            }
            finish()
        }
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

    private fun setupPhoneFormatting() {
        etPhone.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isPhoneFormatting || s == null) return
                isPhoneFormatting = true
                applyPhoneFormatting(s, selectedCountryCode)
                isPhoneFormatting = false
            }
        })
    }

    private fun applyPhoneFormatting(s: Editable, countryCode: String) {
        val digits = s.toString().replace(" ", "")
        val formatted = StringBuilder()
        
        when (countryCode) {
            "+63" -> { // Philippines: 09XX XXX XXXX (11 digits)
                for (i in digits.indices) {
                    formatted.append(digits[i])
                    if ((i == 3 || i == 6) && i != digits.length - 1) {
                        formatted.append(" ")
                    }
                }
            }
            "+1", "+64" -> { // USA, Canada, NZ: XXX XXX XXXX (10 digits)
                for (i in digits.indices) {
                    formatted.append(digits[i])
                    if ((i == 2 || i == 5) && i != digits.length - 1) {
                        formatted.append(" ")
                    }
                }
            }
            "+44" -> { // UK: XXXXX XXXXX
                for (i in digits.indices) {
                    formatted.append(digits[i])
                    if (i == 4 && i != digits.length - 1) {
                        formatted.append(" ")
                    }
                }
            }
            "+61" -> { // Australia: XXX XXX XXX
                for (i in digits.indices) {
                    formatted.append(digits[i])
                    if ((i == 2 || i == 5) && i != digits.length - 1) {
                        formatted.append(" ")
                    }
                }
            }
            "+65" -> { // Singapore: XXXX XXXX
                for (i in digits.indices) {
                    formatted.append(digits[i])
                    if (i == 3 && i != digits.length - 1) {
                        formatted.append(" ")
                    }
                }
            }
            "+353" -> { // Ireland: XX XXX XXXX
                for (i in digits.indices) {
                    formatted.append(digits[i])
                    if ((i == 1 || i == 4) && i != digits.length - 1) {
                        formatted.append(" ")
                    }
                }
            }
            else -> formatted.append(digits)
        }

        if (formatted.toString() != s.toString()) {
            s.replace(0, s.length, formatted.toString())
        }
    }

    private fun updatePhoneFilter() {
        val spaces = when (selectedCountryCode) {
            "+63", "+1", "+61", "+64", "+353" -> 2
            "+44", "+65" -> 1
            else -> 0
        }
        etPhone.filters = arrayOf(InputFilter.LengthFilter(maxPhoneDigits + spaces))
    }
}
