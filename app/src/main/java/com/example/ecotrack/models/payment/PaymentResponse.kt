package com.example.ecotrack.models.payment

import com.google.gson.annotations.SerializedName
import java.util.Date

/**
 * Model class for receiving payment confirmation from the backend
 * This matches the PaymentResponseDTO.java in the backend
 */
data class PaymentResponse(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("orderId")
    val orderId: String,
    
    @SerializedName("status")
    val status: String,
    
    @SerializedName("paymentMethod")
    val paymentMethod: String,
    
    @SerializedName("paymentReference")
    val paymentReference: String,
    
    @SerializedName("createdAt")
    val createdAt: Date,
    
    @SerializedName("message")
    val message: String
)
