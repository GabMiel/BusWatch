package com.example.buswatch

import android.content.res.ColorStateList
import android.os.Bundle
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import com.example.buswatch.common.R as CommonR
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class BusDetails : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var childName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.busdetails)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        childName = intent.getStringExtra("childName")

        val backButton = findViewById<ImageButton>(R.id.btnBusBack)
        backButton.setOnClickListener {
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, CommonR.anim.slide_out_bottom)
        }

        fetchBusAndDriverData()
    }

    private fun fetchBusAndDriverData() {
        val uid = auth.currentUser?.uid ?: return

        db.collection("parents").document(uid).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    // Try to find the assigned bus for the selected child
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

                    // Fallback to default if not found
                    val busId = assignedBusId ?: "Bus 001"
                    loadBusFromFirestore(busId)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error fetching student record", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadBusFromFirestore(busId: String) {
        db.collection("buses").document(busId).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    // Update Bus UI
                    findViewById<TextView>(R.id.valBusNo).text = doc.getString("busNumber") ?: busId
                    
                    val totalStudents = doc.getLong("totalStudents") ?: 0
                    findViewById<TextView>(R.id.valTotalStuds).text = getString(CommonR.string._28_students).replace("28", totalStudents.toString())
                    
                    val availableSeats = doc.getLong("availableSeats") ?: 0
                    findViewById<TextView>(R.id.valSeats).text = getString(CommonR.string._12_seats).replace("12", availableSeats.toString())
                    
                    val capacity = doc.getLong("capacity") ?: 40
                    findViewById<TextView>(R.id.valCap).text = getString(CommonR.string._40_students).replace("40", capacity.toString())
                    
                    val status = doc.getString("status") ?: "Active"
                    val tvStatus = findViewById<TextView>(R.id.valStatus)
                    tvStatus.text = status
                    if (status.lowercase() == "active") {
                        tvStatus.setBackgroundResource(CommonR.drawable.rectangle_shape)
                        tvStatus.backgroundTintList = ColorStateList.valueOf("#F8DC98".toColorInt())
                    }

                    // Update Driver UI
                    findViewById<TextView>(R.id.valName).text = doc.getString("driverName") ?: "---"
                    findViewById<TextView>(R.id.textView118).text = doc.getString("driverEmail") ?: "---"
                    findViewById<TextView>(R.id.textView120).text = doc.getString("driverPhone") ?: "---"
                    findViewById<TextView>(R.id.textView122).text = doc.getString("driverLicense") ?: "---"
                    
                    // Handle Avatar if name is provided
                    val avatarName = doc.getString("driverAvatar") ?: "user"
                    @Suppress("DiscouragedApi")
                    val resId = resources.getIdentifier(avatarName, "drawable", packageName)
                    if (resId != 0) {
                        findViewById<ImageView>(R.id.imgAvatar).setImageResource(resId)
                    }
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error loading bus details", Toast.LENGTH_SHORT).show()
            }
    }
}
