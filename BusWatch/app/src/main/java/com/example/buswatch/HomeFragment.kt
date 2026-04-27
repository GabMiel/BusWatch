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
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Calendar
import java.util.Locale

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
                    
                    val missingStop = currentStudents.any { it.stop == "Not assigned" || it.stop.isEmpty() }
                    
                    if (missingStop) {
                        Toast.makeText(requireContext(), "Please ensure all children have an assigned stop first.", Toast.LENGTH_LONG).show()
                        return@setOnClickListener
                    }

                    // Build detailed confirmation message with categorization
                    val message = StringBuilder("Are you sure you want to proceed with the following ride options?\n\n")
                    
                    val categories = listOf("Morning Trip", "Afternoon Trip", "Not Riding")
                    categories.forEach { category ->
                        val matching = currentStudents.filter { it.rideOption == category }
                        if (matching.isNotEmpty()) {
                            message.append("$category:\n")
                            matching.forEach { s -> message.append("  • ${s.name}\n") }
                            message.append("\n")
                        }
                    }

                    AlertDialog.Builder(requireContext())
                        .setTitle("Confirm Pickup Request")
                        .setMessage(message.toString().trim())
                        .setPositiveButton("Confirm") { _, _ ->
                            val headingToStopText = getString(CommonR.string.status_heading_to_stop)
                            updateAllChildrenStatus(headingToStopText)
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            } else {
                Toast.makeText(requireContext(), "Account pending approval", Toast.LENGTH_SHORT).show()
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

    private fun updateAllChildrenStatus(newStatus: String) {
        val uid = auth.currentUser?.uid ?: return
        val docRef = db.collection("parents").document(uid)

        docRef.get().addOnSuccessListener { document ->
            if (document.exists()) {
                val updates = mutableMapOf<String, Any>()
                
                @Suppress("UNCHECKED_CAST")
                val primaryChild = document.get("child") as? kotlin.collections.Map<String, Any>
                if (primaryChild != null) {
                    val updatedPrimary = primaryChild.toMutableMap()
                    updatedPrimary["status"] = newStatus
                    updates["child"] = updatedPrimary
                }

                @Suppress("UNCHECKED_CAST")
                val children = document.get("children") as? List<kotlin.collections.Map<String, Any>>
                if (children != null) {
                    val updatedChildren = children.map { child ->
                        val mutableChild = child.toMutableMap()
                        mutableChild["status"] = newStatus
                        mutableChild 
                    }
                    updates["children"] = updatedChildren
                }

                if (updates.isNotEmpty()) {
                    docRef.update(updates).addOnSuccessListener {
                        Toast.makeText(requireContext(), "Pickup request sent. Live tracking enabled.", Toast.LENGTH_LONG).show()
                    }
                }
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
        
        db.collection("parents").document(currentUser.uid).addSnapshotListener { document, _ ->
            if (!isAdded || document == null || !document.exists()) {
                progressBar.visibility = View.GONE
                return@addSnapshotListener
            }
            
            @Suppress("UNCHECKED_CAST")
            val profile = document.get("profile") as? kotlin.collections.Map<String, Any>
            val firstName = profile?.get("firstName") as? String ?: document.getString("firstName") ?: "User"

            if (greetingPrefix.isNotEmpty()) {
                tvGreeting.text = getString(CommonR.string.greeting_format, greetingPrefix, firstName)
            }
            
            parentStatus = document.getString("status") ?: "pending"

            db.collection("stops").get().addOnSuccessListener { stopsSnapshot ->
                if (!isAdded) return@addOnSuccessListener
                
                val stopsMap = stopsSnapshot.documents.associate { (it.id) to (it.getString("name") ?: "Unknown") }
                
                db.collection("routes").get().addOnSuccessListener { routesSnapshot ->
                    if (!isAdded) return@addOnSuccessListener
                    progressBar.visibility = View.GONE

                    val studentList = mutableListOf<StudentHome>()

                    @Suppress("UNCHECKED_CAST")
                    val childMap = document.get("child") as? kotlin.collections.Map<String, Any>
                    if (childMap != null) {
                        studentList.add(mapToStudentHome("primary", childMap, stopsMap, routesSnapshot.documents))
                    }

                    @Suppress("UNCHECKED_CAST")
                    val childrenList = document.get("children") as? List<kotlin.collections.Map<String, Any>>
                    childrenList?.forEachIndexed { index, map ->
                        studentList.add(mapToStudentHome(index.toString(), map, stopsMap, routesSnapshot.documents))
                    }

                    setupRecyclerView(studentList)
                }
            }
        }
    }

    private fun mapToStudentHome(
        id: String, 
        map: kotlin.collections.Map<String, Any>, 
        stopsMap: kotlin.collections.Map<String, String>,
        routeDocs: List<DocumentSnapshot>
    ): StudentHome {
        val fName = map["firstName"] as? String ?: ""
        val lName = map["lastName"] as? String ?: ""
        
        val stopId = map["stop"] as? String ?: ""
        val stopDisplay = stopsMap[stopId] ?: if (stopId.isNotEmpty()) stopId else "Not assigned"

        // Find Route Schedule
        var scheduleStr = "Schedule: Not Set"
        if (stopId.isNotEmpty()) {
            val assignedRoute = routeDocs.find { doc ->
                @Suppress("UNCHECKED_CAST")
                val stops = doc.get("stopIds") as? List<String>
                stops?.contains(stopId) == true
            }
            
            if (assignedRoute != null) {
                val ms = assignedRoute.getString("morningStartTime") ?: ""
                val me = assignedRoute.getString("morningEndTime") ?: ""
                val asStart = assignedRoute.getString("afternoonStartTime") ?: ""
                val ae = assignedRoute.getString("afternoonEndTime") ?: ""
                
                if (ms.isNotEmpty() && me.isNotEmpty() && asStart.isNotEmpty() && ae.isNotEmpty()) {
                    scheduleStr = "AM: $ms-$me | PM: $asStart-$ae"
                } else if (ms.isNotEmpty() && me.isNotEmpty()) {
                    scheduleStr = "AM: $ms-$me"
                } else if (asStart.isNotEmpty() && ae.isNotEmpty()) {
                    scheduleStr = "PM: $asStart-$ae"
                }
            }
        }
        
        return StudentHome(
            id = id,
            name = "$fName $lName".trim(),
            grade = map["grade"] as? String ?: getString(CommonR.string.placeholder_hyphen),
            school = map["school"] as? String ?: getString(CommonR.string.the_immaculate_mother_academy_inc),
            status = map["status"] as? String ?: getString(CommonR.string.status_at_home),
            avatarResId = CommonR.drawable.user,
            avatarUrl = map["childAvatarUrl"] as? String ?: map["avatarUrl"] as? String,
            stop = stopDisplay,
            rideOption = map["rideOption"] as? String ?: "Morning Trip",
            schedule = scheduleStr
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
            
            val isTracking = students.any { 
                it.status == getString(CommonR.string.status_heading_to_stop) || 
                it.status == getString(CommonR.string.status_on_board) 
            }
            
            rvStudentsHome.adapter = StudentHomeAdapter(students, isTracking) { student ->
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
}
