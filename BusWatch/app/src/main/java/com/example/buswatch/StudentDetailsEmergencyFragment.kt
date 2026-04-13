package com.example.buswatch

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.buswatch.common.R as CommonR
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class StudentDetailsEmergencyFragment : Fragment() {
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var childName: String? = null
    private var parentData: kotlin.collections.Map<String, Any>? = null
    private var isFromChildrenList: Boolean = false
    private var currentChildData: kotlin.collections.Map<String, Any>? = null
    private var studentListener: ListenerRegistration? = null

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

        studentListener?.remove()
        studentListener = db.collection("parents").document(uid)
            .addSnapshotListener { document, error ->
                if (error != null) return@addSnapshotListener
                if (isAdded && document != null && document.exists()) {
                    parentData = document.data
                    
                    @Suppress("UNCHECKED_CAST")
                    val childMap = document.get("child") as? kotlin.collections.Map<String, Any>
                    @Suppress("UNCHECKED_CAST")
                    val childrenList = document.get("children") as? List<kotlin.collections.Map<String, Any>>
                    
                    var foundChild: kotlin.collections.Map<String, Any>? = null
                    
                    if (childMap != null) {
                        val fullName = "${childMap["firstName"]} ${childMap["lastName"]}".trim()
                        if (childName == null || fullName == childName) {
                            foundChild = childMap
                            isFromChildrenList = false
                        }
                    }
                    
                    if (foundChild == null && childrenList != null) {
                        foundChild = childrenList.find { 
                            "${it["firstName"]} ${it["lastName"]}".trim() == childName 
                        }
                        if (foundChild != null) isFromChildrenList = true
                    }
                    currentChildData = foundChild

                    displayEmergencyInfo(document.data ?: emptyMap(), foundChild)
                }
            }
    }

    private fun displayEmergencyInfo(data: kotlin.collections.Map<String, Any>, child: kotlin.collections.Map<String, Any>?) {
        val view = view ?: return
        val contactList = mutableListOf<EmergencyContact>()
        
        // Use profile data if available, otherwise root data
        @Suppress("UNCHECKED_CAST")
        val profile = data["profile"] as? kotlin.collections.Map<String, Any>
        val pFName = profile?.get("firstName") as? String ?: data["firstName"] as? String ?: "---"
        val pLName = profile?.get("lastName") as? String ?: data["lastName"] as? String ?: ""
        val pPhone = profile?.get("phone") as? String ?: data["phone"] as? String ?: "---"
        val pEmail = profile?.get("email") as? String ?: data["email"] as? String ?: "---"
        
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

        dialogView.findViewById<ImageButton>(R.id.btnDismissEditEmergency).setOnClickListener { dialog.dismiss() }
        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            val updatedContacts = mutableListOf<kotlin.collections.Map<String, String>>()
            if (etC1Name.text.isNotEmpty()) {
                updatedContacts.add(mapOf(
                    "name" to etC1Name.text.toString().trim(),
                    "relationship" to etC1Rel.text.toString().trim(),
                    "email" to etC1Email.text.toString().trim(),
                    "phone" to etC1Phone.text.toString().trim()
                ))
            }
            if (etC2Name.text.isNotEmpty()) {
                updatedContacts.add(mapOf(
                    "name" to etC2Name.text.toString().trim(),
                    "relationship" to etC2Rel.text.toString().trim(),
                    "email" to etC2Email.text.toString().trim(),
                    "phone" to etC2Phone.text.toString().trim()
                ))
            }
            
            saveEmergencyUpdates(updatedContacts, dialog)
        }
        dialog.show()
    }

    private fun saveEmergencyUpdates(updatedContacts: List<kotlin.collections.Map<String, String>>, dialog: AlertDialog) {
        val uid = auth.currentUser?.uid ?: return
        val docRef = db.collection("parents").document(uid)
        
        if (isFromChildrenList) {
            docRef.get().addOnSuccessListener { document ->
                @Suppress("UNCHECKED_CAST")
                val children = document.get("children") as? List<kotlin.collections.Map<String, Any>> ?: return@addOnSuccessListener
                val newList = children.toMutableList()
                val index = newList.indexOfFirst { "${it["firstName"]} ${it["lastName"]}".trim() == childName }
                if (index != -1) {
                    val updatedChild = newList[index].toMutableMap()
                    updatedChild["emergencyContacts"] = updatedContacts
                    newList[index] = updatedChild
                    docRef.update("children", newList).addOnSuccessListener {
                        dialog.dismiss()
                        Toast.makeText(context, "Emergency contacts updated", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } else {
            val updatedChild = currentChildData?.toMutableMap() ?: mutableMapOf()
            updatedChild["emergencyContacts"] = updatedContacts
            docRef.update("child", updatedChild).addOnSuccessListener {
                dialog.dismiss()
                Toast.makeText(context, "Emergency contacts updated", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        studentListener?.remove()
    }
}
