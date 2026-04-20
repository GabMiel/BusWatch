package com.example.buswatch.admin.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.buswatch.admin.AdminHome
import com.example.buswatch.admin.R
import com.example.buswatch.admin.StopRequest
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class StopApprovalDetailFragment : Fragment() {
    private val db = FirebaseFirestore.getInstance()
    private lateinit var request: StopRequest

    companion object {
        fun newInstance(request: StopRequest) = StopApprovalDetailFragment().apply {
            this.request = request
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_stop_approval_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI(view)
        loadRequesterInfo(view)
        setupMaps(view)
    }

    private fun setupUI(view: View) {
        view.findViewById<View>(R.id.btnBackStopApprovalDetail)?.setOnClickListener {
            (requireActivity() as? AdminHome)?.replaceFragment(ApprovalsFragment())
        }

        view.findViewById<View>(R.id.btnApproveStop)?.setOnClickListener { approveRequest() }
        view.findViewById<View>(R.id.btnRejectStop)?.setOnClickListener { rejectRequest() }
    }

    private fun loadRequesterInfo(view: View) {
        view.findViewById<TextView>(R.id.tvStudentName).text = request.studentName
        view.findViewById<TextView>(R.id.tvCurrentStopName).text = request.currentStopName
        view.findViewById<TextView>(R.id.tvProposedStopName).text = request.proposedStopName

        db.collection("parents").document(request.parentId).get().addOnSuccessListener { doc ->
            if (doc.exists()) {
                @Suppress("UNCHECKED_CAST")
                val profile = doc.get("profile") as? Map<String, Any>
                view.findViewById<TextView>(R.id.tvParentFullName).text = "${profile?.get("firstName")} ${profile?.get("lastName")}"
                view.findViewById<TextView>(R.id.tvParentPhone).text = profile?.get("phone") as? String
                
                val avatar = profile?.get("avatarUrl") as? String
                if (!avatar.isNullOrEmpty()) {
                    Glide.with(this).load(avatar).circleCrop().into(view.findViewById(R.id.imgParent))
                }

                val childAvatar = (doc.get("child") as? Map<String, Any>)?.get("childAvatarUrl") as? String
                if (!childAvatar.isNullOrEmpty()) {
                    Glide.with(this).load(childAvatar).circleCrop().into(view.findViewById(R.id.imgStudent))
                }
            }
        }
    }

    private fun setupMaps(view: View) {
        val mapCurrent = view.findViewById<MapView>(R.id.mapCurrentStop)
        val mapProposed = view.findViewById<MapView>(R.id.mapProposedStop)

        if (request.currentStopLat != 0.0) {
            setupMapMarker(mapCurrent, GeoPoint(request.currentStopLat, request.currentStopLng), "Current Stop")
        }
        setupMapMarker(mapProposed, GeoPoint(request.proposedStopLat, request.proposedStopLng), "Proposed Stop")
    }

    private fun setupMapMarker(map: MapView, point: GeoPoint, title: String) {
        map.setMultiTouchControls(true)
        map.controller.setZoom(17.0)
        map.controller.setCenter(point)
        val marker = Marker(map)
        marker.position = point
        marker.title = title
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        map.overlays.add(marker)
        map.invalidate()
    }

    private fun approveRequest() {
        val batch = db.batch()
        
        // 1. Update the student's stop in the parent document
        val parentRef = db.collection("parents").document(request.parentId)
        val updates = hashMapOf<String, Any>(
            "child.stop" to request.proposedStopId,
            "child.lastStopApproved" to Timestamp.now() // For 30-day lock
        )
        batch.update(parentRef, updates)

        // 2. Mark request as approved
        val requestRef = db.collection("stop_requests").document(request.id)
        batch.update(requestRef, "status", "approved")

        batch.commit().addOnSuccessListener {
            Toast.makeText(requireContext(), "Pickup stop updated and locked for 30 days", Toast.LENGTH_SHORT).show()
            (requireActivity() as? AdminHome)?.replaceFragment(ApprovalsFragment())
        }
    }

    private fun rejectRequest() {
        db.collection("stop_requests").document(request.id).update("status", "rejected").addOnSuccessListener {
            Toast.makeText(requireContext(), "Request rejected", Toast.LENGTH_SHORT).show()
            (requireActivity() as? AdminHome)?.replaceFragment(ApprovalsFragment())
        }
    }
}
