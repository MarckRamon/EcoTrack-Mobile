package com.example.grabtrash

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.util.Log
import com.google.android.material.button.MaterialButton
import com.example.grabtrash.models.LoginRequest
import com.example.grabtrash.utils.ApiService
import com.example.grabtrash.utils.SessionManager
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DriverLoginActivity : AppCompatActivity() {

    private val apiService = ApiService.create()
    private val TAG = "DriverLoginActivity"
    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var loginButton: MaterialButton
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            setContentView(R.layout.activity_driver_login)

            // Initialize SessionManager
            sessionManager = SessionManager.getInstance(this)

            // Check if user is already logged in
            if (sessionManager.isLoggedIn()) {
                val savedRole = (sessionManager.getUserType() ?: "customer").lowercase()
                when (savedRole) {
                    "driver" -> startActivity(Intent(this, DriverHomeActivity::class.java))
                    else -> startActivity(Intent(this, HomeActivity::class.java))
                }
                finish()
                return
            }

            // Initialize views
            initializeViews()
            setupClickListeners()

        } catch (e: Exception) {
            Log.e("DriverLoginActivity", "Error in onCreate: ${e.message}")
            e.printStackTrace()
            Toast.makeText(this, "Error initializing app: ${e.message}", Toast.LENGTH_LONG).show()
            finish() // Close the activity if initialization fails
        }
    }

    private fun initializeViews() {
        try {
            emailInput = findViewById(R.id.emailInput)
            passwordInput = findViewById(R.id.passwordInput)
            loginButton = findViewById(R.id.loginButton)
        } catch (e: Exception) {
            Log.e("DriverLoginActivity", "Error in initializeViews: ${e.message}")
            throw Exception("Failed to initialize views: ${e.message}")
        }
    }

    private fun setupClickListeners() {
        try {
            // Change Button to TextView for customer toggle
            findViewById<TextView>(R.id.customerButton).setOnClickListener {
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }

            // Set up login button click listener
            loginButton.setOnClickListener {
                val email = emailInput.text.toString()
                val password = passwordInput.text.toString()
                
                if (email.isNotEmpty() && password.isNotEmpty()) {
                    showLoading(true)
                    loginDriver(email, password)
                } else {
                    Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                }
            }

            // Change Button to TextView for driver button
            findViewById<TextView>(R.id.driverButton).isEnabled = false
        } catch (e: Exception) {
            throw Exception("Failed to setup click listeners: ${e.message}")
        }
    }

    private fun loginDriver(email: String, password: String) {
        Log.d(TAG, "Starting driver login for email: $email")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Create login request object
                val loginRequest = LoginRequest(email, password)
                Log.d(TAG, "Created login request: $loginRequest")
                
                // Send login request to API
                Log.d(TAG, "Sending login request to API...")
                val response = apiService.login(loginRequest)
                Log.d(TAG, "Received response: code=${response.code()}, isSuccessful=${response.isSuccessful}")
                
                // Process response
                if (response.isSuccessful) {
                    val loginResponse = response.body()
                    Log.d(TAG, "Login successful: $loginResponse")
                    
                    // Check if the response and token are valid
                    if (loginResponse != null && loginResponse.token.isNotEmpty()) {
                        // Extract role from JWT token since API response doesn't include role field
                        val roleFromToken = sessionManager.extractRoleFromToken(loginResponse.token)
                        val userRole = (roleFromToken ?: "customer").lowercase().trim()
                        Log.d(TAG, "Role from token: '$roleFromToken', normalized: '$userRole'")

                        // Only allow driver role for driver login
                        Log.d(TAG, "Checking role: userRole='$userRole', expected='driver', match=${userRole == "driver"}")
                        if (userRole != "driver") {
                            Log.w(TAG, "Role validation failed: userRole='$userRole' is not 'driver'")
                            // Clear any previous session to avoid auto-redirects
                            sessionManager.logout()
                            withContext(Dispatchers.Main) {
                                showLoginError("This account is not a driver account. Please use the Customer login. (Role: '${loginResponse.role}')")
                            }
                            return@launch
                        }
                        Log.d(TAG, "Role validation passed: userRole='$userRole' is 'driver'")

                        // Store token, userId, and role in SessionManager
                        sessionManager.saveToken(loginResponse.token)
                        sessionManager.saveUserId(loginResponse.userId)
                        sessionManager.saveUserType(userRole)

                        // Request notification permissions if needed
                        withContext(Dispatchers.Main) {
                            // Check for notification permission for Android 13+
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                requestNotificationPermission()
                            }
                            
                            // Register FCM token with server
                            registerFcmToken()
                            
                            // Navigate to driver home
                            startActivity(Intent(this@DriverLoginActivity, DriverHomeActivity::class.java))
                            finish()
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            showLoginError("Invalid response from server")
                        }
                    }
                } else {
                    // Handle error response
                    Log.e(TAG, "Login failed: code=${response.code()}, message=${response.message()}")
                    Log.e(TAG, "Error body: ${response.errorBody()?.string()}")
                    
                    val errorMessage = when (response.code()) {
                        401 -> "Invalid credentials"
                        403 -> "Account is locked or requires verification"
                        404 -> "User not found"
                        500 -> "Server error"
                        else -> "Login failed: ${response.message()}"
                    }
                    
                    withContext(Dispatchers.Main) {
                        showLoginError(errorMessage)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Login error", e)
                withContext(Dispatchers.Main) {
                    showLoginError("Connection error: ${e.message}")
                }
            }
        }
    }

    // Request notification permission for Android 13+
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d(TAG, "Notification permission granted")
        } else {
            Log.d(TAG, "Notification permission denied")
            Toast.makeText(
                this,
                "Notifications are disabled. You may miss important updates.",
                Toast.LENGTH_LONG
            ).show()
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
        }
    }
    
    private fun registerFcmToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.e(TAG, "FCM token retrieval failed", task.exception)
                return@addOnCompleteListener
            }
            
            // Get the token
            val fcmToken = task.result
            Log.d(TAG, "FCM Token: $fcmToken")
            
            // Save the token locally
            sessionManager.saveFcmToken(fcmToken)
            
            // If user is logged in, register the token with the server
            if (sessionManager.isLoggedIn()) {
                val authToken = sessionManager.getToken()
                if (authToken == null) {
                    Log.e(TAG, "Auth token is null, cannot register FCM token")
                    return@addOnCompleteListener
                }
                
                // Format the authorization header
                val bearerToken = if (authToken.startsWith("Bearer ")) authToken else "Bearer $authToken"
                
                // Register token with server
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        Log.d(TAG, "Registering FCM token with server")
                        val fcmTokenRequest = com.example.grabtrash.models.FcmTokenRequest(fcmToken)
                        val response = apiService.registerFcmToken(fcmTokenRequest, bearerToken)
                        
                        if (response.isSuccessful) {
                            Log.d(TAG, "FCM token registered successfully with server")
                        } else {
                            Log.e(TAG, "Failed to register FCM token: ${response.code()} - ${response.message()}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error registering FCM token", e)
                    }
                }
            }
        }
    }
    
    private fun showLoading(isLoading: Boolean) {
        loginButton.isEnabled = !isLoading
        // You can add a progress bar here if the layout has one
    }

    private fun showLoginError(errorMessage: String) {
        Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
        showLoading(false)
    }
}