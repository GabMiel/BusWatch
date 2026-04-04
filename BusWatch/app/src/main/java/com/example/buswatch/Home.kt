package com.example.buswatch

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.buswatch.common.R as CommonR

class Home : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.home)

        val btnHomeAccount = findViewById<ImageButton>(R.id.btnHomeAccount)
        val btnHomeSettings = findViewById<ImageButton>(R.id.btnHomeSettings)
        val btnHomeNotification = findViewById<ImageButton>(R.id.btnHomeNotification)
        val rvStudentsHome = findViewById<RecyclerView>(R.id.rvStudentsHome)

        // Mock data for students
        val students = listOf(
            StudentHome("Justin Wilson", "Grade 1", "The Immaculate Mother Academy Inc.", "At Home", CommonR.drawable.yans),
            StudentHome("Emma Wilson", "Grade 3", "The Immaculate Mother Academy Inc.", "On the Bus", CommonR.drawable.yans)
        )

        rvStudentsHome.layoutManager = LinearLayoutManager(this)
        rvStudentsHome.adapter = StudentHomeAdapter(students) { student ->
            val intent = Intent(this, Map::class.java)
            startActivity(intent)
            overridePendingTransition(CommonR.anim.fade_in, CommonR.anim.fade_out)
        }

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
    }
}
