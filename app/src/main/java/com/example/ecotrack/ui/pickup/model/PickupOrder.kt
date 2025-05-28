package com.example.ecotrack.ui.pickup.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.text.SimpleDateFormat
import java.util.*

enum class WasteType(val pricePerSack: Double) {
    PLASTIC(120.0),
    PAPER(80.0),
    METAL(150.0),
    GLASS(150.0),
    ELECTRONIC(200.0),
    ORGANIC(100.0),
    MIXED(200.0),
    HAZARDOUS(500.0),
    RECYCLABLE(100.0);
    
    fun getDisplayName(): String {
        return name.lowercase().replaceFirstChar { it.uppercase() }
    }
}

@Parcelize
data class PickupOrder(
    val id: String = UUID.randomUUID().toString(),
    val fullName: String,
    val email: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val amount: Double,
    val tax: Double,
    val total: Double,
    val paymentMethod: PaymentMethod,
    val wasteType: WasteType,
    val numberOfSacks: Int = 0,
    val truckSize: TruckSize = TruckSize.SMALL,
    val sacksCost: Double = 0.0,
    val truckCost: Double = 0.0,
    val barangayId: String? = null,
    val status: OrderStatus = OrderStatus.PROCESSING,
    val createdAt: Date = Date(),
    val estimatedArrival: Date? = null,
    val referenceNumber: String = generateReferenceNumber()
) : Parcelable {
    
    companion object {
        private fun generateReferenceNumber(): String {
            // Generate a reference number in the format shown in the design
            // Format: 0237-7746-8981-9028-5626
            val random = Random()
            val blocks = mutableListOf<String>()
            
            repeat(5) {
                val num = random.nextInt(10000)
                blocks.add(String.format("%04d", num))
            }
            
            return blocks.joinToString("-")
        }
    }
    
    fun getFormattedDate(): String {
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        return dateFormat.format(createdAt)
    }
    
    fun getFormattedTime(): String {
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        return timeFormat.format(createdAt)
    }
    
    fun getFormattedArrivalTime(): String {
        if (estimatedArrival == null) return "N/A"
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        return timeFormat.format(estimatedArrival)
    }
}

enum class OrderStatus {
    PROCESSING, // Waiting for driver to accept
    ACCEPTED,   // Driver has accepted, on the way
    COMPLETED,  // Pickup completed
    CANCELLED   // Pickup cancelled
}

enum class TruckSize(val price: Double) {
    SMALL(500.0),
    MEDIUM(1000.0),
    LARGE(2000.0);
    
    fun getDisplayName(): String {
        return when (this) {
            SMALL -> "Small Truck"
            MEDIUM -> "Medium Truck"
            LARGE -> "Large Truck"
        }
    }
} 