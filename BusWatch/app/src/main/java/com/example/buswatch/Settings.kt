package com.example.buswatch

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.example.buswatch.common.R as CommonR

class Settings : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings)

        val backButton = findViewById<ImageButton>(R.id.btnSettingsSettings)
        backButton.setOnClickListener {
            finish()
        }

        val languageSelector = findViewById<FrameLayout>(R.id.btnSettingsLanguage)
        val tvSelectedLanguage = findViewById<TextView>(R.id.tvSettingsSelectedLanguage)

        languageSelector.setOnClickListener {
            val languages = arrayOf("English", "Filipino")

            val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, languages) {
                override fun isEnabled(position: Int): Boolean {
                    // Disable Filipino (position 1)
                    return position == 0
                }

                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = super.getView(position, convertView, parent)
                    val textView = view.findViewById<TextView>(android.R.id.text1)
                    if (position == 1) {
                        textView.setTextColor(Color.LTGRAY)
                    } else {
                        textView.setTextColor(Color.BLACK)
                    }
                    return view
                }
            }

            val builder = AlertDialog.Builder(this)
            builder.setTitle(getString(CommonR.string.select_language))
            builder.setAdapter(adapter) { _, which ->
                if (which == 0) {
                    tvSelectedLanguage.text = languages[which]
                    tvSelectedLanguage.setTextColor(Color.BLACK)
                }
            }
            builder.show()
        }

        val logoutButton = findViewById<Button>(R.id.btnSettingsLogout)
        logoutButton.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            val intent = Intent(this, Login::class.java)
            // Clear the activity stack so the user can't go back to settings after logging out
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
}
