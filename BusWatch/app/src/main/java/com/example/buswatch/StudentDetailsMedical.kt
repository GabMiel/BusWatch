package com.example.buswatch

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import com.example.buswatch.common.R as CommonR
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class StudentDetailsMedical : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var childName: String? = null
    private var currentChildData: kotlin.collections.Map<String, Any>? = null
    private var isFromChildrenList: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.studentdetails_medical)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        childName = intent.getStringExtra("childName")

        val backButton = findViewById<ImageButton>(R.id.btnMedicalBack)
        val generalButton = findViewById<Button>(R.id.btnMedicalGeneral)
        val emergencyButton = findViewById<Button>(R.id.btnMedicalEmergency)
        val editButton = findViewById<View>(R.id.btnMedicalEdit)
        
        val tvHeaderName = findViewById<TextView>(R.id.tvStudentHeaderName)
        tvHeaderName.text = childName ?: "Student"

        fetchStudentMedicalData()

        backButton.setOnClickListener {
            finish()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, CommonR.anim.stay, CommonR.anim.slide_out_right)
            } else {
                @Suppress("DEPRECATION")
                overridePendingTransition(CommonR.anim.stay, CommonR.anim.slide_out_right)
            }
        }

        generalButton.setOnClickListener {
            val intent = Intent(this, StudentDetailsGeneral::class.java)
            intent.putExtra("childName", childName)
            startActivity(intent)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, CommonR.anim.fade_in, CommonR.anim.fade_out)
            } else {
                @Suppress("DEPRECATION")
                overridePendingTransition(CommonR.anim.fade_in, CommonR.anim.fade_out)
            }
            finish()
        }

        emergencyButton.setOnClickListener {
            val intent = Intent(this, StudentDetailsEmergency::class.java)
            intent.putExtra("childName", childName)
            startActivity(intent)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, CommonR.anim.slide_in_right, CommonR.anim.slide_out_left)
            } else {
                @Suppress("DEPRECATION")
                overridePendingTransition(CommonR.anim.slide_in_right, CommonR.anim.slide_out_left)
            }
            finish()
        }

        editButton.setOnClickListener {
            showEditDialog()
        }
    }

    private fun fetchStudentMedicalData() {
        val uid = auth.currentUser?.uid ?: return

        db.collection("parents").document(uid).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
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
                    @Suppress("UNCHECKED_CAST")
                    val parentMedical = document.get("medical") as? kotlin.collections.Map<String, Any>
                    foundChild?.let { displayMedicalInfo(it, parentMedical) }
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error fetching medical data", Toast.LENGTH_SHORT).show()
            }
    }

    private fun displayMedicalInfo(child: kotlin.collections.Map<String, Any>, parentMedical: kotlin.collections.Map<String, Any>?) {
        @Suppress("UNCHECKED_CAST")
        val medical = child["medical"] as? kotlin.collections.Map<String, Any> ?: parentMedical
            
        findViewById<TextView>(R.id.tvBloodType).text = medical?.get("bloodType") as? String ?: "---"
        findViewById<TextView>(R.id.tvInsuranceProvider).text = medical?.get("insuranceProvider") as? String ?: "---"
        findViewById<TextView>(R.id.tvPolicyNumber).text = medical?.get("policyNumber") as? String ?: "---"
        
        findViewById<TextView>(R.id.tvPhysicianName).text = medical?.get("physicianName") as? String ?: "---"
        findViewById<TextView>(R.id.tvPhysicianPhone).text = medical?.get("physicianPhone") as? String ?: "---"

        setupChips(findViewById(R.id.cgAllergies), medical?.get("allergies") as? String)
        setupChips(findViewById(R.id.cgMedications), medical?.get("medications") as? String)
        setupChips(findViewById(R.id.cgConditions), medical?.get("conditions") as? String)
        setupChips(findViewById(R.id.cgDietary), medical?.get("dietary") as? String)
        
        val specialNeeds = medical?.get("specialNeeds") as? String ?: "None"
        findViewById<TextView>(R.id.tvSpecialNeeds).text = if (specialNeeds.isEmpty()) "None" else specialNeeds
    }

    private fun setupChips(chipGroup: ChipGroup, data: String?) {
        chipGroup.removeAllViews()
        if (data.isNullOrEmpty() || data.equals("none", ignoreCase = true)) {
            chipGroup.visibility = View.GONE
            return
        }
        
        chipGroup.visibility = View.VISIBLE
        data.split(",").forEach { item ->
            val trimmed = item.trim()
            if (trimmed.isNotEmpty()) {
                val chip = Chip(this)
                chip.text = trimmed
                chip.setChipBackgroundColorResource(CommonR.color.card_highlight)
                chip.setTextColor("#944600".toColorInt())
                chip.chipStrokeWidth = 0f
                chip.textSize = 10f
                chipGroup.addView(chip)
            }
        }
    }

    private fun showEditDialog() {
        val child = currentChildData ?: return
        @Suppress("UNCHECKED_CAST")
        val medical = child["medical"] as? kotlin.collections.Map<String, Any> ?: emptyMap()

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_student_medical, null)
        val dialog = AlertDialog.Builder(this, CommonR.style.CustomDialog)
            .setView(dialogView)
            .create()

        val etBloodType = dialogView.findViewById<EditText>(R.id.etEditBloodType)
        val etInsurance = dialogView.findViewById<EditText>(R.id.etEditInsuranceProvider)
        val etPolicy = dialogView.findViewById<EditText>(R.id.etEditPolicyNumber)
        val etPhysName = dialogView.findViewById<EditText>(R.id.etEditPhysicianName)
        val etPhysPhone = dialogView.findViewById<EditText>(R.id.etEditPhysicianPhone)
        val etAllergies = dialogView.findViewById<EditText>(R.id.etEditAllergies)
        val etMeds = dialogView.findViewById<EditText>(R.id.etEditMedications)
        val etConditions = dialogView.findViewById<EditText>(R.id.etEditConditions)
        val etSpecial = dialogView.findViewById<EditText>(R.id.etEditSpecialNeeds)
        val etDietary = dialogView.findViewById<EditText>(R.id.etEditDietary)
        
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancelEdit)
        val btnSave = dialogView.findViewById<Button>(R.id.btnSaveMedical)

        // Pre-fill
        etBloodType.setText(medical["bloodType"] as? String ?: "")
        etInsurance.setText(medical["insuranceProvider"] as? String ?: "")
        etPolicy.setText(medical["policyNumber"] as? String ?: "")
        etPhysName.setText(medical["physicianName"] as? String ?: "")
        etPhysPhone.setText(medical["physicianPhone"] as? String ?: "")
        etAllergies.setText(medical["allergies"] as? String ?: "")
        etMeds.setText(medical["medications"] as? String ?: "")
        etConditions.setText(medical["conditions"] as? String ?: "")
        etSpecial.setText(medical["specialNeeds"] as? String ?: "")
        etDietary.setText(medical["dietary"] as? String ?: "")

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            val updatedMedical = medical.toMutableMap()
            updatedMedical["bloodType"] = etBloodType.text.toString()
            updatedMedical["insuranceProvider"] = etInsurance.text.toString()
            updatedMedical["policyNumber"] = etPolicy.text.toString()
            updatedMedical["physicianName"] = etPhysName.text.toString()
            updatedMedical["physicianPhone"] = etPhysPhone.text.toString()
            updatedMedical["allergies"] = etAllergies.text.toString()
            updatedMedical["medications"] = etMeds.text.toString()
            updatedMedical["conditions"] = etConditions.text.toString()
            updatedMedical["specialNeeds"] = etSpecial.text.toString()
            updatedMedical["dietary"] = etDietary.text.toString()

            saveUpdatedMedicalData(updatedMedical, dialog)
        }

        dialog.show()
    }

    private fun saveUpdatedMedicalData(updatedMedical: kotlin.collections.Map<String, Any>, dialog: AlertDialog) {
        val uid = auth.currentUser?.uid ?: return
        val docRef = db.collection("parents").document(uid)
        val child = currentChildData ?: return
        
        val updatedChild = child.toMutableMap()
        updatedChild["medical"] = updatedMedical

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
        displayMedicalInfo(updatedChild, null)
        dialog.dismiss()
        Toast.makeText(this, "Medical information updated", Toast.LENGTH_SHORT).show()
    }

    private fun onUpdateFailure() {
        Toast.makeText(this, "Failed to update information", Toast.LENGTH_SHORT).show()
    }
}
