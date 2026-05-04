package com.example.buswatch.admin.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.buswatch.admin.AdminHome
import com.example.buswatch.admin.BusAdmin
import com.example.buswatch.admin.R
import com.example.buswatch.common.R as CommonR
import com.google.firebase.firestore.FirebaseFirestore

class BusDetailFragment : Fragment() {
    private val db = FirebaseFirestore.getInstance()
    private lateinit var bus: BusAdmin

    companion object {
        fun newInstance(bus: BusAdmin) = BusDetailFragment().apply {
            this.bus = bus
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_view_bus, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI(view)
        loadData(view)
    }

    private fun setupUI(view: View) {
        view.findViewById<ImageButton>(R.id.btnBackBusDetail)?.setOnClickListener {
            (requireActivity() as? AdminHome)?.replaceFragment(BusesFragment())
        }
    }

    private fun loadData(view: View) {
        val tvBusNum = view.findViewById<TextView>(R.id.tvBusNumber)
        val tvStatus = view.findViewById<TextView>(R.id.tvBusStatus)
        val tvCap = view.findViewById<TextView>(R.id.tvCapacity)
        val tvPlate = view.findViewById<TextView>(R.id.tvPlateNumber)
        val tvType = view.findViewById<TextView>(R.id.tvVehicleType)
        
        val tvDName = view.findViewById<TextView>(R.id.tvDriverName)
        val tvDPhone = view.findViewById<TextView>(R.id.tvDriverPhone)
        val imgDriver = view.findViewById<ImageView>(R.id.imgDriver)

        // 1. Load Bus Details
        db.collection("buses").document(bus.id).get().addOnSuccessListener { doc ->
            if (doc == null || !doc.exists()) return@addOnSuccessListener
            
            tvBusNum.text = doc.getString("busNumber") ?: "N/A"
            tvStatus.text = doc.getString("status") ?: "Active"
            tvCap.text = doc.get("capacity")?.toString() ?: "N/A"
            tvPlate.text = doc.getString("plateNumber") ?: "N/A"
            tvType.text = doc.getString("vehicleType") ?: "N/A"
            
            // 2. Find Assigned Driver via Routes
            // In your system, the driver is linked to the bus through a Route.
            db.collection("routes")
                .whereEqualTo("busId", bus.id)
                .whereEqualTo("status", "Active")
                .limit(1)
                .get()
                .addOnSuccessListener { routeSnapshots ->
                    if (!routeSnapshots.isEmpty) {
                        val routeDoc = routeSnapshots.documents[0]
                        val driverId = routeDoc.getString("driverId")
                        if (!driverId.isNullOrEmpty()) {
                            fetchDriverDetails(driverId, tvDName, tvDPhone, imgDriver)
                        } else {
                            showNoDriver(tvDName, tvDPhone)
                        }
                    } else {
                        // Fallback: check if driverId is directly on the bus document
                        val directDriverId = doc.getString("driverId")
                        if (!directDriverId.isNullOrEmpty()) {
                            fetchDriverDetails(directDriverId, tvDName, tvDPhone, imgDriver)
                        } else {
                            showNoDriver(tvDName, tvDPhone)
                        }
                    }
                }
        }
    }

    private fun fetchDriverDetails(driverId: String, tvName: TextView, tvPhone: TextView, imgAvatar: ImageView?) {
        db.collection("drivers").document(driverId).get().addOnSuccessListener { dDoc ->
            if (dDoc.exists()) {
                val first = dDoc.getString("firstName") ?: ""
                val last = dDoc.getString("lastName") ?: ""
                tvName.text = getString(CommonR.string.name_format, first, last)
                tvPhone.text = dDoc.getString("phone") ?: "No phone listed"
                
                val photoUrl = dDoc.getString("driverAvatar") ?: dDoc.getString("profilePhoto")
                if (!photoUrl.isNullOrEmpty() && imgAvatar != null) {
                    Glide.with(this).load(photoUrl)
                        .placeholder(CommonR.drawable.ic_person_placeholder)
                        .circleCrop()
                        .into(imgAvatar)
                }
            } else {
                showNoDriver(tvName, tvPhone)
            }
        }
    }

    private fun showNoDriver(tvName: TextView, tvPhone: TextView) {
        tvName.text = getString(R.string.no_driver_assigned)
        tvPhone.text = getString(CommonR.string.placeholder_hyphen)
    }
}
