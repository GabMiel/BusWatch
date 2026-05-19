package com.example.buswatch.driver

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Geocoder
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.buswatch.common.R as CommonR
import com.example.buswatch.driver.databinding.FragmentLiveTrackingBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.FirebaseFirestore
import org.osmdroid.bonuspack.routing.OSRMRoadManager
import org.osmdroid.bonuspack.routing.Road
import org.osmdroid.bonuspack.routing.RoadManager
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.util.*

class TrackingFragment : Fragment(), SensorEventListener {

    private var _binding: FragmentLiveTrackingBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DriverViewModel by activityViewModels()
    private val db = FirebaseFirestore.getInstance()

    private var map: MapView? = null
    private var driverMarker: Marker? = null
    private var roadOverlay: Polyline? = null
    private val allMarkers = mutableMapOf<String, Marker>()
    private val stopPoints = mutableListOf<GeoPoint>()
    private val stopIdsList = mutableListOf<String>()

    private var isMapMaximized = false
    private var locationButtonMode = 1
    private var lastActiveFollowMode = 1
    
    private var sensorManager: SensorManager? = null
    private var rotationSensor: Sensor? = null
    private var lastAzimuth = 0f
    private var isFirstSensorReading = true

    private var studentAdapter: StudentAdapter? = null
    
    private var lastRoadRequestTime: Long = 0
    private val MIN_ROAD_REQUEST_INTERVAL = 15000L
    
