package com.example.ecotrack.ui.pickup

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PorterDuff
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.preference.PreferenceManager
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.ecotrack.HomeActivity
import com.example.ecotrack.R
import com.example.ecotrack.models.JobOrderStatusUpdate
import com.example.ecotrack.models.payment.PaymentResponse
import com.example.ecotrack.ui.pickup.model.OrderStatus
import com.example.ecotrack.ui.pickup.model.PaymentMethod
import com.example.ecotrack.ui.pickup.model.PickupOrder
import com.example.ecotrack.utils.ApiService
import com.example.ecotrack.utils.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.*
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.app.AlertDialog

class OrderStatusActivity : AppCompatActivity() {

    private lateinit var tvStatusHeader: TextView
    private lateinit var tvStatusDescription: TextView
    private lateinit var tvWasteType: TextView
    private lateinit var tvSacks: TextView
    private lateinit var tvTruckSize: TextView
    private lateinit var tvAmount: TextView
    private lateinit var tvPaymentMethod: TextView
    private lateinit var tvLocation: TextView
    private lateinit var btnCancel: TextView
    private lateinit var backButton: ImageView
    private lateinit var order: PickupOrder
    private lateinit var apiService: ApiService
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var sessionManager: SessionManager
    
    // For status polling
    private val statusHandler = Handler(Looper.getMainLooper())
    private var statusRunnable: Runnable? = null
    // Set polling interval to 30 seconds to significantly reduce server load
    private val STATUS_POLL_INTERVAL = 30000L // 30 seconds
    
    // Add variables to track last request time and prevent redundant calls
    private val lastRequestTimes = mutableMapOf<String, Long>()
    private val MINIMUM_REQUEST_INTERVAL = 30000L // 30 seconds between requests for the same order

    // Status bar icons
    private lateinit var iconDriverAssigned: ImageView
    private lateinit var iconPickup: ImageView
    private lateinit var iconCompleted: ImageView

    // Status bar progress lines
    private lateinit var progressLine1: View
    private lateinit var progressLine2: View
    private lateinit var progressLine3: View

    // Bottom navigation
    private lateinit var navHome: LinearLayout
    private lateinit var navSchedule: LinearLayout
    private lateinit var navLocation: LinearLayout
    private lateinit var navPickup: LinearLayout

