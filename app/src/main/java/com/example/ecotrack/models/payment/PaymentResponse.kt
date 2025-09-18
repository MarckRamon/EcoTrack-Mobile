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
    val estimatedArrivalAlt: Date? = null,
    
    @SerializedName("numberOfSacks")
    val numberOfSacks: Int = 0,
    
    @SerializedName("wasteType")
    val wasteType: String? = null,
    
    @SerializedName("truckSize")
    val truckSize: String? = null,
    
    @SerializedName("truckId")
    val truckId: String? = null,
    
    @SerializedName("truckMake")
    val truckMake: String? = null,
    
    @SerializedName("truckModel")
    val truckModel: String? = null,
    
    @SerializedName("plateNumber")
    val plateNumber: String? = null
    ,
    @SerializedName("confirmationImageUrl")
    val confirmationImageUrl: String? = null,
    @SerializedName("confirmationImage")
    val confirmationImageAlt: String? = null
) {
    override fun toString(): String {
        return "PaymentResponse(id=$id, orderId=$orderId, status=$status, jobOrderStatus=$jobOrderStatus, driverId=$driverId, estimatedArrival=$estimatedArrival, numberOfSacks=$numberOfSacks)"
    }
    
    // Get estimated arrival from either field
    fun getEffectiveEstimatedArrival(): Date? {
        return estimatedArrival ?: estimatedArrivalAlt
    }

    fun getEffectiveConfirmationImageUrl(): String? {
        return confirmationImageUrl ?: confirmationImageAlt
    }
}
