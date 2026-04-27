package com.example.buswatch.admin.fragments

import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.example.buswatch.admin.AdminHome
import com.example.buswatch.admin.BusAdmin
import com.example.buswatch.admin.R
import com.google.firebase.firestore.FirebaseFirestore

class BusEditFragment : Fragment() {
    private val db = FirebaseFirestore.getInstance()
    private lateinit var bus: BusAdmin
    private var minCap = 10
    private var maxCap = 100
    private var isCapacityValid = true

    companion object {
        fun newInstance(bus: BusAdmin) = BusEditFragment().apply {
            this.bus = bus
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_edit_bus, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI(view)
    }

    private fun setupUI(view: View) {
        val etBusNumber = view.findViewById<EditText>(R.id.etBusNumber)
        val tvSelectedVehicleType = view.findViewById<TextView>(R.id.tvSelectedVehicleType)
        val etCapacity = view.findViewById<EditText>(R.id.etCapacity)
        val etPlateNumber = view.findViewById<EditText>(R.id.etPlateNumber)
        val tvCapacityStatus = view.findViewById<TextView>(R.id.tvCapacityStatus)
        val tvCapacityWarning = view.findViewById<TextView>(R.id.tvCapacityWarning)
        val btnSaveBusChanges = view.findViewById<View>(R.id.btnSaveBusChanges)

        view.findViewById<ImageButton>(R.id.btnBackEditBus)?.setOnClickListener { 
            (requireActivity() as? AdminHome)?.replaceFragment(BusesFragment())
        }

        view.findViewById<View>(R.id.btnCancelEditBus)?.setOnClickListener {
            (requireActivity() as? AdminHome)?.replaceFragment(BusesFragment())
        }

        val vehicleTypes = arrayOf(
            "School Bus (Standard)",
            "Mini Bus",
            "Van",
            "Multi-cab / Utility Vehicle",
            "Others"
        )

        view.findViewById<FrameLayout>(R.id.btnVehicleTypeDropdown).setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Select Vehicle Type")
                .setItems(vehicleTypes) { _, which ->
                    val selected = vehicleTypes[which]
                    tvSelectedVehicleType.text = selected
                    tvSelectedVehicleType.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.black))
                    when (which) {
                        0 -> { minCap = 35; maxCap = 60 }
                        1 -> { minCap = 18; maxCap = 35 }
                        2 -> { minCap = 10; maxCap = 18 }
                        3 -> { minCap = 10; maxCap = 20 }
                        else -> { minCap = 10; maxCap = 100 }
                    }
                    etCapacity.hint = if (which == 4) "10-100" else "$minCap-$maxCap"
                    validateCapacity(etCapacity.text.toString(), tvCapacityStatus, tvCapacityWarning)
                }.show()
        }

        etCapacity.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                validateCapacity(s.toString(), tvCapacityStatus, tvCapacityWarning)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        db.collection("buses").document(bus.id).get().addOnSuccessListener { doc ->
            if (doc == null || !doc.exists()) return@addOnSuccessListener
            etBusNumber.setText(doc.getString("busNumber") ?: "")
            etCapacity.setText(doc.getString("capacity")?.toString() ?: doc.get("capacity")?.toString() ?: "")
            etPlateNumber.setText(doc.getString("plateNumber") ?: "")
            
            val vehicleType = doc.getString("vehicleType") ?: "Select Type"
            tvSelectedVehicleType.text = vehicleType
            if (vehicleType != "Select Type") {
                tvSelectedVehicleType.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.black))
                val typeIndex = vehicleTypes.indexOf(vehicleType)
                if (typeIndex != -1) {
                    when (typeIndex) {
                        0 -> { minCap = 35; maxCap = 60 }
                        1 -> { minCap = 18; maxCap = 35 }
                        2 -> { minCap = 10; maxCap = 18 }
                        3 -> { minCap = 10; maxCap = 20 }
                        else -> { minCap = 10; maxCap = 100 }
                    }
                    validateCapacity(etCapacity.text.toString(), tvCapacityStatus, tvCapacityWarning)
                }
            }
        }

        btnSaveBusChanges.setOnClickListener {
            val busNumber = etBusNumber.text.toString().trim().uppercase()
            val capacityStr = etCapacity.text.toString().trim()
            val vehicleType = tvSelectedVehicleType.text.toString()
            val plateNumber = etPlateNumber.text.toString().trim().uppercase()

            if (busNumber.isEmpty() || capacityStr.isEmpty() || vehicleType == "Select Type") {
                Toast.makeText(requireContext(), "Please fill in all required fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!isCapacityValid) {
                Toast.makeText(requireContext(), "Invalid capacity", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val updates = mapOf(
                "busNumber" to busNumber,
                "vehicleType" to vehicleType,
                "capacity" to capacityStr,
                "availableSeats" to capacityStr,
                "plateNumber" to plateNumber
            )

            db.collection("buses").document(bus.id).update(updates).addOnSuccessListener {
                Toast.makeText(requireContext(), "Update successfully", Toast.LENGTH_SHORT).show()
                (requireActivity() as? AdminHome)?.replaceFragment(BusesFragment())
            }.addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to update: ${it.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun validateCapacity(inputStr: String, label: TextView, warning: TextView) {
        val input = inputStr.toIntOrNull()
        if (input == null) {
            label.isVisible = false
            warning.isVisible = false
            isCapacityValid = false
            return
        }

        when {
            input < minCap -> {
                label.isVisible = false
                warning.text = "Minimum capacity is $minCap"
                warning.isVisible = true
                isCapacityValid = false
            }
            input > maxCap -> {
                label.isVisible = false
                warning.text = "Maximum capacity is $maxCap"
                warning.isVisible = true
                isCapacityValid = false
            }
            else -> {
                warning.isVisible = false
                label.isVisible = true
                isCapacityValid = true
                val range = (maxCap - minCap).coerceAtLeast(1)
                val percentage = (input - minCap).toFloat() / range
                when {
                    percentage < 0.6 -> {
                        label.text = "Normal Capacity"
                        label.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark))
                    }
                    percentage < 0.85 -> {
                        label.text = "High Capacity"
                        label.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), android.R.color.holo_orange_dark))
                    }
                    else -> {
                        label.text = "Risk Capacity"
                        label.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark))
                    }
                }
            }
        }
    }
}
