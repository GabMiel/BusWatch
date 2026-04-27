package com.example.buswatch

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import androidx.preference.PreferenceManager
import com.example.buswatch.common.R as CommonR
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File

class Map : AppCompatActivity() {

    private lateinit var map: MapView
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var childName: String? = null
    
    private var busListener: ListenerRegistration? = null
    private var isMapMaximized = false
    private var driverMarker: Marker? = null
    private val allMarkers = mutableMapOf<String, Marker>()
    private var stopIds = mutableListOf<String>()

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

        findViewById<TextView>(R.id.tvChildName).text = childName ?: "Student"

        map = findViewById(R.id.mapView)
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.controller.setZoom(15.0)
        map.controller.setCenter(GeoPoint(14.5995, 120.9842))

        setupMapInteractions()
        setupMaximizeButton()
        setupLocatorButton()
        fetchInitialData()

        findViewById<ImageButton>(R.id.btnMapBack).setOnClickListener {
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(CommonR.anim.stay, CommonR.anim.slide_out_right)
        }

        findViewById<View>(R.id.btnMapBusDetails).setOnClickListener {
            val intent = Intent(this, BusDetails::class.java)
            intent.putExtra("childName", childName)
            startActivity(intent)
            @Suppress("DEPRECATION")
            overridePendingTransition(CommonR.anim.slide_in_bottom, CommonR.anim.stay)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupMapInteractions() {
        map.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> v.parent.requestDisallowInterceptTouchEvent(true)
                MotionEvent.ACTION_UP -> v.performClick()
            }
            false
        }
    }

    private fun setupLocatorButton() {
        findViewById<ImageButton>(R.id.btnMyLocation)?.setOnClickListener {
            driverMarker?.let { dm ->
                map.controller.animateTo(dm.position)
                map.controller.setZoom(17.5)
            } ?: Toast.makeText(this, "Driver location not available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupMaximizeButton() {
        val btnMaximize = findViewById<ImageButton>(R.id.btnMaximizeMap)
        val banner = findViewById<View>(R.id.bannerNextStop)
        val bottomInfo = findViewById<View>(R.id.layoutBottomInfo)

        btnMaximize?.setOnClickListener {
            isMapMaximized = !isMapMaximized
            banner?.isVisible = !isMapMaximized
            bottomInfo?.isVisible = !isMapMaximized
            btnMaximize.setImageResource(if (isMapMaximized) CommonR.drawable.ic_close else CommonR.drawable.ic_eye)
        }
    }

    private fun fetchInitialData() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("parents").document(uid).get().addOnSuccessListener { doc ->
            if (doc.exists()) {
                @Suppress("UNCHECKED_CAST")
                val childMap = doc.get("child") as? kotlin.collections.Map<String, Any>
                @Suppress("UNCHECKED_CAST")
                val childrenList = doc.get("children") as? List<kotlin.collections.Map<String, Any>>
                
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
                    if (stopId != null) {
                        fetchRouteForStop(stopId)
                    }
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
                    @Suppress("UNCHECKED_CAST")
                    stopIds = (routeDoc.get("stopIds") as? List<String>)?.toMutableList() ?: mutableListOf()
                    
                    val busId = routeDoc.getString("busId") ?: ""
                    if (busId.isNotEmpty()) {
                        startRealTimeBusUpdates(busId)
                    }
                    loadRouteOnMap()
                }
            }
    }

    private fun loadRouteOnMap() {
        if (stopIds.isEmpty()) return
        
        val stopIcon = getScaledDrawable(ContextCompat.getDrawable(this, CommonR.drawable.ic_stop_marker), 36, 36)
        val stopPoints = mutableMapOf<String, GeoPoint>()
        var loaded = 0

        for (sid in stopIds) {
            db.collection("stops").document(sid).get().addOnSuccessListener { sDoc ->
                val lat = sDoc.getDouble("latitude")
                val lng = sDoc.getDouble("longitude")
                if (lat != null && lng != null) {
                    val p = GeoPoint(lat, lng)
                    stopPoints[sid] = p
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
                    val ordered = stopIds.mapNotNull { stopPoints[it] }
                    if (ordered.size > 1) {
                        val poly = Polyline(map)
                        poly.setPoints(ordered)
                        poly.outlinePaint.color = "#4A90E2".toColorInt()
                        poly.outlinePaint.strokeWidth = 8f
                        map.overlays.add(poly)
                    }
                    map.invalidate()
                }
            }
        }
    }

    private fun startRealTimeBusUpdates(busId: String) {
        // Driver Marker Setup
        val busIcon = getScaledDrawable(ContextCompat.getDrawable(this, CommonR.drawable.ic_bus), 48, 48)
        driverMarker = Marker(map)
        driverMarker?.icon = busIcon
        driverMarker?.title = "Bus Location"
        driverMarker?.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        map.overlays.add(driverMarker)

        busListener?.remove()
        busListener = db.collection("buses").document(busId)
            .addSnapshotListener { doc, error ->
                if (error != null || doc == null || !doc.exists()) return@addSnapshotListener

                val driverId = doc.getString("driverId") ?: ""
                val busNo = doc.getString("busNumber") ?: "---"
                
                // Fetch driver details if not in bus doc
                if (driverId.isNotEmpty()) {
                    db.collection("drivers").document(driverId).get().addOnSuccessListener { dDoc ->
                        val dName = "${dDoc.getString("firstName")} ${dDoc.getString("lastName")}"
                        findViewById<TextView>(R.id.tvDriverName).text = dName
                    }
                }
                
                findViewById<TextView>(R.id.tvBusStatus).text = "Bus Driver ($busNo)"
                
                // Real-time location
                val lat = doc.getDouble("latitude")
                val lng = doc.getDouble("longitude")
                if (lat != null && lng != null) {
                    val busPoint = GeoPoint(lat, lng)
                    driverMarker?.position = busPoint
                    if (!isMapMaximized) map.controller.animateTo(busPoint)
                }

                val nextStop = doc.getString("nextStop") ?: "Calculating..."
                findViewById<TextView>(R.id.tvNextStopName).text = nextStop
                
                val eta = doc.getString("eta") ?: "-- MINS"
                findViewById<TextView>(R.id.tvETA).text = eta
                
                map.invalidate()
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

    override fun onResume() { super.onResume(); map.onResume() }
    override fun onPause() { super.onPause(); map.onPause() }
    override fun onDestroy() { super.onDestroy(); busListener?.remove() }
}
