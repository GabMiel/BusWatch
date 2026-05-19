package com.example.buswatch.driver

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.buswatch.driver.databinding.FragmentDriverSettingsBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class SettingsFragment : Fragment() {

    private var _binding: FragmentDriverSettingsBinding? = null
    private val binding get() = _binding!!
    
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDriverSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupBottomNav()
        setupSupportSection()
        
        binding.btnLogout.setOnClickListener {
            val prefs = requireContext().getSharedPreferences("BusWatchPrefs", Context.MODE_PRIVATE)
            val isDemo = prefs.getBoolean("is_demo", false)
            
            auth.signOut()
            
            try {
                val targetClass = if (isDemo) {
                    Class.forName("com.example.buswatch.DemoLogin")
                } else {
                    Class.forName("com.example.buswatch.Login")
                }
                val intent = Intent(requireContext(), targetClass)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                requireActivity().finish()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun setupSupportSection() {
        binding.layoutFAQ.setOnClickListener {
            try {
                val faqFragmentClass = Class.forName("com.example.buswatch.FAQFragment")
                val faqFragment = faqFragmentClass.getDeclaredConstructor().newInstance() as Fragment
                parentFragmentManager.beginTransaction()
                    .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out, android.R.anim.fade_in, android.R.anim.fade_out)
                    .replace(R.id.fragment_container, faqFragment)
                    .addToBackStack(null)
                    .commit()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        binding.layoutTerms.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out, android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.fragment_container, TermsConditionsFragment())
                .addToBackStack(null)
                .commit()
        }

        binding.layoutPrivacy.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out, android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.fragment_container, PrivacyPolicyFragment())
                .addToBackStack(null)
                .commit()
        }
    }

    private fun setupBottomNav() {
        binding.containerNavHome.setOnClickListener { (activity as? DriverHome)?.loadHome() }
        binding.containerNavAccount.setOnClickListener { (activity as? DriverHome)?.loadAccount() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
