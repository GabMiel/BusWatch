package com.example.buswatch.admin

import android.app.TimePickerDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import com.example.buswatch.common.R as CommonR
import com.google.firebase.firestore.FirebaseFirestore
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.util.Calendar
import java.util.Locale
import kotlin.random.Random

class AddRouteDialog(
    private val context: Context,
    private val db: FirebaseFirestore,
    private val onRouteAdded: () -> Unit
) {
    private var selectedDriverId: String? = null
    private var selectedBusId: String? = null
    private var selectedConductorId: String? = null
    private var selectedStopIds = mutableListOf<String>()
    private var busCapacity = 0
    private var isMaximized = false
    private var routePolyline: Polyline? = null
    private val allMarkers = mutableMapOf<String, Marker>()

    private var morningStartTime: String = ""
    private var morningEndTime: String = ""
    private var afternoonStartTime: String = ""
    private var afternoonEndTime: String = ""

    fun show() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_route, null)
        val dialog = AlertDialog.Builder(context).setView(dialogView).create()

        val etRouteName = dialogView.findViewById<EditText>(R.id.etRouteName)
        val tvSelectedDriver = dialogView.findViewById<TextView>(R.id.tvSelectedDriver)
        val tvSelectedBus = dialogView.findViewById<TextView>(R.id.tvSelectedBus)
        val tvBusCapacity = dialogView.findViewById<TextView>(R.id.tvBusCapacity)
        val tvSelectedConductor = dialogView.findViewById<TextView>(R.id.tvSelectedConductor)
        val tvSelectedStopsCount = dialogView.findViewById<TextView>(R.id.tvSelectedStopsCount)
        val mapRoutePicker = dialogView.findViewById<MapView>(R.id.mapRoutePicker)
        val btnMaximize = dialogView.findViewById<ImageButton>(R.id.btnMaximizeMap)
        val btnMyLocation = dialogView.findViewById<ImageButton>(R.id.btnMyLocation)

        val tvMorningStart = dialogView.findViewById<TextView>(R.id.tvMorningStart)
        val tvMorningEnd = dialogView.findViewById<TextView>(R.id.tvMorningEnd)
        val tvAfternoonStart = dialogView.findViewById<TextView>(R.id.tvAfternoonStart)
        val tvAfternoonEnd = dialogView.findViewById<TextView>(R.id.tvAfternoonEnd)

        // UI elements to hide when maximized
        val headerLayout = dialogView.findViewById<View>(R.id.headerLayout)
        val layoutRouteName = dialogView.findViewById<View>(R.id.layoutRouteName)
        val layoutDriver = dialogView.findViewById<View>(R.id.layoutDriver)
        val layoutBus = dialogView.findViewById<View>(R.id.layoutBus)
        val layoutCapacity = dialogView.findViewById<View>(R.id.layoutCapacity)
        val layoutConductor = dialogView.findViewById<View>(R.id.layoutConductor)
        val layoutActions = dialogView.findViewById<View>(R.id.btnSaveRoute)
        val mapContainer = dialogView.findViewById<FrameLayout>(R.id.mapContainer)
        val layoutMorning = dialogView.findViewById<View>(R.id.layoutMorningTime)
        val layoutAfternoon = dialogView.findViewById<View>(R.id.layoutAfternoonTime)

        // Time Pickers
        tvMorningStart?.setOnClickListener { showTimePicker { time -> morningStartTime = time; tvMorningStart.text = time } }
        tvMorningEnd?.setOnClickListener { showTimePicker { time -> morningEndTime = time; tvMorningEnd.text = time } }
        tvAfternoonStart?.setOnClickListener { showTimePicker { time -> afternoonStartTime = time; tvAfternoonStart.text = time } }
        tvAfternoonEnd?.setOnClickListener { showTimePicker { time -> afternoonEndTime = time; tvAfternoonEnd.text = time } }

        // Dropdown Buttons
        dialogView.findViewById<FrameLayout>(R.id.btnDriverDropdown).setOnClickListener {
            showDriverPicker(tvSelectedDriver)
        }
        dialogView.findViewById<FrameLayout>(R.id.btnBusDropdown).setOnClickListener {
            showBusPicker(tvSelectedBus, tvBusCapacity)
        }
        dialogView.findViewById<FrameLayout>(R.id.btnConductorDropdown).setOnClickListener {
            showConductorPicker(tvSelectedConductor)
        }

        // Setup Map for Stop Selection
        setupStopPickerMap(mapRoutePicker, tvSelectedStopsCount)

        btnMaximize?.setOnClickListener {
            isMaximized = !isMaximized
            headerLayout?.isVisible = !isMaximized
            layoutRouteName?.isVisible = !isMaximized
            layoutDriver?.isVisible = !isMaximized
            layoutBus?.isVisible = !isMaximized
            layoutCapacity?.isVisible = !isMaximized
            layoutConductor?.isVisible = !isMaximized
            layoutActions?.isVisible = !isMaximized
            layoutMorning?.isVisible = !isMaximized
            layoutAfternoon?.isVisible = !isMaximized

            val params = mapContainer?.layoutParams
            if (isMaximized) {
                params?.height = (480 * context.resources.displayMetrics.density).toInt()
                btnMaximize.setImageResource(CommonR.drawable.ic_close)
            } else {
                params?.height = (250 * context.resources.displayMetrics.density).toInt()
                btnMaximize.setImageResource(CommonR.drawable.ic_eye)
            }
            mapContainer?.layoutParams = params
            mapRoutePicker?.invalidate()
        }

        btnMyLocation?.setOnClickListener {
            if (allMarkers.isNotEmpty()) {
                val markerList = allMarkers.values.toList()
                val randomMarker = markerList[Random.nextInt(markerList.size)]
                mapRoutePicker?.controller?.animateTo(randomMarker.position)
                mapRoutePicker?.controller?.setZoom(17.0)
            } else {
                mapRoutePicker?.controller?.animateTo(GeoPoint(14.7566, 121.0450))
                mapRoutePicker?.controller?.setZoom(15.0)
            }
        }

        dialogView.findViewById<ImageButton>(R.id.btnCloseAddRoute).setOnClickListener { dialog.dismiss() }
        
        dialogView.findViewById<TextView>(R.id.btnSaveRoute).setOnClickListener {
            saveRoute(etRouteName.text.toString(), dialog)
        }

        dialog.show()
    }

    private fun showTimePicker(onTimeSelected: (String) -> Unit) {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)

        TimePickerDialog(context, { _, h, m ->
            val amPm = if (h < 12) "AM" else "PM"
            val hourFormatted = if (h % 12 == 0) 12 else h % 12
            val timeString = String.format(Locale.getDefault(), "%02d:%02d %s", hourFormatted, m, amPm)
            onTimeSelected(timeString)
        }, hour, minute, false).show()
    }

    private fun showDriverPicker(target: TextView) {
        db.collection("drivers").whereEqualTo("status", "active").get().addOnSuccessListener { snapshots ->
            if (snapshots.isEmpty) {
                Toast.makeText(context, "No active drivers found", Toast.LENGTH_SHORT).show()
                return@addOnSuccessListener
            }
            val items = snapshots.map { "${it.getString("firstName") ?: ""} ${it.getString("lastName") ?: ""}".trim() }.toTypedArray()
            val ids = snapshots.map { it.id }
            AlertDialog.Builder(context).setTitle("Select Driver").setItems(items) { _, which ->
                selectedDriverId = ids[which]
                target.text = items[which]
            }.show()
        }
    }

    private fun showBusPicker(target: TextView, capacityTarget: TextView) {
        db.collection("buses").whereEqualTo("status", "Active").get().addOnSuccessListener { snapshots ->
            if (snapshots.isEmpty) {
                Toast.makeText(context, "No active buses found", Toast.LENGTH_SHORT).show()
                return@addOnSuccessListener
            }
            val buses = snapshots.documents
            val items = buses.map { it.getString("busNumber") ?: "N/A" }.toTypedArray()
            
            AlertDialog.Builder(context).setTitle("Select Bus").setItems(items) { _, which ->
                val doc = buses[which]
                selectedBusId = doc.id
                
                val cap = doc.get("capacity")
                busCapacity = when (cap) {
                    is Number -> cap.toInt()
                    is String -> cap.toIntOrNull() ?: 0
                    else -> 0
                }
                
                target.text = items[which]
                capacityTarget.text = context.getString(CommonR.string._12_seats).replace("12", busCapacity.toString())
            }.show()
        }.addOnFailureListener {
            Toast.makeText(context, "Error fetching buses: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showConductorPicker(target: TextView) {
        db.collection("conductors").whereEqualTo("status", "active").get().addOnSuccessListener { snapshots ->
            if (snapshots.isEmpty) {
                Toast.makeText(context, "No active conductors found", Toast.LENGTH_SHORT).show()
                return@addOnSuccessListener
            }
            val items = snapshots.map { "${it.getString("firstName") ?: ""} ${it.getString("lastName") ?: ""}".trim() }.toTypedArray()
            val ids = snapshots.map { it.id }
            AlertDialog.Builder(context).setTitle("Select Conductor").setItems(items) { _, which ->
                selectedConductorId = ids[which]
                target.text = items[which]
            }.show()
        }
    }

    private fun getScaledDrawable(drawable: Drawable?, widthDp: Int, heightDp: Int): Drawable? {
        if (drawable == null) return null
        val density = context.resources.displayMetrics.density
        val width = (widthDp * density).toInt()
        val height = (heightDp * density).toInt()
        
        val bitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888).applyCanvas {
            drawable.setBounds(0, 0, width, height)
            drawable.draw(this)
        }
        return bitmap.toDrawable(context.resources)
    }

    private fun setupStopPickerMap(map: MapView, countTarget: TextView) {
        map.setMultiTouchControls(true)
        map.controller.setZoom(15.0)
        map.controller.setCenter(GeoPoint(14.7566, 121.0450)) // IMA Area

        routePolyline = Polyline(map)
        routePolyline?.outlinePaint?.color = "#4A90E2".toColorInt() // Modern Blue
        routePolyline?.outlinePaint?.strokeWidth = 8f
        map.overlays.add(routePolyline)

        db.collection("stops").whereEqualTo("status", "active").get().addOnSuccessListener { snapshots ->
            // Making icons larger and more distinguishable
            val unselectedIcon = getScaledDrawable(ContextCompat.getDrawable(context, CommonR.drawable.ic_stop_marker), 36, 36)
            val selectedIcon = getScaledDrawable(ContextCompat.getDrawable(context, CommonR.drawable.ic_stop_marker_red), 48, 48)

            for (doc in snapshots) {
                val lat = doc.getDouble("latitude") ?: 0.0
                val lng = doc.getDouble("longitude") ?: 0.0
                val name = doc.getString("name") ?: ""
                val stopId = doc.id

                val marker = Marker(map)
                marker.position = GeoPoint(lat, lng)
                marker.title = name
                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                marker.icon = if (selectedStopIds.contains(stopId)) selectedIcon else unselectedIcon
                
                allMarkers[stopId] = marker

                marker.setOnMarkerClickListener { m, _ ->
                    if (selectedStopIds.contains(stopId)) {
                        selectedStopIds.remove(stopId)
                        m.icon = unselectedIcon
                        m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    } else {
                        selectedStopIds.add(stopId)
                        m.icon = selectedIcon
                        m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    }
                    updateRouteLine()
                    countTarget.text = context.getString(R.string.selected_stops_count, selectedStopIds.size)
                    map.invalidate()
                    true
                }
                map.overlays.add(marker)
            }
            map.invalidate()
        }
    }

    private fun updateRouteLine() {
        val points = selectedStopIds.mapNotNull { stopId ->
            allMarkers[stopId]?.position
        }
        routePolyline?.setPoints(points)
    }

    private fun saveRoute(name: String, dialog: AlertDialog) {
        if (name.isEmpty() || selectedDriverId == null || selectedBusId == null || selectedStopIds.isEmpty()) {
            Toast.makeText(context, "Please fill all required fields and select stops", Toast.LENGTH_SHORT).show()
            return
        }

        val routeData = hashMapOf(
            "routeName" to name,
            "driverId" to selectedDriverId,
            "busId" to selectedBusId,
            "conductorId" to selectedConductorId,
            "stopIds" to selectedStopIds,
            "status" to "Active",
            "maxCapacity" to busCapacity,
            "currentCapacity" to 0,
            "morningStartTime" to morningStartTime,
            "morningEndTime" to morningEndTime,
            "afternoonStartTime" to afternoonStartTime,
            "afternoonEndTime" to afternoonEndTime,
            "createdAt" to com.google.firebase.Timestamp.now()
        )

        db.collection("routes").add(routeData).addOnSuccessListener {
            onRouteAdded()
            dialog.dismiss()
            Toast.makeText(context, "Route created successfully", Toast.LENGTH_SHORT).show()
        }
    }
}
