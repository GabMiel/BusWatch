package com.example.buswatch.admin.fragments

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
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
import org.osmdroid.views.overlay.Polyline

class RouteDetailFragment : Fragment() {
    private val db = FirebaseFirestore.getInstance()
    private lateinit var route: RouteAdmin
    private var onBack: (() -> Unit)? = null

    companion object {
        fun newInstance(route: RouteAdmin, onBack: () -> Unit) = RouteDetailFragment().apply {
            this.route = route
            this.onBack = onBack
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_view_route, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<ImageButton>(R.id.btnBackRouteDetail)?.setOnClickListener { onBack?.invoke() }
        fetchRouteDetails(view)
    }

    private fun fetchRouteDetails(view: View) {
        db.collection("routes").document(route.id).get().addOnSuccessListener { doc ->
            if (!doc.exists()) return@addOnSuccessListener
            
            val routeName = doc.getString("routeName") ?: "N/A"
            val busId = doc.getString("busId")
            val driverId = doc.getString("driverId")
            val conductorId = doc.getString("conductorId")
            val maxCapacity = doc.getLong("maxCapacity")?.toInt() ?: 0
            @Suppress("UNCHECKED_CAST")
            val stopIds = doc.get("stopIds") as? List<String> ?: emptyList()

            view.findViewById<TextView>(R.id.tvRouteName).text = routeName
            
            // Fetch Driver Name
            if (driverId != null) {
                db.collection("drivers").document(driverId).get().addOnSuccessListener { dDoc ->
                    val firstName = dDoc.getString("firstName") ?: ""
                    val lastName = dDoc.getString("lastName") ?: ""
                    view.findViewById<TextView>(R.id.tvDriverName).text = "$firstName $lastName".trim().ifEmpty { "N/A" }
                }
            } else {
                view.findViewById<TextView>(R.id.tvDriverName).text = "Not Assigned"
            }

            // Fetch Bus Number
            if (busId != null) {
                db.collection("buses").document(busId).get().addOnSuccessListener { bDoc ->
                    view.findViewById<TextView>(R.id.tvBusNumber).text = bDoc.getString("busNumber") ?: "N/A"
                }
            } else {
                view.findViewById<TextView>(R.id.tvBusNumber).text = "Not Assigned"
            }
            
            // Fetch Conductor Name
            if (conductorId != null) {
                db.collection("conductors").document(conductorId).get().addOnSuccessListener { cDoc ->
                    val firstName = cDoc.getString("firstName") ?: ""
                    val lastName = cDoc.getString("lastName") ?: ""
                    view.findViewById<TextView>(R.id.tvConductorName).text = "$firstName $lastName".trim().ifEmpty { "N/A" }
                }
            } else {
                view.findViewById<TextView>(R.id.tvConductorName).text = "Not Assigned"
            }

            // Calculate occupancy
            if (stopIds.isNotEmpty()) {
                db.collection("parents")
                    .whereIn("child.stop", stopIds)
                    .get()
                    .addOnSuccessListener { snapshots ->
                        updateOccupancyUI(view, snapshots.size(), maxCapacity)
                    }
            } else {
                updateOccupancyUI(view, 0, maxCapacity)
            }
            
            loadMap(view, stopIds)
        }
    }

    private fun updateOccupancyUI(view: View, currentOccupancy: Int, maxCapacity: Int) {
        val tvStats = view.findViewById<TextView>(R.id.tvCapacityStats)
        val progressBar = view.findViewById<ProgressBar>(R.id.progressCapacity)
        
        tvStats.text = "$currentOccupancy / $maxCapacity Students"
        
        if (maxCapacity > 0) {
            progressBar.max = maxCapacity
            progressBar.progress = currentOccupancy
            
            if (currentOccupancy > maxCapacity) {
                tvStats.setTextColor(Color.RED)
                Toast.makeText(requireContext(), "Warning: Route is Overloaded!", Toast.LENGTH_LONG).show()
            } else {
                tvStats.setTextColor(Color.BLACK)
            }
        }
    }

    private fun loadMap(view: View, stopIds: List<String>) {
        val map = view.findViewById<MapView>(R.id.mapRouteView) ?: return
        map.setMultiTouchControls(true)
        map.controller.setZoom(15.0)
        
        if (stopIds.isNotEmpty()) {
            val points = mutableListOf<GeoPoint>()
            var loadedCount = 0
            
            // Temporary map to maintain order if possible, but Firestore calls are async
            val stopPointsMap = mutableMapOf<String, GeoPoint>()

            for (stopId in stopIds) {
                db.collection("stops").document(stopId).get().addOnSuccessListener { sDoc ->
                    val lat = sDoc.getDouble("latitude")
                    val lng = sDoc.getDouble("longitude")
                    if (lat != null && lng != null) {
                        val p = GeoPoint(lat, lng)
                        stopPointsMap[stopId] = p
                        
                        val marker = Marker(map)
                        marker.position = p
                        marker.title = sDoc.getString("name") ?: "Stop"
                        marker.icon = ContextCompat.getDrawable(requireContext(), CommonR.drawable.ic_stop_marker)
                        map.overlays.add(marker)
                    }
                    loadedCount++
                    
                    if (loadedCount == stopIds.size) {
                        // All stops loaded, draw polyline in order of stopIds
                        val orderedPoints = stopIds.mapNotNull { stopPointsMap[it] }
                        if (orderedPoints.size > 1) {
                            val line = Polyline(map)
                            line.setPoints(orderedPoints)
                            line.color = Color.BLUE
                            map.overlays.add(line)
                            map.controller.setCenter(orderedPoints[0])
                        } else if (orderedPoints.size == 1) {
                            map.controller.setCenter(orderedPoints[0])
                        }
                        map.invalidate()
                    }
                }
            }
        }
    }
}
