package com.example.buswatch

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import com.example.buswatch.common.R as CommonR

class ParentDetails : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.parentdetails)

        val backButton = findViewById<ImageButton>(R.id.btnParentsBack)
        val view1Button = findViewById<ImageButton>(R.id.btnParentsView)
        val view2Button = findViewById<ImageButton>(R.id.btnParentsView2)

        backButton.setOnClickListener {
            finish()
            overridePendingTransition(CommonR.anim.stay, CommonR.anim.slide_out_right)
        }

        view1Button.setOnClickListener {
            val intent = Intent(this, StudentDetailsGeneral::class.java)
            startActivity(intent)
            overridePendingTransition(CommonR.anim.fade_in, CommonR.anim.fade_out)
        }

        view2Button.setOnClickListener {
            val intent = Intent(this, StudentDetailsGeneral::class.java)
            startActivity(intent)
            overridePendingTransition(CommonR.anim.fade_in, CommonR.anim.fade_out)
        }
    }
}