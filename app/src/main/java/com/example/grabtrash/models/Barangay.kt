package com.example.grabtrash.models

import com.google.gson.annotations.SerializedName

data class Barangay(
    @SerializedName("barangayId")
    val barangayId: String,

    @SerializedName("name")
    val name: String,

    @SerializedName("description")
    val description: String? = null,

    @SerializedName("createdAt")
    val createdAt: CreatedAt? = null,

    @SerializedName("active")
    val isActive: Boolean = true
)

data class CreatedAt(
    @SerializedName("seconds")
    val seconds: Long,

    @SerializedName("nanos")
    val nanos: Int
)
