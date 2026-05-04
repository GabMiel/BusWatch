package com.example.buswatch

import android.annotation.SuppressLint
import android.graphics.DashPathEffect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.example.buswatch.common.R as CommonR
import com.google.firebase.firestore.FirebaseFirestore
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

class StudentDetailsGeneralFragment : Fragment() {
    private lateinit var viewModel: StudentViewModel
    private val db = FirebaseFirestore.getInstance()
    
    private var parentStatus: String = "pending"
    private var homePoint: GeoPoint? = null
    private var childName: String? = null
    
    private val defaultPoint = GeoPoint(14.6760, 121.0437)

    companion object {
        fun newInstance(childName: String?): StudentDetailsGeneralFragment {
            val fragment = StudentDetailsGeneralFragment()
            val args = Bundle()
            args.putString("childName", childName)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[StudentViewModel::class.java]
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_student_details_general, container, false)
        childName = arguments?.getString("childName")
        
        setupUI(view)
        setupObservers(view)
        
        viewModel.loadStudentAndParentData(childName)
        
        return view
    }

    private fun setupUI(view: View) {
        view.findViewById<Button>(R.id.btnAssignStop)?.setOnClickListener {
            if (parentStatus.lowercase() == "approved") {
                val homeLat = homePoint?.latitude ?: 0.0
                val homeLng = homePoint?.longitude ?: 0.0
                StopPickerDialogFragment.newInstance(homeLat, homeLng, childName)
                    .show(childFragmentManager, "stop_picker")
            } else {
                Toast.makeText(requireContext(), "Action restricted until account is approved.", Toast.LENGTH_SHORT).show()
            }
        }
        
        view.findViewById<ImageButton>(R.id.btnGeneralEdit)?.setOnClickListener {
            EditStudentDialogFragment.newInstance(childName, parentStatus)
                .show(childFragmentManager, "edit_student")
        }
        
        view.findViewById<MapView>(R.id.mapHomeLocation)?.let { initMap(it, defaultPoint) }
        view.findViewById<MapView>(R.id.mapStopLocation)?.let { initMap(it, defaultPoint) }
    }

