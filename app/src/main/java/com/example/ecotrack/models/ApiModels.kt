package com.example.ecotrack.models

data class LoginRequest(
    val email: String,
    val password: String
)

data class LoginResponse(
    val token: String,
    val userId: String? = null,
    val role: String? = null
)

data class RegistrationRequest(
    val username: String?,
    val firstName: String,
    val lastName: String,
    val email: String,
    val password: String,
    val role: String = "customer"
)

data class RegisterResponse(
    val message: String,
    val userId: String
)

data class Timestamp(
    val seconds: Long,
    val nanos: Int
)

data class UserProfile(
    val userId: String?,
    val username: String?,
    val firstName: String,
    val lastName: String,
    val email: String,
    val role: String? = null,
    val location: String? = null,
    val createdAt: Timestamp? = null,
    val preferences: Any? = null
)

data class ProfileUpdateRequest(
    val username: String?,
    val firstName: String,
    val lastName: String,
    val email: String
)

data class PasswordUpdateRequest(
    val oldPassword: String,
    val newPassword: String
)

data class ApiError(
    val error: String
) 