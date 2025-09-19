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
import androidx.cardview.widget.CardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import android.util.Log
import android.os.CountDownTimer
import android.widget.LinearLayout
import com.example.ecotrack.models.payment.PaymentResponse
import com.example.ecotrack.ui.pickup.OrderStatusActivity
import com.example.ecotrack.ui.pickup.model.PickupOrder
import com.example.ecotrack.utils.ApiService
import com.example.ecotrack.utils.FileLuService
import com.example.ecotrack.utils.ProfileImageLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.ecotrack.databinding.ActivityHomeBinding
import retrofit2.Response
import androidx.core.content.ContextCompat

class HomeActivity : BaseActivity() {

    private lateinit var binding: ActivityHomeBinding
    private val apiService = ApiService.create()
    private val fileLuService = FileLuService(this)
    private val profileImageLoader = ProfileImageLoader(this)
    private lateinit var welcomeText: TextView
    private lateinit var timeRemainingText: TextView
    private lateinit var reminderCard: CardView
    private lateinit var viewAllText: TextView
    private lateinit var reminderTitle: TextView
    private var countDownTimer: CountDownTimer? = null
    private val TAG = "HomeActivity"
    private var activeOrderId: String? = null
    
    // Add variables for caching and rate limiting
    private var lastOrderCheckTime = 0L
    private val MIN_CHECK_INTERVAL = 60000L // 1 minute interval between API checks
    private var cachedActiveOrders: List<PaymentResponse>? = null

    // This container will hold additional order cards
    private lateinit var additionalCardsContainer: LinearLayout
    private val activeOrderCards = mutableListOf<CardView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // If we have a cached profile image URL, show it immediately
        try {
            val cachedUrl = sessionManager.getProfileImageUrl()
            if (!cachedUrl.isNullOrBlank()) {
                com.bumptech.glide.Glide.with(this)
                    .load(cachedUrl)
                    .placeholder(R.drawable.raph)
                    .error(R.drawable.raph)
                    .into(binding.profileImage)
            }
        } catch (_: Exception) {}

        // Check if user has customer role before proceeding
        validateCustomerAccount()

        // Set click listeners for navigation
        setupNavigation()

        // Parent class BaseActivity already handles session checks
        supportActionBar?.hide()

        initializeViews()
        checkForActiveOrders()
        loadUserData()
        
