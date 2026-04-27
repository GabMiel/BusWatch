package com.example.buswatch.admin

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.example.buswatch.common.R as CommonR
import com.google.firebase.firestore.FirebaseFirestore
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import java.util.Locale

class AddStopDialog(
    private val context: Context,
    private val db: FirebaseFirestore,
    private val onStopAdded: () -> Unit
) {
    private var currentPoint: GeoPoint = GeoPoint(14.5995, 120.9842) // Default to Manila
    private var isMaximized = false
    private var selectionMarker: Marker? = null

    fun show() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_stop, null)
        val dialog = AlertDialog.Builder(context).setView(dialogView).create()

        val etStopName = dialogView.findViewById<EditText>(R.id.etStopName)
        val mapPicker = dialogView.findViewById<MapView>(R.id.mapPicker)
        val tvCoordinates = dialogView.findViewById<TextView>(R.id.tvCoordinates)
        val btnAddAnother = dialogView.findViewById<TextView>(R.id.btnAddAnother)
        val btnSaveStop = dialogView.findViewById<TextView>(R.id.btnSaveStop)
        val btnClose = dialogView.findViewById<ImageButton>(R.id.btnCloseAddStop)
        val btnMyLocation = dialogView.findViewById<ImageButton>(R.id.btnMyLocation)
        val btnMaximize = dialogView.findViewById<ImageButton>(R.id.btnMaximizeMap)
        val btnSmartFill = dialogView.findViewById<ImageButton>(R.id.btnSmartFill)

        val headerLayout = dialogView.findViewById<View>(R.id.headerLayout)
        val layoutStopName = dialogView.findViewById<View>(R.id.layoutStopName)
        val layoutMapLabel = dialogView.findViewById<View>(R.id.layoutMapLabel)
        val layoutActions = dialogView.findViewById<View>(R.id.layoutActions)
        val mapContainer = dialogView.findViewById<FrameLayout>(R.id.mapContainer)

        // Setup Map
        mapPicker?.setTileSource(TileSourceFactory.MAPNIK)
        mapPicker?.setMultiTouchControls(true)
        mapPicker?.controller?.setZoom(15.0)
        mapPicker?.controller?.setCenter(currentPoint)

        // Show already placed stops
        loadExistingStops(mapPicker)

        // Selection Marker (Pin for the new stop)
        selectionMarker = Marker(mapPicker).apply {
            position = currentPoint
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            val drawable = ContextCompat.getDrawable(context, CommonR.drawable.ic_stop_marker_red)
            icon = getScaledDrawable(drawable, 36, 36)
            isDraggable = true
            title = "New Stop Location"
            setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                override fun onMarkerDrag(marker: Marker?) {}
                override fun onMarkerDragEnd(marker: Marker?) {
                    marker?.position?.let { updateLocation(it, tvCoordinates, etStopName) }
                }
                override fun onMarkerDragStart(marker: Marker?) {}
            })
        }
        mapPicker?.overlays?.add(selectionMarker)

        val eventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                p?.let {
                    selectionMarker?.position = it
                    mapPicker?.invalidate()
                    updateLocation(it, tvCoordinates, etStopName)
                }
                return true
            }
            override fun longPressHelper(p: GeoPoint?): Boolean = false
        })
        mapPicker?.overlays?.add(0, eventsOverlay)

        btnMaximize?.setOnClickListener {
            isMaximized = !isMaximized
            headerLayout?.isVisible = !isMaximized
            layoutStopName?.isVisible = !isMaximized
            layoutMapLabel?.isVisible = !isMaximized
            tvCoordinates?.isVisible = !isMaximized
            layoutActions?.isVisible = !isMaximized

            val params = mapContainer?.layoutParams
            if (isMaximized) {
                params?.height = (450 * context.resources.displayMetrics.density).toInt()
                btnMaximize.setImageResource(CommonR.drawable.ic_close)
            } else {
                params?.height = (250 * context.resources.displayMetrics.density).toInt()
                btnMaximize.setImageResource(CommonR.drawable.ic_eye)
            }
            mapContainer?.layoutParams = params
            mapPicker?.postDelayed({ mapPicker.controller.animateTo(currentPoint) }, 200)
        }

        btnMyLocation?.setOnClickListener {
            mapPicker?.let { goToCurrentLocation(it, tvCoordinates, etStopName) }
        }

        btnSmartFill?.setOnClickListener { autoFillStopName(etStopName) }
        btnClose?.setOnClickListener { dialog.dismiss() }

        btnAddAnother?.setOnClickListener {
            saveStopToFirestore(etStopName?.text?.toString() ?: "", currentPoint) {
                etStopName?.text?.clear()
                loadExistingStops(mapPicker) // Refresh existing stops
                Toast.makeText(context, "Stop added! You can add another.", Toast.LENGTH_SHORT).show()
                onStopAdded()
            }
        }

        btnSaveStop?.setOnClickListener {
            saveStopToFirestore(etStopName?.text?.toString() ?: "", currentPoint) {
                dialog.dismiss()
                onStopAdded()
            }
        }

        updateLocation(currentPoint, tvCoordinates, etStopName)
        dialog.show()
    }

    private fun loadExistingStops(mapView: MapView?) {
        if (mapView == null) return
        
        db.collection("stops").whereEqualTo("status", "active").get().addOnSuccessListener { snapshots ->
            val existingIcon = getScaledDrawable(ContextCompat.getDrawable(context, CommonR.drawable.ic_stop_marker), 28, 28)
            for (doc in snapshots) {
                val lat = doc.getDouble("latitude")
                val lng = doc.getDouble("longitude")
                if (lat != null && lng != null) {
                    val p = GeoPoint(lat, lng)
                    val m = Marker(mapView)
                    m.position = p
                    m.icon = existingIcon
                    m.title = doc.getString("name") ?: "Existing Stop"
                    m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    mapView.overlays.add(m)
                }
            }
            mapView.invalidate()
        }
    }

    private fun getScaledDrawable(drawable: Drawable?, widthDp: Int, heightDp: Int): Drawable? {
        if (drawable == null) return null
        val density = context.resources.displayMetrics.density
        val width = (widthDp * density).toInt()
        val height = (heightDp * density).toInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return BitmapDrawable(context.resources, bitmap)
    }

    @SuppressLint("MissingPermission")
    private fun goToCurrentLocation(map: MapView, tvCoords: TextView?, etName: EditText?) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(context, "Location permission not granted", Toast.LENGTH_SHORT).show()
            return
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val location: Location? = try {
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER) 
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } catch (_: SecurityException) { null } catch (_: Exception) { null }

        if (location != null) {
            val userPoint = GeoPoint(location.latitude, location.longitude)
            map.controller.animateTo(userPoint)
            map.controller.setZoom(18.0)
            selectionMarker?.position = userPoint
            map.invalidate()
            updateLocation(userPoint, tvCoords, etName)
        } else {
            Toast.makeText(context, "Could not find current location", Toast.LENGTH_SHORT).show()
        }
    }

    private fun autoFillStopName(etName: EditText?) {
        if (etName == null) return
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                geocoder.getFromLocation(currentPoint.latitude, currentPoint.longitude, 1) { addresses ->
                    if (addresses.isNotEmpty()) {
                        val address = addresses[0]
                        val streetName = address.thoroughfare ?: address.featureName ?: ""
                        if (streetName.isNotEmpty()) { etName.post { etName.setText(streetName) } }
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(currentPoint.latitude, currentPoint.longitude, 1)
                if (!addresses.isNullOrEmpty()) {
                    val streetName = addresses[0].thoroughfare ?: addresses[0].featureName ?: ""
                    if (streetName.isNotEmpty()) { etName.setText(streetName) }
                }
            }
        } catch (_: Exception) { Toast.makeText(context, "Geocoding failed", Toast.LENGTH_SHORT).show() }
    }

    private fun updateLocation(point: GeoPoint, tvCoords: TextView?, etName: EditText?) {
        currentPoint = point
        tvCoords?.text = String.format(Locale.US, "Lat: %.6f, Lng: %.6f", point.latitude, point.longitude)
    }

    private fun saveStopToFirestore(name: String, point: GeoPoint, onSuccess: () -> Unit) {
        if (name.isEmpty()) {
            Toast.makeText(context, "Please enter a stop name", Toast.LENGTH_SHORT).show()
            return
        }
        val stopData = hashMapOf(
            "name" to name,
            "latitude" to point.latitude,
            "longitude" to point.longitude,
            "status" to "active",
            "createdAt" to com.google.firebase.Timestamp.now()
        )
        db.collection("stops").add(stopData)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show() }
    }
}
