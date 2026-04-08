package com.example.buswatch

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.buswatch.common.R as CommonR
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.yalantis.ucrop.UCrop
import java.io.File

class StudentDetailsGeneralFragment : Fragment() {
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private var childName: String? = null
    private var currentChildData: kotlin.collections.Map<String, Any>? = null
    private var isFromChildrenList: Boolean = false
    
    private var tempAvatarUri: Uri? = null
    private lateinit var avatarView: ImageView
    private var lastSourceUriAvatar: Uri? = null
    
    // For Dialog Edit
    private var editAvatarView: ImageView? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            lastSourceUriAvatar = it
            startCrop(it)
        }
    }

    companion object {
        fun newInstance(childName: String?): StudentDetailsGeneralFragment {
            val fragment = StudentDetailsGeneralFragment()
            val args = Bundle()
            args.putString("childName", childName)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_student_details_general, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()
        childName = arguments?.getString("childName")

        avatarView = view.findViewById(R.id.imgStudentAvatar)
        
        view.findViewById<View>(R.id.rlStudentAvatar).setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        view.findViewById<View>(R.id.btnGeneralEdit).setOnClickListener {
            showEditDialog()
        }

        fetchStudentGeneralData()
    }

    private fun fetchStudentGeneralData() {
        val uid = auth.currentUser?.uid ?: return

        db.collection("parents").document(uid).get()
            .addOnSuccessListener { document ->
                if (isAdded && document != null && document.exists()) {
                    @Suppress("UNCHECKED_CAST")
                    val childMap = document.get("child") as? kotlin.collections.Map<String, Any>
                    @Suppress("UNCHECKED_CAST")
                    val childrenList = document.get("children") as? List<kotlin.collections.Map<String, Any>>
                    
                    var foundChild: kotlin.collections.Map<String, Any>? = null
                    
                    if (childMap != null) {
                        val fullName = "${childMap["firstName"]} ${childMap["lastName"]}"
                        if (childName == null || fullName == childName) {
                            foundChild = childMap
                            isFromChildrenList = false
                        }
                    }
                    
                    if (foundChild == null && childrenList != null) {
                        foundChild = childrenList.find { 
                            "${it["firstName"]} ${it["lastName"]}" == childName 
                        }
                        if (foundChild != null) isFromChildrenList = true
                    }

                    currentChildData = foundChild
                    foundChild?.let { fetchClassFromFirebase(it) }
                }
            }
            .addOnFailureListener {
                if (isAdded) Toast.makeText(context, "Error fetching student data", Toast.LENGTH_SHORT).show()
            }
    }

    private fun fetchClassFromFirebase(child: kotlin.collections.Map<String, Any>) {
        val studentId = child["studentId"] as? String
        if (studentId.isNullOrEmpty()) {
            displayGeneralInfo(child, null)
            return
        }

        db.collection("classes").whereArrayContains("studentIds", studentId).get()
            .addOnSuccessListener { querySnapshot ->
                var className: String? = null
                if (!querySnapshot.isEmpty) {
                    val classDoc = querySnapshot.documents[0]
                    className = classDoc.getString("name") ?: classDoc.id
                }
                if (isAdded) {
                    displayGeneralInfo(child, className)
                }
            }
            .addOnFailureListener {
                if (isAdded) {
                    displayGeneralInfo(child, null)
                }
            }
    }

    private fun displayGeneralInfo(child: kotlin.collections.Map<String, Any>, fetchedClass: String?) {
        view?.let { v ->
            val firstName = child["firstName"] as? String ?: ""
            val middleName = child["middleName"] as? String ?: ""
            val lastName = child["lastName"] as? String ?: ""
            val suffix = child["suffix"] as? String ?: ""
            
            val fullName = listOf(firstName, middleName, lastName, suffix)
                .filter { it.isNotBlank() }
                .joinToString(" ")
            
            v.findViewById<TextView>(R.id.tvStudentName).text = fullName
            v.findViewById<TextView>(R.id.tvGrade).text = child["grade"] as? String ?: "---"
            v.findViewById<TextView>(R.id.tvDob).text = child["age"] as? String ?: "---"
            v.findViewById<TextView>(R.id.tvAddress).text = child["address"] as? String ?: "---"
            v.findViewById<TextView>(R.id.tvSchool).text = child["school"] as? String ?: "The Immaculate Mother Academy Inc."
            v.findViewById<TextView>(R.id.tvStudentId).text = child["studentId"] as? String ?: "---"
            
            val classValue = fetchedClass ?: child["class"] as? String ?: ""
            v.findViewById<TextView>(R.id.tvStudentClassBadge).text = classValue
            v.findViewById<TextView>(R.id.tvClass).text = classValue
            
            v.findViewById<TextView>(R.id.tvStudentClassBadge).visibility = if (classValue.isEmpty()) View.GONE else View.VISIBLE

            // Load Avatar
            val avatarUrl = child["avatarUrl"] as? String
            if (!avatarUrl.isNullOrEmpty()) {
                Glide.with(this)
                    .load(avatarUrl)
                    .placeholder(CommonR.drawable.user)
                    .error(CommonR.drawable.user)
                    .circleCrop()
                    .into(avatarView)
            } else {
                avatarView.setImageResource(CommonR.drawable.user)
            }
        }
    }

    private fun startCrop(uri: Uri) {
        val dest = "avatar_crop_${System.currentTimeMillis()}.jpg"
        val uCrop = UCrop.of(uri, Uri.fromFile(File(requireContext().cacheDir, dest)))
        val options = UCrop.Options().apply {
            setToolbarColor(Color.WHITE)
            setToolbarWidgetColor(Color.BLACK)
            setActiveControlsWidgetColor(ContextCompat.getColor(requireContext(), CommonR.color.yellow_primary))
            setHideBottomControls(false)
        }
        uCrop.withAspectRatio(1f, 1f)
        uCrop.withOptions(options)
        uCrop.start(requireContext(), this, UCrop.REQUEST_CROP)
    }

    private fun showPreviewDialog(uri: Uri) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_photo, null)
        val dialog = AlertDialog.Builder(requireContext()).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val ivAvatarPreview = dialogView.findViewById<ImageView>(R.id.ivPreviewAvatar)
        val cvAvatar = dialogView.findViewById<View>(R.id.cvAvatarPreview)
        val btnDelete = dialogView.findViewById<ImageButton>(R.id.btnDeletePhoto)

        cvAvatar.visibility = View.VISIBLE
        ivAvatarPreview.setImageURI(uri)
        
        cvAvatar.setOnClickListener {
            dialog.dismiss()
            lastSourceUriAvatar?.let { startCrop(it) }
        }

        btnDelete.setOnClickListener {
            dialog.dismiss()
            tempAvatarUri = null
            editAvatarView?.setImageResource(CommonR.drawable.user)
            lastSourceUriAvatar = null
        }

        dialogView.findViewById<Button>(R.id.btnPreviewCancel).setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<Button>(R.id.btnPreviewSave).setOnClickListener {
            dialog.dismiss()
            tempAvatarUri = uri
            if (editAvatarView != null) {
                editAvatarView?.setImageURI(uri)
            } else {
                // If not in edit dialog, upload immediately
                uploadNewAvatar(uri)
            }
        }
        dialog.show()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == android.app.Activity.RESULT_OK && data != null && requestCode == UCrop.REQUEST_CROP) {
            val resultUri = UCrop.getOutput(data)
            if (resultUri != null) {
                showPreviewDialog(resultUri)
            }
        }
    }

    private fun uploadNewAvatar(uri: Uri) {
        val uid = auth.currentUser?.uid ?: return
        val fileName = if (isFromChildrenList) "child_${childName?.replace(" ", "_")}_avatar.jpg" else "primaryAvatar.jpg"
        val ref = storage.reference.child("parents/$uid/$fileName")
        
        Toast.makeText(context, "Uploading photo...", Toast.LENGTH_SHORT).show()
        
        ref.putFile(uri)
            .continueWithTask { task ->
                if (!task.isSuccessful) task.exception?.let { throw it }
                ref.downloadUrl
            }
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val downloadUrl = task.result.toString()
                    updateAvatarUrlInFirestore(downloadUrl)
                } else {
                    Toast.makeText(context, "Upload failed", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun updateAvatarUrlInFirestore(url: String) {
        val uid = auth.currentUser?.uid ?: return
        val docRef = db.collection("parents").document(uid)
        
        val updatedChild = currentChildData?.toMutableMap() ?: mutableMapOf()
        updatedChild["avatarUrl"] = url
        
        if (isFromChildrenList) {
            docRef.get().addOnSuccessListener { document ->
                @Suppress("UNCHECKED_CAST")
                val childrenList = document.get("children") as? List<kotlin.collections.Map<String, Any>> ?: return@addOnSuccessListener
                val newList = childrenList.toMutableList()
                val index = newList.indexOfFirst { 
                    val fName = it["firstName"] as? String ?: ""
                    val lName = it["lastName"] as? String ?: ""
                    "$fName $lName" == childName 
                }
                if (index != -1) {
                    newList[index] = updatedChild
                    docRef.update("children", newList).addOnSuccessListener {
                        currentChildData = updatedChild
                        fetchClassFromFirebase(updatedChild)
                        Toast.makeText(context, "Photo updated", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } else {
            docRef.update("child", updatedChild).addOnSuccessListener {
                currentChildData = updatedChild
                fetchClassFromFirebase(updatedChild)
                Toast.makeText(context, "Photo updated", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showEditDialog() {
        val child = currentChildData ?: return
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_student_general, null)
        val dialog = AlertDialog.Builder(requireContext(), CommonR.style.CustomDialog)
            .setView(dialogView)
            .create()

        tempAvatarUri = null
        editAvatarView = dialogView.findViewById(R.id.imgEditStudentAvatar)

        val etFirstName = dialogView.findViewById<EditText>(R.id.etEditFirstName)
        val etLastName = dialogView.findViewById<EditText>(R.id.etEditLastName)
        val etMiddleName = dialogView.findViewById<EditText>(R.id.etEditMiddleName)
        val suffixSelector = dialogView.findViewById<FrameLayout>(R.id.btnEditStudentSuffix)
        val tvSelectedSuffix = dialogView.findViewById<TextView>(R.id.tvEditStudentSelectedSuffix)
        var selectedSuffix = child["suffix"] as? String ?: ""

        val etStudentId = dialogView.findViewById<EditText>(R.id.etEditStudentId)
        val etAge = dialogView.findViewById<EditText>(R.id.etEditDob)
        val etGrade = dialogView.findViewById<EditText>(R.id.etEditGrade)
        val etClass = dialogView.findViewById<EditText>(R.id.etEditClass)
        val etSchool = dialogView.findViewById<EditText>(R.id.etEditSchool)
        val etAddress = dialogView.findViewById<EditText>(R.id.etEditAddress)

        // Pre-fill
        etFirstName.setText(child["firstName"] as? String ?: "")
        etLastName.setText(child["lastName"] as? String ?: "")
        etMiddleName.setText(child["middleName"] as? String ?: "")
        
        if (selectedSuffix.isNotEmpty()) {
            tvSelectedSuffix.text = selectedSuffix
            tvSelectedSuffix.setTextColor(Color.BLACK)
        } else {
            tvSelectedSuffix.text = getString(CommonR.string.suffix)
            tvSelectedSuffix.setTextColor("#888888".toColorInt())
        }

        suffixSelector.setOnClickListener {
            val suffixes = arrayOf("None", "Jr.", "Sr.", "II", "III", "IV", "V")
            AlertDialog.Builder(requireContext())
                .setTitle("Select Suffix")
                .setItems(suffixes) { _, which ->
                    selectedSuffix = if (which == 0) "" else suffixes[which]
                    tvSelectedSuffix.text = if (which == 0) getString(CommonR.string.suffix) else suffixes[which]
                    tvSelectedSuffix.setTextColor(if (which == 0) "#888888".toColorInt() else Color.BLACK)
                }
                .show()
        }

        etStudentId.setText(child["studentId"] as? String ?: "")
        etAge.setText(child["age"] as? String ?: "")
        etGrade.setText(child["grade"] as? String ?: "")
        etClass.setText(child["class"] as? String ?: "")
        etSchool.setText(child["school"] as? String ?: "")
        etAddress.setText(child["address"] as? String ?: "")
        
        val avatarUrl = child["avatarUrl"] as? String
        if (!avatarUrl.isNullOrEmpty()) {
            Glide.with(this).load(avatarUrl).placeholder(CommonR.drawable.user).circleCrop().into(editAvatarView!!)
        }

        dialogView.findViewById<View>(R.id.rlEditStudentAvatar).setOnClickListener { pickImageLauncher.launch("image/*") }
        editAvatarView?.setOnClickListener { tempAvatarUri?.let { showPreviewDialog(it) } }

        dialogView.findViewById<ImageButton>(R.id.btnDismissEditGeneral).setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<Button>(R.id.btnCancelEdit).setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<Button>(R.id.btnSaveStudent).setOnClickListener {
            val updatedData = child.toMutableMap()
            updatedData["firstName"] = etFirstName.text.toString().trim()
            updatedData["lastName"] = etLastName.text.toString().trim()
            updatedData["middleName"] = etMiddleName.text.toString().trim()
            updatedData["suffix"] = selectedSuffix
            updatedData["studentId"] = etStudentId.text.toString().trim()
            updatedData["age"] = etAge.text.toString().trim()
            updatedData["grade"] = etGrade.text.toString().trim()
            updatedData["class"] = etClass.text.toString().trim()
            updatedData["school"] = etSchool.text.toString().trim()
            updatedData["address"] = etAddress.text.toString().trim()

            if (tempAvatarUri != null) {
                uploadEditedAvatar(tempAvatarUri!!, updatedData, dialog)
            } else {
                saveUpdatedGeneralData(updatedData, dialog)
            }
        }
        dialog.setOnDismissListener { editAvatarView = null }
        dialog.show()
    }

    private fun uploadEditedAvatar(uri: Uri, updatedChild: MutableMap<String, Any>, dialog: AlertDialog) {
        val uid = auth.currentUser?.uid ?: return
        val fileName = if (isFromChildrenList) "child_${childName?.replace(" ", "_")}_avatar.jpg" else "primaryAvatar.jpg"
        val ref = storage.reference.child("parents/$uid/$fileName")
        
        Toast.makeText(context, "Saving changes...", Toast.LENGTH_SHORT).show()
        
        ref.putFile(uri)
            .continueWithTask { task ->
                if (!task.isSuccessful) task.exception?.let { throw it }
                ref.downloadUrl
            }
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    updatedChild["avatarUrl"] = task.result.toString()
                    saveUpdatedGeneralData(updatedChild, dialog)
                } else {
                    Toast.makeText(context, "Upload failed", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun saveUpdatedGeneralData(updatedChild: kotlin.collections.Map<String, Any>, dialog: AlertDialog) {
        val uid = auth.currentUser?.uid ?: return
        val docRef = db.collection("parents").document(uid)

        if (isFromChildrenList) {
            docRef.get().addOnSuccessListener { document ->
                @Suppress("UNCHECKED_CAST")
                val childrenList = document.get("children") as? List<kotlin.collections.Map<String, Any>> ?: return@addOnSuccessListener
                val newList = childrenList.toMutableList()
                val index = newList.indexOfFirst { 
                    val fName = it["firstName"] as? String ?: ""
                    val lName = it["lastName"] as? String ?: ""
                    "$fName $lName" == childName 
                }
                if (index != -1) {
                    newList[index] = updatedChild
                    docRef.update("children", newList).addOnSuccessListener {
                        onUpdateSuccess(updatedChild, dialog)
                    }
                }
            }
        } else {
            docRef.update("child", updatedChild).addOnSuccessListener {
                onUpdateSuccess(updatedChild, dialog)
            }
        }
    }

    private fun onUpdateSuccess(updatedChild: kotlin.collections.Map<String, Any>, dialog: AlertDialog) {
        currentChildData = updatedChild
        childName = "${updatedChild["firstName"]} ${updatedChild["lastName"]}"
        fetchClassFromFirebase(updatedChild)
        dialog.dismiss()
        if (isAdded) Toast.makeText(context, "Student information updated", Toast.LENGTH_SHORT).show()
    }
}
