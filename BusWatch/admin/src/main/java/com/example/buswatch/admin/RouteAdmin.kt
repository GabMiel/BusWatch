package com.example.buswatch.admin

data class RouteAdmin(
    val id: String,
    val routeName: String,
    val busNumber: String,
    val driverName: String,
    val currentCapacity: Int,
    val maxCapacity: Int,
    val status: String = "Active",
    val morningStartTime: String = "",
    val morningEndTime: String = "",
    val afternoonStartTime: String = "",
    val afternoonEndTime: String = ""
)
