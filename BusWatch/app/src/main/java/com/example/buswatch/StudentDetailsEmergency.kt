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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.buswatch.common.R as CommonR
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class StudentDetailsEmergency : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var childName: String? = null
    private var parentData: kotlin.collections.Map<String, Any>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.studentdetails_emergency)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        childName = intent.getStringExtra("childName")

        val backButton = findViewById<ImageButton>(R.id.btnEmergencyBack)
        val generalButton = findViewById<Button>(R.id.btnEmergencyGeneral)
        val medicalButton = findViewById<Button>(R.id.btnEmergencyMedical)
        val editButton = findViewById<View>(R.id.btnEmergencyEdit)
        
        val tvHeaderName = findViewById<TextView>(R.id.tvStudentHeaderName)
        tvHeaderName.text = childName ?: "Student"

        fetchEmergencyData()

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
            finish()
        }

        editButton.setOnClickListener {
            showEditDialog()
        }
    }

    private fun fetchEmergencyData() {
        val uid = auth.currentUser?.uid ?: return

        db.collection("parents").document(uid).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    parentData = document.data
                    displayEmergencyInfo(document.data ?: emptyMap())
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error fetching emergency data", Toast.LENGTH_SHORT).show()
            }
    }

    private fun displayEmergencyInfo(data: kotlin.collections.Map<String, Any>) {
        val contactList = mutableListOf<EmergencyContact>()
        
        // 1. Add Parent as Primary
        val pFName = data["firstName"] as? String ?: "---"
        val pLName = data["lastName"] as? String ?: ""
        val pPhone = data["phone"] as? String ?: "---"
        val pEmail = data["email"] as? String ?: "---"
        contactList.add(EmergencyContact("$pFName $pLName".trim(), "Parent", pPhone, pEmail, isPrimary = true))

        // 2. Add other Emergency Contacts from the list
        val contactsData = data["emergencyContacts"]
        if (contactsData is List<*>) {
            contactsData.forEach { item ->
                if (item is kotlin.collections.Map<*, *>) {
                    contactList.add(EmergencyContact(
                        name = item["name"] as? String ?: "---",
                        relation = item["relationship"] as? String ?: "---",
                        phone = item["phone"] as? String ?: "---",
                        email = item["email"] as? String ?: "---"
                    ))
                }
            }
        }
        
        val rv = findViewById<RecyclerView>(R.id.rvEmergencyPickups)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = DetailsEmergencyAdapter(contactList)
    }

    private fun showEditDialog() {
        val data = parentData ?: return
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_student_emergency, null)
        val dialog = AlertDialog.Builder(this, CommonR.style.CustomDialog)
            .setView(dialogView)
            .create()

        val etParentFName = dialogView.findViewById<EditText>(R.id.etEditParentFirstName)
        val etParentLName = dialogView.findViewById<EditText>(R.id.etEditParentLastName)
        val etParentPhone = dialogView.findViewById<EditText>(R.id.etEditParentPhone)
        
        val etC1Name = dialogView.findViewById<EditText>(R.id.etEditContact1Name)
        val etC1Rel = dialogView.findViewById<EditText>(R.id.etEditContact1Rel)
        val etC1Phone = dialogView.findViewById<EditText>(R.id.etEditContact1Phone)
        
        val etC2Name = dialogView.findViewById<EditText>(R.id.etEditContact2Name)
        val etC2Rel = dialogView.findViewById<EditText>(R.id.etEditContact2Rel)
        val etC2Phone = dialogView.findViewById<EditText>(R.id.etEditContact2Phone)

        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancelEdit)
        val btnSave = dialogView.findViewById<Button>(R.id.btnSaveEmergency)

        // Pre-fill Parent
        etParentFName.setText(data["firstName"] as? String ?: "")
        etParentLName.setText(data["lastName"] as? String ?: "")
        etParentPhone.setText(data["phone"] as? String ?: "")

        // Pre-fill Additional Contacts
        @Suppress("UNCHECKED_CAST")
        val contacts = data["emergencyContacts"] as? List<kotlin.collections.Map<String, Any>> ?: emptyList()
        if (contacts.isNotEmpty()) {
            etC1Name.setText(contacts[0]["name"] as? String ?: "")
            etC1Rel.setText(contacts[0]["relationship"] as? String ?: "")
            etC1Phone.setText(contacts[0]["phone"] as? String ?: "")
        }
        if (contacts.size > 1) {
            etC2Name.setText(contacts[1]["name"] as? String ?: "")
            etC2Rel.setText(contacts[1]["relationship"] as? String ?: "")
            etC2Phone.setText(contacts[1]["phone"] as? String ?: "")
        }

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            val updates = mutableMapOf<String, Any>()
            updates["firstName"] = etParentFName.text.toString()
            updates["lastName"] = etParentLName.text.toString()
            updates["phone"] = etParentPhone.text.toString()

            val updatedContacts = mutableListOf<kotlin.collections.Map<String, String>>()
            
            // Contact 1
            if (etC1Name.text.isNotEmpty()) {
                updatedContacts.add(mapOf(
                    "name" to etC1Name.text.toString(),
                    "relationship" to etC1Rel.text.toString(),
                    "phone" to etC1Phone.text.toString(),
                    "email" to (if (contacts.isNotEmpty()) contacts[0]["email"] as? String ?: "" else "")
                ))
            }

            // Contact 2
            if (etC2Name.text.isNotEmpty()) {
                updatedContacts.add(mapOf(
                    "name" to etC2Name.text.toString(),
                    "relationship" to etC2Rel.text.toString(),
                    "phone" to etC2Phone.text.toString(),
                    "email" to (if (contacts.size > 1) contacts[1]["email"] as? String ?: "" else "")
                ))
            }
            
            updates["emergencyContacts"] = updatedContacts

            saveEmergencyUpdates(updates, dialog)
        }

        dialog.show()
    }

    private fun saveEmergencyUpdates(updates: kotlin.collections.Map<String, Any>, dialog: AlertDialog) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("parents").document(uid).update(updates)
            .addOnSuccessListener {
                val newData = parentData?.toMutableMap() ?: mutableMapOf()
                newData.putAll(updates)
                parentData = newData
                displayEmergencyInfo(newData)
                dialog.dismiss()
                Toast.makeText(this, "Emergency contacts updated", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to update emergency contacts", Toast.LENGTH_SHORT).show()
            }
    }
}
