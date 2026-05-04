package com.example.buswatch

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import com.example.buswatch.common.R as CommonR
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon

class StopPickerDialogFragment : DialogFragment() {

    private lateinit var viewModel: StudentViewModel
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    private var homePoint: GeoPoint? = null
    private var childName: String? = null
    private val defaultPoint = GeoPoint(14.6760, 121.0437)
    
    private var selectedStopId: String? = null
    private var selectedStopName: String? = null
    private var selectedStopPoint: GeoPoint? = null

    private var isMaximized = false

    companion object {
        fun newInstance(homeLat: Double, homeLng: Double, childName: String?): StopPickerDialogFragment {
            val fragment = StopPickerDialogFragment()
            val args = Bundle()
            args.putDouble("homeLat", homeLat)
            args.putDouble("homeLng", homeLng)
            args.putString("childName", childName)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, 0)
        // Use activity scope to match other fragments and ensure data is accessible
        viewModel = ViewModelProvider(requireActivity()).get(StudentViewModel::class.java)
        
        arguments?.let {
            val lat = it.getDouble("homeLat")
            val lng = it.getDouble("homeLng")
            if (lat != 0.0 && lng != 0.0) homePoint = GeoPoint(lat, lng)
            childName = it.getString("childName")
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            if (!isMaximized) {
                val width = (resources.displayMetrics.widthPixels * 0.95).toInt()
                window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
            } else {
                window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            }
            window.setBackgroundDrawableResource(android.R.color.transparent)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val currentStopId = viewModel.studentData.value?.get("stop") as? String
        val layoutRes = if (currentStopId.isNullOrEmpty()) {
            R.layout.dialog_assign_pickup_stop
        } else {
            R.layout.dialog_pickup_stop
        }
        return inflater.inflate(layoutRes, container, false)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mapView = view.findViewById<MapView>(R.id.mapStopPicker)
        val tvSelectedStop = view.findViewById<TextView>(R.id.tvSelectedStopName)
        val btnMaximize = view.findViewById<ImageButton>(R.id.btnWholeView)
        val btnMyLocation = view.findViewById<ImageButton>(R.id.btnMapMyLocation)
        
        val markers = mutableListOf<Marker>()

        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.setTilesScaledToDpi(true)
        mapView.controller.setZoom(15.5)
        mapView.controller.setCenter(homePoint ?: defaultPoint)

        homePoint?.let { hp ->
            val homeMarker = Marker(mapView)
            homeMarker.position = hp
            homeMarker.title = "Home Location"
            homeMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            homeMarker.icon = MapUtils.getScaledDrawable(requireContext(), CommonR.drawable.ic_location, 32, 32)
            mapView.overlays.add(homeMarker)

            val circle = Polygon(mapView)
            circle.points = Polygon.pointsAsCircle(hp, 2000.0)
            circle.fillPaint.color = Color.argb(30, 0, 122, 255)
            circle.outlinePaint.color = Color.parseColor("#007AFF")
            circle.outlinePaint.strokeWidth = 2f
            mapView.overlays.add(0, circle)
        }

        viewModel.activeStops.observe(viewLifecycleOwner) { stops ->
            markers.forEach { mapView.overlays.remove(it) }
            markers.clear()
            
            for (stop in stops) {
                val point = GeoPoint(stop["latitude"] as? Double ?: 0.0, stop["longitude"] as? Double ?: 0.0)
                if (point.latitude == 0.0) continue

                val name = stop["name"] as? String ?: "Unknown"
                val marker = Marker(mapView)
                marker.position = point
                marker.title = name
                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                marker.icon = MapUtils.getScaledDrawable(requireContext(), CommonR.drawable.ic_stop_marker_blue, 32, 32)
                
                marker.setOnMarkerClickListener { m, _ ->
                    markers.forEach { it.icon = MapUtils.getScaledDrawable(requireContext(), CommonR.drawable.ic_stop_marker_blue, 32, 32) }
                    m.icon = MapUtils.getScaledDrawable(requireContext(), CommonR.drawable.ic_stop_marker_red, 36, 36)
                    selectedStopId = stop["id"] as? String
                    selectedStopName = name
                    selectedStopPoint = m.position
                    tvSelectedStop.text = name
                    tvSelectedStop.setTextColor(Color.BLACK)
                    m.showInfoWindow()
                    mapView.controller.animateTo(m.position)
                    mapView.invalidate()
                    true
                }
                mapView.overlays.add(marker)
                markers.add(marker)
            }
            mapView.invalidate()
        }
        
        viewModel.loadActiveStops()

        btnMaximize.setOnClickListener {
            isMaximized = !isMaximized
            toggleMaximize(view, btnMaximize)
        }

        btnMyLocation.setOnClickListener {
            homePoint?.let {
                mapView.controller.animateTo(it)
                mapView.controller.setZoom(17.0)
            } ?: run {
                Toast.makeText(requireContext(), "Home location not set", Toast.LENGTH_SHORT).show()
            }
        }

        view.findViewById<Button>(R.id.btnCancelStop).setOnClickListener { dismiss() }
        view.findViewById<Button>(R.id.btnConfirmStop).setOnClickListener {
            if (selectedStopId != null && selectedStopName != null) {
                showConfirmationPopup()
            } else Toast.makeText(requireContext(), "Select a stop from map", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleMaximize(view: View, btnMaximize: ImageButton) {
        val visibility = if (isMaximized) View.GONE else View.VISIBLE
        view.findViewById<View>(R.id.tvPickupStopTitle).visibility = visibility
        view.findViewById<View>(R.id.tvPickupStopSubtitle).visibility = visibility
        view.findViewById<View>(R.id.tvSelectedStopLabel).visibility = visibility
        view.findViewById<View>(R.id.tvSelectedStopName).visibility = visibility
        view.findViewById<View>(R.id.llStopActions).visibility = visibility
        
        val cvMap = view.findViewById<View>(R.id.cvStopMap)
        val params = cvMap.layoutParams as ViewGroup.MarginLayoutParams
        if (isMaximized) {
            params.height = ViewGroup.LayoutParams.MATCH_PARENT
            params.topMargin = 0
            btnMaximize.setImageResource(CommonR.drawable.ic_close)
            dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        } else {
            params.height = (250 * resources.displayMetrics.density).toInt()
            params.topMargin = (20 * resources.displayMetrics.density).toInt()
            btnMaximize.setImageResource(CommonR.drawable.ic_eye)
            val width = (resources.displayMetrics.widthPixels * 0.95).toInt()
            dialog?.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        cvMap.layoutParams = params
    }

    private fun showConfirmationPopup() {
        val currentStopId = viewModel.studentData.value?.get("stop") as? String
        val isFirstTime = currentStopId.isNullOrEmpty()
        
        val message = if (isFirstTime) {
            "Do you want to assign '$selectedStopName' as the pickup stop?"
        } else {
            "Are you sure you want to request a change to '$selectedStopName'? This requires admin approval."
        }
        
        AlertDialog.Builder(requireContext())
            .setTitle("Confirm Selection")
            .setMessage(message)
            .setPositiveButton("CONFIRM") { _, _ -> submitStopRequest() }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun submitStopRequest() {
        val uid = auth.currentUser?.uid ?: return
        val studentData = viewModel.studentData.value ?: run {
            Toast.makeText(requireContext(), "Student data not found", Toast.LENGTH_SHORT).show()
            return
        }
        val currentStopId = studentData["stop"] as? String ?: ""
        
        if (currentStopId.isEmpty()) {
            updateStopDirectly(uid, studentData)
        } else {
            db.collection("stops").document(currentStopId).get().addOnSuccessListener { doc ->
                val currentStopName = doc.getString("name") ?: "Unknown"
                val currentLat = doc.getDouble("latitude") ?: 0.0
                val currentLng = doc.getDouble("longitude") ?: 0.0
                saveRequestToFirestore(uid, currentStopId, currentStopName, currentLat, currentLng, studentData)
            }.addOnFailureListener {
                saveRequestToFirestore(uid, currentStopId, "Unknown", 0.0, 0.0, studentData)
            }
        }
    }

    private fun updateStopDirectly(parentId: String, studentData: kotlin.collections.Map<String, Any>) {
        val firstName = studentData["firstName"] as? String
        val lastName = studentData["lastName"] as? String
        
        db.collection("parents").document(parentId).get().addOnSuccessListener { doc ->
            if (!doc.exists()) return@addOnSuccessListener
            
            val batch = db.batch()
            val parentRef = db.collection("parents").document(parentId)
            
            @Suppress("UNCHECKED_CAST")
            val docChild = doc.get("child") as? kotlin.collections.Map<String, Any>
            if (docChild?.get("firstName") == firstName && docChild?.get("lastName") == lastName) {
                batch.update(parentRef, "child.stop", selectedStopId)
            } else {
                @Suppress("UNCHECKED_CAST")
                val childrenList = doc.get("children") as? List<kotlin.collections.Map<String, Any>>
                val newList = childrenList?.map { c ->
                    if (c["firstName"] == firstName && c["lastName"] == lastName) {
                        val mutableChild = c.toMutableMap()
                        mutableChild["stop"] = selectedStopId ?: ""
                        mutableChild
                    } else c
                }
                if (newList != null) batch.update(parentRef, "children", newList)
            }
            
            batch.commit().addOnSuccessListener {
                Toast.makeText(requireContext(), "Pickup stop assigned successfully", Toast.LENGTH_SHORT).show()
                viewModel.loadStudentAndParentData(childName)
                dismiss()
            }.addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to assign stop", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveRequestToFirestore(uid: String, cId: String, cName: String, cLat: Double, cLng: Double, studentData: kotlin.collections.Map<String, Any>) {
        val firstName = studentData["firstName"] as? String ?: ""
        val lastName = studentData["lastName"] as? String ?: ""

        val request = hashMapOf(
            "parentId" to uid,
            "studentName" to "$firstName $lastName",
            "studentFirstName" to firstName,
            "studentLastName" to lastName,
            "currentStopId" to cId,
            "currentStopName" to cName,
            "currentStopLat" to cLat,
            "currentStopLng" to cLng,
            "proposedStopId" to selectedStopId,
            "proposedStopName" to selectedStopName,
            "proposedStopLat" to (selectedStopPoint?.latitude ?: 0.0),
            "proposedStopLng" to (selectedStopPoint?.longitude ?: 0.0),
            "status" to "pending",
            "timestamp" to com.google.firebase.Timestamp.now()
        )

        db.collection("stop_requests").add(request).addOnSuccessListener {
            Toast.makeText(requireContext(), "Change request submitted for admin approval", Toast.LENGTH_LONG).show()
            dismiss()
        }.addOnFailureListener {
            Toast.makeText(requireContext(), "Failed to submit request", Toast.LENGTH_SHORT).show()
        }
    }
}
