package com.example.buswatch

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
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
import java.io.File

class Map : AppCompatActivity() {

    private lateinit var map: MapView
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var childName: String? = null
    
    private var busListener: ListenerRegistration? = null
    private var isMapMaximized = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure osmdroid
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

        // Initialize MapView
        map = findViewById(R.id.mapView)
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)

        val mapController = map.controller
        mapController.setZoom(15.0)
        mapController.setCenter(GeoPoint(14.5995, 120.9842))

        setupMaximizeButton()
        fetchInitialData()

        // Back button
        findViewById<ImageButton>(R.id.btnMapBack).setOnClickListener {
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(CommonR.anim.stay, CommonR.anim.slide_out_right)
        }

        // Bus Details button
        findViewById<View>(R.id.btnMapBusDetails).setOnClickListener {
            val intent = Intent(this, BusDetails::class.java)
            intent.putExtra("childName", childName)
            startActivity(intent)
            @Suppress("DEPRECATION")
            overridePendingTransition(CommonR.anim.slide_in_bottom, CommonR.anim.stay)
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

        db.collection("parents").document(uid).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    @Suppress("UNCHECKED_CAST")
                    val childMap = document.get("child") as? kotlin.collections.Map<String, Any>
                    val assignedBusId = childMap?.get("assignedBus") as? String ?: "Bus 001"
                    startRealTimeBusUpdates(assignedBusId)
                }
            }
    }

    private fun startRealTimeBusUpdates(busId: String) {
        busListener?.remove()
        busListener = db.collection("buses").document(busId)
            .addSnapshotListener { doc, error ->
                if (error != null || doc == null || !doc.exists()) return@addSnapshotListener

                val driverName = doc.getString("driverName") ?: "---"
                val busNo = doc.getString("busNumber") ?: busId
                
                findViewById<TextView>(R.id.tvDriverName).text = driverName
                findViewById<TextView>(R.id.tvBusStatus).text = "Bus Driver ($busNo)"
                
                // Optional: Update next stop if available in bus document
                val nextStop = doc.getString("nextStop") ?: "Calculating..."
                findViewById<TextView>(R.id.tvNextStopName).text = nextStop
                
                val eta = doc.getString("eta") ?: "-- MINS"
                findViewById<TextView>(R.id.tvETA).text = eta
            }
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
