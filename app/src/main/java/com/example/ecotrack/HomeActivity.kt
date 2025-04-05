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

class HomeActivity : BaseActivity() {

    private val apiService = ApiService.create()
    private lateinit var welcomeText: TextView
    private lateinit var timeRemainingText: TextView
    private var countDownTimer: CountDownTimer? = null
    private val TAG = "HomeActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        // Parent class BaseActivity already handles session checks
        supportActionBar?.hide()

        initializeViews()
        setupClickListeners()
        startCountdownTimer()
        loadUserData()
    }

    private fun initializeViews() {
        welcomeText = findViewById(R.id.welcomeText)
        timeRemainingText = findViewById(R.id.timeRemaining)
    }

    private fun setupClickListeners() {
        // Notification button click
        findViewById<ImageButton>(R.id.notificationButton).setOnClickListener {
            // BaseActivity already calls updateLastActivity() in onUserInteraction
            // TODO: Handle notifications
        }

        // Profile image click
        findViewById<CircleImageView>(R.id.profileImage).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        // View all click
        findViewById<TextView>(R.id.viewAll).setOnClickListener {
            // TODO: Show all reminders
        }

        // Bottom navigation clicks
        findViewById<LinearLayout>(R.id.scheduleNav).setOnClickListener {
            // TODO: Navigate to schedule
        }

        findViewById<LinearLayout>(R.id.pointsNav).setOnClickListener {
            // TODO: Navigate to points
        }

        findViewById<LinearLayout>(R.id.pickupNav).setOnClickListener {
            // TODO: Navigate to pickup
        }
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