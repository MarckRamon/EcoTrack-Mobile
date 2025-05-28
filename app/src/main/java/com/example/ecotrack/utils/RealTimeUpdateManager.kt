package com.example.ecotrack.utils

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.lifecycleScope
import com.example.ecotrack.BaseActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.Timer
import java.util.TimerTask

/**
 * Utility class to handle real-time updates for various activities.
 * This class can be used by any activity that needs to periodically fetch updated data.
 */
class RealTimeUpdateManager(
    private val activity: BaseActivity,
    private val updateInterval: Long = DEFAULT_UPDATE_INTERVAL,
    private val updateCallback: () -> Unit
) {
    private val TAG = "RealTimeUpdateManager"
    private var updateTimer: Timer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isUpdating = false

    /**
     * Start real-time updates with the specified interval
     */
    fun startRealTimeUpdates() {
        // Cancel any existing timer
        stopRealTimeUpdates()
        
        // Create a new timer for real-time updates
        updateTimer = Timer()
        updateTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                fetchUpdates()
            }
        }, updateInterval, updateInterval)
        
        Log.d(TAG, "Real-time updates started with interval: $updateInterval ms")
    }
    
    /**
     * Stop real-time updates
     */
    fun stopRealTimeUpdates() {
        updateTimer?.cancel()
        updateTimer = null
        Log.d(TAG, "Real-time updates stopped")
    }
    
    /**
     * Fetch updates using the provided callback
     */
    private fun fetchUpdates() {
        // Skip if already updating
        if (isUpdating) return
        
        isUpdating = true
        
        activity.lifecycleScope.launch {
            try {
                // Call the provided update callback
                updateCallback()
            } catch (e: Exception) {
                Log.e(TAG, "Error during update: ${e.message}", e)
            } finally {
                isUpdating = false
            }
        }
    }
    
    companion object {
        /**
         * Default update interval in milliseconds (60 seconds)
         * Change this value to modify the update frequency across all activities
         */
        const val DEFAULT_UPDATE_INTERVAL: Long = 60000L
    }
} 