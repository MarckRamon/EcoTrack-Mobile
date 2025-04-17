package com.example.ecotrack.models

import com.google.gson.annotations.SerializedName

data class PickupLocationsResponse(
    val success: Boolean,
    val message: String,
    @SerializedName("locations")
    val pickupSites: List<PickupSite>
) 