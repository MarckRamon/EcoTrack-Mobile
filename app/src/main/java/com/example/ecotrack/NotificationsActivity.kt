package com.example.ecotrack

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import com.example.ecotrack.databinding.ActivityNotificationsBinding

class NotificationsActivity : BaseActivity() {
    private lateinit var binding: ActivityNotificationsBinding
    private val TAG = "NotificationsActivity"
    
    // Track the state of notifications
    private var pushNotificationsEnabled = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotificationsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set up back button
        findViewById<ImageButton>(R.id.backButton).setOnClickListener {
            onBackPressed()
        }

        // Initialize toggle UI
        updatePushToggleUI()

        // Set up click listeners for toggle containers
        setupToggleListeners()
        
        // Set up save button
        binding.saveButton.setOnClickListener {
            saveNotificationPreferences()
        }
    }

    private fun setupToggleListeners() {
        // Push notifications toggle
        binding.pushSwitchContainer.setOnClickListener {
            // Toggle the state
            pushNotificationsEnabled = !pushNotificationsEnabled
            updatePushToggleUI()
            Log.d(TAG, "Push notifications toggled: $pushNotificationsEnabled")
        }
    }
    
    private fun updatePushToggleUI() {
        if (pushNotificationsEnabled) {
            // ON state
            binding.pushOnLabel.setBackgroundResource(R.color.green)
            binding.pushOnLabel.setTextColor(getColor(android.R.color.white))
            binding.pushOffLabel.setBackgroundResource(android.R.color.white)
            binding.pushOffLabel.setTextColor(getColor(android.R.color.black))
        } else {
            // OFF state
            binding.pushOnLabel.setBackgroundResource(android.R.color.white)
            binding.pushOnLabel.setTextColor(getColor(android.R.color.black))
            binding.pushOffLabel.setBackgroundResource(R.color.green)
            binding.pushOffLabel.setTextColor(getColor(android.R.color.white))
        }
    }

    private fun saveNotificationPreferences() {
        // Log the values
        Log.d(TAG, "Saving notification preferences - Push: $pushNotificationsEnabled")
        
        // In a real app, we would save these to shared preferences and/or backend
        // For now, just show a toast message
        Toast.makeText(this, "Notification preferences saved", Toast.LENGTH_SHORT).show()
        
        // Go back to previous screen
        finish()
    }
} 