package com.example.buswatch

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.buswatch.common.R as CommonR

class Signup1 : AppCompatActivity() {
    private var selectedLanguage = "English"
    
    private lateinit var etFirstName: EditText
    private lateinit var etLastName: EditText
    private lateinit var etMiddleName: EditText
    private lateinit var etSuffix: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPhone: EditText
    private lateinit var etPassword: EditText
    private lateinit var etConfirmPassword: EditText
    private lateinit var tvSelectedLanguage: TextView

    private var savedSignupData: Bundle? = null

    private val signup2Launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            data?.let {
                savedSignupData = it.extras
                etFirstName.setText(it.getStringExtra("firstName"))
                etLastName.setText(it.getStringExtra("lastName"))
                etMiddleName.setText(it.getStringExtra("middleName"))
                etSuffix.setText(it.getStringExtra("suffix"))
                etEmail.setText(it.getStringExtra("email"))
                etPhone.setText(it.getStringExtra("phone"))
                etPassword.setText(it.getStringExtra("password"))
                etConfirmPassword.setText(it.getStringExtra("password"))
                selectedLanguage = it.getStringExtra("preferredLanguage") ?: "English"
                tvSelectedLanguage.text = selectedLanguage
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.signup1)

        etFirstName = findViewById(R.id.editTextText3)
        etLastName = findViewById(R.id.editTextText4)
        etMiddleName = findViewById(R.id.editTextText5)
        etSuffix = findViewById(R.id.editTextText6)
        etEmail = findViewById(R.id.etSignup1Email)
        etPhone = findViewById(R.id.editTextText7)
        etPassword = findViewById(R.id.editTextTextPassword6)
        etConfirmPassword = findViewById(R.id.editTextTextPassword7)
        
        val languageSelector = findViewById<FrameLayout>(R.id.btnSignup1Language)
        tvSelectedLanguage = languageSelector.getChildAt(0) as TextView
        
        val nextButton = findViewById<Button>(R.id.btnSignup1Next)
        val signinButton = findViewById<Button>(R.id.btnSignup1Signin)

        languageSelector.setOnClickListener {
            val languages = arrayOf("English", "Tagalog", "Cebuano", "Ilocano")
            val builder = AlertDialog.Builder(this)
            builder.setTitle("Select Preferred Language")
            builder.setItems(languages) { _, which ->
                selectedLanguage = languages[which]
                tvSelectedLanguage.text = selectedLanguage
            }
            builder.show()
        }

        nextButton.setOnClickListener {
            val firstName = etFirstName.text.toString().trim()
            val lastName = etLastName.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val phone = etPhone.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val confirmPassword = etConfirmPassword.text.toString().trim()

            if (firstName.isEmpty() || lastName.isEmpty() || email.isEmpty() || phone.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill in all required fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password != confirmPassword) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val intent = Intent(this, Signup2::class.java).apply {
                // Pass any existing saved data (from Signup2/3)
                savedSignupData?.let { putExtras(it) }
                
                // Overlay current Signup1 fields (in case they were changed)
                putExtra("firstName", firstName)
                putExtra("lastName", lastName)
                putExtra("middleName", etMiddleName.text.toString().trim())
                putExtra("suffix", etSuffix.text.toString().trim())
                putExtra("email", email)
                putExtra("phone", phone)
                putExtra("password", password)
                putExtra("preferredLanguage", selectedLanguage)
            }
            signup2Launcher.launch(intent)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, CommonR.anim.slide_in_right, CommonR.anim.slide_out_left)
            } else {
                @Suppress("DEPRECATION")
                overridePendingTransition(CommonR.anim.slide_in_right, CommonR.anim.slide_out_left)
            }
        }

        signinButton.setOnClickListener {
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
}
