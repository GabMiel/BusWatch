package com.example.buswatch

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import com.example.buswatch.common.R as CommonR

class StudentDetailsEmergency : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.studentdetails_emergency)

        val backButton = findViewById<ImageButton>(R.id.btnEmergencyBack)
        val generalButton = findViewById<Button>(R.id.btnEmergencyGeneral)
        val medicalButton = findViewById<Button>(R.id.btnEmergencyMedical)

        backButton.setOnClickListener {
            val intent = Intent(this, ParentDetails::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
            overridePendingTransition(CommonR.anim.stay, CommonR.anim.slide_out_right)
        }

        generalButton.setOnClickListener {
            val intent = Intent(this, StudentDetailsGeneral::class.java)
            startActivity(intent)
            overridePendingTransition(CommonR.anim.slide_in_right, CommonR.anim.slide_out_left)
            finish()
        }

        medicalButton.setOnClickListener {
            val intent = Intent(this, StudentDetailsMedical::class.java)
            startActivity(intent)
            overridePendingTransition(CommonR.anim.slide_in_right, CommonR.anim.slide_out_left)
            finish()
        }
    }
}