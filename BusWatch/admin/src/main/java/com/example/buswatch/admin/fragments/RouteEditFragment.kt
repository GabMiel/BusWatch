package com.example.buswatch.admin.fragments

import android.annotation.SuppressLint
import android.app.TimePickerDialog
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
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
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.example.buswatch.admin.AdminHome
import com.example.buswatch.admin.R
import com.example.buswatch.admin.RouteAdmin
import com.example.buswatch.common.R as CommonR
import com.google.firebase.firestore.FirebaseFirestore
import org.osmdroid.bonuspack.routing.OSRMRoadManager
import org.osmdroid.bonuspack.routing.Road
import org.osmdroid.bonuspack.routing.RoadManager
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.util.Calendar
import java.util.Locale
import kotlin.random.Random

class RouteEditFragment : Fragment() {
    private val db = FirebaseFirestore.getInstance()
    private lateinit var route: RouteAdmin
    
    private var selectedDriverId: String? = null
    private var selectedBusId: String? = null
    private var selectedConductorId: String? = null
    private var selectedStopIds = mutableListOf<String>()
    private var busCapacity = 0
    private var isMaximized = false

    private var morningStartTime: String = ""
    private var morningEndTime: String = ""
    private var afternoonStartTime: String = ""
    private var afternoonEndTime: String = ""
    
    private var roadPolyline: Polyline? = null
    private val allMarkers = mutableMapOf<String, Marker>()

    companion object {
        fun newInstance(route: RouteAdmin) = RouteEditFragment().apply {
            this.route = route
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_edit_route, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        busCapacity = route.maxCapacity
        setupUI(view)
        fetchRouteData(view)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupUI(view: View) {
        view.findViewById<ImageButton>(R.id.btnBackEditRoute)?.setOnClickListener { 
            (requireActivity() as? AdminHome)?.loadRouting()
        }

        val etRouteName = view.findViewById<EditText>(R.id.etRouteName)
        val tvSelectedDriver = view.findViewById<TextView>(R.id.tvSelectedDriver)
        val tvSelectedBus = view.findViewById<TextView>(R.id.tvSelectedBus)
        val tvBusCapacity = view.findViewById<TextView>(R.id.tvBusCapacity)
        val tvSelectedConductor = view.findViewById<TextView>(R.id.tvSelectedConductor)
        val tvStopsCount = view.findViewById<TextView>(R.id.tvStopsCount)
        val mapRoutePicker = view.findViewById<MapView>(R.id.mapRoutePicker)
        val btnMaximize = view.findViewById<ImageButton>(R.id.btnMaximizeMap)
        val btnMyLocation = view.findViewById<ImageButton>(R.id.btnMyLocation)

        val tvMorningStart = view.findViewById<TextView>(R.id.tvMorningStart)
        val tvMorningEnd = view.findViewById<TextView>(R.id.tvMorningEnd)
        val tvAfternoonStart = view.findViewById<TextView>(R.id.tvAfternoonStart)
        val tvAfternoonEnd = view.findViewById<TextView>(R.id.tvAfternoonEnd)

        // Group elements for maximizing
        val layoutSubHeader = view.findViewById<View>(R.id.layoutSubHeader)
        val layoutRouteName = view.findViewById<View>(R.id.layoutRouteName)
        val layoutDriver = view.findViewById<View>(R.id.layoutDriver)
        val layoutBus = view.findViewById<View>(R.id.layoutBus)
        val layoutCapacity = view.findViewById<View>(R.id.layoutCapacity)
        val layoutConductor = view.findViewById<View>(R.id.layoutConductor)
        val layoutActions = view.findViewById<View>(R.id.layoutActions)
        val mapContainer = view.findViewById<FrameLayout>(R.id.mapContainer)
        val layoutMorning = view.findViewById<View>(R.id.layoutMorningTime)
        val layoutAfternoon = view.findViewById<View>(R.id.layoutAfternoonTime)

        etRouteName.setText(route.routeName)
        tvBusCapacity.text = if (busCapacity > 0) "$busCapacity Seats" else "-"

        // Time Pickers
        tvMorningStart?.setOnClickListener { showTimePicker { time -> morningStartTime = time; tvMorningStart.text = time } }
        tvMorningEnd?.setOnClickListener { showTimePicker { time -> morningEndTime = time; tvMorningEnd.text = time } }
        tvAfternoonStart?.setOnClickListener { showTimePicker { time -> afternoonStartTime = time; tvAfternoonStart.text = time } }
        tvAfternoonEnd?.setOnClickListener { showTimePicker { time -> afternoonEndTime = time; tvAfternoonEnd.text = time } }

        view.findViewById<FrameLayout>(R.id.btnDriverDropdown).setOnClickListener { showDriverPicker(tvSelectedDriver) }
        view.findViewById<FrameLayout>(R.id.btnBusDropdown).setOnClickListener { showBusPicker(tvSelectedBus, tvBusCapacity) }
        view.findViewById<FrameLayout>(R.id.btnConductorDropdown).setOnClickListener { showConductorPicker(tvSelectedConductor) }

        // Setup Map
        mapRoutePicker.setMultiTouchControls(true)
        mapRoutePicker.controller.setZoom(15.0)
        mapRoutePicker.controller.setCenter(GeoPoint(14.7566, 121.0450))

        // Fix scroll conflict
        mapRoutePicker.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.parent.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_UP -> {
                    v.parent.requestDisallowInterceptTouchEvent(false)
                    v.performClick()
                }
            }
            false
        }

        setupAllStopsMarkers(mapRoutePicker, tvStopsCount)

        btnMaximize?.setOnClickListener {
            isMaximized = !isMaximized
            layoutSubHeader.isVisible = !isMaximized
            layoutRouteName.isVisible = !isMaximized
            layoutDriver.isVisible = !isMaximized
            layoutBus.isVisible = !isMaximized
            layoutCapacity.isVisible = !isMaximized
            layoutConductor.isVisible = !isMaximized
            layoutActions.isVisible = !isMaximized
            layoutMorning.isVisible = !isMaximized
            layoutAfternoon.isVisible = !isMaximized

            val params = mapContainer.layoutParams
            if (isMaximized) {
                params.height = (480 * resources.displayMetrics.density).toInt()
                btnMaximize.setImageResource(CommonR.drawable.ic_close)
            } else {
                params.height = (300 * resources.displayMetrics.density).toInt()
                btnMaximize.setImageResource(CommonR.drawable.ic_eye)
            }
            mapContainer.layoutParams = params
            mapRoutePicker.invalidate()
        }

        btnMyLocation?.setOnClickListener {
            if (allMarkers.isNotEmpty()) {
                val markerList = allMarkers.values.toList()
                val randomMarker = markerList[Random.nextInt(markerList.size)]
                mapRoutePicker.controller.animateTo(randomMarker.position)
                mapRoutePicker.controller.setZoom(17.0)
            }
        }

        view.findViewById<View>(R.id.btnCancelEditRoute).setOnClickListener { (requireActivity() as? AdminHome)?.loadRouting() }
        view.findViewById<View>(R.id.btnSaveRouteChanges).setOnClickListener { saveRouteChanges(etRouteName.text.toString()) }
    }

