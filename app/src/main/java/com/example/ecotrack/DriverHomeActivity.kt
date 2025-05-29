package com.example.ecotrack

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ecotrack.adapters.PaymentOrderAdapter
import com.example.ecotrack.models.payment.Payment
import com.example.ecotrack.utils.ApiService
import com.example.ecotrack.utils.RealTimeUpdateManager
import de.hdodenhof.circleimageview.CircleImageView
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

class DriverHomeActivity : BaseActivity() {
    private lateinit var welcomeTextView: TextView
    private lateinit var driverTextView: TextView
    private lateinit var profileImage: CircleImageView
    
    // In-Progress section
    private lateinit var inProgressRecyclerView: RecyclerView
    private lateinit var tvNoInProgressOrders: TextView
    private lateinit var btnViewDeliveryHistory: Button
    
    // No connection layout
    private lateinit var noConnectionLayout: LinearLayout
    private lateinit var btnRetry: Button
    
    // Progress bar
    private lateinit var progressBar: ProgressBar
    
    private val apiService = ApiService.create()
    private val TAG = "DriverHomeActivity"
    
    // Real-time update manager
    private lateinit var realTimeUpdateManager: RealTimeUpdateManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driver_home)
        
        // Initialize UI components
        initViews()
        setupClickListeners()
        
        // Initialize real-time update manager
        realTimeUpdateManager = RealTimeUpdateManager(
            activity = this,
            updateCallback = { loadJobOrders() }
        )
        
        // Check if user has driver role before loading profile
        validateDriverAccount()
    }
    
    private fun initViews() {
        // Find views from the updated layout
        welcomeTextView = findViewById(R.id.welcomeText)
        driverTextView = findViewById(R.id.tvDriverMode)
        profileImage = findViewById(R.id.profileImage)
        
        // In-Progress section
        inProgressRecyclerView = findViewById(R.id.recyclerViewInProgressJobOrders)
        tvNoInProgressOrders = findViewById(R.id.tvNoInProgressOrders)
        btnViewDeliveryHistory = findViewById(R.id.btnViewDeliveryHistory)
        
        // No connection layout
        noConnectionLayout = findViewById(R.id.noConnectionLayout)
        btnRetry = findViewById(R.id.btnRetry)
        
        // Progress bar
        progressBar = findViewById(R.id.progressBar)
        
        // Set up recycler views
        inProgressRecyclerView.layoutManager = LinearLayoutManager(this)
    }
    
    private fun setupClickListeners() {
        // Setup profile image button
        profileImage.setOnClickListener {
            startActivity(Intent(this, DriverProfileActivity::class.java))
        }
        
        // Setup retry button
        btnRetry.setOnClickListener {
            loadJobOrders()
        }
        
        // Setup view delivery history button
        btnViewDeliveryHistory.setOnClickListener {
            startActivity(Intent(this, DeliveryHistoryActivity::class.java))
        }
        
        // Setup bottom navigation items
        setupBottomNavigation()
    }
    
    private fun setupBottomNavigation() {
        // Set up click listeners for each navigation item
        findViewById<View>(R.id.homeNav).setOnClickListener { 
            // Already on home screen
        }
        
        findViewById<View>(R.id.jobOrdersNav).setOnClickListener {
            startActivity(Intent(this, DriverJobOrderActivity::class.java))
            finish()
        }
        
        findViewById<View>(R.id.collectionPointsNav).setOnClickListener {
            startActivity(Intent(this, DriverMapActivity::class.java))
            finish()
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
        
        lifecycleScope.launch {
            try {
                val response = apiService.getProfile(userId, "Bearer $token")
                
                if (response.isSuccessful) {
                    val profile = response.body()
                    profile?.let {
                        // Update UI with profile data
                        welcomeTextView.text = "Welcome, ${it.firstName}!"
                        
                        // Load job orders after profile is loaded
                        loadJobOrders()
                    }
                } else {
                    Log.e(TAG, "Failed to load profile: ${response.code()}")
                    Toast.makeText(
                        this@DriverHomeActivity,
                        "Failed to load profile",
                        Toast.LENGTH_SHORT
                    ).show()
                    showNoConnectionView()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading profile", e)
                Toast.makeText(
                    this@DriverHomeActivity,
                    "Error loading profile: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
                showNoConnectionView()
            }
        }
    }
    
    private fun loadJobOrders() {
        // Get the driver ID from session manager
        val driverId = sessionManager.getUserId()
        
        if (driverId == null) {
            Toast.makeText(this, "Driver ID not found. Please log in again.", Toast.LENGTH_LONG).show()
            navigateToLogin()
            return
        }
        
        // Get the JWT token for authentication
        val token = sessionManager.getToken()
        if (token == null) {
            Toast.makeText(this, "Authentication token not found. Please log in again.", Toast.LENGTH_LONG).show()
            navigateToLogin()
            return
        }
        
        // Show loading indicator
        showLoading()
        
        // Hide all sections initially
        inProgressRecyclerView.visibility = View.GONE
        tvNoInProgressOrders.visibility = View.GONE
        noConnectionLayout.visibility = View.GONE
        
        // Fetch payment orders assigned to this driver
        lifecycleScope.launch {
            try {
                val response = apiService.getPaymentsByDriverId(
                    driverId = driverId,
                    authToken = "Bearer $token"
                )
                
                if (response.isSuccessful) {
                    val payments = response.body()
                    
                    if (payments != null) {
                        // Split payments into in-progress and delivered
                        val inProgressPayments = payments.filter { 
                            it.jobOrderStatus == "In-Progress" || it.jobOrderStatus == "Accepted" 
                        }
                        
                        // Log counts for debugging
                        Log.d(TAG, "Total payments: ${payments.size}")
                        Log.d(TAG, "In-progress payments: ${inProgressPayments.size}")
                        
                        // Handle in-progress payments
                        if (inProgressPayments.isNotEmpty()) {
                            setupInProgressPaymentsAdapter(inProgressPayments)
                            inProgressRecyclerView.visibility = View.VISIBLE
                            tvNoInProgressOrders.visibility = View.GONE
                        } else {
                            inProgressRecyclerView.visibility = View.GONE
                            tvNoInProgressOrders.visibility = View.VISIBLE
                        }
                        
                        // Hide loading and no connection view
                        hideLoading()
                    } else {
                        // No payments found
                        showEmptyState()
                    }
                } else {
                    // Handle error response
                    val errorMessage = "Error: ${response.code()} - ${response.message()}"
                    Log.e(TAG, errorMessage)
                    Toast.makeText(this@DriverHomeActivity, errorMessage, Toast.LENGTH_LONG).show()
                    showNoConnectionView()
                }
            } catch (e: IOException) {
                // Handle network error
                Log.e(TAG, "Network error: ${e.message}")
                Toast.makeText(this@DriverHomeActivity, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
                showNoConnectionView()
            } catch (e: HttpException) {
                // Handle HTTP exception
                Log.e(TAG, "HTTP error: ${e.message}")
                Toast.makeText(this@DriverHomeActivity, "HTTP error: ${e.message}", Toast.LENGTH_LONG).show()
                showNoConnectionView()
            } catch (e: Exception) {
                // Handle other exceptions
                Log.e(TAG, "Error: ${e.message}")
                Toast.makeText(this@DriverHomeActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                showNoConnectionView()
            }
        }
    }
    
    private fun setupInProgressPaymentsAdapter(payments: List<Payment>) {
        val adapter = PaymentOrderAdapter(payments, true, "In-Progress") { payment ->
            // Navigate to the appropriate screen based on job order status
            when (payment.jobOrderStatus) {
                "In-Progress" -> {
                    // If the job is in progress, go directly to the collection completed screen
                    val intent = Intent(this, DriverJobOrderStatusActivity::class.java)
                    intent.putExtra("PAYMENT", payment)
                    intent.putExtra("MODE", DriverJobOrderStatusActivity.JobOrderStatusMode.COMPLETE)
                    startActivity(intent)
                }
                "Accepted" -> {
                    // If the job is accepted, go directly to the arrived at location screen
                    val intent = Intent(this, DriverArrivedAtTheLocationActivity::class.java)
                    intent.putExtra("PAYMENT", payment)
                    startActivity(intent)
                }
            }
        }
        
        inProgressRecyclerView.adapter = adapter
    }
    
    private fun showEmptyState() {
        hideLoading()
        inProgressRecyclerView.visibility = View.GONE
        tvNoInProgressOrders.visibility = View.VISIBLE
    }
    
    private fun showNoConnectionView() {
        hideLoading()
        noConnectionLayout.visibility = View.VISIBLE
        inProgressRecyclerView.visibility = View.GONE
        tvNoInProgressOrders.visibility = View.GONE
    }
    
    private fun showLoading() {
        progressBar.visibility = View.VISIBLE
    }
    
    private fun hideLoading() {
        progressBar.visibility = View.GONE
    }
    
    private fun redirectToCustomerHome() {
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }
    
    override fun onResume() {
        super.onResume()
        // Start real-time updates
        realTimeUpdateManager.startRealTimeUpdates()
    }
    
    override fun onPause() {
        super.onPause()
        // Stop real-time updates
        realTimeUpdateManager.stopRealTimeUpdates()
    }
} 