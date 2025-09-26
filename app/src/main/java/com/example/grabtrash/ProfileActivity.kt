package com.example.grabtrash

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.example.grabtrash.utils.ApiService
import com.example.grabtrash.utils.FileLuService
import com.example.grabtrash.utils.ProfileImageLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.ImageButton

class ProfileActivity : BaseActivity() {
    private val apiService = ApiService.create()
    private val fileLuService = FileLuService(this)
    private val profileImageLoader = ProfileImageLoader(this)
    private val TAG = "ProfileActivity"
    private lateinit var userNameText: TextView
    private lateinit var userEmailText: TextView
    private lateinit var userBarangayText: TextView
    private lateinit var profileAvatar: de.hdodenhof.circleimageview.CircleImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)
        
        // BaseActivity already handles session checks
        supportActionBar?.hide()
        initViews()
        setupClickListeners()
        // Show cached image immediately if available
        try {
            val cachedUrl = sessionManager.getProfileImageUrl()
            if (!cachedUrl.isNullOrBlank()) {
                loadProfileImage(cachedUrl)
            }
        } catch (_: Exception) {}
        loadUserData()
    }

    private fun initViews() {
        userNameText = findViewById(R.id.userName)
        userEmailText = findViewById(R.id.userEmail)
        userBarangayText = findViewById(R.id.userBarangay)
        profileAvatar = findViewById(R.id.profileAvatar)
    }
    
    private fun loadProfileImage(url: String) {
        profileImageLoader.loadProfileImageUltraFast(
            url = url,
            imageView = profileAvatar,
            placeholderResId = R.drawable.raph,
            errorResId = R.drawable.raph
        )
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
                                
                                android.app.AlertDialog.Builder(this@ProfileActivity)
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
            val intent = Intent(this, EditProfileActivity::class.java)
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

        // Remove bottom navigation click listeners since we removed those elements
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
                            // Load profile picture if available
                            try {
                                val url = it.imageUrl ?: it.profileImage
                                if (!url.isNullOrBlank()) {
                                    loadProfileImage(url)
                                    sessionManager.saveProfileImageUrl(url)
                                }
                            } catch (_: Exception) {}
                            
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
                                        this@ProfileActivity, 
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
                                this@ProfileActivity, 
                                "Failed to load profile data: $errorMessage",
                                Toast.LENGTH_SHORT
                            ).show()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing error response", e)
                            Toast.makeText(this@ProfileActivity, "Failed to load profile data", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading user data", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ProfileActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Override onResume to refresh data when returning from EditProfileActivity
    override fun onResume() {
        super.onResume()
        // BaseActivity already handles session management
        
        // Always refresh profile data when returning to this activity
        Log.d(TAG, "onResume - refreshing profile data")
        loadUserData()
    }
} 