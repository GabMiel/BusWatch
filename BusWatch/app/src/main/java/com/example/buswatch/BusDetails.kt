package com.example.buswatch

import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.buswatch.common.R as CommonR
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class BusDetails : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var childName: String? = null
    private var busListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.busdetails)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        childName = intent.getStringExtra("childName")

        val backButton = findViewById<ImageButton>(R.id.btnBusBack)
        backButton?.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        findViewById<TextView>(R.id.btnBackAction)?.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        fetchBusAndDriverData()
    }

    private fun fetchBusAndDriverData() {
        val uid = auth.currentUser?.uid ?: return

        db.collection("parents").document(uid).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    @Suppress("UNCHECKED_CAST")
                    val childMap = document.get("child") as? kotlin.collections.Map<String, Any>
                    @Suppress("UNCHECKED_CAST")
                    val childrenList = document.get("children") as? List<kotlin.collections.Map<String, Any>>
                    
                    var assignedBusId: String? = null
                    
                    if (childMap != null) {
                        val fullName = "${childMap["firstName"]} ${childMap["lastName"]}"
                        if (childName == null || fullName == childName) {
                            assignedBusId = childMap["assignedBus"] as? String
                        }
                    }
                    
                    if (assignedBusId == null && childrenList != null) {
                        val foundChild = childrenList.find { 
                            "${it["firstName"]} ${it["lastName"]}" == childName 
                        }
                        assignedBusId = foundChild?.get("assignedBus") as? String
                    }

                    if (assignedBusId != null) {
                        startRealTimeBusUpdates(assignedBusId)
                    } else {
                        // If no bus assigned to child, try to find a route they are on
                        findBusFromRoute()
                    }
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error fetching student record", Toast.LENGTH_SHORT).show()
            }
    }

    private fun findBusFromRoute() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("parents").document(uid).get().addOnSuccessListener { doc ->
            @Suppress("UNCHECKED_CAST")
            val childMap = doc.get("child") as? kotlin.collections.Map<String, Any>
            @Suppress("UNCHECKED_CAST")
            val childrenList = doc.get("children") as? List<kotlin.collections.Map<String, Any>>
            
            var targetChild: kotlin.collections.Map<String, Any>? = null
            if (childMap != null) {
                val fullName = "${childMap["firstName"]} ${childMap["lastName"]}".trim()
                if (childName == null || fullName == childName) targetChild = childMap
            }
            if (targetChild == null && childrenList != null) {
                targetChild = childrenList.find { "${it["firstName"]} ${it["lastName"]}".trim() == childName }
            }

            val stopId = targetChild?.get("stop") as? String
            if (stopId != null) {
                db.collection("routes")
                    .whereArrayContains("stopIds", stopId)
                    .whereEqualTo("status", "Active")
                    .limit(1)
                    .get()
                    .addOnSuccessListener { snapshots ->
                        if (!snapshots.isEmpty) {
                            val busId = snapshots.documents[0].getString("busId")
                            if (busId != null) startRealTimeBusUpdates(busId)
                        }
                    }
            }
        }
    }

    private fun startRealTimeBusUpdates(busId: String) {
        busListener?.remove()
        
        busListener = db.collection("buses").document(busId)
            .addSnapshotListener { doc, error ->
                if (error != null) {
                    Toast.makeText(this, "Error listening for updates", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (doc != null && doc.exists()) {
                    // Update Bus UI
                    findViewById<TextView>(R.id.valBusNo)?.text = doc.getString("busNumber") ?: busId
                    
                    val totalStudents = doc.getLong("totalStudents") ?: 0
                    val capacity = doc.getLong("capacity") ?: 0
                    val availableSeats = if (capacity > 0) capacity - totalStudents else 0
                    
                    findViewById<TextView>(R.id.valTotalStuds)?.text = "$totalStudents Students"
                    findViewById<TextView>(R.id.valCap)?.text = "$capacity Capacity"
                    findViewById<TextView>(R.id.valSeats)?.text = "$availableSeats Seats Available"
                    
                    val status = doc.getString("status") ?: "Active"
                    findViewById<TextView>(R.id.valStatus)?.text = status

                    val driverId = doc.getString("driverId")
                    if (driverId != null) {
                        db.collection("drivers").document(driverId).get().addOnSuccessListener { dDoc ->
                            if (dDoc.exists()) {
                                findViewById<TextView>(R.id.valName)?.text = "${dDoc.getString("firstName")} ${dDoc.getString("lastName")}"
                                findViewById<TextView>(R.id.tvDriverEmail)?.text = dDoc.getString("email") ?: "---"
                                findViewById<TextView>(R.id.tvDriverPhone)?.text = dDoc.getString("phoneNumber") ?: "---"
                                findViewById<TextView>(R.id.tvDriverLicense)?.text = dDoc.getString("licenseNumber") ?: "---"
                            }
                        }
                    }
                }
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        busListener?.remove()
    }
}
