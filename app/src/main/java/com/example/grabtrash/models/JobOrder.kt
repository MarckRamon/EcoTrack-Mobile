package com.example.grabtrash.models

import java.io.Serializable

/**
 * Represents a job order assigned to a driver for pickup
 */
data class JobOrder(
    val id: String,
    val customerName: String,
    val address: String,
    val city: String,
    val state: String,
    val zipCode: String,
    val price: String,
    val phoneNumber: String = "",
    val paymentMethod: String = "",
    val wasteType: String = "",
    val status: OrderStatus = OrderStatus.PENDING
) : Serializable

enum class OrderStatus {
    PENDING,    // Waiting for driver to accept
    ACCEPTED,   // Driver has accepted, on the way
    COMPLETED,  // Pickup completed
    CANCELLED   // Pickup cancelled
} 