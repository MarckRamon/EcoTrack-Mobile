package com.example.ecotrack.models

import com.google.gson.annotations.SerializedName

/**
 * Data class representing a garbage pickup site.
 */
data class PickupSite(
    val id: String,
    @SerializedName("siteName")
    val name: String,
    @SerializedName("wasteType")
    val garbageType: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val description: String = "",
    val schedule: String = ""
) 