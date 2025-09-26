package com.example.grabtrash.models

import com.google.gson.annotations.SerializedName

/**
 * Model class for updating the delivery status of a payment
 */
data class DeliveryStatusUpdate(
    @SerializedName("isDelivered")
    val isDelivered: Boolean
) 