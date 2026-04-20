package com.example.buswatch.admin.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.buswatch.admin.*
import com.example.buswatch.common.R as CommonR
import com.google.firebase.firestore.FirebaseFirestore
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.util.Locale

class StopDetailFragment : Fragment() {
    private val db = FirebaseFirestore.getInstance()
    private lateinit var stop: StopAdmin
    private var onBack: (() -> Unit)? = null
    private val assignedStudents = mutableListOf<AssignedStudent>()

    companion object {
        fun newInstance(stop: StopAdmin, onBack: () -> Unit) = StopDetailFragment().apply {
            this.stop = stop
            this.onBack = onBack
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_view_stop, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI(view)
        loadMap(view)
        fetchAssignedStudents(view)
    }

    private fun setupUI(view: View) {
        val backHandler = { onBack?.invoke() }
        view.findViewById<ImageButton>(R.id.btnBackStopDetail)?.setOnClickListener { backHandler() }
        view.findViewById<View>(R.id.btnBackStopAction)?.setOnClickListener { backHandler() }
        
        view.findViewById<View>(R.id.btnEditStopAction)?.setOnClickListener {
            (requireActivity() as? AdminHome)?.editStopDetailInternal(stop)
        }

        view.findViewById<TextView>(R.id.tvStopName).text = stop.name
        view.findViewById<TextView>(R.id.tvStopCoordinates).text = String.format(Locale.US, "%.6f, %.6f", stop.latitude, stop.longitude)
        
        val rv = view.findViewById<RecyclerView>(R.id.recyclerAssignedStudents)
        rv?.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun loadMap(view: View) {
        val mapView = view.findViewById<MapView>(R.id.mapStopView)
        if (mapView != null) {
            mapView.setMultiTouchControls(true)
            mapView.controller.setZoom(17.0)
            val point = GeoPoint(stop.latitude, stop.longitude)
            mapView.controller.setCenter(point)
            val marker = Marker(mapView)
            marker.position = point
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            marker.icon = ContextCompat.getDrawable(requireContext(), CommonR.drawable.ic_stop_marker)
            mapView.overlays.add(marker)
            mapView.invalidate()
        }
    }

    private fun fetchAssignedStudents(view: View) {
        db.collection("parents").whereEqualTo("child.stop", stop.id).get().addOnSuccessListener { snapshots ->
            assignedStudents.clear()
            for (doc in snapshots) {
                val child = doc.get("child") as? Map<String, Any>
                val fName = child?.get("firstName") as? String ?: ""
                val lName = child?.get("lastName") as? String ?: ""
                val grade = child?.get("grade") as? String ?: "N/A"
                val photoUrl = child?.get("photoUrl") as? String ?: ""
                assignedStudents.add(AssignedStudent(doc.id, "$fName $lName", grade, photoUrl))
            }
            view.findViewById<TextView>(R.id.tvStopStudentCount).text = "${assignedStudents.size} Students"
            view.findViewById<RecyclerView>(R.id.recyclerAssignedStudents)?.adapter = AssignedStudentAdapter(assignedStudents)
        }
    }
}
