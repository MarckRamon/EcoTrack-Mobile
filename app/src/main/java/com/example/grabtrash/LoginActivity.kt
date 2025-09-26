package com.example.grabtrash

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.grabtrash.databinding.ActivityLoginBinding
import com.example.grabtrash.databinding.ActivityDriverLoginBinding
import com.example.grabtrash.models.LoginRequest
import com.example.grabtrash.utils.ApiService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.firebase.messaging.FirebaseMessaging

class LoginActivity : BaseActivity() {
    private lateinit var customerBinding: ActivityLoginBinding
    private lateinit var driverBinding: ActivityDriverLoginBinding
    private val apiService = ApiService.create()
    private val TAG = "LoginActivity"
    private var isCustomerSelected = true
    private lateinit var rootView: ViewGroup

    // Override to allow access without authentication
    override fun requiresAuthentication(): Boolean {
        return false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Inflate the initial customer layout
        customerBinding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(customerBinding.root)
        
        // Save reference to the root view for later layout swapping
        rootView = findViewById(android.R.id.content)
        
        // Check if user is already logged in with custom backend
        if (sessionManager.isLoggedIn()) {
            val savedRole = (sessionManager.getUserType() ?: "customer").lowercase()
            when (savedRole) {
                "driver" -> startActivity(Intent(this, DriverHomeActivity::class.java))
                else -> startActivity(Intent(this, HomeActivity::class.java))
            }
            finish()
            return
        }

        setupToggleButtons()

        // If an intent extra requests a specific role selection, apply it
        intent.getStringExtra("selectRole")?.lowercase()?.let { role ->
            if (role == "driver") {
                setCustomerToggle(false)
            } else if (role == "customer") {
                setCustomerToggle(true)
            }
        }

        customerBinding.btnLogin.setOnClickListener {
            val email = customerBinding.etEmail.text.toString()
            val password = customerBinding.etPassword.text.toString()

            if (email.isNotEmpty() && password.isNotEmpty()) {
                showLoading(true)
                loginUser(email, password)
            } else {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            }
        }

        customerBinding.btnRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
        
        // Forgot Password button
        customerBinding.forgotPasswordText.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }
    }

    private fun setupToggleButtons() {
        // Set initial state
        customerBinding.customerToggle.setBackgroundResource(R.drawable.toggle_selected_background)
        customerBinding.customerToggle.setTextColor(ContextCompat.getColor(this, android.R.color.white))
        customerBinding.driverToggle.setBackgroundResource(0)
        customerBinding.driverToggle.setTextColor(ContextCompat.getColor(this, android.R.color.black))

        // Set click listeners for both toggles
        customerBinding.customerToggle.setOnClickListener {
            if (!isCustomerSelected) {
                setCustomerToggle(true)
            }
        }

        customerBinding.driverToggle.setOnClickListener {
            if (isCustomerSelected) {
                setCustomerToggle(false)
            }
        }

        // Make the container also clickable to improve UX
        customerBinding.toggleContainer.setOnClickListener {
            setCustomerToggle(!isCustomerSelected)
        }
    }

    private fun setCustomerToggle(isCustomer: Boolean) {
        if (isCustomer == isCustomerSelected) return
        
        isCustomerSelected = isCustomer
        
        if (isCustomer) {
            // Switch to customer layout
            switchToCustomerLayout()
        } else {
            // Switch to driver layout
            switchToDriverLayout()
        }
        
        // Update login logic or UI based on selection if needed
        Log.d(TAG, "User type selected: ${if (isCustomer) "Customer" else "Driver"}")
    }
    
    private fun switchToCustomerLayout() {
        // Remove driver layout if it exists
        rootView.removeAllViews()
        
        // Inflate customer layout
        customerBinding = ActivityLoginBinding.inflate(layoutInflater, rootView, true)
        
        // Set up button listeners again
        setupCustomerLayoutListeners()
        
        // Update toggle appearance
        customerBinding.customerToggle.setBackgroundResource(R.drawable.toggle_selected_background)
        customerBinding.customerToggle.setTextColor(ContextCompat.getColor(this, android.R.color.white))
        customerBinding.driverToggle.setBackgroundResource(0)
        customerBinding.driverToggle.setTextColor(ContextCompat.getColor(this, android.R.color.black))
    }
    
    private fun switchToDriverLayout() {
        // Remove customer layout
        rootView.removeAllViews()
        
        // Inflate driver layout
        driverBinding = ActivityDriverLoginBinding.inflate(layoutInflater, rootView, true)
        
        // Set up button listeners for driver layout
        setupDriverLayoutListeners()
        
        // Update toggle appearance in driver layout
        driverBinding.customerButton.setBackgroundResource(0)
        driverBinding.customerButton.setTextColor(ContextCompat.getColor(this, android.R.color.black))
        driverBinding.driverButton.setBackgroundResource(R.drawable.toggle_selected_background)
        driverBinding.driverButton.setTextColor(ContextCompat.getColor(this, android.R.color.white))
    }
    
    private fun setupCustomerLayoutListeners() {
        // Set click listeners for toggles
        customerBinding.customerToggle.setOnClickListener {
            if (!isCustomerSelected) {
                setCustomerToggle(true)
            }
        }

        customerBinding.driverToggle.setOnClickListener {
            if (isCustomerSelected) {
                setCustomerToggle(false)
            }
        }

        customerBinding.toggleContainer.setOnClickListener {
            setCustomerToggle(!isCustomerSelected)
        }
        
        // Login button
        customerBinding.btnLogin.setOnClickListener {
            val email = customerBinding.etEmail.text.toString()
            val password = customerBinding.etPassword.text.toString()

            if (email.isNotEmpty() && password.isNotEmpty()) {
                showLoading(true)
                loginUser(email, password)
            } else {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Register button
        customerBinding.btnRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
        
        // Add a direct link to driver login for testing
        // You can remove this later once the toggle works properly
        customerBinding.forgotPasswordText.setOnClickListener {
            // Show options: Forgot Password or Driver Login
            android.app.AlertDialog.Builder(this)
                .setTitle("Choose Option")
                .setItems(arrayOf("Forgot Password", "Driver Login")) { _, which ->
                    when (which) {
                        0 -> startActivity(Intent(this, ForgotPasswordActivity::class.java))
                        1 -> startActivity(Intent(this, DriverLoginActivity::class.java))
                    }
                }
                .show()
        }
    }
    
    private fun setupDriverLayoutListeners() {
        // Set click listeners for toggles
        driverBinding.customerButton.setOnClickListener {
            if (!isCustomerSelected) {
                setCustomerToggle(true)
            }
        }

        driverBinding.driverButton.setOnClickListener {
            if (isCustomerSelected) {
                setCustomerToggle(false)
            }
        }

        driverBinding.toggleContainer.setOnClickListener {
            setCustomerToggle(!isCustomerSelected)
        }
        
        // Login button
        driverBinding.loginButton.setOnClickListener {
            val email = driverBinding.emailInput.text.toString()
            val password = driverBinding.passwordInput.text.toString()

            if (email.isNotEmpty() && password.isNotEmpty()) {
                showLoading(true)
                loginUser(email, password)
            } else {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loginUser(email: String, password: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Create login request object
                val loginRequest = LoginRequest(email, password)
                
                // Send login request to API
                val response = apiService.login(loginRequest)
                
                // Process response
                if (response.isSuccessful) {
                    val loginResponse = response.body()
                    Log.d(TAG, "Login successful: $loginResponse")
                    
                    // Check if the response and token are valid
                    if (loginResponse != null && loginResponse.token.isNotEmpty()) {
                        // Extract role from JWT token since API response doesn't include role field
                        val roleFromToken = sessionManager.extractRoleFromToken(loginResponse.token)
                        val userRole = (roleFromToken ?: "customer").lowercase()
                        Log.d(TAG, "LoginActivity - Role from token: '$roleFromToken', normalized: '$userRole'")
                        Log.d(TAG, "LoginActivity - isCustomerSelected: $isCustomerSelected")

                        // Enforce role selection: if customer UI selected, only allow customer; if driver UI selected, only allow driver
                        val isRoleAllowed = (isCustomerSelected && userRole == "customer") || (!isCustomerSelected && userRole == "driver")
                        Log.d(TAG, "LoginActivity - Role check: isCustomerSelected=$isCustomerSelected, userRole='$userRole', isRoleAllowed=$isRoleAllowed")

                        if (!isRoleAllowed) {
                            // Ensure any previous session is cleared to avoid auto-redirects
                            sessionManager.logout()
                            withContext(Dispatchers.Main) {
                                // Inform user and redirect to the correct interface
                                showLoginError("Please use the ${if (userRole == "driver") "Driver" else "Customer"} login to access this account.")
                                
                                // Redirect to the correct interface based on the user's role
                                val intent = if (userRole == "driver") {
                                    // Driver account trying to login through customer interface -> go to driver interface
                                    Intent(this@LoginActivity, LoginActivity::class.java).apply {
                                        putExtra("selectRole", "driver")
                                    }
                                } else {
                                    // Customer account trying to login through driver interface -> go to customer interface
                                    Intent(this@LoginActivity, LoginActivity::class.java).apply {
                                        putExtra("selectRole", "customer")
                                    }
                                }
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                startActivity(intent)
                            }
                            return@launch
                        }

                        // Store token, userId, and role in SessionManager (only after role check passes)
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
                            
                            // Navigate based on role
                            when (userRole) {
                                "driver" -> {
                                    startActivity(Intent(this@LoginActivity, DriverHomeActivity::class.java))
                                }
                                "admin" -> {
                                    Toast.makeText(
                                        this@LoginActivity,
                                        "Admin login not implemented in mobile app",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                                else -> {
                                    startActivity(Intent(this@LoginActivity, HomeActivity::class.java))
                                }
                            }
                            finish()
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            showLoginError("Invalid response from server")
                        }
                    }
                } else {
                    // Handle error response
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
        if (isCustomerSelected) {
            customerBinding.progressBar?.visibility = if (isLoading) View.VISIBLE else View.GONE
            customerBinding.btnLogin.isEnabled = !isLoading
        } else {
            // Assuming the driver layout has a progressBar too
            if (::driverBinding.isInitialized) {
                // driverBinding.progressBar?.visibility = if (isLoading) View.VISIBLE else View.GONE
                driverBinding.loginButton.isEnabled = !isLoading
            }
        }
    }

    private fun showLoginError(errorMessage: String) {
        Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
        showLoading(false)
    }
}