package com.example.ecotrack.models

data class LoginRequest(
    val email: String,
    val password: String
)

data class LoginResponse(
    val token: String,
    val userId: String? = null
)

data class RegisterRequest(
    val email: String,
    val password: String,
    val username: String,
    val firstName: String,
    val lastName: String
)

data class RegisterResponse(
    val message: String,
    val userId: String
)

data class ProfileUpdateRequest(
    val username: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val email: String? = null
)

data class ProfileResponse(
    val userId: String,
    val username: String,
    val firstName: String,
    val lastName: String,
    val email: String
) 