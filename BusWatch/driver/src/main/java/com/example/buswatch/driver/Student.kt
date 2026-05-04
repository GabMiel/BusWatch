package com.example.buswatch.driver

data class Student(
    val id: String,
    val name: String,
    val grade: String,
    var status: String = "At Home",
    val photoUrl: String = "",
    val rideOption: String = "Round Trip",
    val stopId: String = "",
    var distanceMeters: Int? = null,
    val bloodType: String = "N/A",
    val allergies: String = "None",
    val medications: String = "None",
    val medicalConditions: String = "None",
    val emergencyContact: String = "N/A",
    val emergencyPhone: String = "N/A"
)
