package com.example.buswatch.admin

import android.content.Context
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.buswatch.common.R as CommonR
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class AddRouteDialog(
    private val context: Context,
    private val db: FirebaseFirestore,
    private val onRouteAdded: () -> Unit
) {
    private var selectedDriverId: String? = null
    private var selectedBusId: String? = null
    private var selectedConductorId: String? = null
    private var selectedStopIds = mutableListOf<String>()
    private var busCapacity = 0

    fun show() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_route, null)
        val dialog = AlertDialog.Builder(context).setView(dialogView).create()

        val etRouteName = dialogView.findViewById<EditText>(R.id.etRouteName)
        val tvSelectedDriver = dialogView.findViewById<TextView>(R.id.tvSelectedDriver)
        val tvSelectedBus = dialogView.findViewById<TextView>(R.id.tvSelectedBus)
        val tvBusCapacity = dialogView.findViewById<TextView>(R.id.tvBusCapacity)
        val tvSelectedConductor = dialogView.findViewById<TextView>(R.id.tvSelectedConductor)
        val tvSelectedStopsCount = dialogView.findViewById<TextView>(R.id.tvSelectedStopsCount)
        val mapRoutePicker = dialogView.findViewById<MapView>(R.id.mapRoutePicker)

        // Dropdown Buttons
        dialogView.findViewById<FrameLayout>(R.id.btnDriverDropdown).setOnClickListener {
            showDriverPicker(tvSelectedDriver)
        }
        dialogView.findViewById<FrameLayout>(R.id.btnBusDropdown).setOnClickListener {
            showBusPicker(tvSelectedBus, tvBusCapacity)
        }
        dialogView.findViewById<FrameLayout>(R.id.btnConductorDropdown).setOnClickListener {
            showConductorPicker(tvSelectedConductor)
        }

        // Setup Map for Stop Selection
        setupStopPickerMap(mapRoutePicker, tvSelectedStopsCount)

        dialogView.findViewById<ImageButton>(R.id.btnCloseAddRoute).setOnClickListener { dialog.dismiss() }
        
        dialogView.findViewById<TextView>(R.id.btnSaveRoute).setOnClickListener {
            saveRoute(etRouteName.text.toString(), dialog)
        }

        dialog.show()
    }

    private fun showDriverPicker(target: TextView) {
        db.collection("drivers").whereEqualTo("status", "active").get().addOnSuccessListener { snapshots ->
            val items = snapshots.map { it.getString("firstName") + " " + it.getString("lastName") }.toTypedArray()
            val ids = snapshots.map { it.id }
            AlertDialog.Builder(context).setTitle("Select Driver").setItems(items) { _, which ->
                selectedDriverId = ids[which]
                target.text = items[which]
            }.show()
        }
    }

    private fun showBusPicker(target: TextView, capacityTarget: TextView) {
        db.collection("buses").whereEqualTo("status", "Active").get().addOnSuccessListener { snapshots ->
            val items = snapshots.map { it.getString("busNumber") ?: "N/A" }.toTypedArray()
            val capacities = snapshots.map { it.getLong("capacity")?.toInt() ?: 0 }
            val ids = snapshots.map { it.id }
            AlertDialog.Builder(context).setTitle("Select Bus").setItems(items) { _, which ->
                selectedBusId = ids[which]
                busCapacity = capacities[which]
                target.text = items[which]
                capacityTarget.text = "$busCapacity Seats"
            }.show()
        }
    }

    private fun showConductorPicker(target: TextView) {
        db.collection("conductors").whereEqualTo("status", "active").get().addOnSuccessListener { snapshots ->
            val items = snapshots.map { it.getString("firstName") + " " + it.getString("lastName") }.toTypedArray()
            val ids = snapshots.map { it.id }
            AlertDialog.Builder(context).setTitle("Select Conductor").setItems(items) { _, which ->
                selectedConductorId = ids[which]
                target.text = items[which]
            }.show()
        }
    }

    private fun setupStopPickerMap(map: MapView, countTarget: TextView) {
        map.setMultiTouchControls(true)
        map.controller.setZoom(15.0)
        map.controller.setCenter(GeoPoint(14.5995, 120.9842)) // Default Manila

        db.collection("stops").whereEqualTo("status", "active").get().addOnSuccessListener { snapshots ->
            for (doc in snapshots) {
                val lat = doc.getDouble("latitude") ?: 0.0
                val lng = doc.getDouble("longitude") ?: 0.0
                val name = doc.getString("name") ?: ""
                val stopId = doc.id

                val marker = Marker(map)
                marker.position = GeoPoint(lat, lng)
                marker.title = name
                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                
                // Interaction for selection
                marker.setOnMarkerClickListener { m, _ ->
                    if (selectedStopIds.contains(stopId)) {
                        selectedStopIds.remove(stopId)
                        m.icon = null // Default icon
                    } else {
                        selectedStopIds.add(stopId)
                        // Could use a different color icon here to show selected
                    }
                    countTarget.text = "Selected: ${selectedStopIds.size} stops"
                    true
                }
                map.overlays.add(marker)
            }
            map.invalidate()
        }
    }

    private fun saveRoute(name: String, dialog: AlertDialog) {
        if (name.isEmpty() || selectedDriverId == null || selectedBusId == null || selectedStopIds.isEmpty()) {
            Toast.makeText(context, "Please fill all required fields and select stops", Toast.LENGTH_SHORT).show()
            return
        }

        val routeData = hashMapOf(
            "routeName" to name,
            "driverId" to selectedDriverId,
            "busId" to selectedBusId,
            "conductorId" to selectedConductorId,
            "stopIds" to selectedStopIds,
            "status" to "Active",
            "maxCapacity" to busCapacity,
            "currentCapacity" to 0, // Will be updated by student assignments
            "createdAt" to com.google.firebase.Timestamp.now()
        )

        db.collection("routes").add(routeData).addOnSuccessListener {
            onRouteAdded()
            dialog.dismiss()
            Toast.makeText(context, "Route created successfully", Toast.LENGTH_SHORT).show()
        }
    }
}
