package com.example.buswatch

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.buswatch.common.R as CommonR
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class StudentDetailsEmergencyFragment : Fragment() {
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var childName: String? = null
    private var parentData: kotlin.collections.Map<String, Any>? = null
    private var isFromChildrenList: Boolean = false
    private var currentChildData: kotlin.collections.Map<String, Any>? = null

    companion object {
        fun newInstance(childName: String?): StudentDetailsEmergencyFragment {
            val fragment = StudentDetailsEmergencyFragment()
            val args = Bundle()
            args.putString("childName", childName)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_student_details_emergency, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        childName = arguments?.getString("childName")

        view.findViewById<View>(R.id.btnEmergencyEdit).setOnClickListener {
            showEditDialog()
        }

        fetchEmergencyData()
    }

    private fun fetchEmergencyData() {
        val uid = auth.currentUser?.uid ?: return

        db.collection("parents").document(uid).get()
            .addOnSuccessListener { document ->
                if (isAdded && document != null && document.exists()) {
                    parentData = document.data
                    
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

                    displayEmergencyInfo(document.data ?: emptyMap(), foundChild)
                }
            }
            .addOnFailureListener {
                if (isAdded) Toast.makeText(context, "Error fetching emergency data", Toast.LENGTH_SHORT).show()
            }
    }

    private fun displayEmergencyInfo(data: kotlin.collections.Map<String, Any>, child: kotlin.collections.Map<String, Any>?) {
        val view = view ?: return
        val contactList = mutableListOf<EmergencyContact>()
        
        val pFName = data["firstName"] as? String ?: "---"
        val pLName = data["lastName"] as? String ?: ""
        val pPhone = data["phone"] as? String ?: "---"
        val pEmail = data["email"] as? String ?: "---"
        contactList.add(EmergencyContact("$pFName $pLName".trim(), "Parent", pPhone, pEmail, isPrimary = true))

        @Suppress("UNCHECKED_CAST")
        val contactsData = (child?.get("emergencyContacts") ?: data["emergencyContacts"]) as? List<*>
        
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
        
        val rv = view.findViewById<RecyclerView>(R.id.rvEmergencyPickups)
        rv.layoutManager = LinearLayoutManager(context)
        rv.adapter = DetailsEmergencyAdapter(contactList)
    }

    private fun showEditDialog() {
        val data = parentData ?: return
        val child = currentChildData
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_student_emergency, null)
        val dialog = AlertDialog.Builder(requireContext(), CommonR.style.CustomDialog)
            .setView(dialogView)
            .create()

        val etParentFName = dialogView.findViewById<EditText>(R.id.etEditParentFirstName)
        val etParentLName = dialogView.findViewById<EditText>(R.id.etEditParentLastName)
        val etParentEmail = dialogView.findViewById<EditText>(R.id.etEditParentEmail)
        val etParentPhone = dialogView.findViewById<EditText>(R.id.etEditParentPhone)
        
        val etC1Name = dialogView.findViewById<EditText>(R.id.etEditContact1Name)
        val etC1Rel = dialogView.findViewById<EditText>(R.id.etEditContact1Rel)
        val etC1Email = dialogView.findViewById<EditText>(R.id.etEditContact1Email)
        val etC1Phone = dialogView.findViewById<EditText>(R.id.etEditContact1Phone)
        
        val etC2Name = dialogView.findViewById<EditText>(R.id.etEditContact2Name)
        val etC2Rel = dialogView.findViewById<EditText>(R.id.etEditContact2Rel)
        val etC2Email = dialogView.findViewById<EditText>(R.id.etEditContact2Email)
        val etC2Phone = dialogView.findViewById<EditText>(R.id.etEditContact2Phone)

        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancelEdit)
        val btnSave = dialogView.findViewById<Button>(R.id.btnSaveEmergency)

        etParentFName.setText(data["firstName"] as? String ?: "")
        etParentLName.setText(data["lastName"] as? String ?: "")
        etParentEmail.setText(data["email"] as? String ?: "")
        etParentPhone.setText(data["phone"] as? String ?: "")

        @Suppress("UNCHECKED_CAST")
        val contacts = (child?.get("emergencyContacts") ?: data["emergencyContacts"]) as? List<kotlin.collections.Map<String, Any>> ?: emptyList()
        if (contacts.isNotEmpty()) {
            etC1Name.setText(contacts[0]["name"] as? String ?: "")
            etC1Rel.setText(contacts[0]["relationship"] as? String ?: "")
            etC1Email.setText(contacts[0]["email"] as? String ?: "")
            etC1Phone.setText(contacts[0]["phone"] as? String ?: "")
        }
        if (contacts.size > 1) {
            etC2Name.setText(contacts[1]["name"] as? String ?: "")
            etC2Rel.setText(contacts[1]["relationship"] as? String ?: "")
            etC2Email.setText(contacts[1]["email"] as? String ?: "")
            etC2Phone.setText(contacts[1]["phone"] as? String ?: "")
        }

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            val updatedContacts = mutableListOf<kotlin.collections.Map<String, String>>()
            if (etC1Name.text.isNotEmpty()) {
                updatedContacts.add(mapOf(
                    "name" to etC1Name.text.toString(),
                    "relationship" to etC1Rel.text.toString(),
                    "email" to etC1Email.text.toString(),
                    "phone" to etC1Phone.text.toString()
                ))
            }
            if (etC2Name.text.isNotEmpty()) {
                updatedContacts.add(mapOf(
                    "name" to etC2Name.text.toString(),
                    "relationship" to etC2Rel.text.toString(),
                    "email" to etC2Email.text.toString(),
                    "phone" to etC2Phone.text.toString()
                ))
            }
            
            saveEmergencyUpdates(pFName = etParentFName.text.toString(), pLName = etParentLName.text.toString(), pEmail = etParentEmail.text.toString(), pPhone = etParentPhone.text.toString(), updatedContacts = updatedContacts, dialog = dialog)
        }
        dialog.show()
    }

    private fun saveEmergencyUpdates(pFName: String, pLName: String, pEmail: String, pPhone: String, updatedContacts: List<kotlin.collections.Map<String, String>>, dialog: AlertDialog) {
        val uid = auth.currentUser?.uid ?: return
        val docRef = db.collection("parents").document(uid)
        
        val parentUpdates = mapOf("firstName" to pFName, "lastName" to pLName, "email" to pEmail, "phone" to pPhone)

        docRef.update(parentUpdates).addOnSuccessListener {
            if (isFromChildrenList) {
                docRef.get().addOnSuccessListener { document ->
                    @Suppress("UNCHECKED_CAST")
                    val children = document.get("children") as? List<kotlin.collections.Map<String, Any>> ?: return@addOnSuccessListener
                    val newList = children.toMutableList()
                    val index = newList.indexOfFirst { "${it["firstName"]} ${it["lastName"]}" == childName }
                    if (index != -1) {
                        val updatedChild = newList[index].toMutableMap()
                        updatedChild["emergencyContacts"] = updatedContacts
                        newList[index] = updatedChild
                        docRef.update("children", newList).addOnSuccessListener {
                            onUpdateSuccess(pFName, pLName, pEmail, pPhone, updatedContacts, updatedChild, dialog)
                        }
                    }
                }
            } else {
                val updatedChild = currentChildData?.toMutableMap() ?: mutableMapOf()
                updatedChild["emergencyContacts"] = updatedContacts
                docRef.update("child", updatedChild).addOnSuccessListener {
                    onUpdateSuccess(pFName, pLName, pEmail, pPhone, updatedContacts, updatedChild, dialog)
                }
            }
        }.addOnFailureListener {
            if (isAdded) Toast.makeText(context, "Failed to update emergency contacts", Toast.LENGTH_SHORT).show()
        }
    }

    private fun onUpdateSuccess(pFName: String, pLName: String, pEmail: String, pPhone: String, updatedContacts: List<kotlin.collections.Map<String, String>>, updatedChild: kotlin.collections.Map<String, Any>, dialog: AlertDialog) {
        val newData = parentData?.toMutableMap() ?: mutableMapOf()
        newData["firstName"] = pFName
        newData["lastName"] = pLName
        newData["email"] = pEmail
        newData["phone"] = pPhone
        newData["emergencyContacts"] = updatedContacts
        parentData = newData
        currentChildData = updatedChild
        
        displayEmergencyInfo(newData, updatedChild)
        dialog.dismiss()
        if (isAdded) Toast.makeText(context, "Emergency contacts updated", Toast.LENGTH_SHORT).show()
    }
}
