package com.example.buswatch

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import com.example.buswatch.common.R as CommonR

class Home : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.home)

        val btnHomeAccount = findViewById<ImageButton>(R.id.btnHomeAccount)
        val btnHomeSettings = findViewById<ImageButton>(R.id.btnHomeSettings)
        val btnHomeNotification = findViewById<ImageButton>(R.id.btnHomeNotification)
        
        val box1 = findViewById<View>(R.id.view3)
        val box2 = findViewById<View>(R.id.view5)
        val box3 = findViewById<View>(R.id.view6)

        btnHomeAccount.setOnClickListener {
            val intent = Intent(this, ParentDetails::class.java)
            startActivity(intent)
            overridePendingTransition(CommonR.anim.slide_in_right, CommonR.anim.slide_out_left)
        }

        btnHomeSettings.setOnClickListener {
            val intent = Intent(this, Settings::class.java)
            startActivity(intent)
            overridePendingTransition(CommonR.anim.zoom_in, CommonR.anim.fade_out)
        }

        btnHomeNotification.setOnClickListener {
            val intent = Intent(this, Notification::class.java)
            startActivity(intent)
            overridePendingTransition(CommonR.anim.slide_in_bottom, CommonR.anim.stay)
        }

        val navigateToMap = View.OnClickListener {
            val intent = Intent(this, Map::class.java)
            startActivity(intent)
            overridePendingTransition(CommonR.anim.fade_in, CommonR.anim.fade_out)
        }

        box1.setOnClickListener(navigateToMap)
        box2.setOnClickListener(navigateToMap)
        box3.setOnClickListener(navigateToMap)
    }
}