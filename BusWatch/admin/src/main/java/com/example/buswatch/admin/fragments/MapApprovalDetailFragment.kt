package com.example.buswatch.admin.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.buswatch.admin.AdminHome
import com.example.buswatch.admin.MapRequest
import com.example.buswatch.admin.R
import com.example.buswatch.common.NotificationSender
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class MapApprovalDetailFragment : Fragment() {
    private val db = FirebaseFirestore.getInstance()
    private lateinit var request: MapRequest

    companion object {
        fun newInstance(request: MapRequest) = MapApprovalDetailFragment().apply {
            this.request = request
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_map_approval_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI(view)
        loadRequesterInfo(view)
        setupMaps(view)
    }

    private fun setupUI(view: View) {
        view.findViewById<View>(R.id.btnBackMapDetail)?.setOnClickListener {
            (requireActivity() as? AdminHome)?.replaceFragment(ApprovalsFragment())
        }

        view.findViewById<View>(R.id.btnApproveMap)?.setOnClickListener { approveRequest() }
        view.findViewById<View>(R.id.btnRejectMap)?.setOnClickListener { rejectRequest() }
    }

    private fun loadRequesterInfo(view: View) {
        view.findViewById<TextView>(R.id.tvStudentName)?.text = request.studentName
        view.findViewById<TextView>(R.id.tvCurrentAddress)?.text = request.currentAddress
        view.findViewById<TextView>(R.id.tvPendingAddress)?.text = request.pendingAddress

        db.collection("parents").document(request.parentId).get().addOnSuccessListener { doc ->
            if (doc.exists()) {
                @Suppress("UNCHECKED_CAST")
                val profile = doc.get("profile") as? Map<String, Any>
                view.findViewById<TextView>(R.id.tvParentFullName)?.text = getString(com.example.buswatch.common.R.string.name_format, profile?.get("firstName"), profile?.get("lastName"))
                view.findViewById<TextView>(R.id.tvParentPhone)?.text = profile?.get("phone") as? String
                
                val parentAvatar = profile?.get("parentAvatarUrl") as? String
                val imgParent = view.findViewById<ImageView>(R.id.imgParent)
                if (!parentAvatar.isNullOrEmpty() && imgParent != null) {
                    Glide.with(this).load(parentAvatar).circleCrop().into(imgParent)
                }

                // Load Student Avatar - checking primary child or list
                @Suppress("UNCHECKED_CAST")
                val docChild = doc.get("child") as? Map<String, Any>
                var studentAvatarUrl = docChild?.get("childAvatarUrl") as? String
                
                if (studentAvatarUrl.isNullOrEmpty()) {
                    @Suppress("UNCHECKED_CAST")
                    val children = doc.get("children") as? List<Map<String, Any>>
                    val child = children?.find { 
                        val fName = it["firstName"] as? String ?: ""
                        val lName = it["lastName"] as? String ?: ""
                        "$fName $lName" == request.studentName 
                    }
                    studentAvatarUrl = child?.get("childAvatarUrl") as? String
                }

                val imgStudent = view.findViewById<ImageView>(R.id.imgStudent)
                if (!studentAvatarUrl.isNullOrEmpty() && imgStudent != null) {
                    Glide.with(this).load(studentAvatarUrl).circleCrop().into(imgStudent)
                }
            }
        }
    }

    private fun setupMaps(view: View) {
        val mapCurrent = view.findViewById<MapView>(R.id.mapCurrentLocation)
        val mapPending = view.findViewById<MapView>(R.id.mapPendingLocation)

        if (mapCurrent != null) {
            setupMapMarker(mapCurrent, GeoPoint(request.currentLat, request.currentLng), "Current Location")
        }
        if (mapPending != null) {
            setupMapMarker(mapPending, GeoPoint(request.pendingLat, request.pendingLng), "Proposed Location")
        }
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
        val parentRef = db.collection("parents").document(request.parentId)
        
        db.collection("parents").document(request.parentId).get().addOnSuccessListener { doc ->
            @Suppress("UNCHECKED_CAST")
            val docChild = doc.get("child") as? Map<String, Any>
            val primaryName = "${docChild?.get("firstName")} ${docChild?.get("lastName")}"
            
            if (primaryName == request.studentName) {
                val updates = hashMapOf<String, Any>(
                    "child.address" to request.pendingAddress,
                    "child.latitude" to request.pendingLat,
                    "child.longitude" to request.pendingLng,
                    "child.lastHomeLocationApproved" to Timestamp.now()
                )
                batch.update(parentRef, updates)
            } else {
                @Suppress("UNCHECKED_CAST")
                val childrenList = doc.get("children") as? List<Map<String, Any>>
                val newList = childrenList?.map { c ->
                    val cName = "${c["firstName"]} ${c["lastName"]}"
                    if (cName == request.studentName) {
                        val mutableChild = c.toMutableMap()
                        mutableChild["address"] = request.pendingAddress
                        mutableChild["latitude"] = request.pendingLat
                        mutableChild["longitude"] = request.pendingLng
                        mutableChild["lastHomeLocationApproved"] = Timestamp.now()
                        mutableChild
                    } else c
                }
                if (newList != null) {
                    batch.update(parentRef, "children", newList)
                }
            }

            val requestRef = db.collection("map_requests").document(request.id)
            batch.update(requestRef, "status", "approved")

            batch.commit().addOnSuccessListener {
                sendNotificationToParent(true)
                Toast.makeText(requireContext(), "Home location updated and locked for 30 days", Toast.LENGTH_SHORT).show()
                (requireActivity() as? AdminHome)?.replaceFragment(ApprovalsFragment())
            }
        }
    }

    private fun rejectRequest() {
        db.collection("map_requests").document(request.id).update("status", "rejected").addOnSuccessListener {
            sendNotificationToParent(false)
            Toast.makeText(requireContext(), "Request rejected", Toast.LENGTH_SHORT).show()
            (requireActivity() as? AdminHome)?.replaceFragment(ApprovalsFragment())
        }
    }

    private fun sendNotificationToParent(isApproved: Boolean) {
        val type = "Home Address Update"
        val statusText = if (isApproved) "APPROVED" else "REJECTED"
        val title = "$type $statusText"
        val message = if (isApproved) 
            "Your request to update your child's home address has been approved."
        else 
            "Your request to update your child's home address has been rejected. Please contact support for more information."

        val notifData = hashMapOf(
            "title" to title,
            "message" to message,
            "timestamp" to FieldValue.serverTimestamp(),
            "isRead" to false,
            "type" to "approval_status"
        )
        
        db.collection("parents").document(request.parentId).collection("notifications").add(notifData)
        NotificationSender.sendNotification(request.parentId, title, message)
    }
}
