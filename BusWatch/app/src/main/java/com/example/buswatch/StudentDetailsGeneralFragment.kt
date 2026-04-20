package com.example.buswatch

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import com.example.buswatch.common.R as CommonR

class StudentDetailsGeneralFragment : Fragment() {
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private var parentStatus: String = "pending"
    private var homePoint: GeoPoint? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_student_details_general, container, false)
        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        
        setupUI(view)
        loadStudentAndParentData(view)
        
        return view
    }

    private fun setupUI(view: View) {
        view.findViewById<Button>(R.id.btnAssignStop)?.setOnClickListener {
            if (parentStatus.lowercase() == "approved") {
                showStopPickerDialog()
            } else {
                Toast.makeText(requireContext(), "Action restricted until account is approved.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadStudentAndParentData(view: View) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("parents").document(uid).get().addOnSuccessListener { doc ->
            if (doc.exists()) {
                parentStatus = doc.getString("status") ?: "pending"
                
                // Hide button immediately if not approved
                if (parentStatus.lowercase() != "approved") {
                    view.findViewById<Button>(R.id.btnAssignStop).visibility = View.GONE
                }

                @Suppress("UNCHECKED_CAST")
                val child = doc.get("child") as? Map<String, Any>
                if (child != null) {
                    displayChildInfo(view, child)
                    val currentStopId = child["stop"] as? String
                    updateStopAndMapUI(view, child, currentStopId)
                }
            }
        }
    }

    private fun displayChildInfo(view: View, child: Map<String, Any>) {
        view.findViewById<TextView>(R.id.tvStudentName).text = "${child["firstName"]} ${child["lastName"]}"
        view.findViewById<TextView>(R.id.tvDob).text = child["age"]?.toString() ?: "-"
        view.findViewById<TextView>(R.id.tvSchool).text = child["school"] as? String ?: "-"
        view.findViewById<TextView>(R.id.tvGrade).text = child["grade"] as? String ?: "-"
        view.findViewById<TextView>(R.id.tvSection).text = child["class"] as? String ?: "-"
        view.findViewById<TextView>(R.id.tvAddress).text = child["address"] as? String ?: "-"
        
        val lat = child["latitude"] as? Double ?: 0.0
        val lng = child["longitude"] as? Double ?: 0.0
        homePoint = GeoPoint(lat, lng)

        val avatar = child["childAvatarUrl"] as? String ?: ""
        if (avatar.isNotEmpty()) {
            Glide.with(this).load(avatar).circleCrop().into(view.findViewById(R.id.imgStudentAvatar))
        }
    }

    private fun updateStopAndMapUI(view: View, child: Map<String, Any>, stopId: String?) {
        val tvStatus = view.findViewById<TextView>(R.id.tvAssignedStop)
        val btnAssign = view.findViewById<Button>(R.id.btnAssignStop)
        val mapView = view.findViewById<MapView>(R.id.mapHomeLocation)

        if (stopId.isNullOrEmpty()) {
            // No stop selected: Show "Set" button and Home on Map
            tvStatus.visibility = View.VISIBLE
            tvStatus.text = "Pickup Stop: Not Selected"
            if (parentStatus.lowercase() == "approved") btnAssign.visibility = View.VISIBLE
            
            homePoint?.let { setupMap(mapView, it, "Home Location") }
        } else {
            // Stop confirmed: Hide button and update Map with Stop Location
            btnAssign.visibility = View.GONE
            db.collection("stops").document(stopId).get().addOnSuccessListener { stopDoc ->
                val stopName = stopDoc.getString("name") ?: "Selected Stop"
                tvStatus.visibility = View.VISIBLE
                tvStatus.text = "Confirmed Stop: $stopName"
                
                val stopLat = stopDoc.getDouble("latitude") ?: 0.0
                val stopLng = stopDoc.getDouble("longitude") ?: 0.0
                if (stopLat != 0.0) {
                    setupMap(mapView, GeoPoint(stopLat, stopLng), "Pickup: $stopName")
                }
            }
        }
    }

    private fun setupMap(map: MapView, point: GeoPoint, title: String) {
        map.overlays.clear()
        map.setMultiTouchControls(true)
        map.controller.setZoom(18.0)
        map.controller.setCenter(point)
        val marker = Marker(map)
        marker.position = point
        marker.title = title
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        map.overlays.add(marker)
        map.invalidate()
    }

    private fun showStopPickerDialog() {
        db.collection("stops").whereEqualTo("status", "active").get().addOnSuccessListener { snapshots ->
            if (snapshots.isEmpty) {
                Toast.makeText(requireContext(), "No stops available", Toast.LENGTH_SHORT).show()
                return@addOnSuccessListener
            }
            val stopNames = snapshots.map { it.getString("name") ?: "Unknown" }.toTypedArray()
            val stopIds = snapshots.map { it.id }

            AlertDialog.Builder(requireContext())
                .setTitle("Confirm Pickup Stop")
                .setMessage("Selecting a stop will finalize your child's pickup point. Continue?")
                .setItems(stopNames) { _, which ->
                    saveStopSelection(stopIds[which], stopNames[which])
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun saveStopSelection(stopId: String, stopName: String) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("parents").document(uid).get().addOnSuccessListener { doc ->
            @Suppress("UNCHECKED_CAST")
            val child = doc.get("child") as? MutableMap<String, Any>
            if (child != null) {
                child["stop"] = stopId
                db.collection("parents").document(uid).update("child", child).addOnSuccessListener {
                    Toast.makeText(requireContext(), "Stop confirmed: $stopName", Toast.LENGTH_SHORT).show()
                    updateStopAndMapUI(requireView(), child, stopId)
                }
            }
        }
    }
}
