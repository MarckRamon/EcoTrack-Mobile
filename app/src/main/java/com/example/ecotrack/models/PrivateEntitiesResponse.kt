package com.example.ecotrack.models

import com.google.gson.annotations.SerializedName

/**
 * Response class for private entities API
 */
data class PrivateEntitiesResponse(
    @SerializedName("entities")
    val entities: List<PrivateEntity> = emptyList(),
    
    @SerializedName("count")
    val count: Int = 0
) 