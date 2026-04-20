package com.example.buswatch.admin

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import com.google.firebase.firestore.FirebaseFirestore
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import java.util.Locale

class AddStopDialog(
    private val context: Context,
    private val db: FirebaseFirestore,
    private val onStopAdded: () -> Unit
) {
    private var currentPoint: GeoPoint = GeoPoint(14.5995, 120.9842) // Default to Manila

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
        val btnSmartFill = dialogView.findViewById<ImageButton>(R.id.btnSmartFill)

        // Setup Map
        mapPicker.setMultiTouchControls(true)
        mapPicker.controller.setZoom(17.0)
        mapPicker.controller.setCenter(currentPoint)

        // Listener for map movement
        mapPicker.addMapListener(object : org.osmdroid.events.MapListener {
            override fun onScroll(event: org.osmdroid.events.ScrollEvent?): Boolean {
                updateLocation(mapPicker.mapCenter as GeoPoint, tvCoordinates, etStopName)
                return true
            }
            override fun onZoom(event: org.osmdroid.events.ZoomEvent?): Boolean = false
        })

        // My Location Button
        btnMyLocation.setOnClickListener {
            goToCurrentLocation(mapPicker)
        }

        // Smart Fill Button
        btnSmartFill.setOnClickListener {
            autoFillStopName(etStopName)
        }

        btnClose.setOnClickListener { dialog.dismiss() }

        btnAddAnother.setOnClickListener {
            saveStopToFirestore(etStopName.text.toString(), currentPoint) {
                etStopName.text.clear()
                Toast.makeText(context, "Stop added! You can add another.", Toast.LENGTH_SHORT).show()
                onStopAdded()
            }
        }

        btnSaveStop.setOnClickListener {
            saveStopToFirestore(etStopName.text.toString(), currentPoint) {
                dialog.dismiss()
                onStopAdded()
            }
        }

        dialog.show()
    }

    private fun goToCurrentLocation(map: MapView) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(context, "Location permission not granted", Toast.LENGTH_SHORT).show()
            return
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val location: Location? = try {
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER) 
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } catch (e: Exception) {
            null
        }

        if (location != null) {
            val userPoint = GeoPoint(location.latitude, location.longitude)
            map.controller.animateTo(userPoint)
            map.controller.setZoom(18.0)
        } else {
            Toast.makeText(context, "Could not find current location", Toast.LENGTH_SHORT).show()
        }
    }

    private fun autoFillStopName(etName: EditText) {
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocation(currentPoint.latitude, currentPoint.longitude, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                val streetName = address.thoroughfare ?: address.featureName ?: ""
                if (streetName.isNotEmpty()) {
                    etName.setText(streetName)
                } else {
                    Toast.makeText(context, "No street name found here", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Geocoding failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateLocation(point: GeoPoint, tvCoords: TextView, etName: EditText) {
        currentPoint = point
        tvCoords.text = String.format(Locale.US, "Lat: %.6f, Lng: %.6f", point.latitude, point.longitude)
        
        // Dynamic hint as you scroll
        if (etName.text.isEmpty()) {
            try {
                val geocoder = Geocoder(context, Locale.getDefault())
                val addresses = geocoder.getFromLocation(point.latitude, point.longitude, 1)
                if (!addresses.isNullOrEmpty()) {
                    val streetName = addresses[0].thoroughfare ?: addresses[0].featureName ?: ""
                    if (streetName.isNotEmpty()) {
                        etName.hint = "Suggested: $streetName"
                    }
                }
            } catch (e: Exception) {}
        }
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
            .addOnFailureListener { e ->
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