    private var lastKnownStatus: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_order_status)
        
        // Initialize API service
        apiService = ApiService.create()
        
        // Initialize shared preferences and session manager
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        sessionManager = SessionManager.getInstance(this)

        // Setup back button
        backButton = findViewById(R.id.backButton)
        backButton.setOnClickListener {
            onBackPressed()
        }

        // Get order data from intent
        order = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("ORDER_DATA", PickupOrder::class.java) ?: throw IllegalStateException("No order data provided")
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("ORDER_DATA") ?: throw IllegalStateException("No order data provided")
        }

        // Initialize views
        tvStatusHeader = findViewById(R.id.tv_status_header)
        tvStatusDescription = findViewById(R.id.tv_status_description)
        tvWasteType = findViewById(R.id.tv_waste_type)
        tvSacks = findViewById(R.id.tv_sacks)
        tvTruckSize = findViewById(R.id.tv_truck_size)
        tvAmount = findViewById(R.id.tv_amount)
        tvPaymentMethod = findViewById(R.id.tv_payment_method)
        tvLocation = findViewById(R.id.tv_location)
        btnCancel = findViewById(R.id.btn_cancel)

        // Initialize status bar icons and lines
        iconDriverAssigned = findViewById(R.id.icon_driver_assigned)
        iconPickup = findViewById(R.id.icon_pickup)
        iconCompleted = findViewById(R.id.icon_completed)

        val progressContainer = findViewById<LinearLayout>(R.id.progress_container)
        progressLine1 = progressContainer.getChildAt(0) as View
        progressLine2 = progressContainer.getChildAt(2) as View
        progressLine3 = progressContainer.getChildAt(4) as View

        // Initialize bottom navigation if it exists in this layout
        try {
            navHome = findViewById(R.id.nav_home)
            navSchedule = findViewById(R.id.nav_schedule)
            navLocation = findViewById(R.id.nav_location)
            navPickup = findViewById(R.id.nav_pickup)

            // Set click listeners for bottom navigation
            navHome.setOnClickListener {
                navigateToHome()
            }

            navSchedule.setOnClickListener {
                navigateToHome()
            }

            navLocation.setOnClickListener {
                navigateToHome()
            }

            navPickup.setOnClickListener {
                navigateToPickup()
            }
        } catch (e: Exception) {
            // Bottom navigation might not be in this layout
        }

        // Set values
        tvWasteType.text = order.wasteType.getDisplayName()
        tvSacks.text = order.numberOfSacks.toString()
        tvTruckSize.text = order.truckSize.getDisplayName()
        tvAmount.text = "₱${order.total.toInt()}"
        tvPaymentMethod.text = order.paymentMethod.getDisplayName()
        tvLocation.text = order.address

        // Log payment method for debugging
        Log.d("OrderStatusActivity", "Payment method: ${order.paymentMethod.name}, display name: ${order.paymentMethod.getDisplayName()}")

        // Initially, set the status based on local order status
        val initialStatus = when(order.status) {
            OrderStatus.PROCESSING -> "Processing"
            OrderStatus.ACCEPTED -> "Accepted"
            OrderStatus.COMPLETED -> "Completed"
            OrderStatus.CANCELLED -> "Cancelled"
        }
        updateOrderStatus(initialStatus)
        
        // Fetch the latest status immediately
        fetchLatestStatus()
        
        // Set up periodic status checking
        startStatusPolling()

        // If order is in PROCESSING status, show the ability to cancel
        if (initialStatus == "Processing") {
            btnCancel.visibility = View.VISIBLE
            btnCancel.setOnClickListener {
                // Show confirmation dialog before cancellation
                AlertDialog.Builder(this)
                    .setTitle("Cancel Order")
                    .setMessage("Are you sure you want to cancel this order?")
                    .setPositiveButton("Yes") { _, _ ->
                        // Cancel the order via API
                        cancelOrder()
                    }
                    .setNegativeButton("No", null)
                    .show()
            }
        } else {
            btnCancel.visibility = View.GONE
        }

        // Add debug functionality - long press on payment method text to cycle through payment methods
        tvPaymentMethod.setOnLongClickListener {
            // Cycle through payment methods for testing
            val methods = PaymentMethod.values()
            val currentIndex = methods.indexOf(order.paymentMethod)
            val nextIndex = (currentIndex + 1) % methods.size
            val newMethod = methods[nextIndex]

            // Update the order with the new payment method
            order = order.copy(paymentMethod = newMethod)

            // Update the UI
            tvPaymentMethod.text = order.paymentMethod.getDisplayName()

            // Log the change
            Log.d("OrderStatusActivity", "Changed payment method to: ${order.paymentMethod.name}, display name: ${order.paymentMethod.getDisplayName()}")

            return@setOnLongClickListener true
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Fetch the latest status immediately when resuming
        fetchLatestStatus()
        // Restart polling
        startStatusPolling()
    }
    
    override fun onPause() {
        super.onPause()
        // Stop polling when activity is paused
        stopStatusPolling()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Ensure polling is stopped when activity is destroyed
        stopStatusPolling()
    }
    
    private fun startStatusPolling() {
        // Stop any existing polling
        stopStatusPolling()
        
        statusRunnable = object : Runnable {
            override fun run() {
                fetchLatestStatus()
                statusHandler.postDelayed(this, STATUS_POLL_INTERVAL)
            }
        }
        // Start polling with initial delay to avoid immediate duplicate request after onCreate/onResume fetch
        statusHandler.postDelayed(statusRunnable!!, STATUS_POLL_INTERVAL)
    }
    
    private fun stopStatusPolling() {
        statusRunnable?.let {
            statusHandler.removeCallbacks(it)
            statusRunnable = null
        }
    }
    
    private fun fetchLatestStatus() {
        // Use the payment reference number to fetch the latest status
        val orderId = order.id // Assuming this is the orderId used in the backend
        val referenceNumber = order.referenceNumber // Try using the reference number as an alternative
        
        // Skip if we recently made a request for this order (avoid redundant calls)
        val currentTime = System.currentTimeMillis()
        val lastRequestTime = lastRequestTimes[orderId] ?: 0L
        
        if (currentTime - lastRequestTime < MINIMUM_REQUEST_INTERVAL) {
            Log.d("OrderStatusActivity", "Skipping redundant request for orderId: $orderId (last request was ${currentTime - lastRequestTime}ms ago)")
            return
        }
        
        // Update the last request time
        lastRequestTimes[orderId] = currentTime
        
        Log.d("OrderStatusActivity", "Fetching status for orderId: $orderId, referenceNumber: $referenceNumber")
        
        // Get the JWT token from the SessionManager
        val jwtToken = sessionManager.getToken()
        
        var bearerToken = if (!jwtToken.isNullOrBlank()) {
            if (jwtToken.startsWith("Bearer ")) jwtToken else "Bearer $jwtToken"
        } else null
        
        if (bearerToken != null) {
            Log.d("OrderStatusActivity", "Using JWT token from SessionManager")
        } else {
            Log.e("OrderStatusActivity", "No JWT token available from SessionManager - this will likely cause a 403 error")
            
            // Fall back to SharedPreferences if SessionManager doesn't have the token
            val fallbackToken = sharedPreferences.getString("jwt_token", null) 
                ?: sharedPreferences.getString("auth_token", null)
                ?: sharedPreferences.getString("token", null)
                
            if (fallbackToken != null) {
                Log.d("OrderStatusActivity", "Found fallback token in SharedPreferences")
                bearerToken = if (fallbackToken.startsWith("Bearer ")) fallbackToken else "Bearer $fallbackToken"
            } else {
                // List available keys for debugging
                val allKeys = sharedPreferences.all.keys
                Log.d("OrderStatusActivity", "Available SharedPreferences keys: $allKeys")
                
                // DEBUGGING ONLY: Try a hardcoded token to see if it helps with the 403 error
                // REMOVE THIS IN PRODUCTION
                bearerToken = "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VySWQiOiI2NTkzZmRlOTg3Mjc4ZWNkYTVjNWRmNmUiLCJlbWFpbCI6InVzZXJAZXhhbXBsZS5jb20iLCJyb2xlIjoiQ1VTVE9NRVIiLCJpYXQiOjE3MjA0OTk4NTAsImV4cCI6MTcyMDUwMzQ1MH0.uoRqcwx5WKbK5XLQPEyOlw3WFnqxIJ8MlGJLSfm0zJo"
                Log.d("OrderStatusActivity", "*** USING DEBUG TOKEN FOR TESTING - REMOVE IN PRODUCTION ***")
            }
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // First try with order ID
                var response = if (bearerToken != null) {
                    apiService.getPaymentByOrderId(orderId, bearerToken)
                } else {
                    // This will likely fail with 403 Forbidden if JWT is required
                    apiService.getPaymentByOrderId(orderId)
                }
                
                // If first attempt fails, try with reference number
                if (!response.isSuccessful) {
                    Log.d("OrderStatusActivity", "First attempt failed with code ${response.code()}, trying with reference number")
                    response = if (bearerToken != null) {
                        apiService.getPaymentByOrderId(referenceNumber, bearerToken)
                    } else {
                        apiService.getPaymentByOrderId(referenceNumber)
                    }
                }
                
                withContext(Dispatchers.Main) {
                    processApiResponse(response, bearerToken)
                }
            } catch (e: Exception) {
                Log.e("OrderStatusActivity", "Error fetching status", e)
            }
        }
    }
    
    private suspend fun processApiResponse(response: Response<PaymentResponse>, bearerToken: String?) {
        if (response.isSuccessful && response.body() != null) {
            val paymentResponse = response.body()!!
            Log.d("OrderStatusActivity", "API Response: ${response.code()}")
            Log.d("OrderStatusActivity", "Fetched payment: id=${paymentResponse.id}, orderId=${paymentResponse.orderId}")
            Log.d("OrderStatusActivity", "Fetched status: ${paymentResponse.jobOrderStatus}, status: ${paymentResponse.status}")
            Log.d("OrderStatusActivity", "All payment data: $paymentResponse")
            
            // Check if we should use jobOrderStatus or regular status
            val effectiveStatus = paymentResponse.jobOrderStatus.takeIf { status -> status.isNotBlank() } ?: paymentResponse.status
            
            // Only update UI if status has changed
            if (lastKnownStatus != effectiveStatus) {
                Log.d("OrderStatusActivity", "Status change detected: $lastKnownStatus -> $effectiveStatus")
                // Update UI with the latest status
                updateOrderStatus(effectiveStatus, paymentResponse)
                
                // Update order details from API response
                updateOrderDetails(paymentResponse)
                
                // Hide cancel button if status is not Processing
                if (effectiveStatus != "Processing") {
                    btnCancel.visibility = View.GONE
                }
            } else {
                Log.d("OrderStatusActivity", "Status unchanged, skipping UI update: $effectiveStatus")
            }
        } else {
            Log.e("OrderStatusActivity", "Failed to fetch status: ${response.code()} - ${response.message()}")
            try {
                val errorBody = response.errorBody()?.string() ?: "No error body"
                Log.e("OrderStatusActivity", "Error body: $errorBody")
                
                // If we get a 403 Forbidden error, log additional debugging info but don't show a toast
                if (response.code() == 403) {
                    // For debugging, check if a hardcoded token works
                    if (bearerToken?.startsWith("Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9") == true) {
                        Log.d("OrderStatusActivity", "Used hardcoded token but still got 403 - backend may require a valid token")
                    } else {
                        Log.d("OrderStatusActivity", "Not using debug token")
                    }
                }
            } catch (e: Exception) {
                Log.e("OrderStatusActivity", "Error reading error body", e)
            }
        }
    }

    private fun navigateToHome() {
        val intent = Intent(this, HomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        startActivity(intent)
    }

    private fun navigateToPickup() {
        val intent = Intent(this, OrderPickupActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        startActivity(intent)
    }

    // Add animation utils with more subtle parameters
    private fun animateColorChange(view: View, fromColor: Int, toColor: Int, duration: Long = 800) {
        val colorAnimation = ValueAnimator.ofObject(ArgbEvaluator(), fromColor, toColor)
        colorAnimation.duration = duration
        colorAnimation.addUpdateListener { animator ->
            view.setBackgroundColor(animator.animatedValue as Int)
        }
        colorAnimation.start()
    }
    
    private fun animateIconColorFilter(icon: ImageView, fromColor: Int, toColor: Int, duration: Long = 800) {
        val colorAnimation = ValueAnimator.ofObject(ArgbEvaluator(), fromColor, toColor)
        colorAnimation.duration = duration
        colorAnimation.addUpdateListener { animator ->
            icon.setColorFilter(animator.animatedValue as Int)
        }
        colorAnimation.start()
    }
    
    private fun pulseAnimation(view: View) {
        // More subtle pulse with smaller scale change
        view.animate()
            .scaleX(1.1f)
            .scaleY(1.1f)
            .setDuration(400)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                view.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(300)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
            .start()
    }
    
    private fun showStatusChangeNotification(newStatus: String) {
        // Visual feedback for status change - only animate if there's an actual status change
        runOnUiThread {
            // Find the active icon to animate
            val iconToAnimate = when (newStatus) {
                "Processing", "Available" -> null // No icon to animate for processing
                "Accepted" -> iconDriverAssigned
                "In-Progress" -> iconPickup
                "Completed" -> iconCompleted
                else -> null
            }
            
            // Apply a subtle pulse animation to the icon - only if we're not in the initial load
            if (lastKnownStatus != null && iconToAnimate != null) {
                pulseAnimation(iconToAnimate)
            }
        }
    }

    private fun updateOrderStatus(jobOrderStatus: String, paymentResponse: PaymentResponse? = null) {
        Log.d("OrderStatusActivity", "Updating UI status to: $jobOrderStatus")
        
        // Detect status change and provide visual feedback, but only if this isn't the first load
        val isStatusChange = lastKnownStatus != null && lastKnownStatus != jobOrderStatus
        if (isStatusChange) {
            showStatusChangeNotification(jobOrderStatus)
        }
        
        // Store current status for next comparison
        lastKnownStatus = jobOrderStatus
        
        val greenColor = ContextCompat.getColor(this, R.color.green)
        val greyColor = ContextCompat.getColor(this, R.color.stroke_color)

        when (jobOrderStatus) {
            "Processing", "Available" -> {
                tvStatusHeader.text = "Processing"
                tvStatusDescription.text = "Waiting for driver to accept order"
                
                // If this is initial load, just set colors without animation
                if (!isStatusChange) {
                    iconDriverAssigned.setColorFilter(greyColor)
                    iconPickup.setColorFilter(greyColor)
                    iconCompleted.setColorFilter(greyColor)
                    
                    progressLine1.setBackgroundColor(greyColor)
                    progressLine2.setBackgroundColor(greyColor)
                    progressLine3.setBackgroundColor(greyColor)
                } else {
                    // All icons and lines should be grey - with animation for changes
                    animateIconColorFilter(iconDriverAssigned, greenColor, greyColor)
                    animateIconColorFilter(iconPickup, greenColor, greyColor)
                    animateIconColorFilter(iconCompleted, greenColor, greyColor)
                    
                    animateColorChange(progressLine1, greenColor, greyColor)
                    animateColorChange(progressLine2, greenColor, greyColor)
                    animateColorChange(progressLine3, greenColor, greyColor)
                }
            }
            "Accepted" -> {
                // Get estimated arrival time from backend if available
                // If not, calculate a reasonable estimate (e.g., 30 minutes from now)
                val estimatedTime = if (paymentResponse != null) {
                    val arrivalTime = paymentResponse.getEffectiveEstimatedArrival()
                    if (arrivalTime != null) {
                        formatEstimatedTime(arrivalTime)
                    } else {
                        // Default to a 30-minute estimate if no time provided
                        val calendar = Calendar.getInstance()
                        calendar.add(Calendar.MINUTE, 30)
                        formatEstimatedTime(calendar.time)
                    }
                } else {
                    // Default to a 30-minute estimate if no payment response
                    val calendar = Calendar.getInstance()
                    calendar.add(Calendar.MINUTE, 30)
                    formatEstimatedTime(calendar.time)
                }
                
                tvStatusHeader.text = "Arriving by $estimatedTime"
                tvStatusDescription.text = "We've got your pickup order!"
                
                // If this is initial load, just set colors without animation
                if (!isStatusChange) {
                    iconDriverAssigned.setColorFilter(greenColor)
                    progressLine1.setBackgroundColor(greenColor)
                    iconPickup.setColorFilter(greenColor)
                    iconCompleted.setColorFilter(greyColor)
                    progressLine2.setBackgroundColor(greyColor)
                    progressLine3.setBackgroundColor(greyColor)
                } else {
                    // Animate only if there's a status change
                    // Current icon is green
                    animateIconColorFilter(iconDriverAssigned, greyColor, greenColor)
                    // Current icon's line is green
                    animateColorChange(progressLine1, greyColor, greenColor)
                    // Next icon is green but its line is not
                    animateIconColorFilter(iconPickup, greyColor, greenColor)
                    // Make sure other items are grey
                    animateIconColorFilter(iconCompleted, greenColor, greyColor)
                    animateColorChange(progressLine2, greenColor, greyColor)
                    animateColorChange(progressLine3, greenColor, greyColor)
                }
            }
            "In-Progress" -> {
                tvStatusHeader.text = "Pickup in Progress"
                tvStatusDescription.text = "Driver is on the way to your location."
                
                // If this is initial load, just set colors without animation
                if (!isStatusChange) {
                    iconDriverAssigned.setColorFilter(greenColor)
                    progressLine1.setBackgroundColor(greenColor)
                    iconPickup.setColorFilter(greenColor)
                    progressLine2.setBackgroundColor(greenColor)
                    iconCompleted.setColorFilter(greenColor)
                    progressLine3.setBackgroundColor(greyColor)
                } else {
                    // Animate only if there's a status change
                    // Previous icon and its line stay green
                    animateIconColorFilter(iconDriverAssigned, greyColor, greenColor)
                    animateColorChange(progressLine1, greyColor, greenColor)
                    
                    // Current icon is green
                    animateIconColorFilter(iconPickup, greyColor, greenColor)
                    // Current icon's line is green
                    animateColorChange(progressLine2, greyColor, greenColor)
                    
                    // Next icon is green but its line is not
                    animateIconColorFilter(iconCompleted, greyColor, greenColor)
                    // Last line stays grey
                    animateColorChange(progressLine3, greenColor, greyColor)
                }
            }
            "Completed" -> {
                tvStatusHeader.text = "Completed"
                tvStatusDescription.text = "Your trash has been picked up"
                
                // If this is initial load, just set colors without animation
                if (!isStatusChange) {
                    iconDriverAssigned.setColorFilter(greenColor)
                    progressLine1.setBackgroundColor(greenColor)
                    iconPickup.setColorFilter(greenColor)
                    progressLine2.setBackgroundColor(greenColor)
                    iconCompleted.setColorFilter(greenColor)
                    progressLine3.setBackgroundColor(greenColor)
                } else {
                    // Animate only if there's a status change
                    // All previous icons and lines stay green
                    animateIconColorFilter(iconDriverAssigned, greyColor, greenColor)
                    animateColorChange(progressLine1, greyColor, greenColor)
                    animateIconColorFilter(iconPickup, greyColor, greenColor)
                    animateColorChange(progressLine2, greyColor, greenColor)
                    
                    // Current icon is green
                    animateIconColorFilter(iconCompleted, greyColor, greenColor)
                    // Current icon's line is green
                    animateColorChange(progressLine3, greyColor, greenColor)
                }
            }
            "Cancelled" -> {
                tvStatusHeader.text = "Cancelled"
                tvStatusDescription.text = "This order has been cancelled"
                
                // If this is initial load, just set colors without animation
                if (!isStatusChange) {
                    iconDriverAssigned.setColorFilter(greyColor)
                    progressLine1.setBackgroundColor(greyColor)
                    iconPickup.setColorFilter(greyColor)
                    progressLine2.setBackgroundColor(greyColor)
                    iconCompleted.setColorFilter(greyColor)
                    progressLine3.setBackgroundColor(greyColor)
                } else {
                    // All icons and lines revert to grey
                    animateIconColorFilter(iconDriverAssigned, greenColor, greyColor)
                    animateIconColorFilter(iconPickup, greenColor, greyColor)
                    animateIconColorFilter(iconCompleted, greenColor, greyColor)
                    
                    animateColorChange(progressLine1, greenColor, greyColor)
                    animateColorChange(progressLine2, greenColor, greyColor)
                    animateColorChange(progressLine3, greenColor, greyColor)
                }
                
                btnCancel.visibility = View.GONE
            }
            else -> {
                // Handle unknown status
                tvStatusHeader.text = "Unknown Status: $jobOrderStatus"
                tvStatusDescription.text = "An unknown status was received."
            }
        }
    }
    
    private fun formatEstimatedTime(date: Date): String {
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        return timeFormat.format(date)
    }

    private fun cancelOrder() {
        // Get the JWT token from the SessionManager
        val jwtToken = sessionManager.getToken()
        
        if (jwtToken.isNullOrBlank()) {
            Log.e("OrderStatusActivity", "No JWT token available to cancel order")
            return
        }
        
        val bearerToken = if (jwtToken.startsWith("Bearer ")) jwtToken else "Bearer $jwtToken"
        
        // Show loading state
        btnCancel.isEnabled = false
        btnCancel.text = "Cancelling..."
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // First get the payment ID for this order
                val response = apiService.getPaymentByOrderId(order.id, bearerToken)
                
                if (response.isSuccessful && response.body() != null) {
                    val paymentResponse = response.body()!!
                    val paymentId = paymentResponse.id
                    
                    // Now update the job order status to "Cancelled"
                    val statusUpdate = JobOrderStatusUpdate("Cancelled")
                    val updateResponse = apiService.updateJobOrderStatus(paymentId, statusUpdate, bearerToken)
                    
                    withContext(Dispatchers.Main) {
                        if (updateResponse.isSuccessful) {
                            // Update UI to show cancelled state
                            updateOrderStatus("Cancelled")
                            btnCancel.visibility = View.GONE
                            
                            // Stop polling when cancelled
                            stopStatusPolling()
                        } else {
                            Log.e("OrderStatusActivity", "Failed to cancel order: ${updateResponse.code()} - ${updateResponse.message()}")
                            // Show error and reset button
                            btnCancel.isEnabled = true
                            btnCancel.text = "CANCEL ORDER"
                        }
                    }
                } else {
                    Log.e("OrderStatusActivity", "Failed to get payment details: ${response.code()} - ${response.message()}")
                    withContext(Dispatchers.Main) {
                        // Show error and reset button
                        btnCancel.isEnabled = true
                        btnCancel.text = "CANCEL ORDER"
                    }
                }
            } catch (e: Exception) {
                Log.e("OrderStatusActivity", "Error cancelling order", e)
                withContext(Dispatchers.Main) {
                    // Show error and reset button
                    btnCancel.isEnabled = true
                    btnCancel.text = "CANCEL ORDER"
                }
            }
        }
    }

    // Add new method to update order details from API response
    private fun updateOrderDetails(paymentResponse: PaymentResponse) {
        Log.d("OrderStatusActivity", "Updating order details from API: numberOfSacks=${paymentResponse.numberOfSacks}, wasteType=${paymentResponse.wasteType}")
        
        // Update number of sacks if it's provided in the API response
        if (paymentResponse.numberOfSacks > 0) {
            runOnUiThread {
                tvSacks.text = paymentResponse.numberOfSacks.toString()
                Log.d("OrderStatusActivity", "Updated sacks display to: ${paymentResponse.numberOfSacks}")
            }
        }
        
        // Update waste type if it's provided in the API response
        if (!paymentResponse.wasteType.isNullOrBlank()) {
            runOnUiThread {
                tvWasteType.text = paymentResponse.wasteType
                Log.d("OrderStatusActivity", "Updated waste type display to: ${paymentResponse.wasteType}")
            }
        }
        
        // Update truck size if it's provided in the API response
        if (!paymentResponse.truckSize.isNullOrBlank()) {
            runOnUiThread {
                tvTruckSize.text = paymentResponse.truckSize
                Log.d("OrderStatusActivity", "Updated truck size display to: ${paymentResponse.truckSize}")
            }
        }
    }
}