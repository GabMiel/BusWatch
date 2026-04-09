package com.example.buswatch

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.buswatch.common.R as CommonR

class SettingsFragment : Fragment() {
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val languageSelector = view.findViewById<FrameLayout>(R.id.btnSettingsLanguage)
        val tvSelectedLanguage = view.findViewById<TextView>(R.id.tvSettingsSelectedLanguage)

        // Load current language from Firestore
        val uid = auth.currentUser?.uid
        if (uid != null) {
            db.collection("parents").document(uid).get()
                .addOnSuccessListener { document ->
                    if (isAdded && document != null && document.exists()) {
                        val language = document.getString("preferredLanguage") ?: "English"
                        tvSelectedLanguage.text = if (language.equals("ENGLISH", ignoreCase = true)) "English" else language
                    }
                }
        }

        languageSelector.setOnClickListener {
            val languages = arrayOf("English", "Filipino")

            val adapter = object : ArrayAdapter<String>(requireContext(), android.R.layout.simple_list_item_1, languages) {
                override fun isEnabled(position: Int): Boolean {
                    return position == 0 // Disable Filipino
                }

                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val v = super.getView(position, convertView, parent)
                    val textView = v.findViewById<TextView>(android.R.id.text1)
                    if (position == 1) {
                        textView.setTextColor(Color.LTGRAY)
                    } else {
                        textView.setTextColor(Color.BLACK)
                    }
                    return v
                }
            }

            AlertDialog.Builder(requireContext())
                .setTitle(getString(CommonR.string.select_language))
                .setAdapter(adapter) { _, which ->
                    if (which == 0) {
                        val newLanguage = languages[which]
                        tvSelectedLanguage.text = newLanguage
                        tvSelectedLanguage.setTextColor(Color.BLACK)
                        
                        if (uid != null) {
                            db.collection("parents").document(uid).update("preferredLanguage", newLanguage)
                                .addOnSuccessListener {
                                    if (isAdded) Toast.makeText(requireContext(), "Language preference updated to $newLanguage", Toast.LENGTH_SHORT).show()
                                }
                        }
                    }
                }
                .show()
        }

        val logoutButton = view.findViewById<Button>(R.id.btnSettingsLogout)
        logoutButton.setOnClickListener {
            auth.signOut()
            val intent = Intent(requireContext(), Login::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            requireActivity().finish()
        }

        return view
    }
}
