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
        setupUI(view)
        loadMap(view)
        calculateRouteOccupancy(view)
    }

    private fun setupUI(view: View) {
        view.findViewById<ImageButton>(R.id.btnBackRouteDetail)?.setOnClickListener { onBack?.invoke() }
        view.findViewById<TextView>(R.id.tvRouteName).text = route.routeName
        view.findViewById<TextView>(R.id.tvBusNumber).text = route.busNumber
        view.findViewById<TextView>(R.id.tvDriverName).text = route.driverName
    }

    private fun calculateRouteOccupancy(view: View) {
        db.collection("routes").document(route.id).get().addOnSuccessListener { doc ->
            @Suppress("UNCHECKED_CAST")
            val stopIds = doc.get("stopIds") as? List<String> ?: emptyList()
            
            if (stopIds.isEmpty()) {
                updateOccupancyUI(view, 0)
                return@addOnSuccessListener
            }

            // Fetch students assigned to any of these stops
            db.collection("parents")
                .whereIn("child.stop", stopIds)
                .get()
                .addOnSuccessListener { snapshots ->
                    val totalStudents = snapshots.size()
                    updateOccupancyUI(view, totalStudents)
                }
        }
    }

    private fun updateOccupancyUI(view: View, currentOccupancy: Int) {
        val tvStats = view.findViewById<TextView>(R.id.tvCapacityStats)
        val progressBar = view.findViewById<ProgressBar>(R.id.progressCapacity)
        
        tvStats.text = "$currentOccupancy / ${route.maxCapacity} Students"
        
        if (route.maxCapacity > 0) {
            progressBar.max = route.maxCapacity
            progressBar.progress = currentOccupancy
            
            // Visual feedback for overcapacity
            if (currentOccupancy > route.maxCapacity) {
                tvStats.setTextColor(Color.RED)
                Toast.makeText(requireContext(), "Warning: Route is Overloaded!", Toast.LENGTH_LONG).show()
            } else {
                tvStats.setTextColor(Color.BLACK)
            }
        }
    }

    private fun loadMap(view: View) {
        val map = view.findViewById<MapView>(R.id.mapRouteView) ?: return
        map.setMultiTouchControls(true)
        map.controller.setZoom(15.0)
        
        db.collection("routes").document(route.id).get().addOnSuccessListener { doc ->
            @Suppress("UNCHECKED_CAST")
            val stopIds = doc.get("stopIds") as? List<String> ?: emptyList()
            if (stopIds.isNotEmpty()) {
                val points = mutableListOf<GeoPoint>()
                var loadedCount = 0
                for (stopId in stopIds) {
                    db.collection("stops").document(stopId).get().addOnSuccessListener { sDoc ->
                        val lat = sDoc.getDouble("latitude")
                        val lng = sDoc.getDouble("longitude")
                        if (lat != null && lng != null) {
                            val p = GeoPoint(lat, lng)
                            points.add(p)
                            val marker = Marker(map)
                            marker.position = p
                            marker.icon = ContextCompat.getDrawable(requireContext(), CommonR.drawable.ic_stop_marker)
                            map.overlays.add(marker)
                        }
                        loadedCount++
                        if (loadedCount == stopIds.size && points.size > 1) {
                            val line = Polyline(map)
                            line.setPoints(points)
                            line.color = Color.BLUE
                            map.overlays.add(line)
                            map.controller.setCenter(points[0])
                            map.invalidate()
                        }
                    }
                }
            }
        }
    }
}
