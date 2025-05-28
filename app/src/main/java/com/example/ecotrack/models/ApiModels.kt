package com.example.ecotrack.models

import com.google.gson.annotations.SerializedName
import java.time.OffsetDateTime // Import for handling timezone offset

data class LoginRequest(
    val email: String,
    val password: String
)

data class LoginResponse(
    val token: String,
    val userId: String,
    val role: String,
    val message: String?
)

data class RegistrationRequest(
    val username: String?,
    val firstName: String,
    val lastName: String,
    val email: String,
    val password: String,
    val phoneNumber: String,
    val role: String = "customer",
    val securityQuestions: List<SecurityQuestionAnswer>? = null,
    val barangayId: String? = null,
    val barangayName: String? = null
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
    val userId: String,
    val email: String,
    val firstName: String?,
    val lastName: String?,
    val phoneNumber: String?,
    val username: String?,
    val location: Map<String, Any>?,
    val createdAt: TimestampResponse?,
    val preferences: Map<String, Any>?,
    val barangayId: String? = null,
    val barangayName: String? = null,
    val role: String? = null
)

data class ProfileUpdateRequest(
    val firstName: String?,
    val lastName: String?,
    val phoneNumber: String?,
    val username: String?,
    val location: Map<String, Any>?,
    val email: String?,
    val barangayId: String? = null,
    val barangayName: String? = null
)

data class PasswordUpdateRequest(
    val currentPassword: String,
    val newPassword: String
)

data class ApiError(
    val error: String
)

data class CollectionSchedule(
    @SerializedName("scheduleId") // Matches DB field
    val id: String, // Renamed from scheduleId for consistency if needed, but using API response name is safer

    @SerializedName("barangayId")
    val barangayId: String?,

    @SerializedName("barangayName")
    val barangayName: String?,

    // Use the combined field from the database
    @SerializedName("collectionDateTime") // Matches DB field name
    val collectionDateTimeString: String?, // Store the raw string first

    @SerializedName("wasteType")
    val wasteType: String?,

    @SerializedName("isRecurring")
    val isRecurring: Boolean?,

    @SerializedName("recurringDay") // Matches DB field
    val recurringDay: String?, // e.g., "MONDAY"

    @SerializedName("recurringTime") // Matches DB field
    val recurringTime: String?, // e.g., "10:00"

    @SerializedName("notes") // Added from DB schema
    val notes: String?,

    @SerializedName("isActive") // Added from DB schema
    val isActive: Boolean?

    // Add other fields like createdAt, updatedAt if needed by the app
)

data class TimestampResponse(
    val seconds: Long,
    val nanos: Int
)

data class PickupRequest(
    val userId: String,
    val locationId: String,
    val wasteType: String,
    val scheduledTime: String,
    val notes: String?
)

data class FcmTokenRequest(
    @SerializedName("fcmToken")
    val fcmToken: String
)