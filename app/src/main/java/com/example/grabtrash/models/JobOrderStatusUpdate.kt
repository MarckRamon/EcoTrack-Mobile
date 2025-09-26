package com.example.grabtrash.models

import com.google.gson.annotations.SerializedName

/**
 * Model class for updating job order status
 */
data class JobOrderStatusUpdate(
    @SerializedName("jobOrderStatus")
    val status: String
) 