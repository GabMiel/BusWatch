package com.example.buswatch.driver

data class Student(
    val id: String,
    val name: String,
    val grade: String,
    val status: String = "At Home",
    val photoUrl: String = "",
    val rideOption: String = "Round Trip"
)
