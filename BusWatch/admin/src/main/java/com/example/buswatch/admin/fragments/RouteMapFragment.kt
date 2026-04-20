package com.example.buswatch.admin.fragments

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.buswatch.admin.AdminHome
import com.example.buswatch.admin.R
import com.example.buswatch.common.R as CommonR
import com.google.firebase.firestore.FirebaseFirestore
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

class RouteMapFragment : Fragment() {
    private val db = FirebaseFirestore.getInstance()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_route_map, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        view.findViewById<TextView>(R.id.tabRouteList)?.setOnClickListener {
            (requireActivity() as? AdminHome)?.loadRouting()
        }

        val mapView = view.findViewById<MapView>(R.id.mapAllRoutes)
        if (mapView != null) {
            setupMap(mapView)
        }
    }

    private fun setupMap(map: MapView) {
        map.setMultiTouchControls(true)
        map.controller.setZoom(14.0)
        map.controller.setCenter(GeoPoint(14.5995, 120.9842))

        val colors = listOf(Color.RED, Color.BLUE, Color.GREEN, Color.MAGENTA, Color.CYAN, Color.YELLOW)
        var colorIdx = 0

        db.collection("routes").whereEqualTo("status", "Active").get().addOnSuccessListener { snapshots ->
            for (doc in snapshots) {
                val routeName = doc.getString("routeName") ?: "Unknown"
                @Suppress("UNCHECKED_CAST")
                val stopIds = doc.get("stopIds") as? List<String> ?: emptyList()
                val routeColor = colors[colorIdx % colors.size]
                colorIdx++

                if (stopIds.isNotEmpty()) {
                    val routePoints = mutableListOf<GeoPoint>()
                    var loadedCount = 0
                    for (stopId in stopIds) {
                        db.collection("stops").document(stopId).get().addOnSuccessListener { stopDoc ->
                            val lat = stopDoc.getDouble("latitude")
                            val lng = stopDoc.getDouble("longitude")
                            if (lat != null && lng != null) {
                                val p = GeoPoint(lat, lng)
                                routePoints.add(p)
                                
                                val marker = Marker(map)
                                marker.position = p
                                marker.title = "$routeName: ${stopDoc.getString("name")}"
                                marker.icon = ContextCompat.getDrawable(requireContext(), CommonR.drawable.ic_stop_marker)
                                map.overlays.add(marker)
                            }
                            loadedCount++
                            if (loadedCount == stopIds.size && routePoints.size > 1) {
                                val line = Polyline(map)
                                line.setPoints(routePoints)
                                line.color = routeColor
                                map.overlays.add(line)
                                map.invalidate()
                            }
                        }
                    }
                }
            }
        }
    }
}
