package com.example.buswatch.admin.fragments

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.buswatch.admin.*
import com.example.buswatch.common.R as CommonR
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.util.Locale

class StopDetailFragment : Fragment() {
    private val db = FirebaseFirestore.getInstance()
    private lateinit var stop: StopAdmin
    private var onBack: (() -> Unit)? = null
    private val assignedStudents = mutableListOf<AssignedStudent>()
    private var isMaximized = false
    private var parentsListener: ListenerRegistration? = null

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
        startParentsListener(view)
    }

    override fun onDestroyView() {
        parentsListener?.remove()
        super.onDestroyView()
    }

    private fun setupUI(view: View) {
        view.findViewById<ImageButton>(R.id.btnBackStopDetail)?.setOnClickListener { onBack?.invoke() }
        
        view.findViewById<TextView>(R.id.tvStopName).text = stop.name
        view.findViewById<TextView>(R.id.tvStopCoordinates).text = String.format(Locale.US, "%.6f, %.6f", stop.latitude, stop.longitude)
        
        val rv = view.findViewById<RecyclerView>(R.id.recyclerAssignedStudents)
        rv?.layoutManager = LinearLayoutManager(requireContext())

        // Maximize Map Logic
        val btnMaximize = view.findViewById<ImageButton>(R.id.btnMaximizeMap)
        val btnRecenter = view.findViewById<ImageButton>(R.id.btnRecenterStop)
        val mapContainer = view.findViewById<FrameLayout>(R.id.mapContainer)
        val mapView = view.findViewById<MapView>(R.id.mapStopView)
        
        btnMaximize?.setOnClickListener {
            isMaximized = !isMaximized
            val params = mapContainer?.layoutParams
            if (isMaximized) {
                params?.height = (480 * resources.displayMetrics.density).toInt()
                btnMaximize.setImageResource(CommonR.drawable.ic_close)
            } else {
                params?.height = (250 * resources.displayMetrics.density).toInt()
                btnMaximize.setImageResource(CommonR.drawable.ic_eye)
            }
            mapContainer?.layoutParams = params
            mapView?.postDelayed({ 
                mapView.controller.animateTo(GeoPoint(stop.latitude, stop.longitude)) 
            }, 200)
        }

        btnRecenter?.setOnClickListener {
            mapView?.controller?.animateTo(GeoPoint(stop.latitude, stop.longitude))
            mapView?.controller?.setZoom(18.0)
        }
    }

    private fun getScaledDrawable(drawable: Drawable?): Drawable? {
        if (drawable == null) return null
        val density = resources.displayMetrics.density
        val size = (36 * density).toInt()
        
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return BitmapDrawable(resources, bitmap)
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
            
            val pinDrawable = ContextCompat.getDrawable(requireContext(), CommonR.drawable.ic_stop_marker_red)
            marker.icon = getScaledDrawable(pinDrawable)

            mapView.overlays.add(marker)
            mapView.invalidate()
        }
    }

    private fun startParentsListener(view: View) {
        parentsListener?.remove()
        parentsListener = db.collection("parents").addSnapshotListener { snapshots, e ->
            if (e != null || snapshots == null) return@addSnapshotListener
            if (!isAdded) return@addSnapshotListener

            assignedStudents.clear()
            for (doc in snapshots) {
                @Suppress("UNCHECKED_CAST")
                val child = doc.get("child") as? Map<String, Any>
                if (child != null && child["stop"] == stop.id) {
                    addStudentFromMap(doc.id, child)
                }

                @Suppress("UNCHECKED_CAST")
                val childrenList = doc.get("children") as? List<Map<String, Any>>
                childrenList?.forEach { c ->
                    if (c["stop"] == stop.id) {
                        addStudentFromMap(doc.id, c)
                    }
                }
            }
            view.findViewById<TextView>(R.id.tvStopStudentCount).text = getString(R.string.stop_student_count_format, assignedStudents.size)
            view.findViewById<RecyclerView>(R.id.recyclerAssignedStudents)?.adapter = AssignedStudentAdapter(assignedStudents)
        }
    }

    private fun addStudentFromMap(parentId: String, data: Map<String, Any>) {
        val fName = data["firstName"] as? String ?: ""
        val lName = data["lastName"] as? String ?: ""
        val grade = data["grade"] as? String ?: "N/A"
        val photoUrl = (data["childAvatarUrl"] as? String) ?: (data["avatarUrl"] as? String) ?: ""
        assignedStudents.add(AssignedStudent(parentId, "$fName $lName", grade, photoUrl))
    }
}
