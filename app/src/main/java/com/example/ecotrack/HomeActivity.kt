package com.example.ecotrack

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import de.hdodenhof.circleimageview.CircleImageView
import android.widget.TextView
import android.widget.Toast
import android.view.inputmethod.EditorInfo
import androidx.recyclerview.widget.LinearLayoutManager
import android.content.Intent
import android.app.AlertDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import android.util.Log
import android.os.CountDownTimer
import android.widget.LinearLayout
import com.example.ecotrack.utils.ApiService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.ecotrack.databinding.ActivityHomeBinding

class HomeActivity : BaseActivity() {

    private lateinit var binding: ActivityHomeBinding
    private val apiService = ApiService.create()
    private lateinit var welcomeText: TextView
    private lateinit var timeRemainingText: TextView
    private var countDownTimer: CountDownTimer? = null
    private val TAG = "HomeActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Check if user has customer role before proceeding
        validateCustomerAccount()
        
        // Set click listeners for navigation
        setupNavigation()

        // Parent class BaseActivity already handles session checks
        supportActionBar?.hide()

        initializeViews()
        startCountdownTimer()
        loadUserData()
    }

    private fun initializeViews() {
        welcomeText = binding.welcomeText
        timeRemainingText = binding.timeRemaining
    }

    private fun setupNavigation() {
        // Setup navigation, profile button, etc.
        binding.profileImage?.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
    }

    private fun validateCustomerAccount() {
        // First check the stored user type
        val userType = sessionManager.getUserType()
        
        if (userType != "customer") {
            Log.w(TAG, "Non-customer account detected in HomeActivity: $userType")
            
            val message = when (userType) {
                "driver" -> "Driver accounts should use the driver interface. Redirecting..."
                "admin" -> "Admin accounts should use the admin interface. Redirecting..."
                else -> "Invalid account type. Please login again."
            }
            
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            
            // Redirect based on account type
            when (userType) {
                "driver" -> redirectToDriverHome()
                "admin" -> {
                    // For now just logout, later redirect to admin interface
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
        
        // Load the user profile
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
        if (roleFromToken != null && roleFromToken != "customer") {
            Log.w(TAG, "Non-customer role detected in token: $roleFromToken")
            
            val message = when (roleFromToken) {
                "driver" -> "Driver accounts should use the driver interface."
                "admin" -> "Admin accounts should use the admin interface."
                else -> "Invalid account type. Please login again."
            }
            
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            
            when (roleFromToken) {
                "driver" -> {
                    // Save the role and redirect
                    sessionManager.saveUserType("driver")
                    redirectToDriverHome()
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
                            binding.welcomeText?.text = "Welcome, ${it.firstName}"
                        }
                    } else {
                        Log.e(TAG, "Failed to load profile: ${response.code()}")
                        Toast.makeText(
                            this@HomeActivity,
                            "Failed to load profile",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading profile", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@HomeActivity,
                        "Error loading profile: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun redirectToDriverHome() {
        startActivity(Intent(this, DriverHomeActivity::class.java))
        finish()
    }

    private fun startCountdownTimer() {
        // Example: 23 hours countdown
        val totalTimeInMillis = 23 * 60 * 60 * 1000L

        countDownTimer = object : CountDownTimer(totalTimeInMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val hours = millisUntilFinished / (60 * 60 * 1000)
                val minutes = (millisUntilFinished % (60 * 60 * 1000)) / (60 * 1000)
                val seconds = (millisUntilFinished % (60 * 1000)) / 1000

                timeRemainingText.text = "${hours}h ${minutes}m ${seconds}s remaining"
            }

            override fun onFinish() {
                timeRemainingText.text = "Time's up!"
            }
        }.start()
    }

    private fun loadUserData() {
        val token = sessionManager.getToken()
        val userId = sessionManager.getUserId()
        
        if (token == null || userId == null) {
            Log.e(TAG, "loadUserData - Missing credentials - token: $token, userId: $userId")
            // BaseActivity will handle the redirect to login if needed
            return
        }
        
        Log.d(TAG, "Loading profile data for userId: $userId")
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = apiService.getProfile(userId, "Bearer $token")
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        val profile = response.body()
                        profile?.let {
                            Log.d(TAG, "Profile loaded successfully: ${it.firstName} ${it.lastName}, email: ${it.email}")
                            welcomeText.text = "Welcome, ${it.firstName}!"
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
                                Toast.makeText(
                                    this@HomeActivity, 
                                    "Your session has expired after profile update. Please login again.",
                                    Toast.LENGTH_LONG
                                ).show()
                                
                                // Force logout and redirect to login
                                sessionManager.logout()
                                navigateToLogin()
                                return@withContext
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing error response", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading user data", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@HomeActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Override onResume to refresh data when returning from ProfileActivity
    override fun onResume() {
        super.onResume()
        // BaseActivity already handles setCurrentActivity and updateLastActivity
        
        // Always refresh profile data when returning to this activity
        Log.d(TAG, "onResume - refreshing profile data")
        loadUserData()
    }
    
    // This is called when the activity is brought back to the foreground
    override fun onRestart() {
        super.onRestart()
        Log.d(TAG, "onRestart - refreshing profile data")
        loadUserData()
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}