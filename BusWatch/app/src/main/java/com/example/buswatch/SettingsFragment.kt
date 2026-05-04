package com.example.buswatch

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.example.buswatch.common.NotificationSender
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

        setupLanguageSelector(view)
        setupPasswordChange(view)
        setupAboutSection(view)
        setupTestNotificationTrigger(view)

        view.findViewById<Button>(R.id.btnLogout).setOnClickListener {
            auth.signOut()
            val intent = Intent(requireContext(), Login::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            requireActivity().finish()
        }

        return view
    }

    private fun setupLanguageSelector(view: View) {
        val languageSelector = view.findViewById<FrameLayout>(R.id.btnSettingsLanguage)
        val tvSelectedLanguage = view.findViewById<TextView>(R.id.tvSettingsSelectedLanguage)
        val uid = auth.currentUser?.uid ?: return

        db.collection("parents").document(uid).get().addOnSuccessListener { document ->
            if (isAdded && document.exists()) {
                val language = document.getString("preferredLanguage") ?: "English"
                tvSelectedLanguage.text = language
            }
        }

        languageSelector.setOnClickListener {
            val languages = arrayOf("English", "Filipino")
            val adapter = object : ArrayAdapter<String>(requireContext(), android.R.layout.simple_list_item_1, languages) {
                override fun isEnabled(position: Int) = position == 0
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val v = super.getView(position, convertView, parent)
                    v.findViewById<TextView>(android.R.id.text1).setTextColor(if (position == 0) Color.BLACK else Color.LTGRAY)
                    return v
                }
            }

            AlertDialog.Builder(requireContext())
                .setTitle("Select Language")
                .setAdapter(adapter) { _, _ ->
                    // Logic to update language if Filipino is ever enabled
                }.show()
        }
    }

    private fun setupPasswordChange(view: View) {
        val etCurrent = view.findViewById<EditText>(R.id.editTextTextPassword2)
        val etNew = view.findViewById<EditText>(R.id.editTextTextPassword3)
        val etConfirm = view.findViewById<EditText>(R.id.editTextTextPassword4)
        val btnChange = view.findViewById<Button>(R.id.btnChangePassword)

        btnChange.setOnClickListener {
            val current = etCurrent.text.toString()
            val new = etNew.text.toString()
            val confirm = etConfirm.text.toString()

            if (current.isEmpty() || new.isEmpty() || confirm.isEmpty()) {
                Toast.makeText(context, "Please fill all password fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (new != confirm) {
                Toast.makeText(context, "New passwords do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (new.length < 6) {
                Toast.makeText(context, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val user = auth.currentUser
            val credential = EmailAuthProvider.getCredential(user?.email!!, current)

            user.reauthenticate(credential).addOnSuccessListener {
                user.updatePassword(new).addOnSuccessListener {
                    Toast.makeText(context, "Password updated successfully", Toast.LENGTH_SHORT).show()
                    etCurrent.text.clear()
                    etNew.text.clear()
                    etConfirm.text.clear()
                }.addOnFailureListener { e ->
                    Toast.makeText(context, "Update failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }.addOnFailureListener {
                Toast.makeText(context, "Current password incorrect", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupAboutSection(view: View) {
        val layoutTerms = view.findViewById<View>(R.id.layoutTerms)
        val layoutPrivacy = view.findViewById<View>(R.id.layoutPrivacy)

        layoutTerms?.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out, android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.parentFragmentContainer, TermsConditionsFragment())
                .addToBackStack(null)
                .commit()
        }

        layoutPrivacy?.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out, android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.parentFragmentContainer, PrivacyPolicyFragment())
                .addToBackStack(null)
                .commit()
        }
    }

    private fun showInfoDialog(title: String, htmlContent: String) {
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage(Html.fromHtml(htmlContent, Html.FROM_HTML_MODE_COMPACT))
            .setPositiveButton(getString(CommonR.string.close), null)
            .show()
    }

    private fun setupTestNotificationTrigger(view: View) {
        val testTrigger = View.OnLongClickListener {
            sendTestNotification()
            true
        }
        view.findViewById<View>(R.id.textView23)?.setOnLongClickListener(testTrigger)
        view.findViewById<View>(R.id.imageView15)?.setOnLongClickListener(testTrigger)
    }

    private fun sendTestNotification() {
        val uid = auth.currentUser?.uid ?: return
        val title = "Test Notification"
        val message = "This is a catered test message to verify your notification system."
        val testData = hashMapOf(
            "title" to title,
            "message" to message,
            "timestamp" to FieldValue.serverTimestamp(),
            "isRead" to false,
            "type" to "trip_start"
        )

        db.collection("parents").document(uid).collection("notifications").add(testData)
            .addOnSuccessListener {
                if (isAdded) {
                    Toast.makeText(requireContext(), "Test notification sent!", Toast.LENGTH_LONG).show()
                    NotificationSender.sendNotification(uid, title, message)
                }
            }
            .addOnFailureListener { e ->
                if (isAdded) Toast.makeText(requireContext(), "Permission Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