    private var lastStreetRequestTime: Long = 0
    private val STREET_REQUEST_INTERVAL = 10000L

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLiveTrackingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        sensorManager = context?.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        setupRecyclerView()
        setupUI()
        setupObservers()
    }

    private fun setupRecyclerView() {
        val safeContext = context ?: return
        
        binding.recyclerPickup.layoutManager = LinearLayoutManager(safeContext)
        
        studentAdapter = StudentAdapter(
            students = emptyList(),
            currentTab = viewModel.currentTab.value ?: "Morning",
            isPickupLayout = true, 
            onPickUpClick = { student -> 
                val tab = viewModel.currentTab.value ?: "Morning"
                
                // Directly transition to the "on bus" state
                if (tab == "Morning") {
                    viewModel.updateStudentStatus(student.id, "On Board")
                    viewModel.sendStudentBoardingNotification(student.id, student.name)
                } else {
                    // Afternoon: Picking up from School (was "Heading Home") -> Now "Riding"
                    viewModel.updateStudentStatus(student.id, "Riding")
                    viewModel.sendStudentBoardingNotification(student.id, student.name)
                }
            },
            onDropOffClick = { student -> 
                viewModel.dropOffStudent(student)
            },
            onStudentClick = { student ->
                showStudentMedicalInfo(student)
            }
        )
        binding.recyclerPickup.adapter = studentAdapter
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupUI() {
        binding.btnSOS.setOnClickListener { (activity as? DriverHome)?.showSOSConfirmation() }
        binding.btnEndTrip.setOnClickListener { 
            if (isAdded) {
                val tab = viewModel.currentTab.value ?: "Morning"
                if (tab == "Morning") (activity as? DriverHome)?.loadHome() else (activity as? DriverHome)?.loadAfternoon()
            }
        }

        binding.btnDropAll.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext(), CommonR.style.Theme_BusWatch_Dialog_Rounded)
                .setTitle("Drop All Students")
                .setMessage("Are you sure you want to mark all students currently on board as dropped off?")
                .setPositiveButton("Confirm") { _, _ ->
                    viewModel.dropAllStudents()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        
        binding.btnMaximizeMap.setOnClickListener {
            toggleMapMaximization()
        }

        viewModel.userRole.observe(viewLifecycleOwner) { role ->
            val b = _binding ?: return@observe
            val context = context ?: return@observe
            
            b.headerContainer.setBackgroundColor(ContextCompat.getColor(context, CommonR.color.yellow_primary))
            
            // Unified layout for both Driver and Conductor roles
            b.mapContainer.visibility = View.VISIBLE
            b.btnMaximizeMap.visibility = View.VISIBLE
            b.bannerNextStop.visibility = View.VISIBLE
            b.tvRouteInfo.visibility = View.VISIBLE
            b.tvETA.visibility = View.VISIBLE
            b.rosterHandle.visibility = View.VISIBLE
            b.rosterHeaderBar.visibility = View.VISIBLE
            b.conductorTopSpacer.visibility = View.GONE
            
            b.layoutBottomInfo.setBackgroundColor(ContextCompat.getColor(context, CommonR.color.yellow_primary))
            b.bannerNextStop.setBackgroundColor(ContextCompat.getColor(context, CommonR.color.yellow_primary))
            b.root.setBackgroundColor(android.graphics.Color.WHITE)

            // Reset layout params to default split view (map + roster) in case role changed
            val bottomParams = b.layoutBottomInfo.layoutParams as android.widget.LinearLayout.LayoutParams
            bottomParams.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            bottomParams.weight = 0f
            b.layoutBottomInfo.layoutParams = bottomParams
            
            val recyclerParams = b.recyclerPickup.layoutParams as android.widget.LinearLayout.LayoutParams
            recyclerParams.height = (180 * resources.displayMetrics.density).toInt()
            recyclerParams.weight = 0f
            b.recyclerPickup.layoutParams = recyclerParams
            
            if (role == "Conductor") {
                b.btnEndTrip.text = getString(CommonR.string.exit_roster)
            } else {
                b.btnEndTrip.text = getString(CommonR.string.end_trip)
            }
            
            setupMap()
        }

        binding.btnMyLocation.setImageResource(CommonR.drawable.ic_compass)
        binding.btnMyLocation.setColorFilter("#4A90E2".toColorInt())

        binding.btnMyLocation.setOnClickListener {
            when (locationButtonMode) {
                0 -> if (lastActiveFollowMode == 2) enterMode2() else enterMode1()
                1 -> enterMode2()
                2 -> enterMode1()
            }
        }

        binding.btnCompass.visibility = View.GONE
        binding.btnSortRoster.setOnClickListener { showSortPopup(it) }
    }

    private fun toggleMapMaximization() {
        isMapMaximized = !isMapMaximized
        if (isMapMaximized) {
            binding.layoutBottomInfo.visibility = View.GONE
            binding.bannerNextStop.visibility = View.GONE
            binding.btnMaximizeMap.setImageResource(CommonR.drawable.ic_eye_off)
        } else {
            binding.layoutBottomInfo.visibility = View.VISIBLE
            binding.bannerNextStop.visibility = View.VISIBLE
            binding.btnMaximizeMap.setImageResource(CommonR.drawable.ic_eye)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupMap() {
        if (map != null) return // Already initialized
        
        val b = _binding ?: return
        map = b.mapView
        map?.let { mv ->
            mv.setTileSource(TileSourceFactory.MAPNIK)
            mv.setMultiTouchControls(true)
            mv.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            mv.controller.setZoom(18.7)
            
            mv.setOnTouchListener { v, event ->
                if (locationButtonMode == 2) return@setOnTouchListener true
                if (event.action == MotionEvent.ACTION_DOWN) {
                    if (locationButtonMode != 0) {
                        lastActiveFollowMode = locationButtonMode
                        locationButtonMode = 0
                        _binding?.btnMyLocation?.setColorFilter(android.graphics.Color.BLACK)
                        _binding?.btnMyLocation?.setImageResource(CommonR.drawable.ic_my_location)
                        unregisterSensors()
                    }
                }
                v.parent.requestDisallowInterceptTouchEvent(true)
                false
            }

            driverMarker = Marker(mv)
            updateDriverIcon()
            driverMarker?.title = "Your Location"
            driverMarker?.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            mv.overlays.add(driverMarker)

            loadStops()
        }
    }

    private fun loadStops() {
        if (!isAdded) return
        val mv = map ?: return
        
        // Remove existing stop markers before reloading to prevent duplication
        allMarkers.values.forEach { mv.overlays.remove(it) }
        allMarkers.clear()
        
        val route = viewModel.assignedRoute.value ?: return
        val stopIds = route.stopIds
        if (stopIds.isEmpty()) return

        val safeContext = context ?: return
        val stopIcon = getScaledDrawable(ContextCompat.getDrawable(safeContext, CommonR.drawable.ic_stop_marker), 40, 40)
        val nextStopIcon = getScaledDrawable(ContextCompat.getDrawable(safeContext, CommonR.drawable.ic_stop_marker_red), 52, 52)

        var loaded = 0
        val stopPointsMap = mutableMapOf<String, GeoPoint>()
        val currentTab = viewModel.currentTab.value ?: "Morning"
        val displayStopIds = if (currentTab == "Afternoon") stopIds.reversed() else stopIds
        
        stopIdsList.clear()
        stopIdsList.addAll(displayStopIds)
        
        binding.tvNextStopName.text = getString(CommonR.string.calculating)

        for ((index, sid) in displayStopIds.withIndex()) {
            db.collection("stops").document(sid).get().addOnSuccessListener { sDoc ->
                if (!isAdded || _binding == null) return@addOnSuccessListener
                val currentMv = map ?: return@addOnSuccessListener
                
                val lat = sDoc.getDouble("latitude")
                val lng = sDoc.getDouble("longitude")
                if (lat != null && lng != null) {
                    val p = GeoPoint(lat, lng)
                    stopPointsMap[sid] = p
                    val marker = Marker(currentMv)
                    marker.position = p
                    marker.title = sDoc.getString("name")
                    marker.icon = if (index == 0) nextStopIcon else stopIcon
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    currentMv.overlays.add(marker)
                    allMarkers[sid] = marker
                    
                    if (index == 0) {
                        val stopName = sDoc.getString("name") ?: "Next Stop"
                        binding.tvNextStopName.text = stopName
                        binding.tvNextStopName.tag = sid 

                        viewModel.assignedRoute.value?.busId?.let { busId ->
                            db.collection("buses").document(busId).update("nextStop", sid)
                        }
                    }
                }
                loaded++
                if (loaded == displayStopIds.size) {
                    stopPoints.clear()
                    stopPoints.addAll(displayStopIds.mapNotNull { stopPointsMap[it] })
                    currentMv.invalidate()
                    viewModel.lastKnownLocation.value?.let { updateRouteWithCurrentLocation(it, force = true) }
                }
            }
        }
    }

    private fun setupObservers() {
        viewModel.assignedRoute.observe(viewLifecycleOwner) { route ->
            val b = _binding ?: return@observe
            route?.let {
                b.tvRouteInfo.text = String.format(Locale.getDefault(), "%s \u2022 %s", it.name, it.busNumber ?: "N/A")
                loadStops()
            }
        }

        viewModel.students.observe(viewLifecycleOwner) { students ->
            val b = _binding ?: return@observe
            val tab = viewModel.currentTab.value ?: "Morning"
            
            studentAdapter?.updateStudents(students, tab)
            
            val totalExpected = students.count { s ->
                val ride = s.rideOption.lowercase()
                when (tab) {
                    "Morning" -> ride.contains("morning") || ride.contains("round trip")
                    "Afternoon" -> ride.contains("afternoon") || ride.contains("round trip")
                    else -> !ride.contains("not riding")
                }
            }
            val onBoardCount = students.count { it.status == "On Board" || it.status == "Riding" }
            b.tvBoardingCount.text = getString(CommonR.string.on_board_format, onBoardCount, totalExpected)
            b.tvRosterTitle.text = String.format(Locale.getDefault(), "STUDENT ROSTER (%d / %d)", totalExpected, students.size)
            
            // Show "Drop All" button only if there are students on board
            b.btnDropAll.visibility = if (onBoardCount > 0) View.VISIBLE else View.GONE
        }

        viewModel.lastKnownLocation.observe(viewLifecycleOwner) { gp ->
            gp?.let {
                driverMarker?.position = it
                driverMarker?.isEnabled = true
                if (locationButtonMode != 0) {
                    updateMapCamera(it)
                }
                updateRouteWithCurrentLocation(it)
                updateCurrentStreetName(it)
                map?.invalidate()
            }
        }

        viewModel.lastBearing.observe(viewLifecycleOwner) { _ ->
            driverMarker?.rotation = 0f
            map?.invalidate()
        }

        viewModel.toastMessage.observe(viewLifecycleOwner) { msg ->
            if (msg != null) {
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                viewModel.clearToast()
            }
        }
    }

    private fun updateCurrentStreetName(gp: GeoPoint) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastStreetRequestTime < STREET_REQUEST_INTERVAL) return
        lastStreetRequestTime = currentTime

        val safeContext = context ?: return
        Thread {
            try {
                val geocoder = Geocoder(safeContext, Locale.getDefault())
                val addresses = geocoder.getFromLocation(gp.latitude, gp.longitude, 1)
                if (!addresses.isNullOrEmpty()) {
                    val address = addresses[0]
                    val streetName = address.thoroughfare ?: address.getAddressLine(0)?.split(",")?.firstOrNull() ?: "Unknown Street"
                    activity?.runOnUiThread {
                        _binding?.tvCurrentStreet?.text = streetName
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    private fun showStudentMedicalInfo(student: Student) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_student_medical_info, null)
        val dialog = MaterialAlertDialogBuilder(requireContext(), CommonR.style.Theme_BusWatch_Dialog_Rounded)
            .setView(dialogView)
            .create()

        dialogView.findViewById<TextView>(R.id.tvStudentName).text = student.name
        dialogView.findViewById<TextView>(R.id.tvStudentGrade).text = student.grade
        dialogView.findViewById<TextView>(R.id.tvBloodType).text = student.bloodType
        dialogView.findViewById<TextView>(R.id.tvAllergies).text = student.allergies
        dialogView.findViewById<TextView>(R.id.tvConditions).text = student.medicalConditions
        dialogView.findViewById<TextView>(R.id.tvMedications).text = student.medications
        dialogView.findViewById<TextView>(R.id.tvEmergencyName).text = student.emergencyContact
        dialogView.findViewById<TextView>(R.id.tvEmergencyPhone).text = student.emergencyPhone

        val imgPhoto = dialogView.findViewById<ImageView>(R.id.imgStudentPhoto)
        Glide.with(this)
            .load(student.photoUrl)
            .circleCrop()
            .placeholder(CommonR.drawable.ic_person_placeholder)
            .into(imgPhoto)

        val closeBtn = dialogView.findViewById<View>(R.id.btnClose)
        closeBtn?.setOnClickListener { dialog.dismiss() }
        
        dialog.show()
    }

    private fun updateMapCamera(gp: GeoPoint) {
        val mv = map ?: return
        if (locationButtonMode == 2) {
            mv.controller.setCenter(gp)
            mv.mapOrientation = -lastAzimuth
        } else if (locationButtonMode == 1) {
            mv.mapOrientation = 0f
            mv.controller.animateTo(gp)
        }
    }

    private fun updateRouteWithCurrentLocation(currentLoc: GeoPoint, force: Boolean = false) {
        if (stopPoints.isEmpty()) return
        
        val currentTime = System.currentTimeMillis()
        if (!force && currentTime - lastRoadRequestTime < MIN_ROAD_REQUEST_INTERVAL) {
            if (roadOverlay == null && map != null) {
                drawSimplePolyline(map!!, mutableListOf(currentLoc).apply { addAll(stopPoints) })
            }
            return
        }
        
        lastRoadRequestTime = currentTime
        val points = mutableListOf<GeoPoint>()
        points.add(currentLoc)
        points.addAll(stopPoints)
        
        if (points.size > 1 && map != null) {
            drawRoadOverlay(map!!, points)
        }
        
        viewModel.assignedRoute.value?.busId?.let { busId ->
            val nextStopId = binding.tvNextStopName.tag?.toString()
            if (!nextStopId.isNullOrEmpty() && nextStopId != getString(CommonR.string.calculating)) {
                db.collection("buses").document(busId).update("nextStop", nextStopId)
            }
        }
    }

    private fun drawSimplePolyline(mapView: MapView, points: List<GeoPoint>) {
        val line = Polyline(mapView)
        line.setPoints(points)
        line.outlinePaint.color = "#4A90E2".toColorInt()
        line.outlinePaint.strokeWidth = 12f
        
        roadOverlay?.let { mapView.overlays.remove(it) }
        roadOverlay = line
        mapView.overlays.add(0, roadOverlay)
        mapView.invalidate()
    }

    private fun drawRoadOverlay(mapView: MapView, points: List<GeoPoint>) {
        val appContext = context?.applicationContext ?: return
        Thread {
            try {
                val roadManager = OSRMRoadManager(appContext, appContext.packageName)
                val road = roadManager.getRoad(ArrayList(points))
                
                if (road.mStatus == Road.STATUS_OK) {
                    val newRoadOverlay = RoadManager.buildRoadOverlay(road)
                    newRoadOverlay.outlinePaint.color = "#4A90E2".toColorInt()
                    newRoadOverlay.outlinePaint.strokeWidth = 12f
                    
                    activity?.runOnUiThread {
                        val b = _binding ?: return@runOnUiThread
                        roadOverlay?.let { mapView.overlays.remove(it) }
                        roadOverlay = newRoadOverlay
                        mapView.overlays.add(0, roadOverlay)
                        
                        val etaSeconds = if (road.mLegs.isNotEmpty()) road.mLegs[0].mDuration else road.mDuration
                        val durationInMinutes = (etaSeconds / 60).toInt()
                        val etaText = if (durationInMinutes < 1) "1" else durationInMinutes.toString()
                        b.tvETA.text = String.format(Locale.getDefault(), "ETA - %s MINS", etaText)
                        
                        val busId = viewModel.assignedRoute.value?.busId
                        if (busId != null) {
                            val nextStopValue = b.tvNextStopName.tag?.toString() ?: b.tvNextStopName.text.toString()
                            val updateData = mutableMapOf<String, Any>(
                                "nextStop" to nextStopValue,
                                "eta" to etaText
                            )
                            db.collection("buses").document(busId).update(updateData)
                        }
                        
                        mapView.invalidate()
                    }
                } else {
                    activity?.runOnUiThread { drawSimplePolyline(mapView, points) }
                }
            } catch (e: Exception) { 
                e.printStackTrace() 
                activity?.runOnUiThread { drawSimplePolyline(mapView, points) }
            }
        }.start()
    }

    private fun enterMode1() {
        val b = _binding ?: return
        locationButtonMode = 1
        lastActiveFollowMode = 1
        b.btnMyLocation.setColorFilter("#4A90E2".toColorInt())
        b.btnMyLocation.setImageResource(CommonR.drawable.ic_compass)
        updateDriverIcon()
        viewModel.lastKnownLocation.value?.let { gp ->
            map?.controller?.animateTo(gp)
            map?.controller?.setZoom(18.7)
        }
        map?.mapOrientation = 0f
        driverMarker?.rotation = 0f
        unregisterSensors()
        map?.invalidate()
    }

    private fun enterMode2() {
        val b = _binding ?: return
        locationButtonMode = 2
        lastActiveFollowMode = 2
        isFirstSensorReading = true 
        b.btnMyLocation.setColorFilter("#4A90E2".toColorInt())
        b.btnMyLocation.setImageResource(CommonR.drawable.ic_my_location)
        updateDriverIcon()
        registerSensors()
        lastAzimuth = viewModel.lastBearing.value ?: 0f
        viewModel.lastKnownLocation.value?.let { gp ->
            map?.controller?.setCenter(gp)
            map?.controller?.setZoom(19.3)
        }
        driverMarker?.rotation = 0f
        map?.invalidate()
    }

    private fun updateDriverIcon() {
        val safeContext = context ?: return
        val icon = getScaledDrawable(ContextCompat.getDrawable(safeContext, CommonR.drawable.ic_bus_marker_yellow), 58, 58)
        driverMarker?.icon = icon
    }

    private fun registerSensors() {
        rotationSensor?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    private fun unregisterSensors() {
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (locationButtonMode != 2) return
        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            val rotationMatrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            val orientation = FloatArray(3)
            SensorManager.getOrientation(rotationMatrix, orientation)
            val azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
            if (isFirstSensorReading) {
                lastAzimuth = azimuth
                isFirstSensorReading = false
            } else {
                val alpha = 0.18f
                var diff = azimuth - lastAzimuth
                while (diff < -180) diff += 360
                while (diff > 180) diff -= 360
                lastAzimuth = lastAzimuth + alpha * diff
            }
            map?.let { mv ->
                mv.mapOrientation = -lastAzimuth
                driverMarker?.rotation = 0f
                mv.invalidate()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun showSortPopup(view: View) {
        val safeContext = context ?: return
        val popup = PopupMenu(safeContext, view)
        popup.menu.add("Order By:").isEnabled = false
        popup.menu.add("Ascending (A-Z)").setOnMenuItemClickListener {
            viewModel.setSortMode("Name", true)
            true
        }
        popup.menu.add("Descending (Z-A)").setOnMenuItemClickListener {
            viewModel.setSortMode("Name", false)
            true
        }
        popup.menu.add("Default (Stop Order)").setOnMenuItemClickListener {
            viewModel.setSortMode("Stop", true)
            true
        }
        popup.show()
    }

    private fun getScaledDrawable(drawable: Drawable?, widthDp: Int, heightDp: Int): Drawable? {
        if (drawable == null) return null
        val density = resources.displayMetrics.density
        val width = (widthDp * density).toInt()
        val height = (heightDp * density).toInt()
        val bitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.applyCanvas {
            drawable.setBounds(0, 0, width, height)
            drawable.draw(this)
        }
        return bitmap.toDrawable(resources)
    }

    override fun onResume() { 
        super.onResume()
        map?.onResume()
        if (locationButtonMode == 2) registerSensors()
    }
    override fun onPause() { 
        super.onPause()
        map?.onPause()
        unregisterSensors()
    }
    override fun onDestroyView() { 
        super.onDestroyView()
        map = null
        _binding = null 
    }
}
