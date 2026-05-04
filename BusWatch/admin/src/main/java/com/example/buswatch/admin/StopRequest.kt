package com.example.buswatch.admin

data class StopRequest(
    val id: String = "",
    val parentId: String = "",
    val studentId: String? = null,
    val studentName: String = "",
    val studentFirstName: String = "",
    val studentLastName: String = "",
    val currentStopId: String = "",
    val currentStopName: String = "",
    val currentStopLat: Double = 0.0,
    val currentStopLng: Double = 0.0,
    val proposedStopId: String = "",
    val proposedStopName: String = "",
    val proposedStopLat: Double = 0.0,
    val proposedStopLng: Double = 0.0,
    val docPath: String = "",
    val status: String = "pending",
    val parentAvatarUrl: String = ""
)
