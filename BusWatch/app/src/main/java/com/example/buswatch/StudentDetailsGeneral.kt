package com.example.buswatch

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.buswatch.common.R as CommonR
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class StudentDetailsGeneral : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var childName: String? = null
    private var currentChildData: kotlin.collections.Map<String, Any>? = null
    private var isFromChildrenList: Boolean = false
    private var parentAddress: String = "---"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.studentdetails_general)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        childName = intent.getStringExtra("childName")

        val backButton = findViewById<ImageButton>(R.id.btnGeneralBack)
        val medicalButton = findViewById<Button>(R.id.btnGeneralMedical)
        val emergencyButton = findViewById<Button>(R.id.btnGeneralEmergency)
        val editButton = findViewById<View>(R.id.btnGeneralEdit)
        
        val tvHeaderName = findViewById<TextView>(R.id.tvStudentHeaderName)
        tvHeaderName.text = childName ?: "Student"

        fetchStudentData()

        backButton.setOnClickListener {
            finish()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, CommonR.anim.stay, CommonR.anim.slide_out_right)
            } else {
                @Suppress("DEPRECATION")
                overridePendingTransition(CommonR.anim.stay, CommonR.anim.slide_out_right)
            }
        }

        medicalButton.setOnClickListener {
            val intent = Intent(this, StudentDetailsMedical::class.java)
            intent.putExtra("childName", childName)
            startActivity(intent)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, CommonR.anim.fade_in, CommonR.anim.fade_out)
            } else {
                @Suppress("DEPRECATION")
                overridePendingTransition(CommonR.anim.fade_in, CommonR.anim.fade_out)
            }
        }

        emergencyButton.setOnClickListener {
            val intent = Intent(this, StudentDetailsEmergency::class.java)
            intent.putExtra("childName", childName)
            startActivity(intent)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, CommonR.anim.fade_in, CommonR.anim.fade_out)
            } else {
                @Suppress("DEPRECATION")
                overridePendingTransition(CommonR.anim.fade_in, CommonR.anim.fade_out)
            }
        }

        editButton.setOnClickListener {
            showEditDialog()
        }
    }

    private fun fetchStudentData() {
        val uid = auth.currentUser?.uid ?: return

        db.collection("parents").document(uid).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    parentAddress = document.getString("address") ?: "---"
                    
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
                        if (foundChild != null) {
                            isFromChildrenList = true
                        }
                    }

                    currentChildData = foundChild
                    foundChild?.let { displayChildInfo(it, parentAddress) }
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error fetching student data", Toast.LENGTH_SHORT).show()
            }
    }

    private fun displayChildInfo(child: kotlin.collections.Map<String, Any>, parentAddress: String) {
        val fullName = "${child["firstName"]} ${child["lastName"]}"
        findViewById<TextView>(R.id.tvStudentName).text = fullName
        findViewById<TextView>(R.id.tvStudentHeaderName).text = fullName
        
        findViewById<TextView>(R.id.tvStudentId).text = child["studentId"] as? String ?: "---"
        findViewById<TextView>(R.id.tvDob).text = child["dob"] as? String ?: "---"
        findViewById<TextView>(R.id.tvGrade).text = child["grade"] as? String ?: "---"
        findViewById<TextView>(R.id.tvClass).text = child["class"] as? String ?: "---"
        findViewById<TextView>(R.id.tvStudentClassBadge).text = child["grade"] as? String ?: "---"
        
        findViewById<TextView>(R.id.tvSchool).text = child["school"] as? String ?: "The Immaculate Mother Academy Inc."
        findViewById<TextView>(R.id.tvAddress).text = child["address"] as? String ?: parentAddress

        val imgAvatar = findViewById<ImageView>(R.id.imgStudentAvatar)
        val avatarName = child["avatar"] as? String ?: "dingdong"
        
        @Suppress("DiscouragedApi")
        val resId = resources.getIdentifier(avatarName, "drawable", packageName)
        if (resId != 0) {
            imgAvatar.setImageResource(resId)
        }
    }

    private fun showEditDialog() {
        val child = currentChildData ?: return
        
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_student_general, null)
        val dialog = AlertDialog.Builder(this, CommonR.style.CustomDialog)
            .setView(dialogView)
            .create()

        val etFirstName = dialogView.findViewById<EditText>(R.id.etEditFirstName)
        val etLastName = dialogView.findViewById<EditText>(R.id.etEditLastName)
        val etStudentId = dialogView.findViewById<EditText>(R.id.etEditStudentId)
        val etDob = dialogView.findViewById<EditText>(R.id.etEditDob)
        val etGrade = dialogView.findViewById<EditText>(R.id.etEditGrade)
        val etClass = dialogView.findViewById<EditText>(R.id.etEditClass)
        val etSchool = dialogView.findViewById<EditText>(R.id.etEditSchool)
        val etAddress = dialogView.findViewById<EditText>(R.id.etEditAddress)
        val imgAvatar = dialogView.findViewById<ImageView>(R.id.imgEditStudentAvatar)
        
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancelEdit)
        val btnSave = dialogView.findViewById<Button>(R.id.btnSaveStudent)

        // Pre-fill
        etFirstName.setText(child["firstName"] as? String ?: "")
        etLastName.setText(child["lastName"] as? String ?: "")
        etStudentId.setText(child["studentId"] as? String ?: "")
        etDob.setText(child["dob"] as? String ?: "")
        etGrade.setText(child["grade"] as? String ?: "")
        etClass.setText(child["class"] as? String ?: "")
        etSchool.setText(child["school"] as? String ?: "")
        etAddress.setText(child["address"] as? String ?: parentAddress)

        val avatarName = child["avatar"] as? String ?: "dingdong"
        
        @Suppress("DiscouragedApi")
        val resId = resources.getIdentifier(avatarName, "drawable", packageName)
        if (resId != 0) {
            imgAvatar.setImageResource(resId)
        }

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            val updatedChild = child.toMutableMap()
            updatedChild["firstName"] = etFirstName.text.toString()
            updatedChild["lastName"] = etLastName.text.toString()
            updatedChild["studentId"] = etStudentId.text.toString()
            updatedChild["dob"] = etDob.text.toString()
            updatedChild["grade"] = etGrade.text.toString()
            updatedChild["class"] = etClass.text.toString()
            updatedChild["school"] = etSchool.text.toString()
            updatedChild["address"] = etAddress.text.toString()

            saveUpdatedData(updatedChild, dialog)
        }

        dialog.show()
    }

    private fun saveUpdatedData(updatedChild: kotlin.collections.Map<String, Any>, dialog: AlertDialog) {
        val uid = auth.currentUser?.uid ?: return
        val docRef = db.collection("parents").document(uid)

        if (isFromChildrenList) {
            docRef.get().addOnSuccessListener { document ->
                @Suppress("UNCHECKED_CAST")
                val childrenList = document.get("children") as? List<kotlin.collections.Map<String, Any>> ?: return@addOnSuccessListener
                val newList = childrenList.toMutableList()
                
                val index = newList.indexOfFirst { 
                    "${it["firstName"]} ${it["lastName"]}" == childName 
                }
                
                if (index != -1) {
                    newList[index] = updatedChild
                    docRef.update("children", newList)
                        .addOnSuccessListener {
                            onUpdateSuccess(updatedChild, dialog)
                        }
                        .addOnFailureListener { onUpdateFailure() }
                }
            }
        } else {
            docRef.update("child", updatedChild)
                .addOnSuccessListener {
                    onUpdateSuccess(updatedChild, dialog)
                }
                .addOnFailureListener { onUpdateFailure() }
        }
    }

    private fun onUpdateSuccess(updatedChild: kotlin.collections.Map<String, Any>, dialog: AlertDialog) {
        currentChildData = updatedChild
        childName = "${updatedChild["firstName"]} ${updatedChild["lastName"]}"
        displayChildInfo(updatedChild, parentAddress)
        dialog.dismiss()
        Toast.makeText(this, "Student information updated", Toast.LENGTH_SHORT).show()
    }

    private fun onUpdateFailure() {
        Toast.makeText(this, "Failed to update information", Toast.LENGTH_SHORT).show()
    }
}
