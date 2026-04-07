package com.example.buswatch

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.buswatch.common.R as CommonR
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.collections.Map as KMap

class ParentDetails : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var childrenList = mutableListOf<ChildDetail>()
    private lateinit var adapter: DetailsChildAdapter
    private var isDeleteMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.parentdetails)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        findViewById<ImageButton>(R.id.btnParentsBack).setOnClickListener {
            finish()
        }

        findViewById<View>(R.id.btnParentsEdit).setOnClickListener {
            showEditProfileDialog()
        }

        findViewById<ImageButton>(R.id.btnAddChild).setOnClickListener {
            showAddChildDialog()
        }

        val btnConfirmDelete = findViewById<Button>(R.id.btnConfirmDeleteChildren)
        findViewById<ImageButton>(R.id.btnDeleteChild).setOnClickListener {
            if (childrenList.isEmpty()) {
                Toast.makeText(this, "You need to add a child first before you can use the removal feature", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            isDeleteMode = !isDeleteMode
            adapter.setDeleteMode(isDeleteMode)
            btnConfirmDelete.visibility = if (isDeleteMode) View.VISIBLE else View.GONE
            if (isDeleteMode) {
                Toast.makeText(this, "Select the children you wish to remove from your list", Toast.LENGTH_SHORT).show()
            }
        }

        btnConfirmDelete.setOnClickListener {
            val selectedChildren = adapter.getSelectedChildren()
            if (selectedChildren.isEmpty()) {
                // Revert back if nothing was selected
                isDeleteMode = false
                adapter.setDeleteMode(false)
                btnConfirmDelete.visibility = View.GONE
                Toast.makeText(this, "Removal cancelled: No children were selected", Toast.LENGTH_SHORT).show()
            } else {
                showBulkDeleteWarning(selectedChildren)
            }
        }

        setupRecyclerView()
        fetchParentData()
    }

    private fun setupRecyclerView() {
        val rvChildren = findViewById<RecyclerView>(R.id.rvDetailsChildren)
        rvChildren.layoutManager = LinearLayoutManager(this)
        adapter = DetailsChildAdapter(
            childrenList,
            onViewClick = { child ->
                val intent = Intent(this, StudentDetailsActivity::class.java)
                intent.putExtra("childName", child.name)
                startActivity(intent)
            }
        )
        rvChildren.adapter = adapter
    }

    private fun fetchParentData() {
        val uid = auth.currentUser?.uid ?: return

        db.collection("parents").document(uid).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    // Header & Profile Info
                    val fName = document.getString("firstName") ?: ""
                    val lName = document.getString("lastName") ?: ""
                    val fullName = "$fName $lName".trim()
                    
                    findViewById<TextView>(R.id.tvHeaderSub).text = fullName
                    findViewById<TextView>(R.id.textView46).text = fullName
                    findViewById<TextView>(R.id.textView48).text = document.getString("email") ?: ""
                    findViewById<TextView>(R.id.textView50).text = document.getString("phone") ?: ""
                    
                    val tvStatus = findViewById<TextView>(R.id.textView52)
                    val status = document.getString("status") ?: "pending"
                    tvStatus.text = status.uppercase()
                    
                    if (status.lowercase() == "approved") {
                        tvStatus.backgroundTintList = ColorStateList.valueOf("#D4EDDA".toColorInt())
                        tvStatus.setTextColor("#155724".toColorInt())
                    } else {
                        tvStatus.backgroundTintList = ColorStateList.valueOf("#FFF3CD".toColorInt())
                        tvStatus.setTextColor("#856404".toColorInt())
                    }

                    // Children Data
                    val newChildrenList = mutableListOf<ChildDetail>()
                    
                    // Legacy 'child' field
                    val childData = document.get("child")
                    if (childData is KMap<*, *>) {
                        @Suppress("UNCHECKED_CAST")
                        newChildrenList.add(mapToChildDetail(childData as KMap<String, Any>, "primary_child"))
                    }
                    
                    // 'children' list field
                    val childrenListData = document.get("children")
                    if (childrenListData is List<*>) {
                        childrenListData.forEachIndexed { index, item ->
                            if (item is KMap<*, *>) {
                                @Suppress("UNCHECKED_CAST")
                                newChildrenList.add(mapToChildDetail(item as KMap<String, Any>, index.toString()))
                            }
                        }
                    }
                    
                    childrenList = newChildrenList
                    adapter.updateList(childrenList)

                    // Contacts RecyclerView
                    val contactList = mutableListOf<ContactDetail>()
                    
                    val contactsData = document.get("emergencyContacts")
                    if (contactsData is List<*>) {
                        contactsData.forEach { item ->
                            if (item is KMap<*, *>) {
                                val name = item["name"] as? String ?: ""
                                val cPhone = item["phone"] as? String ?: ""
                                val relationship = item["relationship"] as? String ?: ""
                                if (name.isNotEmpty() || cPhone.isNotEmpty()) {
                                    contactList.add(ContactDetail("Emergency Contact", cPhone, name, relationship))
                                }
                            }
                        }
                    }
                    
                    val rvContacts = findViewById<RecyclerView>(R.id.rvDetailsContacts)
                    rvContacts.layoutManager = LinearLayoutManager(this)
                    rvContacts.adapter = DetailsContactAdapter(contactList)
                }
            }
    }

    private fun mapToChildDetail(map: KMap<String, Any>, id: String): ChildDetail {
        val cfName = map["firstName"] as? String ?: ""
        val clName = map["lastName"] as? String ?: ""
        val cmName = map["middleName"] as? String ?: ""
        val csuffix = map["suffix"] as? String ?: ""
        val age = map["age"]?.toString() ?: ""
        return ChildDetail(
            name = "$cfName $clName".trim(),
            grade = map["grade"] as? String ?: map["class"] as? String ?: "---",
            school = map["school"] as? String ?: "The Immaculate Mother Academy Inc.",
            status = map["status"] as? String ?: "AT HOME",
            avatarUrl = map["avatarUrl"] as? String,
            firstName = cfName,
            lastName = clName,
            middleName = cmName,
            suffix = csuffix,
            age = age,
            id = id
        )
    }

    private fun showAddChildDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_child, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        val suffixSelector = dialogView.findViewById<FrameLayout>(R.id.btnAddChildSuffix)
        val tvSelectedSuffix = suffixSelector.getChildAt(0) as TextView
        var selectedSuffix = ""

        suffixSelector.setOnClickListener {
            val suffixes = arrayOf("None", "Jr.", "Sr.", "II", "III", "IV", "V")
            AlertDialog.Builder(this)
                .setTitle("Select Suffix")
                .setItems(suffixes) { _, which ->
                    selectedSuffix = if (which == 0) "" else suffixes[which]
                    tvSelectedSuffix.text = if (which == 0) getString(CommonR.string.suffix) else suffixes[which]
                    tvSelectedSuffix.setTextColor(if (which == 0) "#888888".toColorInt() else Color.BLACK)
                }
                .show()
        }

        dialogView.findViewById<ImageButton>(R.id.btnDismissAddChild).setOnClickListener {
            dialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.btnAddChildConfirm).setOnClickListener {
            val firstName = dialogView.findViewById<EditText>(R.id.etAddChildFirstName).text.toString().trim()
            val lastName = dialogView.findViewById<EditText>(R.id.etAddChildLastName).text.toString().trim()
            val middleName = dialogView.findViewById<EditText>(R.id.etAddChildMiddleName).text.toString().trim()
            val suffix = selectedSuffix
            val age = dialogView.findViewById<EditText>(R.id.etAddChildAge).text.toString().trim()
            val className = dialogView.findViewById<EditText>(R.id.etAddChildClass).text.toString().trim()
            val school = dialogView.findViewById<EditText>(R.id.etAddChildSchool).text.toString().trim()
            val grade = dialogView.findViewById<EditText>(R.id.etAddChildGrade).text.toString().trim()

            if (firstName.isEmpty() || lastName.isEmpty() || age.isEmpty() || className.isEmpty() || school.isEmpty() || grade.isEmpty()) {
                Toast.makeText(this, "Please fill in all required fields marked with an asterisk (*)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val newChild = mapOf(
                "firstName" to firstName,
                "lastName" to lastName,
                "middleName" to middleName,
                "suffix" to suffix,
                "age" to age,
                "class" to className,
                "school" to school,
                "grade" to grade,
                "status" to "AT HOME"
            )

            val uid = auth.currentUser?.uid ?: return@setOnClickListener
            db.collection("parents").document(uid)
                .update("children", FieldValue.arrayUnion(newChild))
                .addOnSuccessListener {
                    Toast.makeText(this, "Child added successfully!", Toast.LENGTH_SHORT).show()
                    fetchParentData()
                    dialog.dismiss()
                }
        }
        dialog.show()
    }

    private fun showBulkDeleteWarning(selectedChildren: List<ChildDetail>) {
        val names = selectedChildren.joinToString(", ") { it.name }
        AlertDialog.Builder(this)
            .setTitle("Confirm Removal")
            .setMessage("Are you sure you want to remove the following children from your profile: $names?")
            .setPositiveButton("Remove") { _, _ ->
                deleteMultipleChildren(selectedChildren)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteMultipleChildren(selectedChildren: List<ChildDetail>) {
        val uid = auth.currentUser?.uid ?: return
        val docRef = db.collection("parents").document(uid)
        
        val childrenToRemove = selectedChildren.filter { it.id != "primary_child" }.map { child ->
            mapOf(
                "firstName" to child.firstName,
                "lastName" to child.lastName,
                "middleName" to child.middleName,
                "suffix" to child.suffix,
                "age" to child.age,
                "class" to child.grade,
                "school" to child.school,
                "status" to child.status
            )
        }

        val hasPrimary = selectedChildren.any { it.id == "primary_child" }

        if (childrenToRemove.isNotEmpty()) {
            docRef.update("children", FieldValue.arrayRemove(*childrenToRemove.toTypedArray()))
                .addOnSuccessListener {
                    if (hasPrimary) {
                        deletePrimaryChild(uid)
                    } else {
                        onDeletionComplete()
                    }
                }
        } else if (hasPrimary) {
            deletePrimaryChild(uid)
        }
    }

    private fun deletePrimaryChild(uid: String) {
        db.collection("parents").document(uid).update("child", FieldValue.delete())
            .addOnSuccessListener {
                onDeletionComplete()
            }
    }

    private fun onDeletionComplete() {
        Toast.makeText(this, "The selected child profiles have been removed", Toast.LENGTH_SHORT).show()
        isDeleteMode = false
        adapter.setDeleteMode(false)
        findViewById<Button>(R.id.btnConfirmDeleteChildren).visibility = View.GONE
        fetchParentData()
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
        val suffixSelector = dialogView.findViewById<FrameLayout>(R.id.btnEditProfileSuffix)
        val tvSelectedSuffix = dialogView.findViewById<TextView>(R.id.tvEditProfileSelectedSuffix)
        var selectedSuffix = ""

        val etEmail = dialogView.findViewById<EditText>(R.id.etEditEmail)
        val etPhone = dialogView.findViewById<EditText>(R.id.etEditPhone)

        db.collection("parents").document(uid).get().addOnSuccessListener { doc ->
            etFirstName.setText(doc.getString("firstName"))
            etLastName.setText(doc.getString("lastName"))
            etMiddleName.setText(doc.getString("middleName"))
            
            selectedSuffix = doc.getString("suffix") ?: ""
            if (selectedSuffix.isNotEmpty()) {
                tvSelectedSuffix.text = selectedSuffix
                tvSelectedSuffix.setTextColor(Color.BLACK)
            } else {
                tvSelectedSuffix.text = getString(CommonR.string.suffix)
                tvSelectedSuffix.setTextColor("#888888".toColorInt())
            }

            etEmail.setText(doc.getString("email"))
            etPhone.setText(doc.getString("phone"))
        }

        suffixSelector.setOnClickListener {
            val suffixes = arrayOf("None", "Jr.", "Sr.", "II", "III", "IV", "V")
            AlertDialog.Builder(this)
                .setTitle("Select Suffix")
                .setItems(suffixes) { _, which ->
                    selectedSuffix = if (which == 0) "" else suffixes[which]
                    tvSelectedSuffix.text = if (which == 0) getString(CommonR.string.suffix) else suffixes[which]
                    tvSelectedSuffix.setTextColor(if (which == 0) "#888888".toColorInt() else Color.BLACK)
                }
                .show()
        }

        dialogView.findViewById<ImageButton>(R.id.btnDismissEditProfile).setOnClickListener {
            dialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.btnCancelEdit).setOnClickListener {
            dialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.btnSaveProfile).setOnClickListener {
            val updateData = mapOf(
                "firstName" to etFirstName.text.toString().trim(),
                "lastName" to etLastName.text.toString().trim(),
                "middleName" to etMiddleName.text.toString().trim(),
                "suffix" to selectedSuffix,
                "email" to etEmail.text.toString().trim(),
                "phone" to etPhone.text.toString().trim()
            )

            db.collection("parents").document(uid).update(updateData)
                .addOnSuccessListener {
                    Toast.makeText(this, "Profile updated successfully", Toast.LENGTH_SHORT).show()
                    fetchParentData()
                    dialog.dismiss()
                }
        }
        dialog.show()
    }
}
