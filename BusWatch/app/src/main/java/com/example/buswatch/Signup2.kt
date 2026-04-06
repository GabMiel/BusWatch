package com.example.buswatch

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.buswatch.common.R as CommonR

class Signup2 : AppCompatActivity() {

    private var avatarUri: Uri? = null
    private var enrollmentUri: Uri? = null

    private val pickAvatarLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            avatarUri = it
            findViewById<ImageView>(R.id.imageView39).setImageURI(it)
        }
    }

    private val pickEnrollmentLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            enrollmentUri = it
            findViewById<ImageView>(R.id.ivEnrollmentPreview).setImageURI(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.signup2)

        val etChildFirstName = findViewById<EditText>(R.id.editTextText2)
        val etChildLastName = findViewById<EditText>(R.id.editTextText8)
        val etChildMiddleName = findViewById<EditText>(R.id.editTextText9)
        val etChildSuffix = findViewById<EditText>(R.id.editTextText10)
        val etChildAge = findViewById<EditText>(R.id.editTextText11)
        val etChildGrade = findViewById<EditText>(R.id.editTextText12)

        val btnAddPhoto = findViewById<Button>(R.id.btnSignup2AddPhoto)
        val btnUploadPhoto = findViewById<Button>(R.id.btnSignup2UploadPhoto)
        val backButton = findViewById<Button>(R.id.btnSignup2Back)
        val nextButton = findViewById<Button>(R.id.btnSignup2Next)

        btnAddPhoto.setOnClickListener {
            pickAvatarLauncher.launch("image/*")
        }

        btnUploadPhoto.setOnClickListener {
            pickEnrollmentLauncher.launch("image/*")
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

        nextButton.setOnClickListener {
            val childFirstName = etChildFirstName.text.toString().trim()
            val childLastName = etChildLastName.text.toString().trim()
            val childAge = etChildAge.text.toString().trim()
            val childGrade = etChildGrade.text.toString().trim()

            if (childFirstName.isEmpty() || childLastName.isEmpty() || childAge.isEmpty() || childGrade.isEmpty()) {
                Toast.makeText(this, "Please fill in all required fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val intent = Intent(this, Signup3::class.java).apply {
                // Pass Signup1 data
                putExtras(this@Signup2.intent)
                // Pass Signup2 data
                putExtra("childFirstName", childFirstName)
                putExtra("childLastName", childLastName)
                putExtra("childMiddleName", etChildMiddleName.text.toString().trim())
                putExtra("childSuffix", etChildSuffix.text.toString().trim())
                putExtra("childAge", childAge)
                putExtra("childGrade", childGrade)
                // Pass selected image URIs as strings
                putExtra("childAvatarUrl", avatarUri?.toString())
                putExtra("enrollmentFormUrl", enrollmentUri?.toString())
            }
            startActivity(intent)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, CommonR.anim.slide_in_right, CommonR.anim.slide_out_left)
            } else {
                @Suppress("DEPRECATION")
                overridePendingTransition(CommonR.anim.slide_in_right, CommonR.anim.slide_out_left)
            }
        }
    }
}
