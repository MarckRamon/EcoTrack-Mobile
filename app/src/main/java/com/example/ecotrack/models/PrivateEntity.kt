package com.example.ecotrack.models

import com.google.gson.annotations.SerializedName
import java.io.Serializable

/**
 * Model class representing a private entity (junkshop)
 */
data class PrivateEntity(
    @SerializedName("entityId")
    val entityId: String = "",
    
    @SerializedName("userId")
    val userId: String = "",
    
    @SerializedName("entityName")
    val entityName: String = "",
    
    @SerializedName("address")
    val address: String = "",
    
    @SerializedName("entityStatus")
    val entityStatus: String = "OPEN",
    
    @SerializedName("entityWasteType")
    val entityWasteType: String = "RECYCLABLE",
    
    @SerializedName("latitude")
    val latitude: Double = 0.0,
    
    @SerializedName("longitude")
    val longitude: Double = 0.0,
    
    @SerializedName("phoneNumber")
    val phoneNumber: String? = null
) : Serializable 