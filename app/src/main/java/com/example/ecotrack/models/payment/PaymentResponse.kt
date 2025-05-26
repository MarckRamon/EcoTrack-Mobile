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
    val message: String,
    
    @SerializedName("jobOrderStatus")
    val jobOrderStatus: String = "Processing",
    
    @SerializedName("driverId")
    val driverId: String? = null,
    
    @SerializedName("customerName")
    val customerName: String? = null,
    
    @SerializedName("customerEmail")
    val customerEmail: String? = null,
    
    @SerializedName("amount")
    val amount: Double? = null,
    
    @SerializedName("totalAmount")
    val totalAmount: Double? = null,
    
    @SerializedName("address")
    val address: String? = null,
    
    @SerializedName("barangayId")
    val barangayId: String? = null,
    
    @SerializedName("latitude")
    val latitude: Double? = null,
    
    @SerializedName("longitude")
    val longitude: Double? = null,
    
    @SerializedName("estimatedArrival")
    val estimatedArrival: Date? = null,
    
    @SerializedName("estimated_arrival") // Alternative field name that might be used
    val estimatedArrivalAlt: Date? = null
) {
    override fun toString(): String {
        return "PaymentResponse(id=$id, orderId=$orderId, status=$status, jobOrderStatus=$jobOrderStatus, driverId=$driverId, estimatedArrival=$estimatedArrival)"
    }
    
    // Get estimated arrival from either field
    fun getEffectiveEstimatedArrival(): Date? {
        return estimatedArrival ?: estimatedArrivalAlt
    }
}
