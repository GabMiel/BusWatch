package com.example.buswatch

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.buswatch.common.R as CommonR
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class StudentDetailsGeneral : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var childName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.studentdetails_general)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        childName = intent.getStringExtra("childName")

        val backButton = findViewById<ImageButton>(R.id.btnGeneralBack)
        val medicalButton = findViewById<Button>(R.id.btnGeneralMedical)
        val emergencyButton = findViewById<Button>(R.id.btnGeneralEmergency)
        
        val tvHeaderName = findViewById<TextView>(R.id.tvStudentHeaderName)
        tvHeaderName.text = childName ?: "Student"

        fetchStudentGeneralData()

        backButton.setOnClickListener {
            finish()
        }

        medicalButton.setOnClickListener {
            val intent = Intent(this, StudentDetailsMedical::class.java)
            intent.putExtra("childName", childName)
            startActivity(intent)
            finish()
        }

        emergencyButton.setOnClickListener {
            val intent = Intent(this, StudentDetailsEmergency::class.java)
            intent.putExtra("childName", childName)
            startActivity(intent)
            finish()
        }
    }

    private fun fetchStudentGeneralData() {
        val uid = auth.currentUser?.uid ?: return

        db.collection("parents").document(uid).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    @Suppress("UNCHECKED_CAST")
                    val childMap = document.get("child") as? Map<String, Any>
                    @Suppress("UNCHECKED_CAST")
                    val childrenList = document.get("children") as? List<Map<String, Any>>
                    
                    var foundChild: Map<String, Any>? = null
                    
                    if (childMap != null) {
                        val fullName = "${childMap["firstName"]} ${childMap["lastName"]}"
                        if (childName == null || fullName == childName) {
                            foundChild = childMap
                        }
                    }
                    
                    if (foundChild == null && childrenList != null) {
                        foundChild = childrenList.find { 
                            "${it["firstName"]} ${it["lastName"]}" == childName 
                        }
                    }

                    foundChild?.let { displayGeneralInfo(it) }
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error fetching student data", Toast.LENGTH_SHORT).show()
            }
    }

    private fun displayGeneralInfo(child: Map<String, Any>) {
        findViewById<TextView>(R.id.tvStudentName).text = "${child["firstName"]} ${child["middleName"] ?: ""} ${child["lastName"]}".replace("  ", " ")
        findViewById<TextView>(R.id.tvStudentGrade).text = child["grade"] as? String ?: "---"
        findViewById<TextView>(R.id.tvStudentDOB).text = child["age"] as? String ?: "---" // Assuming age/dob
        findViewById<TextView>(R.id.tvStudentAddress).text = child["address"] as? String ?: "---"
        findViewById<TextView>(R.id.tvStudentSchool).text = child["school"] as? String ?: "The Immaculate Mother Academy Inc."
    }
}
