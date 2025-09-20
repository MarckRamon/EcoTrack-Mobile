package com.example.ecotrack.models.quote

import com.google.gson.annotations.SerializedName

/**
 * Model class for receiving quote response from the backend
 * This matches the quote endpoint response structure
 */
data class QuoteResponse(
    @SerializedName("quoteId")
    val quoteId: String,
    
    @SerializedName("estimatedAmount")
    val estimatedAmount: Double,
    
    @SerializedName("estimatedTotalAmount")
    val estimatedTotalAmount: Double,
    
    @SerializedName("assignedTruckId")
    val assignedTruckId: String,
    
    @SerializedName("assignedDriverId")
    val assignedDriverId: String,
    
    @SerializedName("truckDetails")
    val truckDetails: String,
    
    @SerializedName("driverDetails")
    val driverDetails: String,
    
    @SerializedName("truckCapacity")
    val truckCapacity: Double,
    
    @SerializedName("message")
    val message: String,
    
    @SerializedName("automationSuccess")
    val automationSuccess: Boolean,
    
    @SerializedName("wasteType")
    val wasteType: String,
    
    @SerializedName("trashWeight")
    val trashWeight: Double
)