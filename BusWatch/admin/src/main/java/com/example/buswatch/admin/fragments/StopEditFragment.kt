package com.example.buswatch.admin.fragments

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
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.example.buswatch.admin.AdminHome
import com.example.buswatch.admin.R
import com.example.buswatch.admin.StopAdmin
import com.example.buswatch.common.R as CommonR
import com.google.firebase.firestore.FirebaseFirestore
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import java.util.Locale

class StopEditFragment : Fragment() {
    private val db = FirebaseFirestore.getInstance()
    private lateinit var stop: StopAdmin
    private var currentPoint: GeoPoint = GeoPoint(0.0, 0.0)
    private var isMaximized = false
    private var selectionMarker: Marker? = null

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

    @SuppressLint("ClickableViewAccessibility")
    private fun setupUI(view: View) {
        view.findViewById<ImageButton>(R.id.btnBackEditStop)?.setOnClickListener { 
            (requireActivity() as? AdminHome)?.loadStops()
        }
        
        val etName = view.findViewById<EditText>(R.id.etStopName)
        val tvCoords = view.findViewById<TextView>(R.id.tvCoordinates)
        val mapView = view.findViewById<MapView>(R.id.mapEditStop)
        val btnMyLocation = view.findViewById<ImageButton>(R.id.btnMyLocation)
        val btnSmartFill = view.findViewById<ImageButton>(R.id.btnSmartFill)
        val btnMaximize = view.findViewById<ImageButton>(R.id.btnMaximizeMap)
        
        // Layout elements for maximizing
        val layoutStopName = view.findViewById<View>(R.id.layoutStopName)
        val tvMapLabel = view.findViewById<View>(R.id.tvMapLabel)
        val layoutActions = view.findViewById<View>(R.id.layoutActions)
        val layoutSubHeader = view.findViewById<View>(R.id.layoutSubHeader)
        val mapContainer = view.findViewById<FrameLayout>(R.id.mapContainer)
        
        etName?.setText(stop.name)
        currentPoint = GeoPoint(stop.latitude, stop.longitude)
        tvCoords?.text = String.format(Locale.US, "Lat: %.6f, Lng: %.6f", stop.latitude, stop.longitude)
        
        if (mapView != null) {
            mapView.setMultiTouchControls(true)
            mapView.controller.setZoom(18.0)
            mapView.controller.setCenter(currentPoint)

            // Handle map touch to prevent parent (NestedScrollView) from intercepting scroll events
            mapView.setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> v.parent.requestDisallowInterceptTouchEvent(true)
                    MotionEvent.ACTION_UP -> v.performClick()
                }
                false
            }
            
