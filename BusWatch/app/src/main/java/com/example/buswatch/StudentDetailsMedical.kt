package com.example.buswatch

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity

class StudentDetailsMedical : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.studentdetails_medical)

        val backButton = findViewById<ImageButton>(R.id.btnMedicalBack)
        val generalButton = findViewById<Button>(R.id.btnMedicalGeneral)
        val emergencyButton = findViewById<Button>(R.id.btnMedicalEmergency)

        backButton.setOnClickListener {
            val intent = Intent(this, ParentDetails::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
            overridePendingTransition(R.anim.stay, R.anim.slide_out_right)
        }

        generalButton.setOnClickListener {
            val intent = Intent(this, StudentDetailsGeneral::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            finish()
        }

        emergencyButton.setOnClickListener {
            val intent = Intent(this, StudentDetailsEmergency::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            finish()
        }
    }
}