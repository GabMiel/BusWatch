package com.example.buswatch.admin.fragments

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.example.buswatch.admin.Emergency
import com.example.buswatch.admin.R
import com.example.buswatch.common.NotificationSender
import com.example.buswatch.common.R as CommonR
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import org.osmdroid.bonuspack.routing.OSRMRoadManager
import org.osmdroid.bonuspack.routing.Road
import org.osmdroid.bonuspack.routing.RoadManager
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.text.SimpleDateFormat
import java.util.Locale

class EmergencyDetailFragment : Fragment() {

    private val db = FirebaseFirestore.getInstance()
    private lateinit var emergency: Emergency
    private var driverLocationListener: ListenerRegistration? = null
    private var driverMarker: Marker? = null
    private var map: MapView? = null
    
    private var roadOverlay: Polyline? = null
    private val allMarkers = mutableMapOf<String, Marker>()
    private val stopPoints = mutableListOf<GeoPoint>()
    
    private var locationButtonMode = 1 // 1: Auto-follow, 0: Manual
    private var isMapMaximized = false

    companion object {
        fun newInstance(emergency: Emergency) = EmergencyDetailFragment().apply {
            this.emergency = emergency
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ctx = requireContext()
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx))
        Configuration.getInstance().userAgentValue = ctx.packageName
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_emergency_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.btnBackEmergency)?.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        setupDetails(view)
        setupMap(view)
        startRealTimeTracking()

        view.findViewById<View>(R.id.btnResolveDetail)?.setOnClickListener {
            resolveEmergency()
        }
        
        val btnMyLocation = view.findViewById<android.widget.ImageButton>(R.id.btnMyLocation)
        btnMyLocation.setOnClickListener {
            enterMode1()
        }

        val btnMaximizeMap = view.findViewById<android.widget.ImageButton>(R.id.btnMaximizeMap)
        btnMaximizeMap.setOnClickListener {
            toggleMapSize()
        }
    }

    private fun setupDetails(view: View) {
        val sdf = SimpleDateFormat("MMM dd, yyyy, hh:mm a", Locale.getDefault())
        view.findViewById<TextView>(R.id.tvDetailTimestamp)?.text = "Received: ${emergency.timestamp?.toDate()?.let { sdf.format(it) } ?: "Just now"}"

        view.findViewById<View>(R.id.rowDriver)?.apply {
            findViewById<TextView>(R.id.tvLabel).text = "Driver"
            findViewById<TextView>(R.id.tvValue).text = emergency.driverName
        }
        view.findViewById<View>(R.id.rowBus)?.apply {
            findViewById<TextView>(R.id.tvLabel).text = "Bus Number"
            findViewById<TextView>(R.id.tvValue).text = emergency.busNumber ?: "N/A"
        }
        view.findViewById<View>(R.id.rowRoute)?.apply {
            findViewById<TextView>(R.id.tvLabel).text = "Route"
            findViewById<TextView>(R.id.tvValue).text = emergency.routeName ?: "N/A"
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupMap(view: View) {
        map = view.findViewById(R.id.mapView)
        map?.let { mv ->
            mv.setTileSource(TileSourceFactory.MAPNIK)
            mv.setMultiTouchControls(true)
            mv.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            mv.controller.setZoom(18.7)

            val initialPoint = GeoPoint(emergency.latitude ?: 14.5995, emergency.longitude ?: 120.9842)
            mv.controller.setCenter(initialPoint)
            
            // Set initial coordinates
            view.findViewById<TextView>(R.id.tvCoords)?.text = "Live Coordinates: ${initialPoint.latitude}, ${initialPoint.longitude}"

            mv.setOnTouchListener { v, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    if (locationButtonMode != 0) {
                        locationButtonMode = 0
                        val btn = view.findViewById<android.widget.ImageButton>(R.id.btnMyLocation)
                        btn.setColorFilter(android.graphics.Color.BLACK)
                        btn.setImageResource(CommonR.drawable.ic_my_location)
                    }
                }
                v.parent.requestDisallowInterceptTouchEvent(true)
                false
            }

            driverMarker = Marker(mv)
            updateDriverIcon()
            driverMarker?.position = initialPoint
            driverMarker?.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            driverMarker?.title = "Driver Location"
            mv.overlays.add(driverMarker)
            
            loadStops()
            mv.invalidate()
        }
        
        enterMode1()
    }

    private fun loadStops() {
        val routeId = emergency.routeId ?: return
        db.collection("routes").document(routeId).get().addOnSuccessListener { rDoc ->
            val stopIds = rDoc.get("stopIds") as? List<*> ?: return@addOnSuccessListener
            if (stopIds.isEmpty()) return@addOnSuccessListener

            val stopIcon = getScaledDrawable(ContextCompat.getDrawable(requireContext(), CommonR.drawable.ic_stop_marker), 40, 40)
            val nextStopIcon = getScaledDrawable(ContextCompat.getDrawable(requireContext(), CommonR.drawable.ic_stop_marker_red), 52, 52)

            var loaded = 0
            val stopPointsMap = mutableMapOf<String, GeoPoint>()

            for ((index, sidObj) in stopIds.withIndex()) {
                val sid = sidObj.toString()
                db.collection("stops").document(sid).get().addOnSuccessListener { sDoc ->
                    if (!isAdded) return@addOnSuccessListener
                    val mv = map ?: return@addOnSuccessListener
                    
                    val lat = sDoc.getDouble("latitude")
                    val lng = sDoc.getDouble("longitude")
                    if (lat != null && lng != null) {
                        val p = GeoPoint(lat, lng)
                        stopPointsMap[sid] = p
                        val marker = Marker(mv)
                        marker.position = p
                        marker.title = sDoc.getString("name")
                        marker.icon = if (index == 0) nextStopIcon else stopIcon
                        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        mv.overlays.add(marker)
                        allMarkers[sid] = marker
                    }
                    loaded++
                    if (loaded == stopIds.size) {
                        stopPoints.clear()
                        stopPoints.addAll(stopIds.mapNotNull { stopPointsMap[it.toString()] })
                        updateRouteOverlay()
                        mv.invalidate()
                    }
                }
            }
        }
    }

    private fun updateRouteOverlay() {
        val currentLoc = driverMarker?.position ?: return
        val points = mutableListOf<GeoPoint>()
        points.add(currentLoc)
        points.addAll(stopPoints)
        
        if (points.size > 1 && map != null) {
            drawRoadOverlay(map!!, points)
        }
    }

    private fun drawRoadOverlay(mapView: MapView, points: List<GeoPoint>) {
        val appContext = context?.applicationContext ?: return
        Thread {
            try {
                val roadManager = OSRMRoadManager(appContext, "BusWatch/1.0")
                val road = roadManager.getRoad(ArrayList(points))
                
                if (road.mStatus == Road.STATUS_OK) {
                    val newRoadOverlay = RoadManager.buildRoadOverlay(road)
                    newRoadOverlay.outlinePaint.color = "#4A90E2".toColorInt()
                    newRoadOverlay.outlinePaint.strokeWidth = 12f
                    
                    activity?.runOnUiThread {
                        if (!isAdded) return@runOnUiThread
                        roadOverlay?.let { mapView.overlays.remove(it) }
                        roadOverlay = newRoadOverlay
                        mapView.overlays.add(0, roadOverlay)
                        mapView.invalidate()
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }.start()
    }

    private fun updateDriverIcon() {
        val icon = getScaledDrawable(ContextCompat.getDrawable(requireContext(), CommonR.drawable.ic_bus_marker_yellow), 58, 58)
        driverMarker?.icon = icon
    }

    private fun enterMode1() {
        locationButtonMode = 1
        val btnMyLocation = view?.findViewById<android.widget.ImageButton>(R.id.btnMyLocation)
        btnMyLocation?.setColorFilter("#4A90E2".toColorInt())
        // Keep the re-centering icon (ic_my_location) instead of switching to compass
        btnMyLocation?.setImageResource(CommonR.drawable.ic_my_location)
        
        driverMarker?.position?.let { gp ->
            map?.controller?.animateTo(gp)
            map?.controller?.setZoom(18.7)
        }
        map?.mapOrientation = 0f
        driverMarker?.rotation = 0f
        map?.invalidate()
    }

    private fun toggleMapSize() {
        val v = view ?: return
        isMapMaximized = !isMapMaximized
        
        val header = v.findViewById<View>(R.id.header_container)
        val details = v.findViewById<View>(R.id.details_container)
        val bottomActions = v.findViewById<View>(R.id.layout_bottom_actions)
        val mapContainer = v.findViewById<View>(R.id.mapContainer)
        val btnMaximizeMap = v.findViewById<android.widget.ImageButton>(R.id.btnMaximizeMap)

        if (isMapMaximized) {
            header?.visibility = View.GONE
            details?.visibility = View.GONE
            bottomActions?.visibility = View.GONE
            
            // Fill the screen
            val params = mapContainer?.layoutParams
            params?.height = ViewGroup.LayoutParams.MATCH_PARENT
            mapContainer?.layoutParams = params
            
            btnMaximizeMap?.setImageResource(CommonR.drawable.ic_close)
        } else {
            header?.visibility = View.VISIBLE
            details?.visibility = View.VISIBLE
            bottomActions?.visibility = View.VISIBLE
            
            // Return to normal height
            val params = mapContainer?.layoutParams
            params?.height = (350 * resources.displayMetrics.density).toInt()
            mapContainer?.layoutParams = params

            btnMaximizeMap?.setImageResource(CommonR.drawable.ic_eye)
        }
        btnMaximizeMap?.setColorFilter(android.graphics.Color.BLACK)
        
        if (locationButtonMode == 1) enterMode1()
    }

    private fun startRealTimeTracking() {
        driverLocationListener = db.collection("drivers").document(emergency.driverId)
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) return@addSnapshotListener
                
                val lat = snapshot.getDouble("latitude")
                val lng = snapshot.getDouble("longitude")
                
                if (lat != null && lng != null) {
                    val newPoint = GeoPoint(lat, lng)
                    driverMarker?.position = newPoint
                    
                    if (locationButtonMode == 1) {
                        map?.controller?.animateTo(newPoint)
                    }
                    
                    updateRouteOverlay()
                    view?.findViewById<TextView>(R.id.tvCoords)?.text = "Live Coordinates: $lat, $lng"
                    map?.invalidate()
                }
            }
    }

    private fun resolveEmergency() {
        db.collection("emergencies").document(emergency.id)
            .update("status", "resolved")
            .addOnSuccessListener {
                if (isAdded) {
                    notifyParentsResolved()
                    // Signal EmergenciesFragment to refresh its list
                    parentFragmentManager.setFragmentResult("emergency_request", Bundle().apply { 
                        putBoolean("refresh", true) 
                    })
                    Toast.makeText(requireContext(), "Emergency resolved", Toast.LENGTH_SHORT).show()
                    parentFragmentManager.popBackStack()
                }
            }
            .addOnFailureListener {
                if (isAdded) {
                    Toast.makeText(requireContext(), "Failed to resolve", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun notifyParentsResolved() {
        val routeId = emergency.routeId ?: return
        val busLabel = emergency.busNumber ?: emergency.routeName ?: "your child's bus"

        db.collection("routes").document(routeId).get().addOnSuccessListener { rDoc ->
            val stopIds = rDoc.get("stopIds") as? List<*> ?: return@addOnSuccessListener
            if (stopIds.isEmpty()) return@addOnSuccessListener

            val chunks = stopIds.map { it.toString() }.chunked(30)
            for (chunk in chunks) {
                db.collection("parents")
                    .whereIn("child.stop", chunk)
                    .get()
                    .addOnSuccessListener { snapshots ->
                        for (doc in snapshots) {
                            val parentId = doc.id
                            val title = "✅ SOS Resolved"
                            val message = "The emergency alert for Bus $busLabel has been resolved. The situation is under control. Thank you for your patience."
                            val notifData = hashMapOf(
                                "title" to title,
                                "message" to message,
                                "timestamp" to FieldValue.serverTimestamp(),
                                "isRead" to false,
                                "type" to "sos_resolved"
                            )
                            db.collection("parents").document(parentId).collection("notifications").add(notifData)
                            NotificationSender.sendNotification(parentId, title, message)
                        }
                    }
            }
        }
    }

    private fun getScaledDrawable(drawable: Drawable?, widthDp: Int, heightDp: Int): Drawable? {
        if (drawable == null) return null
        val density = resources.displayMetrics.density
        val width = (widthDp * density).toInt()
        val height = (heightDp * density).toInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, width, height)
        drawable.draw(canvas)
        return bitmap.toDrawable(resources)
    }

    override fun onResume() {
        super.onResume()
        map?.onResume()
    }

    override fun onPause() {
        super.onPause()
        map?.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        driverLocationListener?.remove()
        map = null
    }
}
