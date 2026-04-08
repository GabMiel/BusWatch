package com.example.buswatch

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.buswatch.common.R as CommonR
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Calendar
import java.util.Locale

class Home : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var progressBar: ProgressBar
    private lateinit var tvNoStudents: TextView
    private lateinit var rvStudentsHome: RecyclerView
    private lateinit var btnPickUp: TextView
    private lateinit var btnHomeAccount: ImageButton
    
    private var parentStatus: String = "pending"
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var timeRunnable: Runnable

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.home)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        btnHomeAccount = findViewById(R.id.btnHomeAccount)
        val btnHomeSettings = findViewById<ImageButton>(R.id.btnHomeSettings)
        val btnHomeNotification = findViewById<ImageButton>(R.id.btnHomeNotification)
        rvStudentsHome = findViewById(R.id.rvStudentsHome)
        progressBar = findViewById(R.id.progressBarHome)
        tvNoStudents = findViewById(R.id.tvNoStudents)
        btnPickUp = findViewById(R.id.btnPickUp)
        
        val tvGreeting = findViewById<TextView>(R.id.textView90)
        val tvTime = findViewById<TextView>(R.id.textView91)

        // Setup real-time clock
        setupRealTimeClock(tvTime)

        // Set greeting based on time of day
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val greetingPrefix = when (hour) {
            in 0..11 -> getString(CommonR.string.good_morning_driver).substringBefore(",") // Reusing "Good morning"
            in 12..16 -> "Good Afternoon"
            else -> "Good Evening"
        }

        fetchUserData(greetingPrefix, tvGreeting)

        btnPickUp.setOnClickListener {
            if (parentStatus.lowercase() == "approved") {
                val adapter = rvStudentsHome.adapter as? StudentHomeAdapter
                if (adapter != null) {
                    // Update all students' status to "Heading to Stop"
                    val currentStudents = adapter.getStudents()
                    val headingToStopText = getString(CommonR.string.status_heading_to_stop)
                    val updatedStudents = currentStudents.map { it.copy(status = headingToStopText) }
                    
                    adapter.isInteractable = true
                    adapter.updateStudents(updatedStudents)

                    Toast.makeText(this, "Tracking enabled. Select a student card.", Toast.LENGTH_SHORT).show()
                }
            } else {
                showApprovalPendingDialog()
            }
        }

        btnHomeAccount.setOnClickListener {
            val intent = Intent(this, ParentDetails::class.java)
            startActivity(intent)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, CommonR.anim.slide_in_right, CommonR.anim.slide_out_left)
            } else {
                @Suppress("DEPRECATION")
                overridePendingTransition(CommonR.anim.slide_in_right, CommonR.anim.slide_out_left)
            }
        }

        btnHomeSettings.setOnClickListener {
            val intent = Intent(this, Settings::class.java)
            startActivity(intent)
        }

        btnHomeNotification.setOnClickListener {
            val intent = Intent(this, Notification::class.java)
            startActivity(intent)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, CommonR.anim.slide_in_bottom, CommonR.anim.stay)
            } else {
                @Suppress("DEPRECATION")
                overridePendingTransition(CommonR.anim.slide_in_bottom, CommonR.anim.stay)
            }
        }
    }

    private fun setupRealTimeClock(tvTime: TextView) {
        timeRunnable = object : Runnable {
            override fun run() {
                val calendar = Calendar.getInstance()
                val hour = calendar.get(Calendar.HOUR_OF_DAY)
                val minute = calendar.get(Calendar.MINUTE)
                val amPm = if (calendar.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"
                val formattedHour = if (hour % 12 == 0) 12 else hour % 12
                tvTime.text = String.format(Locale.getDefault(), "%d:%02d %s", formattedHour, minute, amPm)
                
                // Update every minute, syncing with the start of the next minute
                val seconds = calendar.get(Calendar.SECOND)
                handler.postDelayed(this, (60 - seconds) * 1000L)
            }
        }
        handler.post(timeRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(timeRunnable)
    }

    private fun fetchUserData(greetingPrefix: String, tvGreeting: TextView) {
        val currentUser = auth.currentUser ?: return

        progressBar.visibility = View.VISIBLE
        
        db.collection("parents").document(currentUser.uid).get()
            .addOnSuccessListener { document ->
                progressBar.visibility = View.GONE
                if (document != null && document.exists()) {
                    val firstName = document.getString("firstName") ?: "User"
                    val greeting = "$greetingPrefix, $firstName!"
                    tvGreeting.text = greeting
                    
                    // Parent Avatar
                    val parentAvatarUrl = document.getString("avatarUrl")
                    if (!parentAvatarUrl.isNullOrEmpty()) {
                        Glide.with(this).load(parentAvatarUrl).placeholder(CommonR.drawable.user).circleCrop().into(btnHomeAccount)
                    }

                    // Fetch parent's approval status
                    parentStatus = document.getString("status") ?: "pending"

                    val studentList = mutableListOf<StudentHome>()
                    val parentAddress = document.getString("address") ?: getString(CommonR.string.placeholder_hyphen)

                    // 1. Check Primary Child
                    @Suppress("UNCHECKED_CAST")
                    val childMap = document.get("child") as? kotlin.collections.Map<String, Any>
                    if (childMap != null) {
                        studentList.add(mapToStudentHome("primary", childMap, parentAddress))
                    }

                    // 2. Check Children List
                    @Suppress("UNCHECKED_CAST")
                    val childrenList = document.get("children") as? List<kotlin.collections.Map<String, Any>>
                    childrenList?.forEachIndexed { index, map ->
                        studentList.add(mapToStudentHome(index.toString(), map, parentAddress))
                    }

                    // Visibility logic for Pick Up button
                    if (studentList.isEmpty()) {
                        btnPickUp.visibility = View.GONE
                    } else {
                        btnPickUp.visibility = View.VISIBLE
                    }

                    setupRecyclerView(studentList)
                }
            }
            .addOnFailureListener {
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Error fetching data", Toast.LENGTH_SHORT).show()
            }
    }

    private fun mapToStudentHome(id: String, map: kotlin.collections.Map<String, Any>, parentAddress: String): StudentHome {
        val fName = map["firstName"] as? String ?: ""
        val lName = map["lastName"] as? String ?: ""
        return StudentHome(
            id = id,
            name = "$fName $lName".trim(),
            grade = map["grade"] as? String ?: getString(CommonR.string.placeholder_hyphen),
            school = map["school"] as? String ?: getString(CommonR.string.the_immaculate_mother_academy_inc),
            status = map["status"] as? String ?: getString(CommonR.string.status_at_home),
            avatarResId = CommonR.drawable.user,
            avatarUrl = map["avatarUrl"] as? String,
            stop = map["address"] as? String ?: parentAddress,
            rideOption = map["rideOption"] as? String ?: "Round Trip"
        )
    }

    private fun setupRecyclerView(students: List<StudentHome>) {
        if (students.isEmpty()) {
            tvNoStudents.visibility = View.VISIBLE
            rvStudentsHome.visibility = View.GONE
        } else {
            tvNoStudents.visibility = View.GONE
            rvStudentsHome.visibility = View.VISIBLE
            rvStudentsHome.layoutManager = LinearLayoutManager(this)
            rvStudentsHome.adapter = StudentHomeAdapter(students) { student ->
                // This block executes when a card is clicked (only possible if isInteractable is true)
                val intent = Intent(this, Map::class.java)
                intent.putExtra("childName", student.name)
                startActivity(intent)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, CommonR.anim.fade_in, CommonR.anim.fade_out)
                } else {
                    @Suppress("DEPRECATION")
                    overridePendingTransition(CommonR.anim.fade_in, CommonR.anim.fade_out)
                }
            }
        }
    }
    
    private fun showApprovalPendingDialog() {
        AlertDialog.Builder(this)
            .setTitle("Account Pending")
            .setMessage("Your account is currently waiting for admin approval. You will be able to track your child once your account is approved.")
            .setPositiveButton("OK", null)
            .show()
    }
}
