package com.example.buswatch

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.buswatch.common.R as CommonR
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Calendar
import java.util.Locale
import kotlin.collections.Map as KMap

class HomeFragment : Fragment() {
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var progressBar: ProgressBar
    private lateinit var tvNoStudents: TextView
    private lateinit var rvStudentsHome: RecyclerView
    private lateinit var btnPickUp: TextView
    
    private var parentStatus: String = "pending"
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var timeRunnable: Runnable

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val btnHomeNotification = view.findViewById<ImageButton>(R.id.btnHomeNotification)
        rvStudentsHome = view.findViewById(R.id.rvStudentsHome)
        progressBar = view.findViewById(R.id.progressBarHome)
        tvNoStudents = view.findViewById(R.id.tvNoStudents)
        btnPickUp = view.findViewById(R.id.btnPickUp)
        
        val tvGreeting = view.findViewById<TextView>(R.id.textView90)
        val tvTime = view.findViewById<TextView>(R.id.textView91)

        setupRealTimeClock(tvTime)

        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val greetingPrefix = when (hour) {
            in 0..11 -> getString(CommonR.string.good_morning_driver).substringBefore(",")
            in 12..16 -> "Good Afternoon"
            else -> "Good Evening"
        }

        fetchUserData(greetingPrefix, tvGreeting)

        btnPickUp.setOnClickListener {
            if (parentStatus.lowercase() == "approved") {
                val adapter = rvStudentsHome.adapter as? StudentHomeAdapter
                if (adapter != null) {
                    val currentStudents = adapter.getStudents()
                    val headingToStopText = getString(CommonR.string.status_heading_to_stop)
                    val updatedStudents = currentStudents.map { it.copy(status = headingToStopText) }
                    
                    adapter.isInteractable = true
                    adapter.updateStudents(updatedStudents)

                    Toast.makeText(requireContext(), "Tracking enabled. Select a student card.", Toast.LENGTH_SHORT).show()
                }
            } else {
                showApprovalPendingDialog()
            }
        }

        btnHomeNotification.setOnClickListener {
            val intent = Intent(requireContext(), Notification::class.java)
            startActivity(intent)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                requireActivity().overrideActivityTransition(AppCompatActivity.OVERRIDE_TRANSITION_OPEN, CommonR.anim.slide_in_bottom, CommonR.anim.stay)
            } else {
                @Suppress("DEPRECATION")
                requireActivity().overridePendingTransition(CommonR.anim.slide_in_bottom, CommonR.anim.stay)
            }
        }

        return view
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
                
                val seconds = calendar.get(Calendar.SECOND)
                handler.postDelayed(this, (60 - seconds) * 1000L)
            }
        }
        handler.post(timeRunnable)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(timeRunnable)
    }

    private fun fetchUserData(greetingPrefix: String, tvGreeting: TextView) {
        val currentUser = auth.currentUser ?: return

        progressBar.visibility = View.VISIBLE
        
        db.collection("parents").document(currentUser.uid).get()
            .addOnSuccessListener { document ->
                if (!isAdded) return@addOnSuccessListener
                progressBar.visibility = View.GONE
                if (document != null && document.exists()) {
                    val firstName = document.getString("firstName") ?: "User"
                    val greeting = "$greetingPrefix, $firstName!"
                    tvGreeting.text = greeting
                    
                    parentStatus = document.getString("status") ?: "pending"

                    val studentList = mutableListOf<StudentHome>()
                    val parentAddress = document.getString("address") ?: getString(CommonR.string.placeholder_hyphen)

                    @Suppress("UNCHECKED_CAST")
                    val childMap = document.get("child") as? KMap<String, Any>
                    if (childMap != null) {
                        studentList.add(mapToStudentHome("primary", childMap, parentAddress))
                    }

                    @Suppress("UNCHECKED_CAST")
                    val childrenList = document.get("children") as? List<KMap<String, Any>>
                    childrenList?.forEachIndexed { index, map ->
                        studentList.add(mapToStudentHome(index.toString(), map, parentAddress))
                    }

                    setupRecyclerView(studentList)
                }
            }
            .addOnFailureListener {
                if (!isAdded) return@addOnFailureListener
                progressBar.visibility = View.GONE
                Toast.makeText(requireContext(), "Error fetching data", Toast.LENGTH_SHORT).show()
            }
    }

    private fun mapToStudentHome(id: String, map: KMap<String, Any>, parentAddress: String): StudentHome {
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
            btnPickUp.visibility = View.GONE
        } else {
            tvNoStudents.visibility = View.GONE
            rvStudentsHome.visibility = View.VISIBLE
            btnPickUp.visibility = View.VISIBLE
            rvStudentsHome.layoutManager = LinearLayoutManager(requireContext())
            rvStudentsHome.adapter = StudentHomeAdapter(students) { student ->
                val intent = Intent(requireContext(), com.example.buswatch.Map::class.java)
                intent.putExtra("childName", student.name)
                startActivity(intent)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    requireActivity().overrideActivityTransition(AppCompatActivity.OVERRIDE_TRANSITION_OPEN, CommonR.anim.fade_in, CommonR.anim.fade_out)
                } else {
                    @Suppress("DEPRECATION")
                    requireActivity().overridePendingTransition(CommonR.anim.fade_in, CommonR.anim.fade_out)
                }
            }
        }
    }
    
    private fun showApprovalPendingDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Account Pending")
            .setMessage("Your account is currently waiting for admin approval. You will be able to track your child once your account is approved.")
            .setPositiveButton("OK", null)
            .show()
    }
}
