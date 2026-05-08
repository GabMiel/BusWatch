package com.example.buswatch.admin

data class RouteAdmin(
    val id: String,
    val routeName: String,
    var busNumber: String,
    var driverName: String,
    var currentCapacity: Int,
    val maxCapacity: Int,
    val status: String = "Active",
    val morningStartTime: String = "",
    val morningEndTime: String = "",
    val afternoonStartTime: String = "",
    val afternoonEndTime: String = "",
    val busId: String = "",
    val driverId: String = "",
    val stopIds: List<String> = emptyList()
)
