package com.example.buswatch

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import com.example.buswatch.common.R as CommonR
import java.io.Serializable

class Signup2 : AppCompatActivity() {

    private var avatarUri: Uri? = null
    private var enrollmentUri: Uri? = null

    private lateinit var etChildFirstName: EditText
    private lateinit var etChildLastName: EditText
    private lateinit var etChildMiddleName: EditText
    private lateinit var etChildSuffix: EditText
    private lateinit var etChildAge: EditText
    private lateinit var etChildGrade: EditText
    private lateinit var etChildSchool: EditText
    private lateinit var ivAvatar: ImageView
    private lateinit var ivEnrollment: ImageView
    private lateinit var tvChildNumber: TextView

    private var childrenList = ArrayList<HashMap<String, Any?>>()
    private var currentChildIndex = 0

    private val pickAvatarLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            avatarUri = it
            ivAvatar.setImageURI(it)
        }
    }

    private val pickEnrollmentLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            enrollmentUri = it
            ivEnrollment.setImageURI(it)
        }
    }

    private val signup3Launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            data?.extras?.let { intent.putExtras(it) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.signup2)

        etChildFirstName = findViewById(R.id.editTextText2)
        etChildLastName = findViewById(R.id.editTextText8)
        etChildMiddleName = findViewById(R.id.editTextText9)
        etChildSuffix = findViewById(R.id.editTextText10)
        etChildAge = findViewById(R.id.editTextText11)
        etChildGrade = findViewById(R.id.editTextText12)
        etChildSchool = findViewById(R.id.etSignup2School)
        ivAvatar = findViewById(R.id.imageView39)
        ivEnrollment = findViewById(R.id.ivEnrollmentPreview)
        tvChildNumber = findViewById(R.id.tvChildNumber)

        val btnAddPhoto = findViewById<Button>(R.id.btnSignup2AddPhoto)
        val btnUploadPhoto = findViewById<Button>(R.id.btnSignup2UploadPhoto)
        val backButton = findViewById<Button>(R.id.btnSignup2Back)
        val nextButton = findViewById<Button>(R.id.btnSignup2Next)
        val btnAddChild = findViewById<Button>(R.id.btnSignup2AddChild)

        // Restore state or initialize from intent
        if (savedInstanceState != null) {
            currentChildIndex = savedInstanceState.getInt("currentIndex", 0)
            @Suppress("UNCHECKED_CAST", "DEPRECATION")
            childrenList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                savedInstanceState.getSerializable("childrenList", ArrayList::class.java) as? ArrayList<HashMap<String, Any?>>
            } else {
                savedInstanceState.getSerializable("childrenList") as? ArrayList<HashMap<String, Any?>>
            } ?: ArrayList()
        } else {
            // Initial load from intent
            @Suppress("UNCHECKED_CAST", "DEPRECATION")
            val additionalFromIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getSerializableExtra("additionalChildren", ArrayList::class.java) as? ArrayList<HashMap<String, Any?>>
            } else {
                intent.getSerializableExtra("additionalChildren") as? ArrayList<HashMap<String, Any?>>
            }
            
            if (additionalFromIntent != null || intent.hasExtra("childFirstName")) {
                val firstChild = hashMapOf<String, Any?>(
                    "firstName" to (intent.getStringExtra("childFirstName") ?: ""),
                    "lastName" to (intent.getStringExtra("childLastName") ?: ""),
                    "middleName" to (intent.getStringExtra("childMiddleName") ?: ""),
                    "suffix" to (intent.getStringExtra("childSuffix") ?: ""),
                    "age" to (intent.getStringExtra("childAge") ?: ""),
                    "grade" to (intent.getStringExtra("childGrade") ?: ""),
                    "school" to (intent.getStringExtra("childSchool") ?: ""),
                    "avatarUrl" to intent.getStringExtra("childAvatarUrl"),
                    "enrollmentFormUrl" to intent.getStringExtra("enrollmentFormUrl")
                )
                childrenList.add(firstChild)
                additionalFromIntent?.let { childrenList.addAll(it) }
            }
        }

        loadCurrentChildData()

        btnAddPhoto.setOnClickListener { pickAvatarLauncher.launch("image/*") }
        btnUploadPhoto.setOnClickListener { pickEnrollmentLauncher.launch("image/*") }

        backButton.setOnClickListener { goBack() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { goBack() }
        })

        btnAddChild.setOnClickListener {
            if (validateFields()) {
                saveCurrentChildToList()
                currentChildIndex++
                clearFields()
                updateChildHeader()
                Toast.makeText(this, getString(CommonR.string.child_number_saved, currentChildIndex, currentChildIndex + 1), Toast.LENGTH_SHORT).show()
            }
        }

        nextButton.setOnClickListener {
            if (validateFields()) {
                saveCurrentChildToList()
                
                val nextIntent = Intent(this, Signup3::class.java).apply {
                    putExtras(this@Signup2.intent) // Parent data
                    
                    // Pass first child as primary "child" object for backward compatibility
                    val primaryChild = childrenList[0]
                    putExtra("childFirstName", primaryChild["firstName"] as String)
                    putExtra("childLastName", primaryChild["lastName"] as String)
                    putExtra("childMiddleName", primaryChild["middleName"] as String)
                    putExtra("childSuffix", primaryChild["suffix"] as String)
                    putExtra("childAge", primaryChild["age"] as String)
                    putExtra("childGrade", primaryChild["grade"] as String)
                    putExtra("childSchool", primaryChild["school"] as String)
                    putExtra("childAvatarUrl", primaryChild["avatarUrl"] as String?)
                    putExtra("enrollmentFormUrl", primaryChild["enrollmentFormUrl"] as String?)

                    // Pass entire list including additional children
                    if (childrenList.size > 1) {
                        val additionalChildren = ArrayList(childrenList.subList(1, childrenList.size))
                        putExtra("additionalChildren", additionalChildren as Serializable)
                    }
                }
                signup3Launcher.launch(nextIntent)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, CommonR.anim.slide_in_right, CommonR.anim.slide_out_left)
                } else {
                    @Suppress("DEPRECATION")
                    overridePendingTransition(CommonR.anim.slide_in_right, CommonR.anim.slide_out_left)
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("currentIndex", currentChildIndex)
        outState.putSerializable("childrenList", childrenList)
    }

    private fun validateFields(): Boolean {
        val fName = etChildFirstName.text.toString().trim()
        val lName = etChildLastName.text.toString().trim()
        val age = etChildAge.text.toString().trim()
        val grade = etChildGrade.text.toString().trim()
        val school = etChildSchool.text.toString().trim()

        if (fName.isEmpty() || lName.isEmpty() || age.isEmpty() || grade.isEmpty() || school.isEmpty()) {
            Toast.makeText(this, "Please fill in all required fields marked with *", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun saveCurrentChildToList() {
        val childData = hashMapOf<String, Any?>(
            "firstName" to etChildFirstName.text.toString().trim(),
            "lastName" to etChildLastName.text.toString().trim(),
            "middleName" to etChildMiddleName.text.toString().trim(),
            "suffix" to etChildSuffix.text.toString().trim(),
            "age" to etChildAge.text.toString().trim(),
            "grade" to etChildGrade.text.toString().trim(),
            "school" to etChildSchool.text.toString().trim(),
            "avatarUrl" to avatarUri?.toString(),
            "enrollmentFormUrl" to enrollmentUri?.toString()
        )
        
        if (currentChildIndex < childrenList.size) {
            childrenList[currentChildIndex] = childData
        } else {
            childrenList.add(childData)
        }
    }

    private fun loadCurrentChildData() {
        if (currentChildIndex < childrenList.size) {
            val child = childrenList[currentChildIndex]
            etChildFirstName.setText(child["firstName"] as? String ?: "")
            etChildLastName.setText(child["lastName"] as? String ?: "")
            etChildMiddleName.setText(child["middleName"] as? String ?: "")
            etChildSuffix.setText(child["suffix"] as? String ?: "")
            etChildAge.setText(child["age"] as? String ?: "")
            etChildGrade.setText(child["grade"] as? String ?: "")
            etChildSchool.setText(child["school"] as? String ?: "")
            
            avatarUri = (child["avatarUrl"] as? String)?.toUri()
            if (avatarUri != null) {
                ivAvatar.setImageURI(avatarUri)
            } else {
                ivAvatar.setImageResource(CommonR.drawable.user)
            }
            
            enrollmentUri = (child["enrollmentFormUrl"] as? String)?.toUri()
            if (enrollmentUri != null) {
                ivEnrollment.setImageURI(enrollmentUri)
            } else {
                ivEnrollment.setImageResource(CommonR.drawable.ic_image_placeholder)
            }
        } else {
            clearFields()
        }
        updateChildHeader()
    }

    private fun clearFields() {
        etChildFirstName.text.clear()
        etChildLastName.text.clear()
        etChildMiddleName.text.clear()
        etChildSuffix.text.clear()
        etChildAge.text.clear()
        etChildGrade.text.clear()
        etChildSchool.text.clear()
        ivAvatar.setImageResource(CommonR.drawable.user)
        ivEnrollment.setImageResource(CommonR.drawable.ic_image_placeholder)
        avatarUri = null
        enrollmentUri = null
    }

    private fun updateChildHeader() {
        tvChildNumber.text = getString(CommonR.string.child_n, currentChildIndex + 1)
    }

    private fun goBack() {
        if (currentChildIndex > 0) {
            // Save current if it has at least a first name (optional, but good for UX)
            if (etChildFirstName.text.isNotEmpty()) {
                saveCurrentChildToList()
            }
            currentChildIndex--
            loadCurrentChildData()
        } else {
            val resultIntent = Intent().apply {
                putExtras(intent)
                putExtra("childFirstName", etChildFirstName.text.toString().trim())
                putExtra("childLastName", etChildLastName.text.toString().trim())
                putExtra("childMiddleName", etChildMiddleName.text.toString().trim())
                putExtra("childSuffix", etChildSuffix.text.toString().trim())
                putExtra("childAge", etChildAge.text.toString().trim())
                putExtra("childGrade", etChildGrade.text.toString().trim())
                putExtra("childSchool", etChildSchool.text.toString().trim())
                putExtra("childAvatarUrl", avatarUri?.toString())
                putExtra("enrollmentFormUrl", enrollmentUri?.toString())

                if (childrenList.size > 1) {
                    val additionalChildren = ArrayList(childrenList.subList(1, childrenList.size))
                    putExtra("additionalChildren", additionalChildren as Serializable)
                }
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
    }
}
