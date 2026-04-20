package com.example.buswatch.admin.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.buswatch.admin.AdminHome
import com.example.buswatch.admin.R
import com.example.buswatch.admin.RouteAdmin
import com.example.buswatch.common.R as CommonR
import com.google.firebase.firestore.FirebaseFirestore
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class RouteEditFragment : Fragment() {
    private val db = FirebaseFirestore.getInstance()
    private lateinit var route: RouteAdmin

    companion object {
        fun newInstance(route: RouteAdmin) = RouteEditFragment().apply {
            this.route = route
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_edit_route, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI(view)
    }

    private fun setupUI(view: View) {
        view.findViewById<ImageButton>(R.id.btnBackEditRoute)?.setOnClickListener { 
            (requireActivity() as? AdminHome)?.loadRouting()
        }
        
        view.findViewById<View>(R.id.btnCancelEditRoute)?.setOnClickListener {
            (requireActivity() as? AdminHome)?.loadRouting()
        }
        
        val etName = view.findViewById<EditText>(R.id.etRouteName)
        val tvDriver = view.findViewById<TextView>(R.id.tvSelectedDriver)
        val tvBus = view.findViewById<TextView>(R.id.tvSelectedBus)
        val tvBusCapacity = view.findViewById<TextView>(R.id.tvBusCapacity)
        val tvConductor = view.findViewById<TextView>(R.id.tvSelectedConductor)
        val tvStops = view.findViewById<TextView>(R.id.tvStopsCount)
        val mapPicker = view.findViewById<MapView>(R.id.mapRoutePicker)
        
        etName.setText(route.routeName)
        tvDriver.text = route.driverName
        tvBus.text = route.busNumber
        tvBusCapacity.text = route.maxCapacity.toString()
        
        var selectedDriverId = ""
        var selectedBusId = ""
        var selectedConductorId = ""
        var maxCapacity = route.maxCapacity
        val selectedStopIds = mutableListOf<String>()

        db.collection("routes").document(route.id).get().addOnSuccessListener { doc ->
            selectedDriverId = doc.getString("driverId") ?: ""
            selectedBusId = doc.getString("busId") ?: ""
            selectedConductorId = doc.getString("conductorId") ?: ""
            val conductorName = doc.getString("conductorName") ?: "Select Conductor"
            tvConductor.text = conductorName
            
            @Suppress("UNCHECKED_CAST")
            val stopIds = doc.get("stopIds") as? List<String> ?: emptyList()
            selectedStopIds.addAll(stopIds)
            tvStops.text = "Selected: ${selectedStopIds.size} stops"
            
            mapPicker.setMultiTouchControls(true)
            mapPicker.controller.setZoom(15.0)
            mapPicker.controller.setCenter(GeoPoint(14.5995, 120.9842))

            db.collection("stops").whereEqualTo("status", "active").get().addOnSuccessListener { stopsSnap ->
                for (sDoc in stopsSnap) {
                    val lat = sDoc.getDouble("latitude")
                    val lng = sDoc.getDouble("longitude")
                    if (lat != null && lng != null) {
                        val marker = Marker(mapPicker)
                        marker.position = GeoPoint(lat, lng)
                        marker.title = sDoc.getString("name")
                        marker.icon = ContextCompat.getDrawable(requireContext(), 
                            if (selectedStopIds.contains(sDoc.id)) CommonR.drawable.ic_stop_marker_red 
                            else CommonR.drawable.ic_stop_marker)
                        
                        marker.setOnMarkerClickListener { m, _ ->
                            if (selectedStopIds.contains(sDoc.id)) {
                                selectedStopIds.remove(sDoc.id)
                                m.icon = ContextCompat.getDrawable(requireContext(), CommonR.drawable.ic_stop_marker)
                            } else {
                                selectedStopIds.add(sDoc.id)
                                m.icon = ContextCompat.getDrawable(requireContext(), CommonR.drawable.ic_stop_marker_red)
                            }
                            tvStops.text = "Selected: ${selectedStopIds.size} stops"
                            true
                        }
                        mapPicker.overlays.add(marker)
                    }
                }
                mapPicker.invalidate()
            }
        }

        view.findViewById<View>(R.id.btnDriverDropdown).setOnClickListener {
            db.collection("drivers").whereEqualTo("status", "active").get().addOnSuccessListener { snapshots ->
                val drivers = snapshots.map { "${it.getString("firstName")} ${it.getString("lastName")}" }.toTypedArray()
                val ids = snapshots.map { it.id }
                AlertDialog.Builder(requireContext()).setTitle("Select Driver").setItems(drivers) { _, i ->
                    tvDriver.text = drivers[i]
                    selectedDriverId = ids[i]
                }.show()
            }
        }

        view.findViewById<View>(R.id.btnBusDropdown).setOnClickListener {
            db.collection("buses").whereEqualTo("status", "Active").get().addOnSuccessListener { snapshots ->
                val buses = snapshots.map { it.getString("busNumber") ?: "" }.toTypedArray()
                val ids = snapshots.map { it.id }
                val caps = snapshots.map { it.getString("capacity")?.toIntOrNull() ?: it.getLong("capacity")?.toInt() ?: 0 }
                AlertDialog.Builder(requireContext()).setTitle("Select Bus").setItems(buses) { _, i ->
                    tvBus.text = buses[i]
                    selectedBusId = ids[i]
                    maxCapacity = caps[i]
                    tvBusCapacity.text = maxCapacity.toString()
                }.show()
            }
        }

        view.findViewById<View>(R.id.btnConductorDropdown).setOnClickListener {
            db.collection("conductors").whereEqualTo("status", "active").get().addOnSuccessListener { snapshots ->
                val names = snapshots.map { "${it.getString("firstName")} ${it.getString("lastName")}" }.toTypedArray()
                val ids = snapshots.map { it.id }
                
                val items = arrayOf("None") + names
                
                AlertDialog.Builder(requireContext()).setTitle("Select Conductor").setItems(items) { _, i ->
                    if (i == 0) {
                        tvConductor.text = "Select Conductor"
                        selectedConductorId = ""
                    } else {
                        tvConductor.text = names[i - 1]
                        selectedConductorId = ids[i - 1]
                    }
                }.show()
            }
        }

        view.findViewById<View>(R.id.btnSaveRouteChanges).setOnClickListener {
            val name = etName.text.toString().trim()
            if (name.isEmpty() || selectedDriverId.isEmpty() || selectedBusId.isEmpty() || selectedStopIds.isEmpty()) {
                Toast.makeText(requireContext(), "Please complete all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val updates = hashMapOf(
                "routeName" to name,
                "driverId" to selectedDriverId,
                "driverName" to tvDriver.text.toString(),
                "busId" to selectedBusId,
                "busNumber" to tvBus.text.toString(),
                "conductorId" to selectedConductorId,
                "conductorName" to if (selectedConductorId.isEmpty()) "" else tvConductor.text.toString(),
                "maxCapacity" to maxCapacity,
                "stopIds" to selectedStopIds
            )
            db.collection("routes").document(route.id).update(updates).addOnSuccessListener {
                Toast.makeText(requireContext(), "Route updated", Toast.LENGTH_SHORT).show()
                (requireActivity() as? AdminHome)?.loadRouting()
            }
        }
    }
}
