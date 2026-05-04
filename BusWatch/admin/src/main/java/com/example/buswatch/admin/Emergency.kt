package com.example.buswatch.admin

import com.google.firebase.Timestamp

data class Emergency(
    val id: String = "",
    val driverId: String = "",
    val driverName: String = "",
    val routeId: String? = null,
    val routeName: String? = null,
    val busNumber: String? = null,
    val timestamp: Timestamp? = null,
    val status: String = "active",
    val type: String = "SOS",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val senderRole: String? = null
)