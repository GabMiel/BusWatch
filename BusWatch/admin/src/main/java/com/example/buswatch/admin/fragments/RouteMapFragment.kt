package com.example.buswatch.admin.fragments

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import com.example.buswatch.admin.AdminHome
import com.example.buswatch.admin.R
import com.example.buswatch.common.R as CommonR
import com.google.firebase.firestore.FirebaseFirestore
import org.osmdroid.bonuspack.routing.OSRMRoadManager
import org.osmdroid.bonuspack.routing.Road
import org.osmdroid.bonuspack.routing.RoadManager
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import kotlin.random.Random

class RouteMapFragment : Fragment() {
    private val db = FirebaseFirestore.getInstance()
    private val allMarkers = mutableListOf<Marker>()

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

        view.findViewById<ImageButton>(R.id.btnMyLocation)?.setOnClickListener {
            if (allMarkers.isNotEmpty()) {
                val randomMarker = allMarkers[Random.nextInt(allMarkers.size)]
                mapView?.controller?.animateTo(randomMarker.position)
                mapView?.controller?.setZoom(17.0)
            } else {
                Toast.makeText(requireContext(), "No stops found on map", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupMap(map: MapView) {
        map.setMultiTouchControls(true)
        map.controller.setZoom(14.0)
        map.controller.setCenter(GeoPoint(14.5995, 120.9842))

        val colors = listOf("#4A90E2", "#E24A4A", "#4AE24A", "#E24AE2", "#4AE2E2", "#E2E24A")
        var colorIdx = 0
        allMarkers.clear()

        db.collection("routes").whereEqualTo("status", "Active").get().addOnSuccessListener { snapshots ->
            for (doc in snapshots) {
                val routeName = doc.getString("routeName") ?: "Unknown"
                @Suppress("UNCHECKED_CAST")
                val stopIds = doc.get("stopIds") as? List<String> ?: emptyList()
                val routeColor = colors[colorIdx % colors.size].toColorInt()
                colorIdx++

                if (stopIds.isNotEmpty()) {
                    val stopPointsMap = mutableMapOf<String, GeoPoint>()
                    var loadedCount = 0
                    for (stopId in stopIds) {
                        db.collection("stops").document(stopId).get().addOnSuccessListener { stopDoc ->
                            val lat = stopDoc.getDouble("latitude")
                            val lng = stopDoc.getDouble("longitude")
                            if (lat != null && lng != null) {
                                val p = GeoPoint(lat, lng)
                                stopPointsMap[stopId] = p
                                
                                val marker = Marker(map)
                                marker.position = p
                                marker.title = "$routeName: ${stopDoc.getString("name")}"
                                marker.icon = ContextCompat.getDrawable(requireContext(), CommonR.drawable.ic_stop_marker)
                                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                map.overlays.add(marker)
                                allMarkers.add(marker)
                            }
                            loadedCount++
                            if (loadedCount == stopIds.size) {
                                val orderedPoints = stopIds.mapNotNull { stopPointsMap[it] }
                                if (orderedPoints.size > 1) {
                                    drawRoadOverlay(map, orderedPoints, routeColor)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun drawRoadOverlay(map: MapView, points: List<GeoPoint>, color: Int) {
        Thread {
            try {
                val roadManager = OSRMRoadManager(requireContext(), "BusWatch/1.0")
                val road = roadManager.getRoad(ArrayList(points))
                
                if (road.mStatus == Road.STATUS_OK) {
                    val roadOverlay = RoadManager.buildRoadOverlay(road)
                    roadOverlay.outlinePaint.color = color
                    roadOverlay.outlinePaint.strokeWidth = 10f
                    
                    activity?.runOnUiThread {
                        map.overlays.add(0, roadOverlay) // Add behind markers
                        map.invalidate()
                    }
                } else {
                    // Fallback to simple polyline if routing fails
                    activity?.runOnUiThread {
                        val line = Polyline(map)
                        line.setPoints(points)
                        line.outlinePaint.color = color
                        line.outlinePaint.strokeWidth = 10f
                        map.overlays.add(0, line)
                        map.invalidate()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }
}
