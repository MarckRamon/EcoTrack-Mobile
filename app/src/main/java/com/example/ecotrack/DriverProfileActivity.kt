package com.example.ecotrack

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.example.ecotrack.utils.ApiService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.ImageButton

class DriverProfileActivity : BaseActivity() {
    private val apiService = ApiService.create()
    private val TAG = "DriverProfileActivity"
    private lateinit var userNameText: TextView
    private lateinit var userEmailText: TextView
    private lateinit var userBarangayText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile) // Reusing the same layout as the regular profile activity
        
        // BaseActivity already handles session checks
        supportActionBar?.hide()
        
        // Verify the user is a driver
        validateDriverRole()
        
        initViews()
        setupClickListeners()
        loadUserData()
    }
    
    private fun validateDriverRole() {
        val userType = sessionManager.getUserType()
        if (userType != "driver") {
            Log.w(TAG, "Non-driver account detected: $userType")
            Toast.makeText(this, "This section is for drivers only. Redirecting...", Toast.LENGTH_LONG).show()
            finish()
            return
        }
    }

    private fun initViews() {
        userNameText = findViewById(R.id.userName)
        userEmailText = findViewById(R.id.userEmail)
        userBarangayText = findViewById(R.id.userBarangay)
    }

    private fun setupClickListeners() {
        // Back button
        findViewById<ImageButton>(R.id.backButton).setOnClickListener {
            onBackPressed()
        }

        // Refresh button
        findViewById<ImageButton>(R.id.refreshButton).setOnClickListener {
            Toast.makeText(this, "Refreshing profile data...", Toast.LENGTH_SHORT).show()
            loadUserData()
        }

        // Add debug feature - long press on profile info to show raw data
        findViewById<LinearLayout>(R.id.profileInfo).setOnLongClickListener {
            val userId = sessionManager.getUserId()
            val token = sessionManager.getToken()

            if (userId != null && token != null) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val response = apiService.getProfile(userId, "Bearer $token")
                        withContext(Dispatchers.Main) {
                            if (response.isSuccessful && response.body() != null) {
                                val profile = response.body()!!
                                val rawData = "User ID: $userId\n" +
                                        "First Name: ${profile.firstName}\n" +
                                        "Last Name: ${profile.lastName}\n" +
                                        "Email: ${profile.email}\n" +
                                        "Barangay ID: ${profile.barangayId}\n" +
                                        "Barangay Name: ${profile.barangayName}\n" +
                                        "Phone: ${profile.phoneNumber}\n" +
                                        "Username: ${profile.username}"

                                android.app.AlertDialog.Builder(this@DriverProfileActivity)
                                    .setTitle("Raw Profile Data")
                                    .setMessage(rawData)
                                    .setPositiveButton("OK", null)
                                    .show()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error fetching raw profile data", e)
                    }
                }
            }
            true
        }

        findViewById<LinearLayout>(R.id.editInfoButton).setOnClickListener {
            val intent = Intent(this, DriverEditProfileActivity::class.java)
            startActivity(intent)
        }

        findViewById<LinearLayout>(R.id.changePasswordButton).setOnClickListener {
            val intent = Intent(this, ChangePasswordSecurityActivity::class.java)
            startActivity(intent)
        }

        findViewById<LinearLayout>(R.id.configureNotificationsButton).setOnClickListener {
            val intent = Intent(this, NotificationsActivity::class.java)
            startActivity(intent)
        }

        findViewById<LinearLayout>(R.id.securityQuestionsButton).setOnClickListener {
            val intent = Intent(this, SecurityQuestionsActivity::class.java)
            startActivity(intent)
        }

        findViewById<MaterialButton>(R.id.logoutButton).setOnClickListener {
            sessionManager.logout()
            navigateToLogin()
        }
        
        // Remove the refresh button functionality if it exists
        findViewById<ImageButton>(R.id.refreshButton)?.let { refreshButton ->
            refreshButton.visibility = android.view.View.GONE
        }
    }

    private fun loadUserData() {
        val token = sessionManager.getToken()
        val userId = sessionManager.getUserId()

        if (token == null || userId == null) {
            Log.e(TAG, "loadUserData - Missing credentials - token: $token, userId: $userId")
            // BaseActivity will handle the navigation to login
            return
        }

        Log.d(TAG, "Loading profile data for user ID: $userId")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = apiService.getProfile(userId, "Bearer $token")
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        val profile = response.body()
                        profile?.let {
                            Log.d(TAG, "Profile loaded successfully: ${it.firstName} ${it.lastName}, email: ${it.email}")
                            Log.d(TAG, "Barangay info: ID=${it.barangayId}, Name=${it.barangayName}")
                            userNameText.text = "${it.firstName} ${it.lastName}"
                            userEmailText.text = it.email

                            // Set barangay text or hide it if not available
                            if (it.barangayName != null) {
                                userBarangayText.text = "Barangay: ${it.barangayName}"
                                userBarangayText.visibility = android.view.View.VISIBLE
                            } else {
                                userBarangayText.visibility = android.view.View.GONE
                            }
                        }
                    } else {
                        val errorCode = response.code()
                        val errorMessage = response.message()
                        Log.e(TAG, "Failed to load profile: $errorCode - $errorMessage")

                        try {
                            val errorBody = response.errorBody()?.string()
                            Log.e(TAG, "Error body: $errorBody")

                            // Handle 403 error specially - this usually means the token is invalid
                            // after changing email, the token might no longer be valid
                            if (errorCode == 403) {
                                Log.e(TAG, "403 Forbidden error - token likely invalid after email change")
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        this@DriverProfileActivity,
                                        "Your session has expired after profile update. Please login again.",
                                        Toast.LENGTH_LONG
                                    ).show()

                                    // Force logout and redirect to login
                                    sessionManager.logout()
                                    navigateToLogin()
                                }
                                return@withContext
                            }

                            Toast.makeText(
                                this@DriverProfileActivity,
                                "Failed to load profile data: $errorMessage",
                                Toast.LENGTH_SHORT
                            ).show()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing error response", e)
                            Toast.makeText(this@DriverProfileActivity, "Failed to load profile data", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading user data", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@DriverProfileActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    // Override onResume to refresh data when returning to this activity
    override fun onResume() {
        super.onResume()
        // Always refresh profile data when returning to this activity
        Log.d(TAG, "onResume - refreshing profile data")
        loadUserData()
    }
} 