package com.example.buswatch

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import com.example.buswatch.common.R as CommonR

class StudentDetailsGeneral : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.studentdetails_general)

        val backButton = findViewById<ImageButton>(R.id.btnGeneralBack)
        val medicalButton = findViewById<Button>(R.id.btnGeneralMedical)
        val emergencyButton = findViewById<Button>(R.id.btnGeneralEmergency)

        backButton.setOnClickListener {
            val intent = Intent(this, ParentDetails::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
            overridePendingTransition(CommonR.anim.stay, CommonR.anim.slide_out_right)
        }

        medicalButton.setOnClickListener {
            val intent = Intent(this, StudentDetailsMedical::class.java)
            startActivity(intent)
            overridePendingTransition(CommonR.anim.slide_in_right, CommonR.anim.slide_out_left)
        }

        emergencyButton.setOnClickListener {
            val intent = Intent(this, StudentDetailsEmergency::class.java)
            startActivity(intent)
            overridePendingTransition(CommonR.anim.slide_in_right, CommonR.anim.slide_out_left)
        }
    }
}