            // Initialize Marker with scaled red pin
            selectionMarker = Marker(mapView).apply {
                position = currentPoint
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                val pinDrawable = ContextCompat.getDrawable(requireContext(), CommonR.drawable.ic_stop_marker_red)
                icon = getScaledDrawable(pinDrawable)
                isDraggable = true
                setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                    override fun onMarkerDrag(marker: Marker?) {}
                    override fun onMarkerDragEnd(marker: Marker?) {
                        marker?.position?.let { updateLocation(it, tvCoords, etName) }
                    }
                    override fun onMarkerDragStart(marker: Marker?) {}
                })
            }
            mapView.overlays.add(selectionMarker)

            // Map Click Listener
            val eventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                    p?.let {
                        selectionMarker?.position = it
                        mapView.invalidate()
                        updateLocation(it, tvCoords, etName)
                    }
                    return true
                }
                override fun longPressHelper(p: GeoPoint?): Boolean = false
            })
            mapView.overlays.add(0, eventsOverlay)
        }

        btnMaximize?.setOnClickListener {
            isMaximized = !isMaximized
            layoutStopName?.isVisible = !isMaximized
            tvMapLabel?.isVisible = !isMaximized
            layoutActions?.isVisible = !isMaximized
            layoutSubHeader?.isVisible = !isMaximized
            tvCoords?.isVisible = !isMaximized

            val params = mapContainer?.layoutParams
            if (isMaximized) {
                params?.height = (480 * resources.displayMetrics.density).toInt()
                btnMaximize.setImageResource(CommonR.drawable.ic_close)
            } else {
                params?.height = (300 * resources.displayMetrics.density).toInt()
                btnMaximize.setImageResource(CommonR.drawable.ic_eye)
            }
            mapContainer?.layoutParams = params
            mapView?.postDelayed({ mapView.controller.animateTo(currentPoint) }, 200)
        }

        btnMyLocation?.setOnClickListener {
            if (mapView != null) {
                goToCurrentLocation(mapView, tvCoords, etName)
            }
        }

        btnSmartFill?.setOnClickListener {
            if (etName != null) {
                autoFillStopName(etName)
            }
        }

        view.findViewById<View>(R.id.btnCancelEditStop)?.setOnClickListener { (requireActivity() as? AdminHome)?.loadStops() }
        view.findViewById<View>(R.id.btnSaveStopChanges)?.setOnClickListener {
            val newName = etName?.text?.toString()?.trim() ?: ""
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

    private fun getScaledDrawable(drawable: Drawable?, widthDp: Int = 32, heightDp: Int = 32): Drawable? {
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

    private fun updateLocation(point: GeoPoint, tvCoords: TextView?, etName: EditText?) {
        currentPoint = point
        tvCoords?.text = String.format(Locale.US, "Lat: %.6f, Lng: %.6f", point.latitude, point.longitude)
        
        if (etName != null && etName.text.isEmpty()) {
            try {
                val geocoder = Geocoder(requireContext(), Locale.getDefault())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    geocoder.getFromLocation(point.latitude, point.longitude, 1) { addresses ->
                        if (addresses.isNotEmpty()) {
                            val streetName = addresses[0].thoroughfare ?: addresses[0].featureName ?: ""
                            if (streetName.isNotEmpty()) {
                                etName.post { etName.hint = "Suggested: $streetName" }
                            }
                        }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(point.latitude, point.longitude, 1)
                    if (!addresses.isNullOrEmpty()) {
                        val streetName = addresses[0].thoroughfare ?: addresses[0].featureName ?: ""
                        if (streetName.isNotEmpty()) {
                            etName.hint = "Suggested: $streetName"
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    @SuppressLint("MissingPermission")
    private fun goToCurrentLocation(map: MapView, tvCoords: TextView?, etName: EditText?) {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(requireContext(), "Location permission not granted", Toast.LENGTH_SHORT).show()
            return
        }

        val locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val location: Location? = try {
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER) 
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }

        if (location != null) {
            val userPoint = GeoPoint(location.latitude, location.longitude)
            map.controller.animateTo(userPoint)
            map.controller.setZoom(18.0)
            selectionMarker?.position = userPoint
            map.invalidate()
            updateLocation(userPoint, tvCoords, etName)
        } else {
            Toast.makeText(requireContext(), "Could not find current location", Toast.LENGTH_SHORT).show()
        }
    }

    private fun autoFillStopName(etName: EditText) {
        try {
            val geocoder = Geocoder(requireContext(), Locale.getDefault())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                geocoder.getFromLocation(currentPoint.latitude, currentPoint.longitude, 1) { addresses ->
                    if (addresses.isNotEmpty()) {
                        val address = addresses[0]
                        val streetName = address.thoroughfare ?: address.featureName ?: ""
                        if (streetName.isNotEmpty()) {
                            etName.post { etName.setText(streetName) }
                        }
                    }
                }
            } else {
                @Suppress("DEPRECATION")
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
            }
        } catch (_: Exception) {
            Toast.makeText(requireContext(), "Geocoding failed", Toast.LENGTH_SHORT).show()
        }
    }
}
