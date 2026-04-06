package com.example.buswatch

import android.content.Intent
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

class Signup1 : AppCompatActivity() {
    private var selectedLanguage = "English"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.signup1)

        val etFirstName = findViewById<EditText>(R.id.editTextText3)
        val etLastName = findViewById<EditText>(R.id.editTextText4)
        val etMiddleName = findViewById<EditText>(R.id.editTextText5)
        val etSuffix = findViewById<EditText>(R.id.editTextText6)
        val etEmail = findViewById<EditText>(R.id.etSignup1Email)
        val etPhone = findViewById<EditText>(R.id.editTextText7)
        val etPassword = findViewById<EditText>(R.id.editTextTextPassword6)
        val etConfirmPassword = findViewById<EditText>(R.id.editTextTextPassword7)
        
        val languageSelector = findViewById<FrameLayout>(R.id.btnSignup1Language)
        val tvSelectedLanguage = languageSelector.getChildAt(0) as TextView
        
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
                putExtra("firstName", firstName)
                putExtra("lastName", lastName)
                putExtra("middleName", etMiddleName.text.toString().trim())
                putExtra("suffix", etSuffix.text.toString().trim())
                putExtra("email", email)
                putExtra("phone", phone)
                putExtra("password", password)
                putExtra("preferredLanguage", selectedLanguage)
            }
            startActivity(intent)
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