    private fun showTimePicker(onTimeSelected: (String) -> Unit) {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)

        TimePickerDialog(requireContext(), CommonR.style.TimePickerBlack, { _, h, m ->
            val amPm = if (h < 12) "AM" else "PM"
            val hourFormatted = if (h % 12 == 0) 12 else h % 12
            val timeString = String.format(Locale.getDefault(), "%02d:%02d %s", hourFormatted, m, amPm)
            onTimeSelected(timeString)
        }, hour, minute, false).show()
    }

    private fun fetchRouteData(view: View) {
        db.collection("routes").document(route.id).get().addOnSuccessListener { doc ->
            if (doc.exists()) {
                val fetchedRouteName = doc.getString("routeName")
                if (fetchedRouteName != null) {
                    view.findViewById<EditText>(R.id.etRouteName).setText(fetchedRouteName)
                }

                selectedDriverId = doc.getString("driverId")
                selectedBusId = doc.getString("busId")
                selectedConductorId = doc.getString("conductorId")
                
                morningStartTime = doc.getString("morningStartTime") ?: ""
                morningEndTime = doc.getString("morningEndTime") ?: ""
                afternoonStartTime = doc.getString("afternoonStartTime") ?: ""
                afternoonEndTime = doc.getString("afternoonEndTime") ?: ""

                if (morningStartTime.isNotEmpty()) view.findViewById<TextView>(R.id.tvMorningStart).text = morningStartTime
                if (morningEndTime.isNotEmpty()) view.findViewById<TextView>(R.id.tvMorningEnd).text = morningEndTime
                if (afternoonStartTime.isNotEmpty()) view.findViewById<TextView>(R.id.tvAfternoonStart).text = afternoonStartTime
                if (afternoonEndTime.isNotEmpty()) view.findViewById<TextView>(R.id.tvAfternoonEnd).text = afternoonEndTime

                val savedMaxCapacity = (doc.get("maxCapacity") as? Number)?.toInt()
                if (savedMaxCapacity != null) {
                    busCapacity = savedMaxCapacity
                    view.findViewById<TextView>(R.id.tvBusCapacity).text = "$busCapacity Seats"
                }

                @Suppress("UNCHECKED_CAST")
                selectedStopIds = (doc.get("stopIds") as? List<String>)?.toMutableList() ?: mutableListOf()
                
                // Fetch Names for display
                selectedDriverId?.let { id -> 
                    db.collection("drivers").document(id).get().addOnSuccessListener { 
                        if (!isAdded) return@addOnSuccessListener
                        view.findViewById<TextView>(R.id.tvSelectedDriver).text = "${it.getString("firstName")} ${it.getString("lastName")}" 
                    } 
                }
                selectedBusId?.let { id -> 
                    db.collection("buses").document(id).get().addOnSuccessListener { 
                        if (!isAdded) return@addOnSuccessListener
                        view.findViewById<TextView>(R.id.tvSelectedBus).text = it.getString("busNumber")
                        val busDocCapacity = (it.get("capacity") as? Number)?.toInt()
                        if (busDocCapacity != null) {
                            busCapacity = busDocCapacity
                            view.findViewById<TextView>(R.id.tvBusCapacity).text = "$busCapacity Seats"
                        }
                    } 
                }
                selectedConductorId?.let { id -> 
                    db.collection("conductors").document(id).get().addOnSuccessListener { 
                        if (!isAdded) return@addOnSuccessListener
                        view.findViewById<TextView>(R.id.tvSelectedConductor).text = "${it.getString("firstName")} ${it.getString("lastName")}" 
                    } 
                }
                
                // Wait for markers to be ready before updating route line
                view.postDelayed({ updateRouteLine(view.findViewById(R.id.mapRoutePicker), view.findViewById(R.id.tvStopsCount)) }, 1000)
            }
        }
    }

    private fun setupAllStopsMarkers(map: MapView, countTarget: TextView) {
        db.collection("stops").whereEqualTo("status", "active").get().addOnSuccessListener { snapshots ->
            if (!isAdded) return@addOnSuccessListener
            val unselectedIcon = getScaledDrawable(ContextCompat.getDrawable(requireContext(), CommonR.drawable.ic_stop_marker), 36, 36)
            val selectedIcon = getScaledDrawable(ContextCompat.getDrawable(requireContext(), CommonR.drawable.ic_stop_marker_red), 48, 48)

            for (doc in snapshots) {
                val point = GeoPoint(doc.getDouble("latitude") ?: 0.0, doc.getDouble("longitude") ?: 0.0)
                val stopId = doc.id

                val marker = Marker(map)
                marker.position = point
                marker.title = doc.getString("name")
                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                marker.icon = if (selectedStopIds.contains(stopId)) selectedIcon else unselectedIcon
                
                allMarkers[stopId] = marker

                marker.setOnMarkerClickListener { m, _ ->
                    if (selectedStopIds.contains(stopId)) {
                        selectedStopIds.remove(stopId)
                        m.icon = unselectedIcon
                    } else {
                        if (selectedStopIds.size >= 10) {
                            Toast.makeText(requireContext(), "Maximum of 10 stops reached for demo", Toast.LENGTH_SHORT).show()
                            return@setOnMarkerClickListener true
                        }
                        selectedStopIds.add(stopId)
                        m.icon = selectedIcon
                    }
                    updateRouteLine(map, countTarget)
                    true
                }
                map.overlays.add(marker)
            }
            map.invalidate()
        }
    }

    private fun updateRouteLine(map: MapView, countTarget: TextView) {
        val waypoints = ArrayList<GeoPoint>()
        selectedStopIds.forEach { stopId ->
            allMarkers[stopId]?.let { waypoints.add(it.position) }
        }

        if (waypoints.size < 2) {
            roadPolyline?.let { map.overlays.remove(it) }
            roadPolyline = null
            map.invalidate()
            countTarget.text = "Selected: ${selectedStopIds.size} stops"
            return
        }

        Thread {
            try {
                if (!isAdded) return@Thread
                val roadManager = OSRMRoadManager(requireContext(), "BusWatch/1.0")
                val road = roadManager.getRoad(waypoints)
                
                if (road.mStatus == Road.STATUS_OK) {
                    val newPolyline = RoadManager.buildRoadOverlay(road)
                    newPolyline.outlinePaint.color = "#4A90E2".toColorInt()
                    newPolyline.outlinePaint.strokeWidth = 10f

                    activity?.runOnUiThread {
                        if (isAdded) {
                            roadPolyline?.let { map.overlays.remove(it) }
                            roadPolyline = newPolyline
                            map.overlays.add(0, roadPolyline)
                            map.invalidate()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()

        countTarget.text = "Selected: ${selectedStopIds.size} stops"
        
        // Refresh markers icons state
        val unselectedIcon = getScaledDrawable(ContextCompat.getDrawable(requireContext(), CommonR.drawable.ic_stop_marker), 36, 36)
        val selectedIcon = getScaledDrawable(ContextCompat.getDrawable(requireContext(), CommonR.drawable.ic_stop_marker_red), 48, 48)
        allMarkers.forEach { (id, marker) ->
            marker.icon = if (selectedStopIds.contains(id)) selectedIcon else unselectedIcon
        }
    }

    private fun showDriverPicker(target: TextView) {
        db.collection("drivers").whereEqualTo("status", "active").get().addOnSuccessListener { snapshots ->
            if (!isAdded) return@addOnSuccessListener
            val items = snapshots.map { "${it.getString("firstName")} ${it.getString("lastName")}" }.toTypedArray()
            val ids = snapshots.map { it.id }
            AlertDialog.Builder(requireContext()).setTitle("Select Driver").setItems(items) { _, which ->
                selectedDriverId = ids[which]
                target.text = items[which]
            }.show()
        }
    }

    private fun showBusPicker(target: TextView, capacityTarget: TextView) {
        db.collection("buses").whereEqualTo("status", "Active").get().addOnSuccessListener { snapshots ->
            if (!isAdded) return@addOnSuccessListener
            val items = snapshots.map { it.getString("busNumber") ?: "N/A" }.toTypedArray()
            val docs = snapshots.documents
            AlertDialog.Builder(requireContext()).setTitle("Select Bus").setItems(items) { _, which ->
                val doc = docs[which]
                selectedBusId = doc.id
                busCapacity = (doc.get("capacity") as? Number)?.toInt() ?: 0
                target.text = items[which]
                capacityTarget.text = "$busCapacity Seats"
            }.show()
        }
    }

    private fun showConductorPicker(target: TextView) {
        db.collection("conductors").whereEqualTo("status", "active").get().addOnSuccessListener { snapshots ->
            if (!isAdded) return@addOnSuccessListener
            val items = snapshots.map { "${it.getString("firstName")} ${it.getString("lastName")}" }.toTypedArray()
            val ids = snapshots.map { it.id }
            AlertDialog.Builder(requireContext()).setTitle("Select Conductor").setItems(items) { _, which ->
                selectedConductorId = ids[which]
                target.text = items[which]
            }.show()
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

    private fun saveRouteChanges(name: String) {
        if (name.isEmpty() || selectedDriverId == null || selectedBusId == null || selectedStopIds.isEmpty()) {
            Toast.makeText(requireContext(), "Please fill all fields and select stops", Toast.LENGTH_SHORT).show()
            return
        }

        // Validation: Afternoon schedule should not use AM
        if (afternoonStartTime.contains("AM", ignoreCase = true) || afternoonEndTime.contains("AM", ignoreCase = true)) {
            Toast.makeText(requireContext(), "Afternoon schedule cannot use AM times", Toast.LENGTH_SHORT).show()
            return
        }

        val updates = hashMapOf(
            "routeName" to name,
            "driverId" to selectedDriverId,
            "busId" to selectedBusId,
            "conductorId" to selectedConductorId,
            "stopIds" to selectedStopIds,
            "maxCapacity" to busCapacity,
            "morningStartTime" to morningStartTime,
            "morningEndTime" to morningEndTime,
            "afternoonStartTime" to afternoonStartTime,
            "afternoonEndTime" to afternoonEndTime
        )

        db.collection("routes").document(route.id).update(updates).addOnSuccessListener {
            Toast.makeText(requireContext(), "Route updated successfully", Toast.LENGTH_SHORT).show()
            (requireActivity() as? AdminHome)?.loadRouting()
        }
    }
}
