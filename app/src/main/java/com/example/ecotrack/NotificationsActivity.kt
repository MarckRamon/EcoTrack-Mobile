package com.example.ecotrack

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.ecotrack.databinding.ActivityNotificationsBinding
import com.example.ecotrack.utils.SessionManager

class NotificationsActivity : BaseActivity() {
    private lateinit var binding: ActivityNotificationsBinding
    private val TAG = "NotificationsActivity"
    
    // Track the state of notifications
    private var pushNotificationsEnabled = true

    // Permission launcher for notification permission
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d(TAG, "Notification permission granted")
            pushNotificationsEnabled = true
            updatePushToggleUI()
        } else {
            Log.d(TAG, "Notification permission denied")
            pushNotificationsEnabled = false
            updatePushToggleUI()
            Toast.makeText(
                this,
                "Notifications are disabled. You may miss important updates.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotificationsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set up back button
        findViewById<ImageButton>(R.id.backButton).setOnClickListener {
            finish()
        }

        // Check current notification permission status
        checkNotificationPermission()

        // Set up click listeners for toggle containers
        setupToggleListeners()
        
        // Set up save button
        binding.saveButton.setOnClickListener {
            saveNotificationPreferences()
        }
    }

    private fun checkNotificationPermission() {
        // First check system permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pushNotificationsEnabled = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // For older Android versions, use the saved preference
            pushNotificationsEnabled = sessionManager.getNotificationPreference()
        }
        updatePushToggleUI()
    }

    private fun setupToggleListeners() {
        // Push notifications toggle
        binding.pushSwitchContainer.setOnClickListener {
            if (pushNotificationsEnabled) {
                // If notifications are enabled, we need to direct the user to system settings
                // as apps cannot programmatically disable notifications
                openNotificationSettings()
            } else {
                // If notifications are disabled, request permission
                requestNotificationPermission()
            }
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

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // Permission already granted
                    Log.d(TAG, "Notification permission already granted")
                    pushNotificationsEnabled = true
                    updatePushToggleUI()
                }
                
                ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) -> {
                    // Show rationale and request permission
                    Toast.makeText(
                        this,
                        "Notifications help you stay updated on your pickup requests",
                        Toast.LENGTH_LONG
                    ).show()
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                
                else -> {
                    // Request permission directly
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            // For older Android versions, open notification settings
            openNotificationSettings()
        }
    }

    private fun openNotificationSettings() {
        Toast.makeText(
            this,
            "Please enable or disable notifications in settings",
            Toast.LENGTH_LONG
        ).show()

        // Open app notification settings
        val intent = Intent().apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            } else {
                action = "android.settings.APP_NOTIFICATION_SETTINGS"
                putExtra("app_package", packageName)
                putExtra("app_uid", applicationInfo.uid)
            }
        }

        // If the intent can be resolved, start it
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            // Fallback to application details settings
            val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(fallbackIntent)
        }
    }

    private fun saveNotificationPreferences() {
        // Log the values
        Log.d(TAG, "Saving notification preferences - Push: $pushNotificationsEnabled")
        
        // Save to SessionManager
        sessionManager.saveNotificationPreference(pushNotificationsEnabled)
        
        Toast.makeText(this, "Notification preferences saved", Toast.LENGTH_SHORT).show()
        
        // Go back to previous screen
        finish()
    }

    override fun onResume() {
        super.onResume()
        // Check permission status again when returning to the app
        // (in case user changed it in system settings)
        checkNotificationPermission()
    }
} 