package com.example.ecotrack.models.quote

import com.google.gson.annotations.SerializedName

/**
 * Model class for requesting a quote from the backend
 * This matches the quote endpoint request structure
 */
data class QuoteRequest(
    @SerializedName("customerEmail")
    val customerEmail: String,
    
    @SerializedName("address")
    val address: String,
    
    @SerializedName("latitude")
    val latitude: Double,
    
    @SerializedName("longitude")
    val longitude: Double,
    
    @SerializedName("wasteType")
    val wasteType: String,
    
    @SerializedName("trashWeight")
    val trashWeight: Double
)