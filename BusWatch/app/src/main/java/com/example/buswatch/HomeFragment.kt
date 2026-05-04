package com.example.buswatch

import android.content.Intent
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
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.buswatch.common.R as CommonR
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.Calendar
import java.util.Locale

class HomeFragment : Fragment() {
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var progressBar: ProgressBar
    private lateinit var tvNoStudents: TextView
    private lateinit var rvStudentsHome: RecyclerView
    private lateinit var btnPickUp: TextView
    private lateinit var viewNotificationBadge: View
    
    private var parentStatus: String = "pending"
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var timeRunnable: Runnable
    private var notificationListener: ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val btnHomeNotification = view.findViewById<ImageButton>(R.id.btnHomeNotification)
        viewNotificationBadge = view.findViewById(R.id.viewNotificationBadge)
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
            in 0..11 -> "Good Morning"
            in 12..16 -> "Good Afternoon"
            else -> "Good Evening"
        }

        fetchUserData(greetingPrefix, tvGreeting)
        setupNotificationBadge()

        btnPickUp.setOnClickListener {
            if (parentStatus.lowercase() == "approved") {
                val adapter = rvStudentsHome.adapter as? StudentHomeAdapter
                if (adapter != null) {
                    val currentStudents = adapter.getStudents()
                    
                    val message = StringBuilder("Are you sure you want to proceed with the following ride options?\n\n")
                    
                    val categories = listOf(
                        "Round Trip (Morning & Afternoon)", 
                        "Morning Only (Home to School)", 
                        "Afternoon Only (School to Home)", 
                        "Not Riding"
                    )
                    categories.forEach { category ->
                        val matching = currentStudents.filter { it.rideOption == category }
                        if (matching.isNotEmpty()) {
                            message.append("$category:\n")
                            matching.forEach { s -> message.append("  • ${s.name}\n") }
                            message.append("\n")
                        }
                    }

                    showCustomConfirmDialog(message.toString().trim())
                }
            } else {
                Toast.makeText(requireContext(), "Account pending approval", Toast.LENGTH_SHORT).show()
            }
        }

        btnHomeNotification.setOnClickListener {
            (activity as? ParentMainActivity)?.switchToNotifications()
        }

        return view
    }

    private fun setupNotificationBadge() {
        val userId = auth.currentUser?.uid ?: return
        
        notificationListener = db.collection("parents").document(userId)
            .collection("notifications")
            .whereEqualTo("isRead", false)
            .addSnapshotListener { snapshots, e ->
                if (e != null || snapshots == null) return@addSnapshotListener
                
                if (snapshots.isEmpty) {
                    viewNotificationBadge.visibility = View.GONE
                } else {
                    viewNotificationBadge.visibility = View.VISIBLE
                }
            }
    }

    private fun showCustomConfirmDialog(message: String) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_confirm_pickup_request, null)
        val dialog = AlertDialog.Builder(requireContext(), CommonR.style.CustomDialog)
            .setView(dialogView)
            .create()

        dialogView.findViewById<TextView>(R.id.tvDialogMessage).text = message
        
        dialogView.findViewById<View>(R.id.btnCancel).setOnClickListener {
            dialog.dismiss()
        }
        
        dialogView.findViewById<View>(R.id.btnConfirm).setOnClickListener {
            val headingToStopText = getString(CommonR.string.status_heading_to_stop)
            updateAllChildrenStatus(headingToStopText)
            dialog.dismiss()
        }

        dialog.show()
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
        notificationListener?.remove()
    }

    private fun fetchUserData(greetingPrefix: String, tvGreeting: TextView) {
        val currentUser = auth.currentUser ?: return

        progressBar.visibility = View.VISIBLE
        
        db.collection("parents").document(currentUser.uid).addSnapshotListener { document, _ ->
            if (!isAdded || document == null || !document.exists()) {
                if (isAdded) progressBar.visibility = View.GONE
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
                
                val stopsMap = snapshotsToStopsMap(stopsSnapshot.documents)
                
                progressBar.visibility = View.GONE

                val studentList = mutableListOf<StudentHome>()
                val addedNames = mutableSetOf<String>()

                @Suppress("UNCHECKED_CAST")
                val childMap = document.get("child") as? kotlin.collections.Map<String, Any>
                if (childMap != null) {
                    val student = mapToStudentHome("primary", childMap, stopsMap)
                    studentList.add(student)
                    addedNames.add(student.name)
                }

                @Suppress("UNCHECKED_CAST")
                val childrenList = document.get("children") as? List<kotlin.collections.Map<String, Any>>
                childrenList?.forEachIndexed { index, map ->
                    val student = mapToStudentHome(index.toString(), map, stopsMap)
                    if (student.name !in addedNames) {
                        studentList.add(student)
                        addedNames.add(student.name)
                    }
                }
                
                setupRecyclerView(studentList)
            }
        }
    }
    
    private fun snapshotsToStopsMap(docs: List<DocumentSnapshot>): kotlin.collections.Map<String, String> {
        return docs.associate { it.id to (it.getString("name") ?: "Unknown") }
    }

    private fun mapToStudentHome(
        id: String, 
        map: kotlin.collections.Map<String, Any>, 
        stopsMap: kotlin.collections.Map<String, String>
    ): StudentHome {
        val fName = map["firstName"] as? String ?: ""
        val lName = map["lastName"] as? String ?: ""
        
        val stopId = map["stop"] as? String ?: ""
        val stopDisplay = stopsMap[stopId] ?: stopId.ifEmpty { "Not assigned" }

        return StudentHome(
            id = id,
            name = "$fName $lName".trim(),
            grade = map["grade"] as? String ?: getString(CommonR.string.placeholder_hyphen),
            school = map["school"] as? String ?: getString(CommonR.string.the_immaculate_mother_academy_inc),
            status = map["status"] as? String ?: getString(CommonR.string.status_at_home),
            avatarUrl = map["childAvatarUrl"] as? String ?: map["avatarUrl"] as? String,
            stop = stopDisplay,
            rideOption = (map["rideOption"] as? String)?.takeIf { it.isNotBlank() } ?: "Round Trip (Morning & Afternoon)"
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
                val intent = Intent(requireContext(), StudentDetailsActivity::class.java)
                intent.putExtra("childName", student.name)
                startActivity(intent)
            }
        }
    }
}
