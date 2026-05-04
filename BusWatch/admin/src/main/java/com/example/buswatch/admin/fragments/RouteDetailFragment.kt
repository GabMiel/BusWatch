package com.example.buswatch.admin.fragments

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.buswatch.admin.*
import com.example.buswatch.common.R as CommonR
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import org.osmdroid.bonuspack.routing.OSRMRoadManager
import org.osmdroid.bonuspack.routing.Road
import org.osmdroid.bonuspack.routing.RoadManager
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

class RouteDetailFragment : Fragment() {
    private val db = FirebaseFirestore.getInstance()
    private lateinit var route: RouteAdmin
    private var onBack: (() -> Unit)? = null
    private var isMaximized = false
    private var firstStopPoint: GeoPoint? = null
    
    private var routeListener: ListenerRegistration? = null
    private var occupancyListener: ListenerRegistration? = null
    private val assignedStudents = mutableListOf<AssignedStudent>()
    private var mapView: MapView? = null

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
        
        mapView = view.findViewById(R.id.mapRouteView)
        mapView?.setTileSource(TileSourceFactory.MAPNIK)
        mapView?.setMultiTouchControls(true)

        // Allow map scrolling inside NestedScrollView
        mapView?.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> v.parent.requestDisallowInterceptTouchEvent(true)
                MotionEvent.ACTION_UP -> v.parent.requestDisallowInterceptTouchEvent(false)
            }
            false
        }
        
        val rv = view.findViewById<RecyclerView>(R.id.recyclerRouteStudents)
        rv?.layoutManager = LinearLayoutManager(requireContext())
        
        setupMapControls(view)
        startRouteListener(view)
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView?.onPause()
    }

    override fun onDestroyView() {
        routeListener?.remove()
        occupancyListener?.remove()
        mapView = null
        super.onDestroyView()
    }

    private fun setupMapControls(view: View) {
        val btnMaximize = view.findViewById<ImageButton>(R.id.btnMaximizeMap)
        val btnRecenter = view.findViewById<ImageButton>(R.id.btnRecenterRoute)
        val mapContainer = view.findViewById<FrameLayout>(R.id.mapContainer)

        btnMaximize?.setOnClickListener {
            isMaximized = !isMaximized
            val params = mapContainer?.layoutParams
            if (isMaximized) {
                params?.height = (500 * resources.displayMetrics.density).toInt()
                btnMaximize.setImageResource(CommonR.drawable.ic_close)
            } else {
                params?.height = (300 * resources.displayMetrics.density).toInt()
                btnMaximize.setImageResource(CommonR.drawable.ic_eye)
            }
            mapContainer?.layoutParams = params
            mapView?.postDelayed({
                firstStopPoint?.let { mapView?.controller?.animateTo(it) }
            }, 200)
        }

        btnRecenter?.setOnClickListener {
            firstStopPoint?.let {
                mapView?.controller?.animateTo(it)
                mapView?.controller?.setZoom(16.0)
            }
        }
    }

    private fun startRouteListener(view: View) {
        routeListener?.remove()
        routeListener = db.collection("routes").document(route.id).addSnapshotListener { doc, e ->
            if (e != null || doc == null || !doc.exists()) return@addSnapshotListener
            
            val routeName = doc.getString("routeName") ?: "N/A"
            val busId = doc.getString("busId")
            val driverId = doc.getString("driverId")
            val conductorId = doc.getString("conductorId")
            val maxCapacity = doc.getLong("maxCapacity")?.toInt() ?: 0
            
            val ms = doc.getString("morningStartTime") ?: "---"
            val me = doc.getString("morningEndTime") ?: "---"
            val asTime = doc.getString("afternoonStartTime") ?: "---"
            val ae = doc.getString("afternoonEndTime") ?: "---"
            
            view.findViewById<TextView>(R.id.tvMorningStart).text = ms
            view.findViewById<TextView>(R.id.tvMorningEnd).text = me
            view.findViewById<TextView>(R.id.tvAfternoonStart).text = asTime
            view.findViewById<TextView>(R.id.tvAfternoonEnd).text = ae

            @Suppress("UNCHECKED_CAST")
            val stopIds = doc.get("stopIds") as? List<String> ?: emptyList()

            view.findViewById<TextView>(R.id.tvRouteName).text = routeName
            
            if (driverId != null) {
                db.collection("drivers").document(driverId).get().addOnSuccessListener { dDoc ->
                    if (!isAdded) return@addOnSuccessListener
                    val firstName = dDoc.getString("firstName") ?: ""
                    val lastName = dDoc.getString("lastName") ?: ""
                    view.findViewById<TextView>(R.id.tvDriverName).text = "$firstName $lastName".trim().ifEmpty { "N/A" }
                }
            } else {
                view.findViewById<TextView>(R.id.tvDriverName).text = "Not Assigned"
            }

            if (busId != null) {
                db.collection("buses").document(busId).get().addOnSuccessListener { bDoc ->
                    if (!isAdded) return@addOnSuccessListener
                    view.findViewById<TextView>(R.id.tvBusNumber).text = bDoc.getString("busNumber") ?: "N/A"
                }
            } else {
                view.findViewById<TextView>(R.id.tvBusNumber).text = "Not Assigned"
            }
            
            if (conductorId != null) {
                db.collection("conductors").document(conductorId).get().addOnSuccessListener { cDoc ->
                    if (!isAdded) return@addOnSuccessListener
                    val firstName = cDoc.getString("firstName") ?: ""
                    val lastName = cDoc.getString("lastName") ?: ""
                    view.findViewById<TextView>(R.id.tvConductorName).text = "$firstName $lastName".trim().ifEmpty { "N/A" }
                }
            } else {
                view.findViewById<TextView>(R.id.tvConductorName).text = "Not Assigned"
            }

            startOccupancyListener(view, stopIds, maxCapacity)
            loadMap(stopIds)
        }
    }

    private fun startOccupancyListener(view: View, stopIds: List<String>, maxCapacity: Int) {
        occupancyListener?.remove()
        if (stopIds.isEmpty()) {
            updateOccupancyUI(view, 0, maxCapacity)
            assignedStudents.clear()
            view.findViewById<RecyclerView>(R.id.recyclerRouteStudents)?.adapter = AssignedStudentAdapter(emptyList())
            return
        }

        occupancyListener = db.collection("parents")
            .whereIn("child.stop", stopIds)
            .addSnapshotListener { snapshots, e ->
                if (e != null || snapshots == null) return@addSnapshotListener
                if (!isAdded) return@addSnapshotListener
                
                assignedStudents.clear()
                for (doc in snapshots) {
                    @Suppress("UNCHECKED_CAST")
                    val child = doc.get("child") as? Map<String, Any>
                    if (child != null && stopIds.contains(child["stop"] as? String)) {
                        addStudentToList(doc.id, child)
                    }

                    @Suppress("UNCHECKED_CAST")
                    val additional = doc.get("children") as? List<Map<String, Any>>
                    additional?.forEach { c ->
                        if (stopIds.contains(c["stop"] as? String)) {
                            addStudentToList(doc.id, c)
                        }
                    }
                }
                
                updateOccupancyUI(view, assignedStudents.size, maxCapacity)
                view.findViewById<RecyclerView>(R.id.recyclerRouteStudents)?.adapter = AssignedStudentAdapter(assignedStudents)
            }
    }

    private fun addStudentToList(parentId: String, data: Map<String, Any>) {
        val fName = data["firstName"] as? String ?: ""
        val lName = data["lastName"] as? String ?: ""
        val grade = data["grade"] as? String ?: "N/A"
        val photoUrl = (data["childAvatarUrl"] as? String) ?: (data["avatarUrl"] as? String) ?: ""
        assignedStudents.add(AssignedStudent(parentId, "$fName $lName", grade, photoUrl))
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
            } else {
                tvStats.setTextColor(Color.BLACK)
            }
        }
    }

    private fun loadMap(stopIds: List<String>) {
        val map = mapView ?: return
        
        map.overlays.clear()
        
        if (stopIds.isNotEmpty()) {
            var loadedCount = 0
            val stopPointsMap = mutableMapOf<String, GeoPoint>()

            for (stopId in stopIds) {
                db.collection("stops").document(stopId).get().addOnSuccessListener { sDoc ->
                    if (!isAdded) return@addOnSuccessListener
                    val lat = sDoc.getDouble("latitude")
                    val lng = sDoc.getDouble("longitude")
                    if (lat != null && lng != null) {
                        val p = GeoPoint(lat, lng)
                        stopPointsMap[stopId] = p
                        
                        val marker = Marker(map)
                        marker.position = p
                        marker.title = sDoc.getString("name") ?: "Stop"
                        marker.icon = ContextCompat.getDrawable(requireContext(), CommonR.drawable.ic_stop_marker)
                        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        map.overlays.add(marker)
                    }
                    loadedCount++
                    
                    if (loadedCount == stopIds.size) {
                        val orderedPoints = stopIds.mapNotNull { stopPointsMap[it] }
                        if (orderedPoints.isNotEmpty()) {
                            firstStopPoint = orderedPoints[0]
                            map.controller?.setCenter(firstStopPoint)
                            map.controller?.setZoom(15.0)

                            if (orderedPoints.size > 1) {
                                drawRoadOverlay(map, orderedPoints)
                            }
                        }
                        map.invalidate()
                    }
                }
            }
        }
    }

    private fun drawRoadOverlay(map: MapView, points: List<GeoPoint>) {
        Thread {
            try {
                if (!isAdded) return@Thread
                val roadManager = OSRMRoadManager(requireContext(), "BusWatch/1.0")
                val road = roadManager.getRoad(ArrayList(points))
                
                if (road.mStatus == Road.STATUS_OK) {
                    val roadOverlay = RoadManager.buildRoadOverlay(road)
                    roadOverlay.outlinePaint.color = "#4A90E2".toColorInt()
                    roadOverlay.outlinePaint.strokeWidth = 10f
                    
                    activity?.runOnUiThread {
                        if (isAdded) {
                            map.overlays.add(0, roadOverlay)
                            map.invalidate()
                        }
                    }
                } else {
                    activity?.runOnUiThread {
                        if (isAdded) {
                            val line = Polyline(map)
                            line.setPoints(points)
                            line.outlinePaint.color = "#4A90E2".toColorInt()
                            line.outlinePaint.strokeWidth = 10f
                            map.overlays.add(0, line)
                            map.invalidate()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }
}
