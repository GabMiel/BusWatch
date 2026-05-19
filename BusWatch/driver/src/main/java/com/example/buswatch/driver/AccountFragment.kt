package com.example.buswatch.driver

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.bumptech.glide.Glide
import com.example.buswatch.common.R as CommonR
import com.example.buswatch.driver.databinding.FragmentDriverAccountBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class AccountFragment : Fragment() {

    private var _binding: FragmentDriverAccountBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: DriverViewModel by activityViewModels()
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDriverAccountBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupBottomNav()
        loadUserData()
    }

    private fun loadUserData() {
        val uid = auth.currentUser?.uid ?: return
        val role = viewModel.userRole.value ?: "Driver"
        val collection = if (role == "Driver") "drivers" else "conductors"
        
        // Hide license number for conductors
        binding.layoutLicenseNumber.visibility = if (role == "Driver") View.VISIBLE else View.GONE
        
        db.collection(collection).document(uid).get().addOnSuccessListener { doc ->
            if (doc.exists()) {
                binding.tvProfileFirstName.text = doc.getString("firstName") ?: "-"
                binding.tvProfileMiddleName.text = doc.getString("middleName") ?: "-"
                binding.tvProfileLastName.text = doc.getString("lastName") ?: "-"
                binding.tvProfileSuffix.text = doc.getString("suffix") ?: "-"
                binding.tvProfileEmail.text = doc.getString("email") ?: "-"
                binding.tvProfilePhone.text = doc.getString("phone") ?: doc.getString("phoneNumber") ?: "-"
                binding.tvProfileLicenseNumber.text = doc.getString("licenseNumber") ?: "-"
                binding.tvProfileLanguage.text = doc.getString("preferredLanguage") ?: "English"
                
                val avatarUrl = if (role == "Driver") {
                    doc.getString("driverAvatar")
                } else {
                    doc.getString("conductorAvatar")
                } ?: ""
                
                if (avatarUrl.isNotEmpty()) {
                    Glide.with(this)
                        .load(avatarUrl)
                        .placeholder(CommonR.drawable.ic_person_placeholder)
                        .circleCrop()
                        .into(binding.imgDriverProfile)
                }
            }
        }
    }

    private fun setupBottomNav() {
        binding.containerNavHome.setOnClickListener { (activity as? DriverHome)?.loadHome() }
        binding.containerNavSettings.setOnClickListener { (activity as? DriverHome)?.loadSettings() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
