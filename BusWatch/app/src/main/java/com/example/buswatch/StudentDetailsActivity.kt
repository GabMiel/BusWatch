package com.example.buswatch

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.buswatch.common.R as CommonR

class StudentDetailsActivity : AppCompatActivity() {
    private var childName: String? = null
    private lateinit var btnGeneral: Button
    private lateinit var btnMedical: Button
    private lateinit var btnEmergency: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_details)

        childName = intent.getStringExtra("childName")
        
        val tvHeaderName = findViewById<TextView>(R.id.tvStudentHeaderName)
        tvHeaderName.text = childName ?: "Student"

        val backButton = findViewById<ImageButton>(R.id.btnStudentDetailsBack)
        backButton.setOnClickListener { finish() }

        btnGeneral = findViewById(R.id.btnTabGeneral)
        btnMedical = findViewById(R.id.btnTabMedical)
        btnEmergency = findViewById(R.id.btnTabEmergency)

        setupTabs()

        // Default fragment
        if (savedInstanceState == null) {
            showFragment(StudentDetailsGeneralFragment.newInstance(childName))
            updateTabUI(btnGeneral)
        }
    }

    private fun setupTabs() {
        btnGeneral.setOnClickListener {
            showFragment(StudentDetailsGeneralFragment.newInstance(childName))
            updateTabUI(btnGeneral)
        }
        btnMedical.setOnClickListener {
            showFragment(StudentDetailsMedicalFragment.newInstance(childName))
            updateTabUI(btnMedical)
        }
        btnEmergency.setOnClickListener {
            showFragment(StudentDetailsEmergencyFragment.newInstance(childName))
            updateTabUI(btnEmergency)
        }
    }

    private fun showFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.studentDetailsFragmentContainer, fragment)
            .commit()
    }

    private fun updateTabUI(selectedButton: Button) {
        val buttons = listOf(btnGeneral, btnMedical, btnEmergency)
        buttons.forEach { button ->
            if (button == selectedButton) {
                button.setBackgroundResource(CommonR.drawable.rectangle_shape)
                button.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FEBE1E"))
            } else {
                button.setBackgroundResource(android.R.color.transparent)
                button.backgroundTintList = null
            }
        }
    }
}
