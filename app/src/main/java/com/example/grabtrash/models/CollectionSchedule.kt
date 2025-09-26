package com.example.grabtrash.models

import com.google.gson.annotations.SerializedName

data class CollectionScheduleResponse(
    @SerializedName("scheduleId")
    val scheduleId: String,
    
    @SerializedName("barangayId")
    val barangayId: String,
    
    @SerializedName("barangayName")
    val barangayName: String,
    
    @SerializedName("wasteType")
    val wasteType: String,
    
    @SerializedName("collectionDateTime")
    val collectionDateTime: String?, // For one-time schedules
    
    @SerializedName("isRecurring")
    val isRecurring: Boolean,
    
    @SerializedName("recurringDay")
    val recurringDay: String?, // For recurring schedules - day of the week
    
    @SerializedName("recurringTime")
    val recurringTime: String?, // For recurring schedules - time in HH:MM format
    
    @SerializedName("notes")
    val notes: String?,
    
    @SerializedName("isActive")
    val isActive: Boolean,
    
    @SerializedName("createdAt")
    val createdAt: String,
    
    @SerializedName("updatedAt")
    val updatedAt: String
) 