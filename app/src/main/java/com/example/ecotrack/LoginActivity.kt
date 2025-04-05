package com.example.ecotrack

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.LayoutInflater
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.ecotrack.databinding.ActivityLoginBinding
import com.example.ecotrack.databinding.ActivityDriverLoginBinding
import com.example.ecotrack.models.LoginRequest
import com.example.ecotrack.utils.ApiService
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
            return
        }

        setupToggleButtons()

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
                // Sign out from Firebase first to prevent interference
                try {
                    FirebaseAuth.getInstance().signOut()
                } catch (e: Exception) {
                    Log.e(TAG, "Error signing out from Firebase", e)
                    // Continue anyway - we'll use our custom backend
                }
                
                Log.d(TAG, "Attempting to login with email: $email")
                val loginRequest = LoginRequest(email.trim(), password)
                
                try {
                    val response = apiService.login(loginRequest)
                    withContext(Dispatchers.Main) {
                        if (response.isSuccessful) {
                            val loginResponse = response.body()
                            loginResponse?.let {
                                Log.d(TAG, "Login successful. Token: ${it.token}, UserId: ${it.userId}")
                                sessionManager.saveToken(it.token)
                                if (it.userId != null) {
                                    sessionManager.saveUserId(it.userId)
                                }
                                // Save user type
                                sessionManager.saveUserType(if (isCustomerSelected) "customer" else "driver")
                                
                                Toast.makeText(this@LoginActivity, "Login successful", Toast.LENGTH_SHORT).show()
                                startActivity(Intent(this@LoginActivity, HomeActivity::class.java))
                                finish()
                            }
                        } else {
                            val statusCode = response.code()
                            Log.e(TAG, "Login failed with code: $statusCode, message: ${response.message()}")
                            
                            val errorMessage = when (statusCode) {
                                400 -> "Invalid email or password"
                                401 -> "Unauthorized access"
                                403 -> "Account locked"
                                404 -> "User not found"
                                500 -> "Server error, please try again later"
                                else -> "Login failed: ${response.message()}"
                            }
                            
                            Toast.makeText(this@LoginActivity, errorMessage, Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Network error during login", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@LoginActivity, "Network error: Please check your connection", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during login", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@LoginActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    showLoading(false)
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
}