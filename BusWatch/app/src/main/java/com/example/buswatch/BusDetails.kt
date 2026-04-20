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

                    val busId = assignedBusId ?: "Bus 001"
                    startRealTimeBusUpdates(busId)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error fetching student record", Toast.LENGTH_SHORT).show()
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
                    val capacity = doc.getLong("capacity") ?: 40
                    val availableSeats = capacity - totalStudents
                    
                    findViewById<TextView>(R.id.valTotalStuds)?.text = "$totalStudents Students"
                    findViewById<TextView>(R.id.valCap)?.text = "$capacity Capacity"
                    findViewById<TextView>(R.id.valSeats)?.text = "$availableSeats Seats Available"
                    
                    val status = doc.getString("status") ?: "Active"
                    findViewById<TextView>(R.id.valStatus)?.text = status

                    // Update Driver UI
                    findViewById<TextView>(R.id.valName)?.text = doc.getString("driverName") ?: "---"
                    findViewById<TextView>(R.id.tvDriverEmail)?.text = doc.getString("driverEmail") ?: "---"
                    findViewById<TextView>(R.id.tvDriverPhone)?.text = doc.getString("driverPhone") ?: "---"
                    findViewById<TextView>(R.id.tvDriverLicense)?.text = doc.getString("driverLicense") ?: "---"
                }
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        busListener?.remove()
    }
}
