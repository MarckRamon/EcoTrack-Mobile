package com.example.ecotrack.models.payment

import com.google.gson.annotations.SerializedName

/**
 * Model class for sending payment information to the backend
 * This matches the PaymentRequestDTO.java in the backend
 */
data class PaymentRequest(
    @SerializedName("orderId")
    val orderId: String,
    
    @SerializedName("customerName")
    val customerName: String,
    
    @SerializedName("customerEmail")
    val customerEmail: String,
    
    @SerializedName("address")
    val address: String,
    
    @SerializedName("latitude")
    val latitude: Double,
    
    @SerializedName("longitude")
    val longitude: Double,
    
    @SerializedName("amount")
    val amount: Double,
    
    @SerializedName("tax")
    val tax: Double,
    
    @SerializedName("totalAmount")
    val totalAmount: Double,
    
    @SerializedName("paymentMethod")
    val paymentMethod: String,
    
    @SerializedName("paymentReference")
    val paymentReference: String,
    
    @SerializedName("notes")
    val notes: String?,

    @SerializedName("wasteType")
    val wasteType: String,

    @SerializedName("barangayId")
    val barangayId: String?,
    
    @SerializedName("selectedTruckId")
    val selectedTruckId: String? = null,
    
    @SerializedName("truckId")
    val truckId: String? = null,
    
    @SerializedName("truckMake")
    val truckMake: String? = null,
    
    @SerializedName("truckModel")
    val truckModel: String? = null,
    
    @SerializedName("plateNumber")
    val plateNumber: String? = null
)