    private fun setupObservers(view: View) {
        viewModel.studentData.observe(viewLifecycleOwner) { childData ->
            if (childData != null) {
                displayChildInfo(view, childData)
                val currentStopId = childData["stop"] as? String
                updateStopAndMapUI(view, currentStopId)
            }
        }

        viewModel.parentStatus.observe(viewLifecycleOwner) { status ->
            parentStatus = status
        }

        viewModel.updateResult.observe(viewLifecycleOwner) { result ->
            result.onSuccess { message ->
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            }.onFailure { error ->
                Toast.makeText(requireContext(), error.message ?: "Action failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initMap(map: MapView, center: GeoPoint) {
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.setTilesScaledToDpi(true)
        map.controller.setZoom(15.0)
        map.controller.setCenter(center)

        // Allow map to handle touches inside NestedScrollView
        map.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> v.parent.requestDisallowInterceptTouchEvent(true)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.parent.requestDisallowInterceptTouchEvent(false)
            }
            false
        }
    }

    private fun displayChildInfo(view: View, childData: kotlin.collections.Map<String, Any>) {
        view.findViewById<TextView>(R.id.tvStudentName).text = getString(CommonR.string.name_format, childData["firstName"], childData["lastName"])
        
        val grade = childData["grade"] as? String ?: ""
        val section = childData["class"] as? String ?: childData["section"] as? String ?: ""
        view.findViewById<TextView>(R.id.tvHeaderGradeSection).text = if (grade.isNotEmpty() || section.isNotEmpty()) {
            "$grade - $section".trim(' ', '-')
        } else {
            ""
        }

        val studentId = childData["studentId"] as? String ?: ""
        val tvId = view.findViewById<TextView>(R.id.tvStudentId)
        if (studentId.isNotEmpty()) {
            tvId.text = "Student ID: $studentId"
            tvId.visibility = View.VISIBLE
        } else {
            tvId.visibility = View.GONE
        }

        view.findViewById<TextView>(R.id.tvDob).text = childData["age"]?.toString() ?: "-"
        view.findViewById<TextView>(R.id.tvSchool).text = childData["school"] as? String ?: "-"
        view.findViewById<TextView>(R.id.tvGrade).text = childData["grade"] as? String ?: "-"
        view.findViewById<TextView>(R.id.tvSection).text = childData["class"] as? String ?: "-"
        view.findViewById<TextView>(R.id.tvAddress).text = childData["address"] as? String ?: "-"
        
        val lat = childData["latitude"] as? Double ?: 0.0
        val lng = childData["longitude"] as? Double ?: 0.0
        homePoint = if (lat != 0.0 && lng != 0.0) GeoPoint(lat, lng) else null

        val homeMap = view.findViewById<MapView>(R.id.mapHomeLocation)
        homePoint?.let { 
            MapUtils.setupMarkerOnMap(requireContext(), homeMap, it, CommonR.drawable.ic_location) 
        } ?: run {
            homeMap.controller.setCenter(defaultPoint)
        }

        val avatar = childData["childAvatarUrl"] as? String ?: childData["avatarUrl"] as? String ?: ""
        val avatarView = view.findViewById<ImageView>(R.id.imgStudentAvatar)
        if (avatar.isNotEmpty()) {
            Glide.with(this).load(avatar).circleCrop().into(avatarView)
        } else {
            avatarView.setImageResource(CommonR.drawable.user)
        }
    }

    private fun updateStopAndMapUI(view: View, stopId: String?) {
        val tvStatus = view.findViewById<TextView>(R.id.tvAssignedStop)
        val btnAssign = view.findViewById<Button>(R.id.btnAssignStop)
        val stopMap = view.findViewById<MapView>(R.id.mapStopLocation)

        if (stopId.isNullOrEmpty()) {
            tvStatus?.setText(CommonR.string.pickup_stop_not_selected)
            btnAssign?.isVisible = true
            loadAllStopsOnMap(stopMap, null)
        } else {
            btnAssign?.isVisible = false
            db.collection("stops").document(stopId).get().addOnSuccessListener { stopDoc ->
                if (!isAdded) return@addOnSuccessListener
                val stopName = stopDoc.getString("name") ?: "Selected Stop"
                tvStatus?.text = getString(CommonR.string.confirmed_stop_format, stopName)
                loadAllStopsOnMap(stopMap, stopId)
            }
        }
    }

    private fun loadAllStopsOnMap(map: MapView?, selectedStopId: String?) {
        if (map == null) return
        map.overlays.clear()
        
        val points = mutableListOf<GeoPoint>()
        homePoint?.let { points.add(it) }

        db.collection("stops").whereEqualTo("status", "active").get().addOnSuccessListener { snapshots ->
            if (!isAdded) return@addOnSuccessListener
            
            var selectedPoint: GeoPoint? = null
            for (doc in snapshots) {
                val point = GeoPoint(doc.getDouble("latitude") ?: 0.0, doc.getDouble("longitude") ?: 0.0)
                if (point.latitude == 0.0) continue
                
                val isSelected = doc.id == selectedStopId
                if (isSelected) selectedPoint = point
                
                val marker = Marker(map)
                marker.position = point
                marker.title = doc.getString("name")
                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                
                val iconRes = if (isSelected) CommonR.drawable.ic_stop_marker_red else CommonR.drawable.ic_stop_marker_blue
                marker.icon = MapUtils.getScaledDrawable(requireContext(), iconRes, if (isSelected) 36 else 28, if (isSelected) 36 else 28)
                map.overlays.add(marker)
                points.add(point)
            }

            if (selectedPoint != null && homePoint != null) {
                val line = Polyline(map)
                line.setPoints(listOf(homePoint!!, selectedPoint))
                line.outlinePaint.color = "#FEBE1E".toColorInt()
                line.outlinePaint.strokeWidth = 6f
                line.outlinePaint.pathEffect = DashPathEffect(floatArrayOf(15f, 15f), 0f)
                map.overlays.add(0, line)
            }

            homePoint?.let { hp ->
                val homeMarker = Marker(map)
                homeMarker.position = hp
                homeMarker.title = "Home Location"
                homeMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                homeMarker.icon = MapUtils.getScaledDrawable(requireContext(), CommonR.drawable.ic_location, 32, 32)
                map.overlays.add(homeMarker)
            }

            if (points.isNotEmpty()) {
                val box = if (points.size > 1) BoundingBox.fromGeoPoints(points) else {
                    val p = points[0]
                    BoundingBox(p.latitude + 0.005, p.longitude + 0.005, p.latitude - 0.005, p.longitude - 0.005)
                }
                map.post { map.zoomToBoundingBox(box, true, 120) }
            }
            map.invalidate()
        }
    }
}
