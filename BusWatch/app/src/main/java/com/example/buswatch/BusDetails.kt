package com.example.buswatch

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.buswatch.databinding.BusdetailsBinding
import com.example.buswatch.common.R as CommonR
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

typealias FireMap = kotlin.collections.Map<String, Any>

class BusDetails : AppCompatActivity() {
    private lateinit var binding: BusdetailsBinding
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    private var childName: String? = null
    private var busListener: ListenerRegistration? = null
    private var lastDriverId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = BusdetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        childName = intent.getStringExtra("childName")
        binding.btnBusBack.setOnClickListener { finish() }

        fetchInitialBusData()
    }

    private fun fetchInitialBusData() {
        val uid = auth.currentUser?.uid ?: return

        db.collection("parents").document(uid).get()
            .addOnSuccessListener { document ->
                if (document?.exists() != true) return@addOnSuccessListener
                
                val childData = findChildData(document)
                val assignedBusId = childData?.get("assignedBus") as? String
                
                if (assignedBusId != null) {
                    prepareBusData(assignedBusId)
                } else {
                    (childData?.get("stop") as? String)?.let { findBusByStop(it) }
                }
            }
    }

    private fun findChildData(document: DocumentSnapshot): FireMap? {
        @Suppress("UNCHECKED_CAST")
        val childMap = document.get("child") as? FireMap
        @Suppress("UNCHECKED_CAST")
        val childrenList = document.get("children") as? List<FireMap>

        fun getFullName(map: FireMap) = getString(CommonR.string.name_format, 
            map["firstName"] ?: "", map["lastName"] ?: "").trim()

        if (childMap != null) {
            val fullName = getFullName(childMap)
            if (childName == null || fullName == childName) return childMap
        }

        return childrenList?.find { getFullName(it) == childName }
    }

    private fun findBusByStop(stopId: String) {
        db.collection("routes")
            .whereArrayContains("stopIds", stopId)
            .whereEqualTo("status", "Active")
            .limit(1).get()
            .addOnSuccessListener { snapshots ->
                val routeDoc = snapshots.documents.firstOrNull()
                routeDoc?.getString("busId")?.let { busId ->
                    startRealTimeBusUpdates(busId, routeDoc)
                }
            }
    }

    private fun prepareBusData(busId: String) {
        db.collection("routes").whereEqualTo("busId", busId).limit(1).get()
            .addOnSuccessListener { routeSnap ->
                startRealTimeBusUpdates(busId, routeSnap.documents.firstOrNull())
            }
    }

    private fun startRealTimeBusUpdates(busId: String, routeDoc: DocumentSnapshot?) {
        // Update schedule UI from routeDoc
        routeDoc?.let {
            binding.tvMorningStart.text = it.getString("morningStartTime") ?: "---"
            binding.tvMorningEnd.text = it.getString("morningEndTime") ?: "---"
            binding.tvAfternoonStart.text = it.getString("afternoonStartTime") ?: "---"
            binding.tvAfternoonEnd.text = it.getString("afternoonEndTime") ?: "---"
        }

        busListener?.remove()
        busListener = db.collection("buses").document(busId)
            .addSnapshotListener { doc, error ->
                if (error != null || doc?.exists() != true) return@addSnapshotListener

                with(binding) {
                    valBusNo.text = doc.getString("busNumber") ?: "---"
                    valStatus.text = doc.getString("status") ?: "Active"
                    valVehicleType.text = doc.getString("vehicleType") ?: doc.getString("type") ?: "---"
                    valPlateNumber.text = doc.getString("plateNumber") ?: "---"
                    valCap.text = (getSafeLong(routeDoc, "maxCapacity") ?: getSafeLong(doc, "maxCapacity") 
                        ?: getSafeLong(doc, "capacity") ?: 0L).toString()
                }

                (doc.getString("driverId") ?: routeDoc?.getString("driverId"))?.let { driverId ->
                    if (driverId != lastDriverId) {
                        lastDriverId = driverId
                        fetchDriverDetails(driverId)
                    }
                }
            }
    }

    private fun fetchDriverDetails(driverId: String) {
        db.collection("drivers").document(driverId).get().addOnSuccessListener { dDoc ->
            if (!dDoc.exists()) return@addOnSuccessListener
            with(binding) {
                valName.text = getString(CommonR.string.name_format, 
                    dDoc.getString("firstName") ?: "", dDoc.getString("lastName") ?: "").trim().ifEmpty { "---" }
                tvDriverPhone.text = dDoc.getString("phone") ?: dDoc.getString("phoneNumber") ?: "---"
                tvDriverEmail.text = dDoc.getString("email") ?: "---"
                tvDriverLicense.text = dDoc.getString("licenseNumber") ?: "---"
                
                Glide.with(this@BusDetails)
                    .load(dDoc.getString("driverAvatar") ?: dDoc.getString("photoUrl"))
                    .placeholder(CommonR.drawable.ic_person_placeholder)
                    .circleCrop().into(ivDriverPhoto)
            }
        }
    }

    private fun getSafeLong(doc: DocumentSnapshot?, field: String): Long? = when (val v = doc?.get(field)) {
        is Number -> v.toLong()
        is String -> v.toLongOrNull()
        else -> null
    }

    override fun onDestroy() {
        super.onDestroy()
        busListener?.remove()
    }
}
