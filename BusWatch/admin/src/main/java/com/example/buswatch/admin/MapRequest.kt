package com.example.buswatch.admin

data class MapRequest(
    val id: String = "",
    val parentId: String = "",
    val studentId: String? = null,
    val studentName: String = "",
    val currentAddress: String = "",
    val currentLat: Double = 0.0,
    val currentLng: Double = 0.0,
    val pendingAddress: String = "",
    val pendingLat: Double = 0.0,
    val pendingLng: Double = 0.0,
    val docPath: String = "",
    val status: String = "pending",
    val parentAvatarUrl: String = ""
)