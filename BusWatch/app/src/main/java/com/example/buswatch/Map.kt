package com.example.buswatch

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import androidx.preference.PreferenceManager
import com.bumptech.glide.Glide
import com.example.buswatch.common.R as CommonR
import com.google.firebase.auth.FirebaseAuth
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
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay
import java.io.File
import java.util.ArrayList
import java.util.Locale

class Map : AppCompatActivity() {

    private lateinit var map: MapView
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var childName: String? = null
    
    private var busListener: ListenerRegistration? = null
    private var isMapMaximized = false
    private var driverMarker: Marker? = null
    private var roadOverlay: Polyline? = null
    private val allMarkers = mutableMapOf<String, Marker>()
    private var stopIds = mutableListOf<String>()
    private val stopPoints = mutableListOf<GeoPoint>()
    private var routeName: String = "Route"

    private var locationButtonMode = 1 // 1: North Up (Follow), 0: Manual
    private var lastKnownBusLocation: GeoPoint? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val ctx = applicationContext
        val config = Configuration.getInstance()
        config.userAgentValue = ctx.packageName
        config.osmdroidBasePath = File(ctx.filesDir, "osmdroid")
        config.osmdroidTileCache = File(ctx.filesDir, "osmdroid/tiles")
        config.load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx))

        setContentView(R.layout.map)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        childName = intent.getStringExtra("childName")

        findViewById<TextView>(R.id.tvChildName)?.text = childName ?: "Student"

        map = findViewById(R.id.mapView)
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        map.controller.setZoom(18.7)
        
        val rotationGestureOverlay = RotationGestureOverlay(map)
        rotationGestureOverlay.isEnabled = true
        map.overlays.add(rotationGestureOverlay)

        setupMapInteractions()
        setupMaximizeButton()
        setupLocatorButton()
        fetchInitialData()

        findViewById<ImageButton>(R.id.btnMapBack)?.setOnClickListener {
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(CommonR.anim.stay, CommonR.anim.slide_out_right)
        }

        findViewById<View>(R.id.btnMapBusDetails)?.setOnClickListener {
            val intent = android.content.Intent(this, BusDetails::class.java)
            intent.putExtra("childName", childName)
            startActivity(intent)
            @Suppress("DEPRECATION")
            overridePendingTransition(CommonR.anim.slide_in_bottom, CommonR.anim.stay)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupMapInteractions() {
        map.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                if (locationButtonMode != 0) {
                    locationButtonMode = 0
                    val btnMyLocation = findViewById<ImageButton>(R.id.btnMyLocation)
                    btnMyLocation?.setColorFilter(Color.BLACK)
                    btnMyLocation?.setImageResource(CommonR.drawable.ic_my_location)
                }
            }
            v.parent.requestDisallowInterceptTouchEvent(true)
            false
        }
    }

    private fun setupLocatorButton() {
        val btnMyLocation = findViewById<ImageButton>(R.id.btnMyLocation)
        // Changed from ic_compass to ic_my_location as requested
        btnMyLocation?.setImageResource(CommonR.drawable.ic_my_location)
        btnMyLocation?.setColorFilter("#4A90E2".toColorInt())

        btnMyLocation?.setOnClickListener {
            if (lastKnownBusLocation == null) {
                Toast.makeText(this, "Bus location not available", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            enterMode1()
        }
    }

    private fun enterMode1() {
        locationButtonMode = 1
        val btnMyLocation = findViewById<ImageButton>(R.id.btnMyLocation)
        // Consistent icon for re-centering
        btnMyLocation?.setImageResource(CommonR.drawable.ic_my_location)
        btnMyLocation?.setColorFilter("#4A90E2".toColorInt())
        
        lastKnownBusLocation?.let { gp ->
            map.controller.animateTo(gp)
            map.controller.setZoom(18.7)
        }
        map.mapOrientation = 0f
        driverMarker?.rotation = 0f
        map.invalidate()
    }

    private fun setupMaximizeButton() {
        val btnMaximize = findViewById<ImageButton>(R.id.btnMaximizeMap)
        val banner = findViewById<View>(R.id.bannerNextStop)
        val bottomInfo = findViewById<View>(R.id.layoutBottomInfo)
        val cvAvatar = findViewById<View>(R.id.cvAvatarContainer)

        btnMaximize?.setOnClickListener {
            isMapMaximized = !isMapMaximized
            banner?.isVisible = !isMapMaximized
            bottomInfo?.isVisible = !isMapMaximized
            cvAvatar?.isVisible = !isMapMaximized
            btnMaximize.setImageResource(if (isMapMaximized) CommonR.drawable.ic_close else CommonR.drawable.ic_eye)
        }
    }

    private fun fetchInitialData() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("parents").document(uid).get().addOnSuccessListener { doc ->
            if (!doc.exists()) return@addOnSuccessListener
            
            @Suppress("UNCHECKED_CAST")
            val childrenList = doc.get("children") as? List<kotlin.collections.Map<String, Any>>
            @Suppress("UNCHECKED_CAST")
            val childMap = doc.get("child") as? kotlin.collections.Map<String, Any>
            
            var targetChild: kotlin.collections.Map<String, Any>? = null
            if (childMap != null) {
                val fullName = "${childMap["firstName"]} ${childMap["lastName"]}".trim()
                if (childName == null || fullName == childName) targetChild = childMap
            }
            if (targetChild == null && childrenList != null) {
                targetChild = childrenList.find { "${it["firstName"]} ${it["lastName"]}".trim() == childName }
            }

            targetChild?.let { child ->
                val stopId = child["stop"] as? String
                if (!stopId.isNullOrEmpty()) {
                    fetchRouteForStop(stopId)
                }
            }
        }
    }

    private fun fetchRouteForStop(stopId: String) {
        db.collection("routes")
            .whereArrayContains("stopIds", stopId)
            .whereEqualTo("status", "Active")
            .limit(1)
            .get()
            .addOnSuccessListener { snapshots ->
                if (!snapshots.isEmpty) {
                    val routeDoc = snapshots.documents[0]
                    routeName = routeDoc.getString("routeName") ?: routeDoc.getString("name") ?: "Route"
                    @Suppress("UNCHECKED_CAST")
                    stopIds = (routeDoc.get("stopIds") as? List<String>)?.toMutableList() ?: mutableListOf()
                    
                    val busId = routeDoc.getString("busId") ?: ""
                    val rDriverId = routeDoc.getString("driverId")
                    val rConductorId = routeDoc.getString("conductorId")
                    
                    // Fetch initial staff info from route document immediately
                    if (!rDriverId.isNullOrEmpty()) {
                        fetchStaffInfo(rDriverId, "drivers")
                    } else if (!rConductorId.isNullOrEmpty()) {
                        fetchStaffInfo(rConductorId, "conductors")
                    }

                    if (busId.isNotEmpty()) {
                        startRealTimeBusUpdates(busId, rDriverId, rConductorId)
                    }
                    loadRouteOnMap()
                }
            }
    }

    private fun loadRouteOnMap() {
        if (stopIds.isEmpty()) return
        
        val stopIcon = getScaledDrawable(ContextCompat.getDrawable(this, CommonR.drawable.ic_stop_marker), 36, 36)
        val stopPointsLocalMap = mutableMapOf<String, GeoPoint>()
        var loaded = 0

        for (sid in stopIds) {
            db.collection("stops").document(sid).get().addOnSuccessListener { sDoc ->
                val lat = sDoc.getDouble("latitude")
                val lng = sDoc.getDouble("longitude")
                if (lat != null && lng != null) {
                    val p = GeoPoint(lat, lng)
                    stopPointsLocalMap[sid] = p
                    val marker = Marker(map)
                    marker.position = p
                    marker.title = sDoc.getString("name")
                    marker.icon = stopIcon
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    map.overlays.add(marker)
                    allMarkers[sid] = marker
                }
                loaded++
                if (loaded == stopIds.size) {
                    val ordered = stopIds.mapNotNull { stopPointsLocalMap[it] }
                    stopPoints.clear()
                    stopPoints.addAll(ordered)
                    
                    val nextStopTv = findViewById<TextView>(R.id.tvNextStopName)
                    if (nextStopTv != null && (nextStopTv.text == "Calculating..." || nextStopTv.text == "") && stopIds.isNotEmpty()) {
                        db.collection("stops").document(stopIds[0]).get().addOnSuccessListener { sDoc ->
                             if (nextStopTv.text == "Calculating..." || nextStopTv.text == "") {
                                 nextStopTv.text = sDoc.getString("name") ?: "Next Stop"
                             }
                        }
                    }

                    updateRouteWithBusLocation()
                    map.invalidate()
                }
            }
        }
    }

    private fun updateRouteWithBusLocation() {
        val busLoc = lastKnownBusLocation ?: return
        if (stopPoints.isEmpty()) return
        
        val points = mutableListOf<GeoPoint>()
        points.add(busLoc)
        points.addAll(stopPoints)
        
        if (points.size >= 2) {
            drawRoadOverlay(points)
        }
    }

    private fun drawRoadOverlay(points: List<GeoPoint>) {
        Thread {
            try {
                val roadManager = OSRMRoadManager(this, "BusWatch/1.0")
                val road = roadManager.getRoad(ArrayList(points))
                
                if (road.mStatus == Road.STATUS_OK) {
                    val newRoadOverlay = RoadManager.buildRoadOverlay(road)
                    newRoadOverlay.outlinePaint.color = "#4A90E2".toColorInt()
                    newRoadOverlay.outlinePaint.strokeWidth = 10f
                    
                    val etaSeconds = if (road.mLegs.isNotEmpty()) road.mLegs[0].mDuration else road.mDuration
                    val durationInMinutes = (etaSeconds / 60).toInt()
                    val etaText = if (durationInMinutes < 1) "1" else durationInMinutes.toString()

                    runOnUiThread {
                        roadOverlay?.let { map.overlays.remove(it) }
                        roadOverlay = newRoadOverlay
                        map.overlays.add(0, roadOverlay)
                        
                        findViewById<TextView>(R.id.tvETA)?.text = String.format(Locale.getDefault(), "ETA - %s MINS", etaText)
                        map.invalidate()
                    }
                } else {
                    runOnUiThread {
                        val line = Polyline(map)
                        line.setPoints(points)
                        line.outlinePaint.color = "#4A90E2".toColorInt()
                        line.outlinePaint.strokeWidth = 10f
                        
                        roadOverlay?.let { map.overlays.remove(it) }
                        roadOverlay = line
                        map.overlays.add(0, roadOverlay)
                        map.invalidate()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    private fun startRealTimeBusUpdates(busId: String, routeDriverId: String? = null, routeConductorId: String? = null) {
        val busIcon = getScaledDrawable(ContextCompat.getDrawable(this, CommonR.drawable.ic_bus_marker_yellow), 58, 58)
        driverMarker = Marker(map)
        driverMarker?.icon = busIcon
        driverMarker?.title = "Bus Location"
        driverMarker?.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        map.overlays.add(driverMarker)

        busListener?.remove()
        busListener = db.collection("buses").document(busId)
            .addSnapshotListener { doc, error ->
                if (error != null || doc == null || !doc.exists()) return@addSnapshotListener

                val busDriverId = doc.getString("driverId")
                val busConductorId = doc.getString("conductorId")
                val busNo = doc.getString("busNumber") ?: "---"
                
                findViewById<TextView>(R.id.tvRouteInfo)?.text = "$routeName \u2022 $busNo"
                findViewById<TextView>(R.id.tvBusStatus)?.text = "Bus Driver \u2022 $busNo"
                
                // Prioritize IDs from bus document, fallback to route document IDs
                val finalDriverId = if (!busDriverId.isNullOrEmpty()) busDriverId else routeDriverId ?: ""
                val finalConductorId = if (!busConductorId.isNullOrEmpty()) busConductorId else routeConductorId ?: ""
                
                if (finalDriverId.isNotEmpty()) {
                    fetchStaffInfo(finalDriverId, "drivers")
                } else if (finalConductorId.isNotEmpty()) {
                    fetchStaffInfo(finalConductorId, "conductors")
                }
                
                val lat = doc.getDouble("latitude")
                val lng = doc.getDouble("longitude")
                if (lat != null && lng != null) {
                    val busPoint = GeoPoint(lat, lng)
                    lastKnownBusLocation = busPoint
                    driverMarker?.position = busPoint
                    
                    if (locationButtonMode != 0) {
                        updateMapCamera(busPoint)
                    }
                    updateRouteWithBusLocation()
                }

                val nextStopRaw = doc.getString("nextStop") ?: ""
                if (nextStopRaw.isNotEmpty() && nextStopRaw != "Calculating...") {
                    if (nextStopRaw.length >= 15 && !nextStopRaw.contains(" ")) {
                        db.collection("stops").document(nextStopRaw).get().addOnSuccessListener { sDoc ->
                            val stopName = sDoc.getString("name") ?: nextStopRaw
                            findViewById<TextView>(R.id.tvNextStopName)?.text = stopName
                        }.addOnFailureListener {
                            findViewById<TextView>(R.id.tvNextStopName)?.text = nextStopRaw
                        }
                    } else {
                        findViewById<TextView>(R.id.tvNextStopName)?.text = nextStopRaw
                    }
                }
                
                val etaRaw = doc.getString("eta") ?: ""
                if (etaRaw.isNotEmpty()) {
                    val etaDisplay = if (etaRaw.contains("MIN")) etaRaw else "$etaRaw MINS"
                    findViewById<TextView>(R.id.tvETA)?.text = "ETA - $etaDisplay"
                }
                
                map.invalidate()
            }
    }

    private fun fetchStaffInfo(staffId: String, collection: String) {
        db.collection(collection).document(staffId).get().addOnSuccessListener { dDoc ->
            if (dDoc.exists()) {
                val fName = dDoc.getString("firstName") ?: ""
                val lName = dDoc.getString("lastName") ?: ""
                val dName = "$fName $lName".trim()
                findViewById<TextView>(R.id.tvDriverName)?.text = if (dName.isEmpty()) "Staff Member" else dName
                
                val avatarUrl = dDoc.getString("driverAvatar") ?: dDoc.getString("conductorAvatar") 
                    ?: dDoc.getString("driverAvatarUrl") ?: dDoc.getString("conductorAvatarUrl")
                    ?: dDoc.getString("photoUrl") ?: dDoc.getString("photoURL")
                
                val ivAvatar = findViewById<ImageView>(R.id.ivDriverAvatar)
                if (ivAvatar != null) {
                    if (!avatarUrl.isNullOrEmpty()) {
                        Glide.with(this@Map)
                            .load(avatarUrl)
                            .placeholder(CommonR.drawable.ic_person_placeholder)
                            .circleCrop()
                            .into(ivAvatar)
                    } else {
                        ivAvatar.setImageResource(CommonR.drawable.ic_person_placeholder)
                    }
                }
                
                if (collection == "conductors") {
                     val currentStatus = findViewById<TextView>(R.id.tvBusStatus)?.text?.toString() ?: ""
                     if (currentStatus.startsWith("Bus Driver")) {
                         findViewById<TextView>(R.id.tvBusStatus)?.text = currentStatus.replace("Bus Driver", "Bus Conductor")
                     }
                }
            }
        }
    }

    private fun updateMapCamera(gp: GeoPoint) {
        if (locationButtonMode == 1) {
            map.mapOrientation = 0f
            if (!isMapMaximized) {
                map.controller.animateTo(gp)
            } else {
                map.controller.setCenter(gp)
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
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return BitmapDrawable(resources, bitmap)
    }

    override fun onResume() { 
        super.onResume()
        map.onResume()
    }
    
    override fun onPause() { 
        super.onPause()
        map.onPause() 
    }
    
    override fun onDestroy() { 
        super.onDestroy()
        busListener?.remove() 
    }
}