        // Setup SwipeRefreshLayout for manual refresh
        binding.swipeRefreshLayout.setOnRefreshListener {
            Log.d(TAG, "Manual refresh triggered")
            checkForActiveOrders(isManualRefresh = true)
        }
    }

    private fun initializeViews() {
        welcomeText = binding.welcomeText
        timeRemainingText = binding.timeRemaining
        reminderCard = binding.reminderCard
        viewAllText = binding.viewAll
        reminderTitle = binding.reminderTitle
        additionalCardsContainer = binding.additionalCardsContainer
        
        // Hide the reminder card initially until we check for active orders
        reminderCard.visibility = View.GONE
        
        // Set click listener for "View all" text to navigate to OrderStatusActivity
        viewAllText.setOnClickListener {
            if (activeOrderId != null) {
                // Navigate to OrderStatusActivity with the active order
                navigateToOrderStatus(activeOrderId!!)
            }
        }
    }

    private fun setupNavigation() {
        // Setup navigation, profile button, etc.
        binding.profileImage?.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        // Setup navigation to Map when Location is clicked
        binding.pointsNav?.setOnClickListener {
            startActivity(Intent(this, MapActivity::class.java))
        }

        // Setup navigation to Schedule when Schedule is clicked
        binding.scheduleNav?.setOnClickListener {
            startActivity(Intent(this, ScheduleActivity::class.java))
        }

        // Setup navigation to Order Pickup when Order Pickup is clicked
        binding.pickupNav?.setOnClickListener {
            // Navigate to OrderPickupActivity
            startActivity(Intent(this, com.example.ecotrack.ui.pickup.OrderPickupActivity::class.java))
        }
    }

    private fun checkForActiveOrders(isManualRefresh: Boolean = false) {
        val token = sessionManager.getToken()
        val userId = sessionManager.getUserId()

        if (token == null || userId == null) {
            Log.e(TAG, "Missing credentials - token: $token, userId: $userId")
            reminderCard.visibility = View.GONE
            clearAdditionalOrderCards()
            binding.swipeRefreshLayout.isRefreshing = false
            return
        }
        
        // Skip if we recently checked and this is not a manual refresh
        val currentTime = System.currentTimeMillis()
        if (!isManualRefresh && currentTime - lastOrderCheckTime < MIN_CHECK_INTERVAL && cachedActiveOrders != null) {
            Log.d(TAG, "Using cached active orders - last check was ${currentTime - lastOrderCheckTime}ms ago")
            // Use cached data
            cachedActiveOrders?.let { orders ->
                CoroutineScope(Dispatchers.IO).launch {
                    handleCachedOrders(orders)
                }
            }
            binding.swipeRefreshLayout.isRefreshing = false
            return
        }
        
        // Update the last check time
        lastOrderCheckTime = currentTime

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Get the proper bearer token format
                val bearerToken = if (token.startsWith("Bearer ")) token else "Bearer $token"
                
                // Call API to get user's active orders
                Log.d(TAG, "Fetching active orders for user: $userId")
                val response = apiService.getUserActiveOrders(userId, bearerToken)
                
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful && !response.body().isNullOrEmpty()) {
                        // Cache the response
                        cachedActiveOrders = response.body()
                    }
                    handleActiveOrdersResponse(response)
                    binding.swipeRefreshLayout.isRefreshing = false
                }
                
                // If the first endpoint fails or returns no results, try a fallback
                if (!response.isSuccessful || response.body().isNullOrEmpty()) {
                    Log.d(TAG, "Primary endpoint returned no results, trying fallback")
                    
                    // Get all customer orders and filter locally
                    val fallbackResponse = apiService.getPaymentsByCustomerEmail(
                        sessionManager.getUserEmail() ?: "",
                        bearerToken
                    )
                    
                    withContext(Dispatchers.Main) {
                        if (fallbackResponse.isSuccessful && !fallbackResponse.body().isNullOrEmpty()) {
                            // Cache the fallback response
                            cachedActiveOrders = fallbackResponse.body()
                        }
                        handleFallbackResponse(fallbackResponse)
                        binding.swipeRefreshLayout.isRefreshing = false
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking for active orders", e)
                withContext(Dispatchers.Main) {
                    // Use cached data if available when there's an error
                    if (cachedActiveOrders != null) {
                        Log.d(TAG, "Using cached data due to network error")
                        CoroutineScope(Dispatchers.Main).launch {
                            handleCachedOrders(cachedActiveOrders!!)
                        }
                    } else {
                        reminderCard.visibility = View.GONE
                        clearAdditionalOrderCards()
                    }
                    binding.swipeRefreshLayout.isRefreshing = false
                    
                    if (isManualRefresh) {
                        Toast.makeText(
                            this@HomeActivity,
                            "Error refreshing: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }
    
    private suspend fun handleActiveOrdersResponse(response: Response<List<PaymentResponse>>) {
        if (response.isSuccessful) {
            val orders = response.body()
            Log.d(TAG, "API response successful. Got ${orders?.size ?: 0} orders")
            
            if (!orders.isNullOrEmpty()) {
                // Log all orders for debugging
                orders.forEachIndexed { index, order ->
                    Log.d(TAG, "Order $index - orderId: ${order.orderId}, jobOrderStatus: '${order.jobOrderStatus}', status: '${order.status}'")
                }
                
                // Find orders with status "Available", "Accepted", or "In-Progress"
                val activeOrders = orders.filter { order -> 
                    val jobOrderStatus = order.jobOrderStatus?.trim()?.lowercase() ?: ""
                    val regularStatus = order.status?.trim()?.lowercase() ?: ""
                    
                    Log.d(TAG, "Detailed status check for order ${order.orderId}:")
                    Log.d(TAG, "  - jobOrderStatus: '$jobOrderStatus'")
                    Log.d(TAG, "  - regularStatus: '$regularStatus'")
                    
                    // Check both status fields with different formats
                    val isActiveJobOrder = jobOrderStatus == "available" || 
                                        jobOrderStatus == "accepted" || 
                                        jobOrderStatus == "in-progress" ||
                                        jobOrderStatus == "in progress" ||
                                        jobOrderStatus.contains("avail") ||
                                        jobOrderStatus.contains("accept") ||
                                        jobOrderStatus.contains("progress")
                                        
                    val isActiveRegular = regularStatus == "available" || 
                                       regularStatus == "accepted" || 
                                       regularStatus == "in-progress" ||
                                       regularStatus == "in progress" ||
                                       regularStatus.contains("avail") ||
                                       regularStatus.contains("accept") ||
                                       regularStatus.contains("progress")
                    
                    val isActive = isActiveJobOrder || isActiveRegular
                    
                    Log.d(TAG, "  - isActiveJobOrder: $isActiveJobOrder")
                    Log.d(TAG, "  - isActiveRegular: $isActiveRegular")
                    Log.d(TAG, "  - Final isActive: $isActive")
                    
                    isActive
                }
                
                Log.d(TAG, "Found ${activeOrders.size} active orders")
                
                if (activeOrders.isNotEmpty()) {
                    // Get the first active order for the main card
                    val firstOrder = activeOrders.first()
                    activeOrderId = firstOrder.orderId
                    
                    Log.d(TAG, "Using primary active order with ID: $activeOrderId and status: ${firstOrder.jobOrderStatus}")
                    
                    // Clear any existing reminder cards first
                    withContext(Dispatchers.Main) {
                        // Show the main reminder card for the first active order
                        updateReminderCard(firstOrder)
                        CoroutineScope(Dispatchers.Main).launch {
                            reminderCard.visibility = View.VISIBLE
                        }
                        
                        // If there are multiple active orders, create additional cards for each
                        if (activeOrders.size > 1) {
                            Log.d(TAG, "Creating ${activeOrders.size - 1} additional cards")
                            // Create and display additional reminder cards for each active order
                            displayAdditionalOrderCards(activeOrders.drop(1))
                        } else {
                            Log.d(TAG, "No additional active orders, clearing any existing additional cards")
                            // If there's only one order, make sure any additional cards are removed
                            CoroutineScope(Dispatchers.Main).launch {
                                clearAdditionalOrderCards()
                            }
                        }
                    }
                    
                    // Start countdown timer based on the first active order
                    val effectiveStatus = firstOrder.jobOrderStatus.takeIf { it.isNotBlank() } ?: firstOrder.status
                    when (effectiveStatus.trim().lowercase()) {
                        "accepted" -> {
                            // Show estimated arrival time if available
                            val estimatedArrival = firstOrder.getEffectiveEstimatedArrival()
                            if (estimatedArrival != null) {
                                CoroutineScope(Dispatchers.Main).launch {
                                    startCountdownToTime(estimatedArrival.time)
                                }
                            } else {
                                // Default to 30 min countdown
                                CoroutineScope(Dispatchers.Main).launch {
                                    startCountdownTimer()
                                }
                            }
                        }
                        else -> {
                            // Default countdown for other statuses
                            CoroutineScope(Dispatchers.Main).launch {
                                startCountdownTimer()
                            }
                        }
                    }
                } else {
                    Log.d(TAG, "No active orders found after filtering")
                    withContext(Dispatchers.Main) {
                        reminderCard.visibility = View.GONE
                        clearAdditionalOrderCards()
                    }
                    CoroutineScope(Dispatchers.Main).launch {
                        stopCountdownTimer()
                    }
                }
            } else {
                // No orders found
                Log.d(TAG, "No orders found in API response")
                withContext(Dispatchers.Main) {
                    reminderCard.visibility = View.GONE
                    clearAdditionalOrderCards()
                }
                CoroutineScope(Dispatchers.Main).launch {
                    stopCountdownTimer()
                }
            }
        } else {
            // API call not successful
            Log.e(TAG, "API call failed with error code: ${response.code()}")
            Log.e(TAG, "Error message: ${response.message()}")
            
            try {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "Error body: $errorBody")
            } catch (e: Exception) {
                Log.e(TAG, "Error reading error body", e)
            }
            
            withContext(Dispatchers.Main) {
                reminderCard.visibility = View.GONE
                clearAdditionalOrderCards()
            }
            CoroutineScope(Dispatchers.Main).launch {
                stopCountdownTimer()
            }
        }
    }
    
    private suspend fun handleFallbackResponse(response: Response<List<PaymentResponse>>) {
        if (response.isSuccessful && !response.body().isNullOrEmpty()) {
            val orders = response.body()!!
            
            Log.d(TAG, "Fallback response successful with ${orders.size} orders")
            
            // Log all orders
            orders.forEachIndexed { index, order ->
                Log.d(TAG, "Fallback Order $index - orderId: ${order.orderId}, jobOrderStatus: '${order.jobOrderStatus}', status: '${order.status}'")
            }
            
            // Filter for active orders manually
            val activeOrders = orders.filter { order ->
                val jobOrderStatus = order.jobOrderStatus?.trim()?.lowercase() ?: ""
                val regularStatus = order.status?.trim()?.lowercase() ?: ""
                
                Log.d(TAG, "Fallback detailed status check for order ${order.orderId}:")
                Log.d(TAG, "  - jobOrderStatus: '$jobOrderStatus'")
                Log.d(TAG, "  - regularStatus: '$regularStatus'")
                
                // Check both status fields with different formats
                val isActiveJobOrder = jobOrderStatus == "available" || 
                                    jobOrderStatus == "accepted" || 
                                    jobOrderStatus == "in-progress" ||
                                    jobOrderStatus == "in progress" ||
                                    jobOrderStatus.contains("avail") ||
                                    jobOrderStatus.contains("accept") ||
                                    jobOrderStatus.contains("progress")
                                    
                val isActiveRegular = regularStatus == "available" || 
                                   regularStatus == "accepted" || 
                                   regularStatus == "in-progress" ||
                                   regularStatus == "in progress" ||
                                   regularStatus.contains("avail") ||
                                   regularStatus.contains("accept") ||
                                   regularStatus.contains("progress")
                
                val isActive = isActiveJobOrder || isActiveRegular
                
                Log.d(TAG, "  - isActiveJobOrder: $isActiveJobOrder")
                Log.d(TAG, "  - isActiveRegular: $isActiveRegular")
                Log.d(TAG, "  - Final fallback isActive: $isActive")
                
                isActive
            }
            
            Log.d(TAG, "Found ${activeOrders.size} active orders from fallback")
            
            if (activeOrders.isNotEmpty()) {
                // Get the first active order for the main card
                val firstOrder = activeOrders.first()
                activeOrderId = firstOrder.orderId
                
                Log.d(TAG, "Using primary fallback order with ID: $activeOrderId and status: ${firstOrder.jobOrderStatus ?: firstOrder.status}")
                
                withContext(Dispatchers.Main) {
                    // Update the main reminder card
                    updateReminderCard(firstOrder)
                    CoroutineScope(Dispatchers.Main).launch {
                        reminderCard.visibility = View.VISIBLE
                    }
                    
                    // If there are multiple active orders, create additional cards
                    if (activeOrders.size > 1) {
                        Log.d(TAG, "Creating ${activeOrders.size - 1} additional cards from fallback")
                        displayAdditionalOrderCards(activeOrders.drop(1))
                    } else {
                        Log.d(TAG, "No additional active orders from fallback, clearing any existing additional cards")
                        clearAdditionalOrderCards()
                    }
                }
                
                // Set countdown based on the first order status
                val estimatedArrival = firstOrder.getEffectiveEstimatedArrival()
                if (estimatedArrival != null) {
                    startCountdownToTime(estimatedArrival.time)
                } else {
                    startCountdownTimer()
                }
            } else {
                withContext(Dispatchers.Main) {
                    reminderCard.visibility = View.GONE
                    clearAdditionalOrderCards()
                }
                CoroutineScope(Dispatchers.Main).launch {
                    stopCountdownTimer()
                }
            }
        } else {
            Log.d(TAG, "Fallback response unsuccessful: ${response.code()}")
            withContext(Dispatchers.Main) {
                reminderCard.visibility = View.GONE
                clearAdditionalOrderCards()
            }
        }
    }
    
    private suspend fun updateReminderCard(order: PaymentResponse) {
        val effectiveStatus = order.jobOrderStatus.takeIf { it.isNotBlank() } ?: order.status
        Log.d(TAG, "Updating reminder card with status: '$effectiveStatus'")
        
        // Update the reminder title based on status (case-insensitive)
        val statusToCheck = effectiveStatus.trim().lowercase()
        
        // Use withContext in a suspend function
        withContext(Dispatchers.Main) {
            // Set title based on status
            val titleText = when {
                statusToCheck.contains("avail") -> "PICKUP REQUESTED"
                statusToCheck.contains("accept") -> "DRIVER ON THE WAY"
                statusToCheck.contains("progress") -> "PICKUP IN PROGRESS"
                else -> "TRUCK REMINDER"
            }
            Log.d(TAG, "Setting main reminder title to: $titleText for status: $statusToCheck")
            reminderTitle.text = titleText
            
            // Save the active order ID
            activeOrderId = order.orderId
            Log.d(TAG, "Active order ID set to: $activeOrderId")
        }
    }
    
    private fun navigateToOrderStatus(orderId: String) {
        // First we need to get the order details from the API
        val token = sessionManager.getToken() ?: return
        val bearerToken = if (token.startsWith("Bearer ")) token else "Bearer $token"
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = apiService.getPaymentByOrderId(orderId, bearerToken)
                
                if (response.isSuccessful && response.body() != null) {
                    val paymentResponse = response.body()!!
                    
                    // Map the payment method string to the appropriate enum value
                    val paymentMethod = when (paymentResponse.paymentMethod.uppercase()) {
                        "GCASH" -> com.example.ecotrack.ui.pickup.model.PaymentMethod.GCASH
                        "CASH ON HAND" -> com.example.ecotrack.ui.pickup.model.PaymentMethod.CASH_ON_HAND
                        "CREDIT CARD" -> com.example.ecotrack.ui.pickup.model.PaymentMethod.CREDIT_CARD
                        "PAYMAYA" -> com.example.ecotrack.ui.pickup.model.PaymentMethod.PAYMAYA
                        "GRABPAY" -> com.example.ecotrack.ui.pickup.model.PaymentMethod.GRABPAY
                        "BANK TRANSFER" -> com.example.ecotrack.ui.pickup.model.PaymentMethod.BANK_TRANSFER
                        "OVER THE COUNTER" -> com.example.ecotrack.ui.pickup.model.PaymentMethod.OTC
                        "XENDIT PAYMENT GATEWAY" -> com.example.ecotrack.ui.pickup.model.PaymentMethod.XENDIT_PAYMENT_GATEWAY
                        else -> com.example.ecotrack.ui.pickup.model.PaymentMethod.CASH_ON_HAND // Default fallback
                    }
                    
                    Log.d(TAG, "Payment method from API: ${paymentResponse.paymentMethod}, mapped to enum: ${paymentMethod.name}")

                    // Map the waste type string to the appropriate enum value
                    val wasteType = try {
                        com.example.ecotrack.ui.pickup.model.WasteType.valueOf(paymentResponse.wasteType?.uppercase() ?: "MIXED")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing waste type: ${paymentResponse.wasteType}", e)
                        com.example.ecotrack.ui.pickup.model.WasteType.MIXED
                    }
                    
                    // Create truck information if available
                    val truck = if (!paymentResponse.truckId.isNullOrBlank() || !paymentResponse.truckSize.isNullOrBlank()) {
                        com.example.ecotrack.models.Truck(
                            truckId = paymentResponse.truckId ?: "truck_${paymentResponse.orderId}", // Use actual truckId if available
                            size = paymentResponse.truckSize ?: "MEDIUM",
                            wasteType = paymentResponse.wasteType ?: "MIXED",
                            status = "ACTIVE",
                            make = paymentResponse.truckMake ?: paymentResponse.truckSize ?: "EcoTrack",
                            model = paymentResponse.truckModel ?: "Standard",
                            plateNumber = paymentResponse.plateNumber ?: "ECO-${paymentResponse.orderId.takeLast(4)}",
                            truckPrice = paymentResponse.amount ?: 0.0,
                            createdAt = paymentResponse.createdAt.toString()
                        )
                    } else {
                        null
                    }
                    
                    // Create a PickupOrder object from the PaymentResponse
                    val pickupOrder = PickupOrder(
                        id = paymentResponse.orderId,
                        referenceNumber = paymentResponse.paymentReference,
                        fullName = paymentResponse.customerName ?: "",
                        email = paymentResponse.customerEmail ?: "",
                        address = paymentResponse.address ?: "",
                        latitude = paymentResponse.latitude ?: 0.0,
                        longitude = paymentResponse.longitude ?: 0.0,
                        amount = paymentResponse.amount ?: 0.0,
                        tax = 0.0, // Not available in response, defaulting to 0
                        total = paymentResponse.totalAmount ?: 0.0,
                        paymentMethod = paymentMethod,
                        wasteType = wasteType,
                        barangayId = paymentResponse.barangayId ?: "",
                        selectedTruck = truck
                    )
                    
                    withContext(Dispatchers.Main) {
                        // Navigate to order status activity with the order
                        val intent = Intent(this@HomeActivity, OrderStatusActivity::class.java)
                        intent.putExtra("ORDER_DATA", pickupOrder)
                        // Pass along any existing confirmation image URL so it can render immediately
                        intent.putExtra("PROOF_IMAGE_URL", paymentResponse.getEffectiveConfirmationImageUrl())
                        startActivity(intent)
                    }
                } else {
                    Log.e(TAG, "Failed to get order details: ${response.code()}")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@HomeActivity, "Could not retrieve order details", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting order details", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@HomeActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
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

            // For any non-customer role, log out and return to LoginActivity with the appropriate tab preselected
            sessionManager.logout()
            val intent = Intent(this, LoginActivity::class.java)
            if (userType == "driver") {
                intent.putExtra("selectRole", "driver")
            } else if (userType == "customer") {
                intent.putExtra("selectRole", "customer")
            }
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
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

            // For any non-customer role detected in token, force logout and return to Login with role hint
            sessionManager.logout()
            val intent = Intent(this, LoginActivity::class.java)
            if (roleFromToken == "driver") {
                intent.putExtra("selectRole", "driver")
            } else if (roleFromToken == "customer") {
                intent.putExtra("selectRole", "customer")
            }
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
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
                            // Load profile image if available
                            try {
                                val url = it.imageUrl ?: it.profileImage
                                if (!url.isNullOrBlank()) {
                                    loadProfileImage(url)
                                    sessionManager.saveProfileImageUrl(url)
                                }
                            } catch (_: Exception) {}
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
    
    private fun loadProfileImage(url: String) {
        profileImageLoader.loadProfileImageUltraFast(
            url = url,
            imageView = binding.profileImage,
            placeholderResId = R.drawable.raph,
            errorResId = R.drawable.raph
        )
    }

    private fun startCountdownTimer() {
        // Stop any existing timer
        stopCountdownTimer()
        
        // Example: 1 hour countdown
        val totalTimeInMillis = 60 * 60 * 1000L

        countDownTimer = object : CountDownTimer(totalTimeInMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val hours = millisUntilFinished / (60 * 60 * 1000)
                val minutes = (millisUntilFinished % (60 * 60 * 1000)) / (60 * 1000)
                val seconds = (millisUntilFinished % (60 * 1000)) / 1000

                timeRemainingText.text = "${hours}h ${minutes}m ${seconds}s remaining"
            }

            override fun onFinish() {
                timeRemainingText.text = "Time's up!"
                // Check for updated status when timer finishes
                checkForActiveOrders()
            }
        }.start()
    }
    
    private fun startCountdownToTime(targetTimeMillis: Long) {
        // Stop any existing timer
        stopCountdownTimer()
        
        // Calculate time difference
        val currentTimeMillis = System.currentTimeMillis()
        val timeRemaining = targetTimeMillis - currentTimeMillis
        
        // Only start timer if target time is in the future
        if (timeRemaining > 0) {
            countDownTimer = object : CountDownTimer(timeRemaining, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    val hours = millisUntilFinished / (60 * 60 * 1000)
                    val minutes = (millisUntilFinished % (60 * 60 * 1000)) / (60 * 1000)
                    val seconds = (millisUntilFinished % (60 * 1000)) / 1000

                    timeRemainingText.text = "${hours}h ${minutes}m ${seconds}s remaining"
                }

                override fun onFinish() {
                    timeRemainingText.text = "Driver should be arriving"
                    // Check for updated status when timer finishes
                    checkForActiveOrders()
                }
            }.start()
        } else {
            timeRemainingText.text = "Driver should be arriving"
        }
    }
    
    private fun stopCountdownTimer() {
        countDownTimer?.cancel()
        countDownTimer = null
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

        // Check for active orders when returning to this activity - use isManualRefresh=false
        // to allow using cached data if available and recent
        checkForActiveOrders(isManualRefresh = false)
        
        // Only refresh profile data if we've never loaded it or it's been a long time
        if (!::welcomeText.isInitialized || welcomeText.text.isNullOrBlank() || 
            System.currentTimeMillis() - lastOrderCheckTime > MIN_CHECK_INTERVAL * 10) {
            Log.d(TAG, "onResume - refreshing profile data")
            loadUserData()
        } else {
            Log.d(TAG, "onResume - skipping profile data refresh")
        }
    }

    // This is called when the activity is brought back to the foreground
    override fun onRestart() {
        super.onRestart()
        // Check for active orders - use isManualRefresh=false to allow using cached data
        checkForActiveOrders(isManualRefresh = false)
        
        // Only refresh profile data if it's been a long time
        if (System.currentTimeMillis() - lastOrderCheckTime > MIN_CHECK_INTERVAL * 10) {
            Log.d(TAG, "onRestart - refreshing profile data")
            loadUserData()
        } else {
            Log.d(TAG, "onRestart - skipping profile data refresh")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCountdownTimer()
    }

    // Add a helper method to handle possible alternative status values
    private fun isActiveOrderStatus(status: String?): Boolean {
        if (status.isNullOrBlank()) {
            Log.d(TAG, "isActiveOrderStatus: Status is null or blank")
            return false
        }
        
        val normalizedStatus = status.trim().lowercase()
        Log.d(TAG, "isActiveOrderStatus checking normalized status: '$normalizedStatus'")
        
        // Check for standard statuses
        if (normalizedStatus == "available" || 
            normalizedStatus == "accepted" || 
            normalizedStatus == "in-progress" ||
            normalizedStatus == "in progress") {
            Log.d(TAG, "isActiveOrderStatus: Standard active status found: $normalizedStatus")
            return true
        }
        
        // Check for possible alternative status values
        val isAlternativeActive = normalizedStatus == "processing" || 
               normalizedStatus == "pending" ||
               normalizedStatus == "waiting" ||
               normalizedStatus == "pickup" ||
               normalizedStatus == "ongoing" ||
               normalizedStatus.contains("pick") ||
               normalizedStatus.contains("progress") ||
               normalizedStatus.contains("avail") ||
               normalizedStatus.contains("accept")
               
        Log.d(TAG, "isActiveOrderStatus: Alternative status check result: $isAlternativeActive for '$normalizedStatus'")
        return isAlternativeActive
    }

    // Display additional order cards for multiple active orders
    private suspend fun displayAdditionalOrderCards(additionalOrders: List<PaymentResponse>) {
        Log.d(TAG, "displayAdditionalOrderCards called with ${additionalOrders.size} orders")
        
        withContext(Dispatchers.Main) {
            // Clear existing cards first
            clearAdditionalOrderCards()
            
            // Create a new card for each additional active order
            for (order in additionalOrders) {
                Log.d(TAG, "Creating additional card for order: ${order.orderId} with status: ${order.jobOrderStatus ?: order.status}")
                val card = createOrderCard(order)
                additionalCardsContainer.addView(card)
                activeOrderCards.add(card)
                Log.d(TAG, "Added card to container. Current card count: ${activeOrderCards.size}")
            }
            
            // Make sure the container is visible
            additionalCardsContainer.visibility = View.VISIBLE
            Log.d(TAG, "Set additionalCardsContainer to VISIBLE")
        }
    }
    
    // Clear any additional order cards
    private fun clearAdditionalOrderCards() {
        Log.d(TAG, "Clearing ${additionalCardsContainer.childCount} additional order cards")
        additionalCardsContainer.removeAllViews()
        activeOrderCards.clear()
    }
    
    // Create a new CardView for an order
    private fun createOrderCard(order: PaymentResponse): CardView {
        // Get effective status
        val effectiveStatus = order.jobOrderStatus.takeIf { it.isNotBlank() } ?: order.status
        val statusToCheck = effectiveStatus.trim().lowercase()
        
        Log.d(TAG, "Creating card for order ${order.orderId} with status: $statusToCheck")
        
        // Create card layout
        val cardView = CardView(this)
        cardView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(24.dpToPx(), 16.dpToPx(), 24.dpToPx(), 0)
        }
        cardView.radius = 16f.dpToPx().toFloat()
        cardView.setCardBackgroundColor(ContextCompat.getColor(this, R.color.secondary))
        cardView.elevation = 4f.dpToPx().toFloat()
        
        // Create card content layout
        val constraintLayout = androidx.constraintlayout.widget.ConstraintLayout(this)
        constraintLayout.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        constraintLayout.setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
        
        // Create title TextView
        val titleTextView = TextView(this)
        titleTextView.id = View.generateViewId()
        titleTextView.layoutParams = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(
            androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.WRAP_CONTENT,
            androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.WRAP_CONTENT
        )
        titleTextView.setTextColor(ContextCompat.getColor(this, R.color.white))
        titleTextView.textSize = 16f
        
        // Set title based on status
        val titleText = when {
            statusToCheck.contains("avail") -> "PICKUP REQUESTED"
            statusToCheck.contains("accept") -> "DRIVER ON THE WAY"
            statusToCheck.contains("progress") -> "PICKUP IN PROGRESS"
            else -> "TRUCK REMINDER"
        }
        Log.d(TAG, "Setting card title to: $titleText for status: $statusToCheck")
        titleTextView.text = titleText
        
        // Create countdown timer TextView
        val timeRemainingTextView = TextView(this)
        timeRemainingTextView.id = View.generateViewId()
        timeRemainingTextView.layoutParams = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(
            androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.WRAP_CONTENT,
            androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.WRAP_CONTENT
        )
        timeRemainingTextView.text = "0h 59m 55s remaining"
        timeRemainingTextView.setTextColor(ContextCompat.getColor(this, R.color.white))
        timeRemainingTextView.alpha = 0.8f
        timeRemainingTextView.textSize = 14f
        
        // Set drawable for time icon
        val timeDrawable = ContextCompat.getDrawable(this, R.drawable.ic_time)
        timeDrawable?.setTint(ContextCompat.getColor(this, R.color.white))
        timeRemainingTextView.setCompoundDrawablesWithIntrinsicBounds(timeDrawable, null, null, null)
        timeRemainingTextView.compoundDrawablePadding = 8.dpToPx()
        
        // Create View All TextView
        val viewAllTextView = TextView(this)
        viewAllTextView.id = View.generateViewId()
        viewAllTextView.layoutParams = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(
            androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.WRAP_CONTENT,
            androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.WRAP_CONTENT
        )
        viewAllTextView.text = "View all →"
        viewAllTextView.setTextColor(ContextCompat.getColor(this, R.color.white))
        viewAllTextView.textSize = 14f
        
        // Add click listener to View All
        val orderId = order.orderId
        viewAllTextView.setOnClickListener {
            Log.d(TAG, "View all clicked for order: $orderId")
            navigateToOrderStatus(orderId)
        }
        
        // Add views to constraint layout with constraints
        constraintLayout.addView(titleTextView)
        constraintLayout.addView(timeRemainingTextView)
        constraintLayout.addView(viewAllTextView)
        
        // Set constraints for title
        val titleParams = titleTextView.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
        titleParams.startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
        titleParams.topToTop = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
        titleTextView.layoutParams = titleParams
        
        // Set constraints for time remaining
        val timeParams = timeRemainingTextView.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
        timeParams.startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
        timeParams.topToBottom = titleTextView.id
        timeParams.topMargin = 8.dpToPx()
        timeRemainingTextView.layoutParams = timeParams
        
        // Set constraints for view all
        val viewAllParams = viewAllTextView.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
        viewAllParams.endToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
        viewAllParams.topToTop = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
        viewAllParams.bottomToBottom = timeRemainingTextView.id
        viewAllTextView.layoutParams = viewAllParams
        
        // Add constraint layout to card
        cardView.addView(constraintLayout)
        
        Log.d(TAG, "Card created successfully for order: $orderId")
        return cardView
    }
    
    // Extension function to convert dp to pixels
    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }
    
    // Extension function to convert Float dp to pixels
    private fun Float.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    private suspend fun handleCachedOrders(orders: List<PaymentResponse>) {
        // Filter for active orders
        val activeOrders = orders.filter { order -> 
            val jobOrderStatus = order.jobOrderStatus?.trim()?.lowercase() ?: ""
            val regularStatus = order.status?.trim()?.lowercase() ?: ""
            
            // Check both status fields with different formats
            val isActiveJobOrder = jobOrderStatus == "available" || 
                                jobOrderStatus == "accepted" || 
                                jobOrderStatus == "in-progress" ||
                                jobOrderStatus == "in progress" ||
                                jobOrderStatus.contains("avail") ||
                                jobOrderStatus.contains("accept") ||
                                jobOrderStatus.contains("progress")
                                
            val isActiveRegular = regularStatus == "available" || 
                               regularStatus == "accepted" || 
                               regularStatus == "in-progress" ||
                               regularStatus == "in progress" ||
                               regularStatus.contains("avail") ||
                               regularStatus.contains("accept") ||
                               regularStatus.contains("progress")
            
            isActiveJobOrder || isActiveRegular
        }
        
        if (activeOrders.isNotEmpty()) {
            // Get the first active order for the main card
            val firstOrder = activeOrders.first()
            activeOrderId = firstOrder.orderId
            
            // Show the main reminder card for the first active order
            updateReminderCard(firstOrder)
            CoroutineScope(Dispatchers.Main).launch {
                reminderCard.visibility = View.VISIBLE
            }
            
            // If there are multiple active orders, create additional cards for each
            if (activeOrders.size > 1) {
                // Create and display additional reminder cards for each active order
                CoroutineScope(Dispatchers.Main).launch {
                    displayAdditionalOrderCards(activeOrders.drop(1))
                }
            } else {
                // If there's only one order, make sure any additional cards are removed
                CoroutineScope(Dispatchers.Main).launch {
                    clearAdditionalOrderCards()
                }
            }
            
            // Start countdown timer based on the first active order
            val effectiveStatus = firstOrder.jobOrderStatus.takeIf { it.isNotBlank() } ?: firstOrder.status
            when (effectiveStatus.trim().lowercase()) {
                "accepted" -> {
                    // Show estimated arrival time if available
                    val estimatedArrival = firstOrder.getEffectiveEstimatedArrival()
                    if (estimatedArrival != null) {
                        CoroutineScope(Dispatchers.Main).launch {
                            startCountdownToTime(estimatedArrival.time)
                        }
                    } else {
                        // Default to 30 min countdown
                        CoroutineScope(Dispatchers.Main).launch {
                            startCountdownTimer()
                        }
                    }
                }
                else -> {
                    // Default countdown for other statuses
                    CoroutineScope(Dispatchers.Main).launch {
                        startCountdownTimer()
                    }
                }
            }
        } else {
            CoroutineScope(Dispatchers.Main).launch {
                reminderCard.visibility = View.GONE
                clearAdditionalOrderCards()
            }
            CoroutineScope(Dispatchers.Main).launch {
                stopCountdownTimer()
            }
        }
    }
}