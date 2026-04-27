package com.example.buswatch.admin.fragments

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.example.buswatch.admin.AdminHome
import com.example.buswatch.admin.R
import com.example.buswatch.admin.UserAdmin
import com.example.buswatch.common.R as CommonR
import com.google.firebase.firestore.FirebaseFirestore

class ConductorEditFragment : Fragment() {
    private val db = FirebaseFirestore.getInstance()
    private lateinit var user: UserAdmin
    private var selectedImageUri: Uri? = null
    private val PICK_IMAGE_REQUEST = 1001

    companion object {
        fun newInstance(user: UserAdmin) = ConductorEditFragment().apply {
            this.user = user
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_edit_conductor, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI(view)
        loadData(view)
    }

    private fun setupUI(view: View) {
        view.findViewById<ImageButton>(R.id.btnBackEditConductor)?.setOnClickListener { 
            (requireActivity() as? AdminHome)?.replaceFragment(ConductorsFragment())
        }

        view.findViewById<TextView>(R.id.btnCancelEditConductor)?.setOnClickListener {
            (requireActivity() as? AdminHome)?.replaceFragment(ConductorsFragment())
        }

        view.findViewById<TextView>(R.id.btnSaveConductorChanges)?.setOnClickListener {
            saveChanges(view)
        }

        view.findViewById<View>(R.id.rlEditConductorAvatar)?.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(intent, PICK_IMAGE_REQUEST)
        }

        view.findViewById<View>(R.id.btnSuffixDropdown)?.setOnClickListener {
            showSuffixDialog(view.findViewById(R.id.tvSuffix))
        }

        view.findViewById<View>(R.id.btnLanguageDropdown)?.setOnClickListener {
            showLanguageDialog(view.findViewById(R.id.tvLanguage))
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null && data.data != null) {
            selectedImageUri = data.data
            view?.findViewById<ImageView>(R.id.imgConductor)?.let {
                Glide.with(this).load(selectedImageUri).circleCrop().into(it)
            }
        }
    }

    private fun showSuffixDialog(textView: TextView) {
        val options = arrayOf("None", "JR.", "SR.", "II", "III", "IV")
        AlertDialog.Builder(requireContext())
            .setTitle("Select Suffix")
            .setItems(options) { _, which -> textView.text = options[which] }
            .show()
    }

    private fun showLanguageDialog(textView: TextView) {
        val options = arrayOf("English", "Filipino")
        AlertDialog.Builder(requireContext())
            .setTitle("Select Language")
            .setItems(options) { _, which -> textView.text = options[which] }
            .show()
    }

    private fun loadData(view: View) {
        db.collection("conductors").document(user.id).get().addOnSuccessListener { doc ->
            if (doc == null || !doc.exists()) return@addOnSuccessListener
            view.findViewById<EditText>(R.id.etFirstName).setText(doc.getString("firstName") ?: "")
            view.findViewById<EditText>(R.id.etMiddleName).setText(doc.getString("middleName") ?: "")
            view.findViewById<EditText>(R.id.etLastName).setText(doc.getString("lastName") ?: "")
            view.findViewById<TextView>(R.id.tvSuffix).text = doc.getString("suffix") ?: "None"
            view.findViewById<EditText>(R.id.etEmail).setText(doc.getString("email") ?: "")
            
            val fullPhone = doc.getString("phone") ?: ""
            if (fullPhone.contains(" ")) {
                val parts = fullPhone.split(" ", limit = 2)
                view.findViewById<TextView>(R.id.tvCountryCode).text = parts[0]
                view.findViewById<EditText>(R.id.etPhone).setText(parts[1])
            } else {
                view.findViewById<EditText>(R.id.etPhone).setText(fullPhone)
            }

            view.findViewById<TextView>(R.id.tvLanguage).text = doc.getString("language") ?: "English"
            
            val avatar = doc.getString("conductorAvatar") ?: doc.getString("profilePhoto") ?: ""
            if (avatar.isNotEmpty()) {
                Glide.with(this).load(avatar).placeholder(CommonR.drawable.ic_person_placeholder).circleCrop().into(view.findViewById(R.id.imgConductor))
            }
        }
    }

    private fun saveChanges(view: View) {
        val firstName = view.findViewById<EditText>(R.id.etFirstName).text.toString().trim()
        val lastName = view.findViewById<EditText>(R.id.etLastName).text.toString().trim()
        val email = view.findViewById<EditText>(R.id.etEmail).text.toString().trim()
        val phone = view.findViewById<EditText>(R.id.etPhone).text.toString().trim()
        val code = view.findViewById<TextView>(R.id.tvCountryCode).text.toString()

        if (firstName.isEmpty() || lastName.isEmpty() || email.isEmpty() || phone.isEmpty()) {
            Toast.makeText(requireContext(), "Please fill in all required fields", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedImageUri != null) {
            uploadImageAndSave(view, firstName, lastName, email, phone, code)
        } else {
            performUpdate(firstName, lastName, email, phone, code, null)
        }
    }

    private fun uploadImageAndSave(view: View, firstName: String, lastName: String, email: String, phone: String, code: String) {
        Toast.makeText(requireContext(), "Uploading photo...", Toast.LENGTH_SHORT).show()
        MediaManager.get().upload(selectedImageUri)
            .unsigned("buswatch_unsigned")
            .option("folder", "conductors/${user.id}")
            .callback(object : UploadCallback {
                override fun onStart(requestId: String) {}
                override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {}
                override fun onSuccess(requestId: String, resultData: MutableMap<Any?, Any?>?) {
                    val url = resultData?.get("secure_url") as? String
                    performUpdate(firstName, lastName, email, phone, code, url)
                }
                override fun onError(requestId: String, error: ErrorInfo?) {
                    Toast.makeText(requireContext(), "Photo upload failed", Toast.LENGTH_SHORT).show()
                    performUpdate(firstName, lastName, email, phone, code, null)
                }
                override fun onReschedule(requestId: String, error: ErrorInfo?) {}
            }).dispatch()
    }

    private fun performUpdate(firstName: String, lastName: String, email: String, phone: String, code: String, imageUrl: String?) {
        val updates = hashMapOf<String, Any>(
            "firstName" to firstName,
            "middleName" to view?.findViewById<EditText>(R.id.etMiddleName)?.text.toString().trim(),
            "lastName" to lastName,
            "suffix" to view?.findViewById<TextView>(R.id.tvSuffix)?.text.toString(),
            "email" to email,
            "phone" to "$code $phone",
            "language" to view?.findViewById<TextView>(R.id.tvLanguage)?.text.toString()
        )
        imageUrl?.let { updates["conductorAvatar"] = it }
        
        db.collection("conductors").document(user.id).update(updates).addOnSuccessListener {
            Toast.makeText(requireContext(), "Update successfully", Toast.LENGTH_LONG).show()
            (requireActivity() as? AdminHome)?.replaceFragment(ConductorsFragment())
        }.addOnFailureListener {
            Toast.makeText(requireContext(), "Update failed", Toast.LENGTH_SHORT).show()
        }
    }
}
