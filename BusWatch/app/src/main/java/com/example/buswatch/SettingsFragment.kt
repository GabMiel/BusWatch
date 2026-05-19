package com.example.buswatch

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Switch
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

    private lateinit var switchBusDeparture: Switch
    private lateinit var switchBusArrival: Switch
    private lateinit var switchChildBoarding: Switch
    private lateinit var switchBusNearStop: Switch

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        switchBusDeparture = view.findViewById(R.id.switchBusDeparture)
        switchBusArrival = view.findViewById(R.id.switchBusArrival)
        switchChildBoarding = view.findViewById(R.id.switchChildBoarding)
        switchBusNearStop = view.findViewById(R.id.switchBusNearStop)

        setupLanguageSelector(view)
        setupPasswordChange(view)
        setupSupportSection(view)
        setupNotificationSwitches()
        setupTestNotificationTrigger(view)
        setupDeleteAccount(view)

        view.findViewById<Button>(R.id.btnLogout).setOnClickListener {
            val prefs = requireContext().getSharedPreferences("BusWatchPrefs", Context.MODE_PRIVATE)
            val isDemo = prefs.getBoolean("is_demo", false)
            
            auth.signOut()
            
            val intent = if (isDemo) {
                Intent(requireContext(), DemoLogin::class.java)
            } else {
                Intent(requireContext(), Login::class.java)
            }

            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            requireActivity().finish()
        }

        return view
    }

    private fun setupNotificationSwitches() {
        val uid = auth.currentUser?.uid ?: return
        val docRef = db.collection("parents").document(uid)

        // Load existing preferences
        docRef.get().addOnSuccessListener { document ->
            if (isAdded && document.exists()) {
                val prefs = document.get("notificationPreferences") as? kotlin.collections.Map<*, *>
                
                switchBusDeparture.isChecked = (prefs?.get("busDeparture") as? Boolean) ?: true
                switchBusArrival.isChecked = (prefs?.get("busArrival") as? Boolean) ?: true
                switchChildBoarding.isChecked = (prefs?.get("childBoarding") as? Boolean) ?: true
                switchBusNearStop.isChecked = (prefs?.get("busNearStop") as? Boolean) ?: true
            }
        }

        // Setup listeners to save changes
        val changeListener = View.OnClickListener {
            val updatedPrefs = mapOf(
                "busDeparture" to switchBusDeparture.isChecked,
                "busArrival" to switchBusArrival.isChecked,
                "childBoarding" to switchChildBoarding.isChecked,
                "busNearStop" to switchBusNearStop.isChecked
            )
            docRef.update("notificationPreferences", updatedPrefs)
        }

        switchBusDeparture.setOnClickListener(changeListener)
        switchBusArrival.setOnClickListener(changeListener)
        switchChildBoarding.setOnClickListener(changeListener)
        switchBusNearStop.setOnClickListener(changeListener)
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

    private fun setupDeleteAccount(view: View) {
        view.findViewById<TextView>(R.id.btnDeleteAccount).setOnClickListener {
            val prefs = requireContext().getSharedPreferences("BusWatchPrefs", Context.MODE_PRIVATE)
            val isDemo = prefs.getBoolean("is_demo", false)
            
            if (isDemo) {
                Toast.makeText(requireContext(), R.string.demo_account_delete_error, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            showDeletionAgreementDialog()
        }
    }

    private fun showDeletionAgreementDialog() {
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_account_agreement_title)
            .setMessage(getString(R.string.delete_account_agreement_msg, 10))
            .setCancelable(false)
            .setPositiveButton("AGREE", null)
            .setNegativeButton(CommonR.string.cancel_caps, null)
            .create()

        dialog.setOnShowListener {
            val agreeBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            agreeBtn.isEnabled = false
            agreeBtn.setTextColor(Color.GRAY)

            val timer = object : CountDownTimer(10000, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    val secondsLeft = (millisUntilFinished / 1000) + 1
                    dialog.setMessage(getString(R.string.delete_account_agreement_msg, secondsLeft))
                }

                override fun onFinish() {
                    dialog.setMessage(getString(R.string.delete_account_ready))
                    agreeBtn.isEnabled = true
                    agreeBtn.setTextColor(Color.parseColor("#008577"))
                }
            }.start()

            agreeBtn.setOnClickListener {
                dialog.dismiss()
                showFinalDeleteConfirmationDialog()
            }

            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                timer.cancel()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun showFinalDeleteConfirmationDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_delete_account, null)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvDeleteTitle)
        val tvMsg = dialogView.findViewById<TextView>(R.id.tvDeleteTimerMsg)
        val etConfirm = dialogView.findViewById<EditText>(R.id.etDeleteConfirm)
        
        tvTitle.text = getString(R.string.delete_account_final_title)
        tvMsg.text = getString(R.string.delete_account_final_msg)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .setPositiveButton("CONFIRM", null)
            .setNegativeButton(CommonR.string.cancel_caps, null)
            .create()

        dialog.setOnShowListener {
            val confirmBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            confirmBtn.isEnabled = false
            confirmBtn.setTextColor(Color.GRAY)

            etConfirm.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (s?.toString()?.trim() == "DELETE") {
                        confirmBtn.isEnabled = true
                        confirmBtn.setTextColor(Color.RED)
                    } else {
                        confirmBtn.isEnabled = false
                        confirmBtn.setTextColor(Color.GRAY)
                    }
                }
                override fun afterTextChanged(s: Editable?) {}
            })

            confirmBtn.setOnClickListener {
                deleteAccountPermanently()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun deleteAccountPermanently() {
        val user = auth.currentUser ?: return
        val uid = user.uid

        // 1. Delete Firestore Data
        db.collection("parents").document(uid).delete().addOnCompleteListener {
            // 2. Delete Auth User
            user.delete().addOnSuccessListener {
                if (isAdded) {
                    Toast.makeText(requireContext(), R.string.account_deleted_success, Toast.LENGTH_LONG).show()
                    val intent = Intent(requireContext(), Login::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    requireActivity().finish()
                }
            }.addOnFailureListener { e ->
                if (isAdded) Toast.makeText(requireContext(), "Auth Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupSupportSection(view: View) {
        val layoutFAQ = view.findViewById<View>(R.id.layoutFAQ)
        val layoutTerms = view.findViewById<View>(R.id.layoutTerms)
        val layoutPrivacy = view.findViewById<View>(R.id.layoutPrivacy)

        layoutFAQ?.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out, android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.parentFragmentContainer, FAQFragment())
                .addToBackStack(null)
                .commit()
        }

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

    private fun setupTestNotificationTrigger(view: View) {
        val testTrigger = View.OnLongClickListener {
            sendTestNotification()
            true
        }
        view.findViewById<View>(R.id.textView21)?.setOnLongClickListener(testTrigger)
        view.findViewById<View>(R.id.imageView12)?.setOnLongClickListener(testTrigger)
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
