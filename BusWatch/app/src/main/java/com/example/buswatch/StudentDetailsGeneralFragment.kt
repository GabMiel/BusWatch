package com.example.buswatch

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.buswatch.common.R as CommonR
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

class StudentDetailsGeneralFragment : Fragment() {
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private var childName: String? = null
    private var currentChildData: kotlin.collections.Map<String, Any>? = null
    private var isFromChildrenList: Boolean = false
    
    private var tempAvatarUri: Uri? = null
    private lateinit var avatarView: ImageView

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            tempAvatarUri = it
            avatarView.setImageURI(it)
            uploadNewAvatar(it)
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
                    foundChild?.let { displayGeneralInfo(it) }
                }
            }
            .addOnFailureListener {
                if (isAdded) Toast.makeText(context, "Error fetching student data", Toast.LENGTH_SHORT).show()
            }
    }

    private fun displayGeneralInfo(child: kotlin.collections.Map<String, Any>) {
        view?.let { v ->
            v.findViewById<TextView>(R.id.tvStudentName).text = "${child["firstName"]} ${child["middleName"] ?: ""} ${child["lastName"]}".replace("  ", " ")
            v.findViewById<TextView>(R.id.tvGrade).text = child["grade"] as? String ?: "---"
            v.findViewById<TextView>(R.id.tvDob).text = child["age"] as? String ?: "---"
            v.findViewById<TextView>(R.id.tvAddress).text = child["address"] as? String ?: "---"
            v.findViewById<TextView>(R.id.tvSchool).text = child["school"] as? String ?: "The Immaculate Mother Academy Inc."
            v.findViewById<TextView>(R.id.tvStudentId).text = child["studentId"] as? String ?: "---"
            
            val classValue = child["class"] as? String ?: ""
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
                val index = newList.indexOfFirst { "${it["firstName"]} ${it["lastName"]}" == childName }
                if (index != -1) {
                    newList[index] = updatedChild
                    docRef.update("children", newList).addOnSuccessListener {
                        currentChildData = updatedChild
                        Toast.makeText(context, "Photo updated", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } else {
            docRef.update("child", updatedChild).addOnSuccessListener {
                currentChildData = updatedChild
                Toast.makeText(context, "Photo updated", Toast.makeText(context, "Photo updated", Toast.LENGTH_SHORT).show())
            }
        }
    }

    private fun showEditDialog() {
        val child = currentChildData ?: return
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_student_general, null)
        val dialog = AlertDialog.Builder(requireContext(), CommonR.style.CustomDialog)
            .setView(dialogView)
            .create()

        val etFirstName = dialogView.findViewById<EditText>(R.id.etEditFirstName)
        val etLastName = dialogView.findViewById<EditText>(R.id.etEditLastName)
        val etStudentId = dialogView.findViewById<EditText>(R.id.etEditStudentId)
        val etAge = dialogView.findViewById<EditText>(R.id.etEditDob)
        val etGrade = dialogView.findViewById<EditText>(R.id.etEditGrade)
        val etClass = dialogView.findViewById<EditText>(R.id.etEditClass)
        val etSchool = dialogView.findViewById<EditText>(R.id.etEditSchool)
        val etAddress = dialogView.findViewById<EditText>(R.id.etEditAddress)
        val imgEditAvatar = dialogView.findViewById<ImageView>(R.id.imgEditStudentAvatar)

        // Pre-fill
        etFirstName.setText(child["firstName"] as? String ?: "")
        etLastName.setText(child["lastName"] as? String ?: "")
        etStudentId.setText(child["studentId"] as? String ?: "")
        etAge.setText(child["age"] as? String ?: "")
        etGrade.setText(child["grade"] as? String ?: "")
        etClass.setText(child["class"] as? String ?: "")
        etSchool.setText(child["school"] as? String ?: "")
        etAddress.setText(child["address"] as? String ?: "")
        
        val avatarUrl = child["avatarUrl"] as? String
        if (!avatarUrl.isNullOrEmpty()) {
            Glide.with(this).load(avatarUrl).placeholder(CommonR.drawable.user).circleCrop().into(imgEditAvatar)
        }

        dialogView.findViewById<Button>(R.id.btnCancelEdit).setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<Button>(R.id.btnSaveStudent).setOnClickListener {
            val updatedData = child.toMutableMap()
            updatedData["firstName"] = etFirstName.text.toString()
            updatedData["lastName"] = etLastName.text.toString()
            updatedData["studentId"] = etStudentId.text.toString()
            updatedData["age"] = etAge.text.toString()
            updatedData["grade"] = etGrade.text.toString()
            updatedData["class"] = etClass.text.toString()
            updatedData["school"] = etSchool.text.toString()
            updatedData["address"] = etAddress.text.toString()

            saveUpdatedGeneralData(updatedData, dialog)
        }
        dialog.show()
    }

    private fun saveUpdatedGeneralData(updatedChild: kotlin.collections.Map<String, Any>, dialog: AlertDialog) {
        val uid = auth.currentUser?.uid ?: return
        val docRef = db.collection("parents").document(uid)

        if (isFromChildrenList) {
            docRef.get().addOnSuccessListener { document ->
                @Suppress("UNCHECKED_CAST")
                val childrenList = document.get("children") as? List<kotlin.collections.Map<String, Any>> ?: return@addOnSuccessListener
                val newList = childrenList.toMutableList()
                val index = newList.indexOfFirst { "${it["firstName"]} ${it["lastName"]}" == childName }
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
        displayGeneralInfo(updatedChild)
        dialog.dismiss()
        if (isAdded) Toast.makeText(context, "Student information updated", Toast.LENGTH_SHORT).show()
    }
}
