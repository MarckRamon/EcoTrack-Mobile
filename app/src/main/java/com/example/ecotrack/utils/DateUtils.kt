package com.example.ecotrack.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object DateUtils {
    fun getCurrentFormattedDateTime(): String {
        val dateFormat = SimpleDateFormat("MMMM d, yyyy 'at' h:mm:ss a 'UTC'Z", Locale.ENGLISH)
        dateFormat.timeZone = TimeZone.getDefault()
        return dateFormat.format(Date())
    }
} 