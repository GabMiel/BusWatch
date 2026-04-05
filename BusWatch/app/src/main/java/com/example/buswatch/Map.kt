package com.example.buswatch

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.example.buswatch.common.R as CommonR
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure osmdroid safely for SDK 30+
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

        // Center on Manila (Placeholder)
        val startPoint = GeoPoint(14.5995, 120.9842)
        mapController.setCenter(startPoint)

        fetchDriverAndBusData()

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

    private fun fetchDriverAndBusData() {
        val uid = auth.currentUser?.uid ?: return

        db.collection("parents").document(uid).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    @Suppress("UNCHECKED_CAST")
                    val childMap = document.get("child") as? kotlin.collections.Map<String, Any>
                    val assignedBusId = childMap?.get("assignedBus") as? String ?: "Bus 001"
                    
                    db.collection("buses").document(assignedBusId).get()
                        .addOnSuccessListener { busDoc ->
                            if (busDoc.exists()) {
                                val driverName = busDoc.getString("driverName") ?: "Mike Johnson"
                                val busNo = busDoc.getString("busNumber") ?: assignedBusId
                                findViewById<TextView>(R.id.tvDriverName).text = driverName
                                
                                val statusText = getString(CommonR.string.bus_driver_bus_001).replace("BUS-001", busNo)
                                findViewById<TextView>(R.id.tvBusStatus).text = statusText
                            }
                        }
                }
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
}