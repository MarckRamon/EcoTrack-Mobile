package com.example.ecotrack.models

import com.google.gson.annotations.SerializedName
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Data class representing a missed collection report
 */
data class MissedReportResponse(
    @SerializedName("reportId")
    val reportId: String,
    
    @SerializedName("userId")
    val userId: String,
    
    @SerializedName("barangayId")
    val barangayId: String,
    
    @SerializedName("barangayName")
    val barangayName: String,
    
    @SerializedName("reportDate")
    val reportDateString: String,
    
    @SerializedName("wasteType")
    val wasteType: String,
    
    @SerializedName("description")
    val description: String?,
    
    @SerializedName("status")
    val status: String,
    
    @SerializedName("createdAt")
    val createdAt: String,
    
    @SerializedName("updatedAt")
    val updatedAt: String?
) {
    // Helper function to parse the report date
    fun getReportDate(): LocalDateTime? {
        return try {
            val formatter = DateTimeFormatter.ofPattern("MMMM d, yyyy 'at' h:mm:ss a 'UTC'X")
            LocalDateTime.parse(reportDateString, formatter)
        } catch (e: Exception) {
            null
        }
    }
    
    // Helper function to get formatted date for display
    fun getFormattedDate(): String {
        return getReportDate()?.format(DateTimeFormatter.ofPattern("MMM dd, yyyy")) ?: reportDateString
    }
    
    // Helper function to get formatted time for display
    fun getFormattedTime(): String {
        return getReportDate()?.format(DateTimeFormatter.ofPattern("hh:mm a")) ?: ""
    }
}
