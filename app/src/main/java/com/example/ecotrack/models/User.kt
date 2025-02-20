package com.example.ecotrack.models

data class User(
    val uid: String = "",
    val username: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val role: String = "user",
    val password: String = "",  // This will store the hashed password
    val createdAt: String = ""  // This will store the formatted timestamp
) 