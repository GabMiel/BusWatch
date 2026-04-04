package com.example.buswatch.driver

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.buswatch.common.R as CommonR

class DriverHome : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadHome()
    }

    private fun loadHome() {
        setContentView(R.layout.fragment_driver_home)
        setupStudentList()
        setupBottomNav()
        
        findViewById<TextView>(R.id.btnStartTrip)?.setOnClickListener {
            loadLiveTracking()
        }

        findViewById<TextView>(R.id.tabAfternoon)?.setOnClickListener {
            loadAfternoon()
        }
    }

    private fun loadAfternoon() {
        setContentView(R.layout.fragment_driver_afternoon)
        setupStudentList()
        setupBottomNav()

        findViewById<TextView>(R.id.tabMorning)?.setOnClickListener {
            loadHome()
        }
        
        findViewById<TextView>(R.id.btnStartTrip)?.setOnClickListener {
            loadLiveTracking()
        }
    }

    private fun loadLiveTracking() {
        setContentView(R.layout.fragment_live_tracking)
        
        findViewById<ImageButton>(R.id.btnBackTracking)?.setOnClickListener {
            loadHome()
        }

        findViewById<TextView>(R.id.btnEndTrip)?.setOnClickListener {
            loadHome()
        }
        
        // Setup pickup list (mock data)
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerPickup)
        if (recyclerView != null) {
            recyclerView.layoutManager = LinearLayoutManager(this)
            // Reusing StudentAdapter for simplicity or use a dedicated one if needed
            val students = listOf(
                Student("Justin Wilson", "50 meters away")
            )
            recyclerView.adapter = StudentAdapter(students)
        }
    }

    private fun setupStudentList() {
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerStudents)
        if (recyclerView == null) return

        val students = listOf(
            Student("Justin Wilson", "Grade 1"),
            Student("Sophia Garcia", "Grade 2"),
            Student("Liam Johnson", "Grade 1"),
            Student("Emma Smith", "Grade 3"),
            Student("Noah Brown", "Grade 2"),
            Student("Olivia Davis", "Grade 1")
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = StudentAdapter(students)
    }

    private fun setupBottomNav() {
        val homeNav = findViewById<LinearLayout>(R.id.containerNavHome)
        val accountNav = findViewById<LinearLayout>(R.id.containerNavAccount)
        val settingsNav = findViewById<LinearLayout>(R.id.containerNavSettings)

        homeNav?.setOnClickListener {
            loadHome()
        }

        accountNav?.setOnClickListener {
            setContentView(R.layout.fragment_driver_account)
            setupBottomNav()
            findViewById<ImageButton>(R.id.btnBackAccount)?.setOnClickListener { loadHome() }
        }

        settingsNav?.setOnClickListener {
            loadSettings()
        }
    }

    private fun loadSettings() {
        setContentView(R.layout.fragment_driver_settings)
        setupBottomNav()
        
        findViewById<ImageButton>(R.id.btnBackSettings)?.setOnClickListener { loadHome() }
        
        findViewById<TextView>(R.id.btnLogout)?.setOnClickListener {
            val intent = Intent(this, Class.forName("com.example.buswatch.Login"))
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
}
