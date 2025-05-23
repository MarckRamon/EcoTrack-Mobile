package com.example.ecotrack.models.payment

import com.google.gson.annotations.SerializedName
import java.io.Serializable
import java.util.Date

/**
 * Model class representing a payment order with all fields
 */
data class Payment(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("orderId")
    val orderId: String,
    
    @SerializedName("customerName")
    val customerName: String,
    
    @SerializedName("customerEmail")
    val customerEmail: String,
    
    @SerializedName("address")
    val address: String,
    
    @SerializedName("phoneNumber")
    val phoneNumber: String,
    
    @SerializedName("paymentMethod")
    val paymentMethod: String,
    
    @SerializedName("amount")
    val amount: Double,
    
    @SerializedName("tax")
    val tax: Double,
    
    @SerializedName("totalAmount")
    val totalAmount: Double,
    
    @SerializedName("notes")
    val notes: String,
    
    @SerializedName("status")
    val status: String,
    
    @SerializedName("paymentReference")
    val paymentReference: String,
    
    @SerializedName("barangayId")
    val barangayId: String,
    
    @SerializedName("latitude")
    val latitude: Double,
    
    @SerializedName("longitude")
    val longitude: Double,
    
    @SerializedName("driverId")
    val driverId: String,
    
    @SerializedName("createdAt")
    val createdAt: Date,
    
    @SerializedName("updatedAt")
    val updatedAt: Date,
    
    @SerializedName("jobOrderStatus")
    val jobOrderStatus: String = "Available",
    
    @SerializedName("wasteType")
    val wasteType: String = "Recyclable"
) : Serializable 