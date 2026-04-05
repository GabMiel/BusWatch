package com.example.buswatch

import android.content.Intent
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

class ParentDetails : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.parentdetails)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        findViewById<ImageButton>(R.id.btnParentsBack).setOnClickListener {
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(CommonR.anim.stay, CommonR.anim.slide_out_right)
        }

        findViewById<View>(R.id.btnParentsEdit).setOnClickListener {
            showEditProfileDialog()
        }

        fetchParentData()
    }

    private fun fetchParentData() {
        val uid = auth.currentUser?.uid ?: return

        db.collection("parents").document(uid).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    // 1. Header & Profile Info
                    val fName = document.getString("firstName") ?: ""
                    val lName = document.getString("lastName") ?: ""
                    val fullName = "$fName $lName"
                    
                    findViewById<TextView>(R.id.tvHeaderSub).text = fullName
                    findViewById<TextView>(R.id.textView46).text = fullName
                    findViewById<TextView>(R.id.textView48).text = document.getString("email") ?: ""
                    findViewById<TextView>(R.id.textView50).text = document.getString("phone") ?: ""
                    
                    val tvStatus = findViewById<TextView>(R.id.textView52)
                    val status = document.getString("status") ?: "Approved"
                    tvStatus.text = status

                    // 2. Children RecyclerView
                    val childrenList = mutableListOf<ChildDetail>()
                    
                    val childData = document.get("child")
                    if (childData is kotlin.collections.Map<*, *>) {
                        val cfName = childData["firstName"] as? String ?: ""
                        val clName = childData["lastName"] as? String ?: ""
                        val cGrade = childData["grade"] as? String ?: ""
                        val cAvatar = childData["avatar"] as? String
                        
                        childrenList.add(ChildDetail(
                            name = "$cfName $clName",
                            grade = cGrade,
                            school = childData["school"] as? String ?: "The Immaculate Mother Academy Inc.",
                            status = childData["status"] as? String ?: "AT HOME",
                            avatarName = cAvatar
                        ))
                    }
                    
                    val childrenListData = document.get("children")
                    if (childrenListData is List<*>) {
                        childrenListData.forEach { item ->
                            if (item is kotlin.collections.Map<*, *>) {
                                val cfName = item["firstName"] as? String ?: ""
                                val clName = item["lastName"] as? String ?: ""
                                val cGrade = item["grade"] as? String ?: ""
                                val cAvatar = item["avatar"] as? String
                                childrenList.add(ChildDetail(
                                    name = "$cfName $clName",
                                    grade = cGrade,
                                    school = item["school"] as? String ?: "The Immaculate Mother Academy Inc.",
                                    status = item["status"] as? String ?: "AT HOME",
                                    avatarName = cAvatar
                                ))
                            }
                        }
                    }
                    
                    val rvChildren = findViewById<RecyclerView>(R.id.rvDetailsChildren)
                    rvChildren.layoutManager = LinearLayoutManager(this)
                    rvChildren.adapter = DetailsChildAdapter(childrenList) { child ->
                        val intent = Intent(this, StudentDetailsGeneral::class.java)
                        intent.putExtra("childName", child.name)
                        startActivity(intent)
                        @Suppress("DEPRECATION")
                        overridePendingTransition(CommonR.anim.fade_in, CommonR.anim.fade_out)
                    }

                    // 3. Contacts RecyclerView
                    val contactList = mutableListOf<ContactDetail>()
                    
                    // Add primary contact info (Parent)
                    val phone = document.getString("phone") ?: ""
                    if (phone.isNotEmpty()) contactList.add(ContactDetail("Phone", phone))
                    val email = document.getString("email") ?: ""
                    if (email.isNotEmpty()) contactList.add(ContactDetail("Email", email))
                    
                    // Add secondary emergency contacts
                    val contactsData = document.get("emergencyContacts")
                    if (contactsData is List<*>) {
                        contactsData.forEach { item ->
                            if (item is kotlin.collections.Map<*, *>) {
                                val name = item["name"] as? String ?: ""
                                val cPhone = item["phone"] as? String ?: ""
                                val relationship = item["relationship"] as? String ?: ""
                                if (name.isNotEmpty() || cPhone.isNotEmpty()) {
                                    contactList.add(ContactDetail(
                                        label = "Emergency Contact",
                                        value = cPhone,
                                        name = name,
                                        relationship = relationship
                                    ))
                                }
                            }
                        }
                    }
                    
                    val rvContacts = findViewById<RecyclerView>(R.id.rvDetailsContacts)
                    rvContacts.layoutManager = LinearLayoutManager(this)
                    rvContacts.adapter = DetailsContactAdapter(contactList)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load profile", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showEditProfileDialog() {
        val uid = auth.currentUser?.uid ?: return
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_profile, null)
        val dialog = AlertDialog.Builder(this, android.R.style.Theme_Material_Light_NoActionBar_Fullscreen)
            .setView(dialogView)
            .create()

        val etFirstName = dialogView.findViewById<EditText>(R.id.etEditFirstName)
        val etLastName = dialogView.findViewById<EditText>(R.id.etEditLastName)
        val etMiddleName = dialogView.findViewById<EditText>(R.id.etEditMiddleName)
        val etSuffix = dialogView.findViewById<EditText>(R.id.etEditSuffix)
        val etEmail = dialogView.findViewById<EditText>(R.id.etEditEmail)
        val etPhone = dialogView.findViewById<EditText>(R.id.etEditPhone)

        // Pre-fill with current data
        db.collection("parents").document(uid).get().addOnSuccessListener { doc ->
            etFirstName.setText(doc.getString("firstName"))
            etLastName.setText(doc.getString("lastName"))
            etMiddleName.setText(doc.getString("middleName"))
            etSuffix.setText(doc.getString("suffix"))
            etEmail.setText(doc.getString("email"))
            etPhone.setText(doc.getString("phone"))
        }

        dialogView.findViewById<Button>(R.id.btnCancelEdit).setOnClickListener {
            dialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.btnSaveProfile).setOnClickListener {
            val updateData = mapOf(
                "firstName" to etFirstName.text.toString().trim(),
                "lastName" to etLastName.text.toString().trim(),
                "middleName" to etMiddleName.text.toString().trim(),
                "suffix" to etSuffix.text.toString().trim(),
                "email" to etEmail.text.toString().trim(),
                "phone" to etPhone.text.toString().trim()
            )

            if (updateData["firstName"].isNullOrEmpty() || updateData["lastName"].isNullOrEmpty() || 
                updateData["email"].isNullOrEmpty() || updateData["phone"].isNullOrEmpty()) {
                Toast.makeText(this, "Please fill in all required fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            db.collection("parents").document(uid).update(updateData)
                .addOnSuccessListener {
                    Toast.makeText(this, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
                    fetchParentData() // Refresh UI
                    dialog.dismiss()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to update profile", Toast.LENGTH_SHORT).show()
                }
        }

        dialog.show()
    }
}
