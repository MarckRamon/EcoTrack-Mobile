package com.example.grabtrash.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Truck(
    val truckId: String,
    val size: String,
    val wasteType: String,
    val status: String,
    val make: String,
    val model: String,
    val plateNumber: String,
    val truckPrice: Double,
    val createdAt: String,
    val updatedAt: String? = null,
    val message: String? = null
) : Parcelable 