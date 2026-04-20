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
        val onBack = { (requireActivity() as? AdminHome)?.replaceFragment(BusesFragment()) }
        
        view.findViewById<ImageButton>(R.id.btnBackBusDetail)?.setOnClickListener { onBack() }
        view.findViewById<View>(R.id.btnBackBusAction)?.setOnClickListener { onBack() }
        
        view.findViewById<View>(R.id.btnEditBusAction)?.setOnClickListener {
            (requireActivity() as? AdminHome)?.editBusDetail(bus)
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

        db.collection("buses").document(bus.id).get().addOnSuccessListener { doc ->
            if (doc == null || !doc.exists()) return@addOnSuccessListener
            
            val busNum = doc.getString("busNumber") ?: "N/A"
            val status = doc.getString("status") ?: "Active"
            val capacity = doc.getString("capacity") ?: doc.getLong("capacity")?.toString() ?: "N/A"
            val plate = doc.getString("plateNumber") ?: "N/A"
            val type = doc.getString("vehicleType") ?: "N/A"
            
            tvBusNum.text = busNum
            tvStatus.text = status
            tvCap.text = capacity
            tvPlate.text = plate
            tvType.text = type
            
            val driverId = doc.getString("driverId")
            if (driverId != null && driverId.isNotEmpty()) {
                db.collection("drivers").document(driverId).get().addOnSuccessListener { dDoc ->
                    if (dDoc.exists()) {
                        val first = dDoc.getString("firstName") ?: ""
                        val last = dDoc.getString("lastName") ?: ""
                        tvDName.text = "$first $last".trim()
                        tvDPhone.text = dDoc.getString("phone") ?: "No Phone"
                        
                        val photoUrl = dDoc.getString("profilePhoto")
                        if (!photoUrl.isNullOrEmpty() && imgDriver != null) {
                            Glide.with(this).load(photoUrl).circleCrop().into(imgDriver)
                        }
                    } else {
                        tvDName.text = "No Driver Assigned"
                        tvDPhone.text = "---"
                    }
                }
            } else {
                tvDName.text = "No Driver Assigned"
                tvDPhone.text = "---"
            }
        }
    }
}
