package com.example.ecotrack

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import android.widget.ImageButton
import com.example.ecotrack.utils.ApiService
import com.example.ecotrack.utils.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.ImageView

class DriverHomeActivity : BaseActivity() {
    private lateinit var welcomeTextView: TextView
    private lateinit var driverTextView: TextView
    private lateinit var profileImage: ImageView
    private lateinit var notificationButton: ImageButton
    private val apiService = ApiService.create()
    private val TAG = "DriverHomeActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driver_home)
        
        // Initialize UI components
        initViews()
        setupClickListeners()
        
        // Check if user has driver role before loading profile
        validateDriverAccount()
    }
    
    private fun initViews() {
        // Find views from the updated layout
        welcomeTextView = findViewById(R.id.welcomeText)
        driverTextView = findViewById(R.id.tvDriverMode)
        profileImage = findViewById(R.id.profileImage)
        notificationButton = findViewById(R.id.notificationButton)
    }
    
    private fun setupClickListeners() {
        // Setup profile image button
        profileImage.setOnClickListener {
            startActivity(Intent(this, DriverProfileActivity::class.java))
        }
        
        // Setup notification button
        notificationButton.setOnClickListener {
            Toast.makeText(this, "Notifications feature coming soon", Toast.LENGTH_SHORT).show()
        }
        
        // Setup bottom navigation items
        setupBottomNavigation()
    }
    
    private fun setupBottomNavigation() {
        // Set up click listeners for each navigation item
        findViewById<View>(R.id.homeNav).setOnClickListener { 
            // Already on home screen
        }
        
        findViewById<View>(R.id.scheduleNav).setOnClickListener {
            Toast.makeText(this, "Collection Schedule coming soon", Toast.LENGTH_SHORT).show()
        }
        
        findViewById<View>(R.id.pointsNav).setOnClickListener {
            Toast.makeText(this, "Collection Points coming soon", Toast.LENGTH_SHORT).show()
        }
        
        findViewById<View>(R.id.pickupNav).setOnClickListener {
            Toast.makeText(this, "Order Pickup coming soon", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Validates that the current account is a driver account.
     * If not, redirects to the appropriate screen.
     */
    private fun validateDriverAccount() {
        // First check the stored user type
        val userType = sessionManager.getUserType()
        if (userType != "driver") {
            Log.w(TAG, "Non-driver account detected in DriverHomeActivity: $userType")
            
            val message = if (userType == "admin") {
                "Admin accounts should use the admin interface."
            } else {
                "This section is for drivers only. Redirecting to customer area."
            }
            
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            
            // Redirect based on account type
            when (userType) {
                "customer" -> redirectToCustomerHome()
                "admin" -> {
                    // Logout for now, later can redirect to admin screen
                    sessionManager.logout()
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                }
                else -> {
                    // Unknown role, logout and redirect to login
                    sessionManager.logout()
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                }
            }
            return
        }
        
        // Double-check with the backend
        loadUserProfile()
    }
    
    private fun loadUserProfile() {
        val userId = sessionManager.getUserId()
        val token = sessionManager.getToken()
        
        if (userId == null || token == null) {
            Log.e(TAG, "Missing user credentials, redirecting to login")
            // BaseActivity will handle redirection
            return
        }
        
        // Extract role directly from token for a more reliable check
        val roleFromToken = sessionManager.extractRoleFromToken(token)
        if (roleFromToken != null && roleFromToken != "driver") {
            Log.w(TAG, "Non-driver role detected in token: $roleFromToken")
            
            val message = when (roleFromToken) {
                "customer" -> "Customer accounts should use the customer interface."
                "admin" -> "Admin accounts should use the admin interface."
                else -> "Invalid account type. Please login again."
            }
            
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            
            when (roleFromToken) {
                "customer" -> {
                    // Save the role and redirect
                    sessionManager.saveUserType("customer")
                    redirectToCustomerHome()
                }
                "admin" -> {
                    // For now just logout, later redirect to admin interface
                    sessionManager.logout()
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                }
                else -> {
                    sessionManager.logout()
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                }
            }
            return
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = apiService.getProfile(userId, "Bearer $token")
                
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        val profile = response.body()
                        profile?.let {
                            // Update UI with profile data
                            welcomeTextView.text = "Welcome, ${it.firstName}!"
                        }
                    } else {
                        Log.e(TAG, "Failed to load profile: ${response.code()}")
                        Toast.makeText(
                            this@DriverHomeActivity,
                            "Failed to load profile",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading profile", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@DriverHomeActivity,
                        "Error loading profile: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
    
    private fun redirectToCustomerHome() {
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }
} 