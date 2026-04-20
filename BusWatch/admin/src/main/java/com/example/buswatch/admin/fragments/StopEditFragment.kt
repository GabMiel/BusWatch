package com.example.buswatch.admin.fragments

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.example.buswatch.admin.AdminHome
import com.example.buswatch.admin.R
import com.example.buswatch.admin.StopAdmin
import com.google.firebase.firestore.FirebaseFirestore
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import java.util.Locale

class StopEditFragment : Fragment() {
    private val db = FirebaseFirestore.getInstance()
    private lateinit var stop: StopAdmin
    private var currentPoint: GeoPoint = GeoPoint(0.0, 0.0)

    companion object {
        fun newInstance(stop: StopAdmin) = StopEditFragment().apply {
            this.stop = stop
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_edit_stop, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI(view)
    }

    private fun setupUI(view: View) {
        view.findViewById<ImageButton>(R.id.btnBackEditStop)?.setOnClickListener { 
            (requireActivity() as? AdminHome)?.loadStops()
        }
        
        val etName = view.findViewById<EditText>(R.id.etStopName)
        val tvCoords = view.findViewById<TextView>(R.id.tvCoordinates)
        val mapView = view.findViewById<MapView>(R.id.mapEditStop)
        val btnMyLocation = view.findViewById<ImageButton>(R.id.btnMyLocation)
        val btnSmartFill = view.findViewById<ImageButton>(R.id.btnSmartFill)
        
        etName.setText(stop.name)
        currentPoint = GeoPoint(stop.latitude, stop.longitude)
        tvCoords.text = String.format(Locale.US, "Lat: %.6f, Lng: %.6f", stop.latitude, stop.longitude)
        
        if (mapView != null) {
            mapView.setMultiTouchControls(true)
            mapView.controller.setZoom(18.0)
            mapView.controller.setCenter(currentPoint)
            
            mapView.addMapListener(object : org.osmdroid.events.MapListener {
                override fun onScroll(event: org.osmdroid.events.ScrollEvent?): Boolean {
                    updateLocation(mapView.mapCenter as GeoPoint, tvCoords, etName)
                    return true
                }
                override fun onZoom(event: org.osmdroid.events.ZoomEvent?): Boolean = false
            })
        }

        btnMyLocation?.setOnClickListener {
            goToCurrentLocation(mapView)
        }

        btnSmartFill?.setOnClickListener {
            autoFillStopName(etName)
        }

        view.findViewById<View>(R.id.btnCancelEditStop).setOnClickListener { (requireActivity() as? AdminHome)?.loadStops() }
        view.findViewById<View>(R.id.btnSaveStopChanges).setOnClickListener {
            val newName = etName.text.toString().trim()
            if (newName.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter a stop name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val updates = hashMapOf<String, Any>(
                "name" to newName,
                "latitude" to currentPoint.latitude,
                "longitude" to currentPoint.longitude
            )
            db.collection("stops").document(stop.id).update(updates).addOnSuccessListener {
                Toast.makeText(requireContext(), "Stop updated successfully", Toast.LENGTH_SHORT).show()
                (requireActivity() as? AdminHome)?.loadStops()
            }
        }
    }

    private fun updateLocation(point: GeoPoint, tvCoords: TextView, etName: EditText) {
        currentPoint = point
        tvCoords.text = String.format(Locale.US, "Lat: %.6f, Lng: %.6f", point.latitude, point.longitude)
        
        if (etName.text.isEmpty()) {
            try {
                val geocoder = Geocoder(requireContext(), Locale.getDefault())
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

    private fun goToCurrentLocation(map: MapView?) {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(requireContext(), "Location permission not granted", Toast.LENGTH_SHORT).show()
            return
        }

        val locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val location: Location? = try {
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER) 
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } catch (e: Exception) {
            null
        }

        if (location != null && map != null) {
            val userPoint = GeoPoint(location.latitude, location.longitude)
            map.controller.animateTo(userPoint)
            map.controller.setZoom(18.0)
        } else {
            Toast.makeText(requireContext(), "Could not find current location", Toast.LENGTH_SHORT).show()
        }
    }

    private fun autoFillStopName(etName: EditText) {
        try {
            val geocoder = Geocoder(requireContext(), Locale.getDefault())
            val addresses = geocoder.getFromLocation(currentPoint.latitude, currentPoint.longitude, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                val streetName = address.thoroughfare ?: address.featureName ?: ""
                if (streetName.isNotEmpty()) {
                    etName.setText(streetName)
                } else {
                    Toast.makeText(requireContext(), "No street name found here", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Geocoding failed", Toast.LENGTH_SHORT).show()
        }
    }
}
