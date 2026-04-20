package com.example.buswatch.admin.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.buswatch.admin.AdminHome
import com.example.buswatch.admin.R
import com.example.buswatch.admin.UserAdmin
import com.example.buswatch.common.R as CommonR
import com.google.firebase.firestore.FirebaseFirestore

class DriverEditFragment : Fragment() {
    private val db = FirebaseFirestore.getInstance()
    private lateinit var user: UserAdmin

    companion object {
        fun newInstance(user: UserAdmin) = DriverEditFragment().apply {
            this.user = user
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_edit_driver, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI(view)
        loadData(view)
    }

    private fun setupUI(view: View) {
        view.findViewById<ImageButton>(R.id.btnBackEditDriver)?.setOnClickListener { 
            (requireActivity() as? AdminHome)?.loadDrivers()
        }

        view.findViewById<Button>(R.id.btnSaveDriverChanges)?.setOnClickListener {
            saveChanges(view)
        }
    }

    private fun loadData(view: View) {
        db.collection("drivers").document(user.id).get().addOnSuccessListener { doc ->
            if (doc == null || !doc.exists()) return@addOnSuccessListener
            view.findViewById<EditText>(R.id.etFirstName).setText(doc.getString("firstName") ?: "")
            view.findViewById<EditText>(R.id.etMiddleName).setText(doc.getString("middleName") ?: "")
            view.findViewById<EditText>(R.id.etLastName).setText(doc.getString("lastName") ?: "")
            view.findViewById<EditText>(R.id.etEmail).setText(doc.getString("email") ?: "")
            view.findViewById<EditText>(R.id.etPhone).setText(doc.getString("phone") ?: "")
            view.findViewById<EditText>(R.id.etLicenseNumber).setText(doc.getString("licenseNumber") ?: "")
            
            val avatar = doc.getString("driverAvatar") ?: ""
            if (avatar.isNotEmpty()) {
                Glide.with(this).load(avatar).placeholder(CommonR.drawable.ic_person_placeholder).circleCrop().into(view.findViewById(R.id.imgDriver))
            }
        }
    }

    private fun saveChanges(view: View) {
        val updates = hashMapOf<String, Any>(
            "firstName" to view.findViewById<EditText>(R.id.etFirstName).text.toString(),
            "lastName" to view.findViewById<EditText>(R.id.etLastName).text.toString(),
            "email" to view.findViewById<EditText>(R.id.etEmail).text.toString(),
            "phone" to view.findViewById<EditText>(R.id.etPhone).text.toString(),
            "licenseNumber" to view.findViewById<EditText>(R.id.etLicenseNumber).text.toString()
        )
        db.collection("drivers").document(user.id).update(updates).addOnSuccessListener {
            Toast.makeText(requireContext(), "Driver updated", Toast.LENGTH_SHORT).show()
            (requireActivity() as? AdminHome)?.loadDrivers()
        }
    }
}
