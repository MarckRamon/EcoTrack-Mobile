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
    }

    private fun setupClickListeners() {
        // Back button
        findViewById<ImageButton>(R.id.backButton).setOnClickListener {
            onBackPressed()
        }

        findViewById<LinearLayout>(R.id.editInfoButton).setOnClickListener {
            val intent = Intent(this, DriverEditProfileActivity::class.java)
            startActivity(intent)
        }

        findViewById<LinearLayout>(R.id.forgotPasswordButton).setOnClickListener {
            // TODO: Handle forgot password
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
                            userNameText.text = "${it.firstName} ${it.lastName}"
                            userEmailText.text = it.email
                        }
                    } else {
                        val errorCode = response.code()
                        val errorMessage = response.message()
                        Log.e(TAG, "Failed to load profile: $errorCode - $errorMessage")
                        
                        Toast.makeText(
                            this@DriverProfileActivity,
                            "Failed to load profile data",
                            Toast.LENGTH_SHORT
                        ).show()
                        
                        val shouldLogout = if (errorCode == 401) {
                            // Session expired
                            sessionManager.logout()
                            startActivity(Intent(this@DriverProfileActivity, LoginActivity::class.java))
                            finish()
                            true
                        } else {
                            false
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

    // Override onResume to refresh data when returning from DriverEditProfileActivity
    override fun onResume() {
        super.onResume()
        // BaseActivity already handles session management
        
        // Always refresh profile data when returning to this activity
        Log.d(TAG, "onResume - refreshing profile data")
        loadUserData()
    }
} 