package com.example.buswatch

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.example.buswatch.common.R as CommonR

class Signup1 : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.signup1)

        val nextButton = findViewById<Button>(R.id.btnSignup1Next)
        val signinButton = findViewById<Button>(R.id.btnSignup1Signin)

        nextButton.setOnClickListener {
            val intent = Intent(this, Signup2::class.java)
            startActivity(intent)
            overridePendingTransition(CommonR.anim.slide_in_right, CommonR.anim.slide_out_left)
        }

        signinButton.setOnClickListener {
            val intent = Intent(this, Login::class.java)
            startActivity(intent)
            overridePendingTransition(CommonR.anim.stay, CommonR.anim.slide_out_right)
            finish()
        }
    }
}