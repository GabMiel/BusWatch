package com.example.buswatch

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
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
import kotlin.collections.Map as KMap

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
    private var parentListener: ListenerRegistration? = null

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
        val logo = view.findViewById<ImageView>(R.id.imageView6)

        rvStudentsHome.layoutManager = LinearLayoutManager(requireContext())
        rvStudentsHome.adapter = StudentHomeAdapter(emptyList()) {}

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
            if (!btnPickUp.isEnabled) return@setOnClickListener
            
            if (parentStatus.lowercase() == "approved") {
                val adapter = rvStudentsHome.adapter as? StudentHomeAdapter
                if (adapter != null) {
                    val currentStudents = adapter.getStudents()
                    
                    val ridingStudents = currentStudents.filter { !it.rideOption.contains("Not Riding", ignoreCase = true) }
                    
                    if (ridingStudents.isEmpty()) {
                        context?.let { Toast.makeText(it, "No students are currently set to ride", Toast.LENGTH_SHORT).show() }
                        return@setOnClickListener
                    }

                    val studentsWithoutStop = ridingStudents.filter { it.stop == "Not assigned" || it.stop == "---" || it.stop.isBlank() }
                    if (studentsWithoutStop.isNotEmpty()) {
                        val names = studentsWithoutStop.joinToString(", ") { it.name }
                        Toast.makeText(requireContext(), "Pickup stop not assigned for: $names", Toast.LENGTH_LONG).show()
                        return@setOnClickListener
                    }
                    
                    val message = StringBuilder("Are you sure you want to proceed with the following ride options?\n\n")
                    val options = resources.getStringArray(CommonR.array.ride_options)
                    options.filter { !it.contains("Not Riding", ignoreCase = true) }.forEach { optionName ->
                        val matching = ridingStudents.filter { it.rideOption == optionName }
                        if (matching.isNotEmpty()) {
                            message.append("$optionName:\n")
                            matching.forEach { s -> message.append("  • ${s.name}\n") }
                            message.append("\n")
                        }
                    }

                    showCustomConfirmDialog(message.toString().trim(), ridingStudents)
                }
            } else {
                context?.let { Toast.makeText(it, "Account pending approval", Toast.LENGTH_SHORT).show() }
            }
        }

        btnHomeNotification.setOnClickListener {
            (activity as? ParentMainActivity)?.switchToNotifications()
        }

        // Secret Reset Function: Long press Logo to reset children statuses to At Home
        logo?.setOnLongClickListener {
            AlertDialog.Builder(requireContext(), CommonR.style.CustomDialog)
                .setTitle("Secret Reset")
                .setMessage("Reset all your children's statuses to 'At Home'?")
                .setPositiveButton("Reset") { _, _ ->
                    resetChildrenStatus()
                }
                .setNegativeButton("Cancel", null)
                .show()
            true
        }

        return view
    }

    private fun resetChildrenStatus() {
        val userId = auth.currentUser?.uid ?: return
        val docRef = db.collection("parents").document(userId)

        docRef.get().addOnSuccessListener { document ->
            if (isAdded && document.exists()) {
                val updates = mutableMapOf<String, Any>()
                val statusAtHome = getString(CommonR.string.status_at_home)

                @Suppress("UNCHECKED_CAST")
                val primaryChild = document.get("child") as? KMap<String, Any>
                if (primaryChild != null) {
                    val updatedChild = primaryChild.toMutableMap()
                    updatedChild["status"] = statusAtHome
                    updates["child"] = updatedChild
                }

                @Suppress("UNCHECKED_CAST")
                val childrenList = document.get("children") as? List<KMap<String, Any>>
                if (childrenList != null) {
                    val newChildren = childrenList.map { child ->
                        val updated = child.toMutableMap()
                        updated["status"] = statusAtHome
                        updated
                    }
                    updates["children"] = newChildren
                }

                if (updates.isNotEmpty()) {
                    docRef.update(updates).addOnSuccessListener {
                        if (isAdded) Toast.makeText(requireContext(), "Status reset successfully", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun setupNotificationBadge() {
        val userId = auth.currentUser?.uid ?: return
        notificationListener?.remove()
        notificationListener = db.collection("parents").document(userId)
            .collection("notifications")
            .whereEqualTo("isRead", false)
            .addSnapshotListener { snapshots, e ->
                if (!isAdded || e != null || snapshots == null) return@addSnapshotListener
                viewNotificationBadge.visibility = if (snapshots.isEmpty) View.GONE else View.VISIBLE
            }
    }

    private fun showCustomConfirmDialog(message: String, ridingStudents: List<StudentHome>) {
        val context = context ?: return
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_confirm_pickup_request, null)
        val dialog = AlertDialog.Builder(context, CommonR.style.CustomDialog)
            .setView(dialogView)
            .create()

        dialogView.findViewById<TextView>(R.id.tvDialogMessage).text = message
        val btnCancel = dialogView.findViewById<View>(R.id.btnCancel)
        val btnConfirm = dialogView.findViewById<View>(R.id.btnConfirm)

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnConfirm.setOnClickListener {
            btnConfirm.isEnabled = false
            btnCancel.isEnabled = false
            updateSpecificChildrenStatus(ridingStudents)
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun updateSpecificChildrenStatus(ridingStudents: List<StudentHome>) {
        val uid = auth.currentUser?.uid ?: return
        val docRef = db.collection("parents").document(uid)

        docRef.get().addOnSuccessListener { document ->
            if (isAdded && document.exists()) {
                val updates = mutableMapOf<String, Any>()
                val statusHeadingToStop = getString(CommonR.string.status_heading_to_stop)
                val statusHeadingHome = getString(CommonR.string.status_heading_home)

                fun transitionStatus(childData: KMap<String, Any>?): KMap<String, Any>? {
                    if (childData == null) return null
                    
                    val rideOption = childData["rideOption"] as? String ?: ""
                    if (rideOption.contains("Not Riding", ignoreCase = true)) return null
                    
                    val currentStatus = (childData["status"] as? String ?: "").lowercase().trim()
                    
                    val newStatus = when {
                        currentStatus == "" || currentStatus == "at home" -> statusHeadingToStop
                        currentStatus == "at school" -> statusHeadingHome
                        else -> null
                    }
                    
                    return if (newStatus != null) {
                        childData.toMutableMap().apply { put("status", newStatus) }
                    } else null
                }

                @Suppress("UNCHECKED_CAST")
                val primaryChild = document.get("child") as? KMap<String, Any>
                if (ridingStudents.any { it.id == "primary" }) {
                    transitionStatus(primaryChild)?.let { updates["child"] = it }
                }

                @Suppress("UNCHECKED_CAST")
                val children = document.get("children") as? List<KMap<String, Any>>
                if (children != null) {
                    var changed = false
                    val newChildren = children.mapIndexed { index, child ->
                        if (ridingStudents.any { it.id == index.toString() }) {
                            transitionStatus(child)?.let {
                                changed = true
                                it
                            } ?: child
                        } else child
                    }
                    if (changed) updates["children"] = newChildren
                }

                if (updates.isNotEmpty()) {
                    docRef.update(updates).addOnSuccessListener {
                        if (isAdded) Toast.makeText(requireContext(), "Pickup request sent. Live tracking enabled.", Toast.LENGTH_LONG).show()
                    }
                } else {
                    if (isAdded) Toast.makeText(requireContext(), "Students are already active or not riding.", Toast.LENGTH_SHORT).show()
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
        parentListener?.remove()
    }

    private fun fetchUserData(greetingPrefix: String, tvGreeting: TextView) {
        val currentUser = auth.currentUser ?: return
        if (isAdded) progressBar.visibility = View.VISIBLE
        parentListener?.remove()
        parentListener = db.collection("parents").document(currentUser.uid).addSnapshotListener { document, _ ->
            if (!isAdded || document == null || !document.exists()) {
                if (isAdded) progressBar.visibility = View.GONE
                return@addSnapshotListener
            }
            @Suppress("UNCHECKED_CAST")
            val profile = document.get("profile") as? KMap<String, Any>
            val firstName = profile?.get("firstName") as? String ?: document.getString("firstName") ?: "User"
            if (greetingPrefix.isNotEmpty()) tvGreeting.text = getString(CommonR.string.greeting_format, greetingPrefix, firstName)
            parentStatus = document.getString("status") ?: "pending"
            db.collection("stops").get().addOnSuccessListener { stopsSnapshot ->
                if (!isAdded) return@addOnSuccessListener
                val stopsMap = snapshotsToStopsMap(stopsSnapshot.documents)
                progressBar.visibility = View.GONE
                val studentList = mutableListOf<StudentHome>()
                val addedNames = mutableSetOf<String>()
                @Suppress("UNCHECKED_CAST")
                val childMap = document.get("child") as? KMap<String, Any>
                if (childMap != null) {
                    val student = mapToStudentHome("primary", childMap, stopsMap)
                    studentList.add(student)
                    addedNames.add(student.name)
                }
                @Suppress("UNCHECKED_CAST")
                val childrenList = document.get("children") as? List<KMap<String, Any>>
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
    
    private fun snapshotsToStopsMap(docs: List<DocumentSnapshot>): KMap<String, String> {
        return docs.associate { it.id to (it.getString("name") ?: "Unknown") }
    }

    private fun mapToStudentHome(id: String, map: KMap<String, Any>, stopsMap: KMap<String, String>): StudentHome {
        val fName = map["firstName"] as? String ?: ""
        val lName = map["lastName"] as? String ?: ""
        val stopId = map["stop"] as? String ?: ""
        val stopDisplay = stopsMap[stopId] ?: stopId.ifEmpty { "Not assigned" }
        val rideOption = (map["rideOption"] as? String)?.takeIf { it.isNotBlank() } ?: "Round Trip (Morning & Afternoon)"
        val status = if (rideOption.contains("Not Riding", ignoreCase = true)) getString(CommonR.string.status_at_home)
        else map["status"] as? String ?: getString(CommonR.string.status_at_home)
        return StudentHome(id, "$fName $lName".trim(), map["grade"] as? String ?: getString(CommonR.string.placeholder_hyphen),
            map["school"] as? String ?: getString(CommonR.string.the_immaculate_mother_academy_inc), status,
            map["childAvatarUrl"] as? String ?: map["avatarUrl"] as? String, stopDisplay, rideOption)
    }

    private fun setupRecyclerView(students: List<StudentHome>) {
        if (!isAdded) return
        if (students.isEmpty()) {
            tvNoStudents.visibility = View.VISIBLE
            rvStudentsHome.visibility = View.GONE
            btnPickUp.visibility = View.GONE
        } else {
            tvNoStudents.visibility = View.GONE
            rvStudentsHome.visibility = View.VISIBLE
            btnPickUp.visibility = View.VISIBLE
            btnPickUp.isEnabled = true
            btnPickUp.alpha = 1.0f
            btnPickUp.text = getString(CommonR.string.pick_up)
            rvStudentsHome.adapter = StudentHomeAdapter(students) { student ->
                val intent = Intent(requireContext(), com.example.buswatch.Map::class.java)
                intent.putExtra("childName", student.name)
                startActivity(intent)
            }
        }
    }
}
