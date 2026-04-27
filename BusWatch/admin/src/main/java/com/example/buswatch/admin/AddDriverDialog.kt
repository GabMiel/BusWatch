package com.example.buswatch.admin

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.example.buswatch.common.R as CommonR
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class AddDriverDialog(
    private val activity: AppCompatActivity,
    private val db: FirebaseFirestore,
    private val pickImageLauncher: ActivityResultLauncher<Intent>,
    private val onDriverAdded: () -> Unit
) {
    private var isPasswordVisible = false
    private var isConfirmPasswordVisible = false
    private var isPhoneFormatting = false
    private var selectedCountryCode = "+63"
    private var maxPhoneDigits = 10
    private var selectedImageUri: Uri? = null
    private var imgDriverPhoto: ImageView? = null

    // This property will be used to update the UI when an image is picked
    // The activity/fragment should call this dialog's handleImageResult
    fun handleImageResult(uri: Uri?) {
        selectedImageUri = uri
        imgDriverPhoto?.setImageURI(uri)
    }

    fun show() {
        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_add_driver, null)
        val dialog = AlertDialog.Builder(activity).setView(dialogView).create()

        imgDriverPhoto = dialogView.findViewById(R.id.imgDriverPhoto)
        val frameDriverPhoto = dialogView.findViewById<FrameLayout>(R.id.frameDriverPhoto)
        val etFirstName = dialogView.findViewById<EditText>(R.id.etFirstName)
        val etMiddleName = dialogView.findViewById<EditText>(R.id.etMiddleName)
        val etLastName = dialogView.findViewById<EditText>(R.id.etLastName)
        val tvSuffix = dialogView.findViewById<TextView>(R.id.tvSuffix)
        val etEmail = dialogView.findViewById<EditText>(R.id.etEmail)
        val etContactNumber = dialogView.findViewById<EditText>(R.id.etContactNumber)
        val etLicense = dialogView.findViewById<EditText>(R.id.etLicense)
        val etPassword = dialogView.findViewById<EditText>(R.id.etPassword)
        val etConfirmPassword = dialogView.findViewById<EditText>(R.id.etConfirmPassword)
        val tvLanguage = dialogView.findViewById<TextView>(R.id.tvLanguage)
        val tvCountryCode = dialogView.findViewById<TextView>(R.id.tvCountryCode)

        val tvFirstNameWarning = dialogView.findViewById<TextView>(R.id.tvFirstNameWarning)
        val tvMiddleNameWarning = dialogView.findViewById<TextView>(R.id.tvMiddleNameWarning)
        val tvLastNameWarning = dialogView.findViewById<TextView>(R.id.tvLastNameWarning)
        val tvEmailWarning = dialogView.findViewById<TextView>(R.id.tvEmailWarning)
        val tvPhoneWarning = dialogView.findViewById<TextView>(R.id.tvPhoneWarning)
        val tvConfirmPasswordWarning = dialogView.findViewById<TextView>(R.id.tvConfirmPasswordWarning)

        val btnViewPassword = dialogView.findViewById<ImageButton>(R.id.btnViewPassword)
        val btnViewConfirmPassword = dialogView.findViewById<ImageButton>(R.id.btnViewConfirmPassword)

        frameDriverPhoto?.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            pickImageLauncher.launch(intent)
        }

        etFirstName?.filters = arrayOf(InputFilter.LengthFilter(50))
        etLastName?.filters = arrayOf(InputFilter.LengthFilter(50))
        etMiddleName?.filters = arrayOf(InputFilter.LengthFilter(20))

        btnViewPassword?.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            etPassword?.transformationMethod = if (isPasswordVisible) HideReturnsTransformationMethod.getInstance() else PasswordTransformationMethod.getInstance()
            btnViewPassword.setImageResource(if (isPasswordVisible) CommonR.drawable.ic_eye else CommonR.drawable.ic_eye_off)
            etPassword?.setSelection(etPassword.text.length)
        }

        btnViewConfirmPassword?.setOnClickListener {
            isConfirmPasswordVisible = !isConfirmPasswordVisible
            etConfirmPassword?.transformationMethod = if (isConfirmPasswordVisible) HideReturnsTransformationMethod.getInstance() else PasswordTransformationMethod.getInstance()
            btnViewConfirmPassword.setImageResource(if (isConfirmPasswordVisible) CommonR.drawable.ic_eye else CommonR.drawable.ic_eye_off)
            etConfirmPassword?.setSelection(etConfirmPassword.text.length)
        }

        if (etFirstName != null && tvFirstNameWarning != null) setupNameWatcher(etFirstName, tvFirstNameWarning)
        if (etLastName != null && tvLastNameWarning != null) setupNameWatcher(etLastName, tvLastNameWarning)
        if (etMiddleName != null && tvMiddleNameWarning != null) setupNameWatcher(etMiddleName, tvMiddleNameWarning)

        etEmail?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val email = s?.toString() ?: ""
                val isValid = android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
                tvEmailWarning?.isVisible = email.isNotEmpty() && !isValid
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        fun updatePhoneFilter() {
            val spaces = when (selectedCountryCode) {
                "+63", "+1", "+61", "+64", "+353" -> 2
                "+44", "+65" -> 1
                else -> 0
            }
            etContactNumber?.filters = arrayOf(InputFilter.LengthFilter(maxPhoneDigits + spaces))
        }
        updatePhoneFilter()

        etContactNumber?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isPhoneFormatting || s == null) return
                isPhoneFormatting = true
                val digits = s.toString().replace(" ", "")
                val formatted = StringBuilder()
                when (selectedCountryCode) {
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
                    val selection = etContactNumber.selectionStart
                    val oldLength = s.length
                    s.replace(0, s.length, formatted.toString())
                    val newLength = formatted.length
                    val newSelection = (selection + (newLength - oldLength)).coerceIn(0, newLength)
                    etContactNumber.setSelection(newSelection)
                }
                isPhoneFormatting = false
                tvPhoneWarning?.isVisible = digits.isNotEmpty() && digits.length != maxPhoneDigits
            }
        })

        dialogView.findViewById<FrameLayout>(R.id.btnCountryCode)?.setOnClickListener {
            val countries = arrayOf("Philippines (+63)", "USA/Canada (+1)", "UK (+44)", "Australia (+61)", "New Zealand (+64)", "Singapore (+65)", "Ireland (+353)")
            val codes = arrayOf("+63", "+1", "+44", "+61", "+64", "+65", "+353")
            val lengths = arrayOf(10, 10, 10, 9, 10, 8, 9)
            AlertDialog.Builder(activity)
                .setTitle(activity.getString(CommonR.string.select_country_code))
                .setItems(countries) { _, which ->
                    selectedCountryCode = codes[which]
                    maxPhoneDigits = lengths[which]
                    tvCountryCode?.text = selectedCountryCode
                    etContactNumber?.text?.clear()
                    updatePhoneFilter()
                }.show()
        }

        dialogView.findViewById<FrameLayout>(R.id.btnSuffixDropdown)?.setOnClickListener {
            val suffixes = arrayOf("None", "Jr.", "Sr.", "II", "III", "IV")
            AlertDialog.Builder(activity)
                .setTitle(activity.getString(CommonR.string.select_suffix))
                .setItems(suffixes) { _, which ->
                    tvSuffix?.text = suffixes[which]
                    tvSuffix?.setTextColor(Color.BLACK)
                }.show()
        }

        dialogView.findViewById<FrameLayout>(R.id.btnLanguageDropdown)?.setOnClickListener {
            val languages = arrayOf(activity.getString(CommonR.string.english), "Filipino (Inactive)")
            AlertDialog.Builder(activity)
                .setTitle(activity.getString(CommonR.string.select_language))
                .setItems(languages) { _, which ->
                    if (which == 0) {
                        tvLanguage?.text = languages[0]
                        tvLanguage?.setTextColor(Color.BLACK)
                    } else {
                        Toast.makeText(activity, "Filipino is currently unavailable", Toast.LENGTH_SHORT).show()
                    }
                }.show()
        }

        dialogView.findViewById<ImageButton>(R.id.btnCloseAddDriver)?.setOnClickListener { dialog.dismiss() }

        dialogView.findViewById<Button>(R.id.btnAddDriverSubmit)?.setOnClickListener {
            val firstName = etFirstName?.text?.toString()?.trim() ?: ""
            val middleName = etMiddleName?.text?.toString()?.trim() ?: ""
            val lastName = etLastName?.text?.toString()?.trim() ?: ""
            val email = etEmail?.text?.toString()?.trim() ?: ""
            val phone = etContactNumber?.text?.toString()?.replace(" ", "") ?: ""
            val license = etLicense?.text?.toString()?.trim() ?: ""
            val password = etPassword?.text?.toString()?.trim() ?: ""
            val confirmPassword = etConfirmPassword?.text?.toString()?.trim() ?: ""
            val language = tvLanguage?.text?.toString() ?: "English"

            if (firstName.isEmpty() || lastName.isEmpty() || email.isEmpty() || phone.isEmpty() || license.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(activity, activity.getString(CommonR.string.fill_all_required), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val passwordRegex = "^(?=.*[A-Z])(?=.*\\d).{8,}$".toRegex()
            if (!password.matches(passwordRegex)) {
                Toast.makeText(activity, activity.getString(CommonR.string.password_validation_hint), Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            if (password != confirmPassword) {
                tvConfirmPasswordWarning?.isVisible = true
                Toast.makeText(activity, "Passwords do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            db.collection("drivers").whereEqualTo("email", email).get()
                .addOnSuccessListener { documents ->
                    if (!documents.isEmpty) {
                        tvEmailWarning?.setText(CommonR.string.email_already_registered)
                        tvEmailWarning?.isVisible = true
                    } else {
                        registerDriverAccount(email, password, firstName, middleName, lastName, tvSuffix?.text?.toString() ?: "", phone, selectedCountryCode, license, language, dialog)
                    }
                }
        }
        dialog.show()
    }

    private fun registerDriverAccount(email: String, pass: String, fName: String, mName: String, lName: String, suffix: String, phone: String, countryCode: String, license: String, lang: String, dialog: AlertDialog) {
        val options = com.google.firebase.FirebaseApp.getInstance().options
        val secondaryApp = try {
            com.google.firebase.FirebaseApp.initializeApp(activity, options, "SecondaryDriver")
        } catch (_: Exception) {
            com.google.firebase.FirebaseApp.getInstance("SecondaryDriver")
        }
        val secondaryAuth = FirebaseAuth.getInstance(secondaryApp)

        secondaryAuth.createUserWithEmailAndPassword(email, pass)
            .addOnSuccessListener { authResult ->
                val uid = authResult.user?.uid ?: return@addOnSuccessListener
                
                if (selectedImageUri != null) {
                    uploadPhotoToCloudinary(uid) { avatarUrl ->
                        saveDriverToFirestore(uid, email, fName, mName, lName, suffix, phone, countryCode, license, lang, avatarUrl, secondaryAuth, dialog)
                    }
                } else {
                    saveDriverToFirestore(uid, email, fName, mName, lName, suffix, phone, countryCode, license, lang, "", secondaryAuth, dialog)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(activity, "Auth Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun uploadPhotoToCloudinary(uid: String, onComplete: (String) -> Unit) {
        selectedImageUri?.let { uri ->
            MediaManager.get().upload(uri)
                .unsigned("buswatch_unsigned")
                .option("folder", "drivers/$uid")
                .option("public_id", "avatar")
                .callback(object : UploadCallback {
                    override fun onStart(requestId: String) {}
                    override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {}
                    override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                        onComplete(resultData["secure_url"] as? String ?: "")
                    }
                    override fun onError(requestId: String, _error: ErrorInfo) {
                        onComplete("")
                    }
                    override fun onReschedule(requestId: String, _error: ErrorInfo) {}
                }).dispatch()
        } ?: onComplete("")
    }

    private fun saveDriverToFirestore(uid: String, email: String, fName: String, mName: String, lName: String, suffix: String, phone: String, countryCode: String, license: String, lang: String, avatarUrl: String, auth: FirebaseAuth, dialog: AlertDialog) {
        val driverData = hashMapOf(
            "firstName" to fName,
            "middleName" to mName,
            "lastName" to lName,
            "suffix" to (if (suffix == "None" || suffix == "Suffix" || suffix == "") "" else suffix),
            "email" to email,
            "phone" to "$countryCode $phone",
            "licenseNumber" to license,
            "preferredLanguage" to lang,
            "driverAvatar" to avatarUrl,
            "status" to "active",
            "role" to "Driver",
            "createdAt" to com.google.firebase.Timestamp.now()
        )

        db.collection("drivers").document(uid).set(driverData)
            .addOnSuccessListener {
                auth.signOut()
                Toast.makeText(activity, activity.getString(CommonR.string.driver_added_success), Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                onDriverAdded()
            }
            .addOnFailureListener { e ->
                Toast.makeText(activity, "Firestore Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun setupNameWatcher(editText: EditText, warningView: TextView) {
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val input = s?.toString() ?: ""
                warningView.isVisible = input.isNotEmpty() && !input.matches(Regex("^[a-zA-Z\\s.-]*$"))
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }
}
