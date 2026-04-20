package com.example.buswatch.admin

import android.content.Context
import android.content.res.ColorStateList
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.example.buswatch.common.R as CommonR
import com.google.firebase.firestore.FirebaseFirestore

class AddBusDialog(
    private val context: Context,
    private val db: FirebaseFirestore,
    private val onBusAdded: () -> Unit
) {
    private var minCap = 10
    private var maxCap = 100
    private var isCapacityValid = true

    fun show() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_bus, null)
        val dialog = AlertDialog.Builder(context).setView(dialogView).create()

        val etBusNumber = dialogView.findViewById<EditText>(R.id.etBusNumber)
        val tvSelectedVehicleType = dialogView.findViewById<TextView>(R.id.tvSelectedVehicleType)
        val etCapacity = dialogView.findViewById<EditText>(R.id.etCapacity)
        val etPlateNumber = dialogView.findViewById<EditText>(R.id.etPlateNumber)
        val tvCapacityLabel = dialogView.findViewById<TextView>(R.id.tvCapacityLabel)
        val tvCapacityWarning = dialogView.findViewById<TextView>(R.id.tvCapacityWarning)
        val btnSaveBus = dialogView.findViewById<TextView>(R.id.btnSaveBus)

        val vehicleTypes = arrayOf(
            "School Bus (Standard)",
            "Mini Bus",
            "Van",
            "Multi-cab / Utility Vehicle",
            "Others"
        )

        dialogView.findViewById<FrameLayout>(R.id.btnVehicleTypeDropdown).setOnClickListener {
            AlertDialog.Builder(context)
                .setTitle("Select Vehicle Type")
                .setItems(vehicleTypes) { _, which ->
                    val selected = vehicleTypes[which]
                    tvSelectedVehicleType.text = selected
                    tvSelectedVehicleType.setTextColor(ContextCompat.getColor(context, android.R.color.black))
                    val defaultVal = when (which) {
                        0 -> { minCap = 35; maxCap = 60; "45" }
                        1 -> { minCap = 18; maxCap = 35; "25" }
                        2 -> { minCap = 10; maxCap = 18; "14" }
                        3 -> { minCap = 10; maxCap = 20; "14" }
                        else -> { minCap = 10; maxCap = 100; "" }
                    }
                    etCapacity.setText(defaultVal)
                    etCapacity.hint = if (which == 4) "10-100" else "$minCap-$maxCap"
                    validateCapacity(etCapacity.text.toString(), minCap, maxCap, tvCapacityLabel, tvCapacityWarning) { valid ->
                        isCapacityValid = valid
                    }
                }.show()
        }

        etCapacity.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                validateCapacity(s.toString(), minCap, maxCap, tvCapacityLabel, tvCapacityWarning) { valid ->
                    isCapacityValid = valid
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        dialogView.findViewById<ImageButton>(R.id.btnCloseAddBus).setOnClickListener { dialog.dismiss() }

        btnSaveBus.setOnClickListener {
            val busNumber = etBusNumber.text.toString().trim()
            val capacityStr = etCapacity.text.toString().trim()
            val vehicleType = tvSelectedVehicleType.text.toString()
            val plateNumber = etPlateNumber.text.toString().trim()

            if (busNumber.isEmpty() || capacityStr.isEmpty() || vehicleType == "Select Type") {
                Toast.makeText(context, "Please fill all required fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!isCapacityValid) {
                Toast.makeText(context, "Invalid capacity", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val busData = hashMapOf(
                "busNumber" to busNumber,
                "vehicleType" to vehicleType,
                "capacity" to capacityStr,
                "availableSeats" to capacityStr,
                "plateNumber" to plateNumber,
                "status" to "Active",
                "createdAt" to com.google.firebase.Timestamp.now()
            )

            db.collection("buses").add(busData)
                .addOnSuccessListener {
                    Toast.makeText(context, "Bus added successfully", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    onBusAdded()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
        dialog.show()
    }

    private fun validateCapacity(
        inputStr: String,
        min: Int,
        max: Int,
        label: TextView,
        warning: TextView,
        onResult: (Boolean) -> Unit
    ) {
        val input = inputStr.toIntOrNull()
        if (input == null) {
            label.isVisible = false
            warning.isVisible = false
            onResult(false)
            return
        }

        when {
            input < min -> {
                label.isVisible = false
                warning.text = "Minimum capacity is $min"
                warning.isVisible = true
                onResult(false)
            }
            input > max -> {
                label.isVisible = false
                warning.text = "Maximum capacity is $max"
                warning.isVisible = true
                onResult(false)
            }
            else -> {
                warning.isVisible = false
                label.isVisible = true
                onResult(true)
                val range = (max - min).coerceAtLeast(1)
                val percentage = (input - min).toFloat() / range
                when {
                    percentage < 0.6 -> {
                        label.text = "Normal Capacity"
                        label.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, android.R.color.holo_green_dark))
                    }
                    percentage < 0.85 -> {
                        label.text = "High Capacity"
                        label.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, android.R.color.holo_orange_dark))
                    }
                    else -> {
                        label.text = "Risk Capacity"
                        label.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, android.R.color.holo_red_dark))
                    }
                }
            }
        }
    }
}
