package com.example.ecotrack.models

import com.google.gson.annotations.SerializedName

/**
 * Model class for updating job order status
 */
data class JobOrderStatusUpdate(
    @SerializedName("jobOrderStatus")
    val jobOrderStatus: String
) 