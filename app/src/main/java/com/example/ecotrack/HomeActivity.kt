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
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

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
    private var allOrders: List<PaymentResponse>? = null
    private var currentFilter: String = "SHOW_ALL"
    
    // Filter UI elements
    private lateinit var statusFilterChipGroup: ChipGroup
    private lateinit var chipShowAll: Chip
    private lateinit var chipAvailable: Chip
    private lateinit var chipInProgress: Chip
    private lateinit var chipCompleted: Chip
    private lateinit var chipCancelled: Chip

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
        // Always fetch fresh data when home page is visited
        Log.d(TAG, "onCreate - fetching fresh data")
        checkForActiveOrders(isManualRefresh = true)
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
        
        // Initialize filter UI elements
        statusFilterChipGroup = binding.statusFilterChipGroup
        chipShowAll = binding.chipShowAll
        chipAvailable = binding.chipAvailable
        chipInProgress = binding.chipInProgress
        chipCompleted = binding.chipCompleted
        chipCancelled = binding.chipCancelled
        
        // Set up filter chip listeners
        setupFilterChips()
        
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
    
    private fun setupFilterChips() {
        // Set chip colors to match banner colors
        setChipColors()
        
        chipShowAll.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                Log.d(TAG, "🎯 SHOW_ALL filter selected")
                currentFilter = "SHOW_ALL"
                applyCurrentFilter()
            }
        }
        
        chipAvailable.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                Log.d(TAG, "🎯 AVAILABLE filter selected")
                currentFilter = "AVAILABLE"
                applyCurrentFilter()
            }
        }
        
        chipInProgress.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                Log.d(TAG, "🎯 IN_PROGRESS filter selected")
                currentFilter = "IN_PROGRESS"
                applyCurrentFilter()
            }
        }
        
        chipCompleted.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                Log.d(TAG, "🎯 COMPLETED filter selected")
                currentFilter = "COMPLETED"
                applyCurrentFilter()
            }
        }
        
        chipCancelled.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                Log.d(TAG, "🎯 CANCELLED filter selected")
                currentFilter = "CANCELLED"
                applyCurrentFilter()
            }
        }
    }
    
    private fun setChipColors() {
        // Create color state lists for each chip to match banner colors
        val availableColorStateList = android.content.res.ColorStateList.valueOf(
            ContextCompat.getColor(this, R.color.secondary)
        )
        val inProgressColorStateList = android.content.res.ColorStateList.valueOf(
            ContextCompat.getColor(this, R.color.material_yellow)
        )
        val completedColorStateList = android.content.res.ColorStateList.valueOf(
            ContextCompat.getColor(this, R.color.grey)
        )
        val cancelledColorStateList = android.content.res.ColorStateList.valueOf(
            ContextCompat.getColor(this, R.color.material_red)
        )
        val showAllColorStateList = android.content.res.ColorStateList.valueOf(
            ContextCompat.getColor(this, R.color.primary)
        )
        
        // Apply colors to chips
        chipShowAll.chipBackgroundColor = showAllColorStateList
        chipShowAll.chipStrokeColor = showAllColorStateList
        
        chipAvailable.chipBackgroundColor = availableColorStateList
        chipAvailable.chipStrokeColor = availableColorStateList
        
        chipInProgress.chipBackgroundColor = inProgressColorStateList
        chipInProgress.chipStrokeColor = inProgressColorStateList
        
        chipCompleted.chipBackgroundColor = completedColorStateList
        chipCompleted.chipStrokeColor = completedColorStateList
        
        chipCancelled.chipBackgroundColor = cancelledColorStateList
        chipCancelled.chipStrokeColor = cancelledColorStateList
        
        Log.d(TAG, "Filter chip colors updated to match banner colors")
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
        if (!isManualRefresh && currentTime - lastOrderCheckTime < MIN_CHECK_INTERVAL && allOrders != null) {
            Log.d(TAG, "Using cached orders - last check was ${currentTime - lastOrderCheckTime}ms ago")
            // Use cached data
            allOrders?.let { orders ->
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
                        allOrders = response.body()
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
                            allOrders = fallbackResponse.body()
                        }
                        handleFallbackResponse(fallbackResponse)
                        binding.swipeRefreshLayout.isRefreshing = false
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking for active orders", e)
                withContext(Dispatchers.Main) {
                    // Use cached data if available when there's an error
                    if (allOrders != null) {
                        Log.d(TAG, "Using cached data due to network error")
                        CoroutineScope(Dispatchers.Main).launch {
                            handleCachedOrders(allOrders!!)
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
    
    private fun applyCurrentFilter() {
        val orders = allOrders ?: return
        
        Log.d(TAG, "🔍 CURRENT FILTER STATE: '$currentFilter'")
        Log.d(TAG, "Chip states: ShowAll=${chipShowAll.isChecked}, Available=${chipAvailable.isChecked}, InProgress=${chipInProgress.isChecked}, Completed=${chipCompleted.isChecked}, Cancelled=${chipCancelled.isChecked}")
        
        // Log all order statuses for debugging
        Log.d(TAG, "=== Applying filter '$currentFilter' to ${orders.size} orders ===")
        orders.forEachIndexed { index, order ->
            Log.d(TAG, "Order $index [${order.orderId}]: jobStatus='${order.jobOrderStatus}', status='${order.status}'")
        }
        
        val filteredOrders = when (currentFilter) {
            "AVAILABLE" -> {
                Log.d(TAG, "Filtering for AVAILABLE orders (available, processing)")
                val availableOrders = orders.filter { order ->
                    val jobOrderStatus = order.jobOrderStatus?.trim()?.lowercase() ?: ""
                    // Prioritize jobOrderStatus, fallback to status only if jobOrderStatus is empty
                    val effectiveStatus = if (jobOrderStatus.isNotEmpty()) jobOrderStatus else (order.status?.trim()?.lowercase() ?: "")
                    val isAvailable = effectiveStatus == "available" || effectiveStatus == "processing"
                    
                    Log.d(TAG, "Order ${order.orderId}: jobStatus='$jobOrderStatus', effectiveStatus='$effectiveStatus', isAvailable=$isAvailable")
                    isAvailable
                }
                Log.d(TAG, "Found ${availableOrders.size} available orders")
                availableOrders
            }
            "IN_PROGRESS" -> {
                Log.d(TAG, "Filtering for IN_PROGRESS orders (accepted, in-progress)")
                val inProgressOrders = orders.filter { order ->
                    val jobOrderStatus = order.jobOrderStatus?.trim()?.lowercase() ?: ""
                    // Prioritize jobOrderStatus, fallback to status only if jobOrderStatus is empty
                    val effectiveStatus = if (jobOrderStatus.isNotEmpty()) jobOrderStatus else (order.status?.trim()?.lowercase() ?: "")
                    val isInProgress = effectiveStatus == "accepted" || effectiveStatus == "in-progress" || effectiveStatus == "in progress"
                    
                    Log.d(TAG, "Order ${order.orderId}: jobStatus='$jobOrderStatus', effectiveStatus='$effectiveStatus', isInProgress=$isInProgress")
                    isInProgress
                }
                Log.d(TAG, "Found ${inProgressOrders.size} in-progress orders")
                inProgressOrders
            }
            "COMPLETED" -> {
                Log.d(TAG, "Filtering for COMPLETED orders (completed)")
                val completedOrders = orders.filter { order ->
                    val jobOrderStatus = order.jobOrderStatus?.trim()?.lowercase() ?: ""
                    // Prioritize jobOrderStatus, fallback to status only if jobOrderStatus is empty
                    val effectiveStatus = if (jobOrderStatus.isNotEmpty()) jobOrderStatus else (order.status?.trim()?.lowercase() ?: "")
                    val isCompleted = effectiveStatus == "completed"
                    
                    Log.d(TAG, "Order ${order.orderId}: jobStatus='$jobOrderStatus', effectiveStatus='$effectiveStatus', isCompleted=$isCompleted")
                    isCompleted
                }
                Log.d(TAG, "Found ${completedOrders.size} completed orders")
                completedOrders
            }
            "CANCELLED" -> {
                Log.d(TAG, "Filtering for CANCELLED orders (cancelled)")
                val cancelledOrders = orders.filter { order ->
                    val jobOrderStatus = order.jobOrderStatus?.trim()?.lowercase() ?: ""
                    // Prioritize jobOrderStatus, fallback to status only if jobOrderStatus is empty
                    val effectiveStatus = if (jobOrderStatus.isNotEmpty()) jobOrderStatus else (order.status?.trim()?.lowercase() ?: "")
                    val isCancelled = effectiveStatus == "cancelled"
                    
                    Log.d(TAG, "Order ${order.orderId}: jobStatus='$jobOrderStatus', effectiveStatus='$effectiveStatus', isCancelled=$isCancelled")
                    isCancelled
                }
                Log.d(TAG, "Found ${cancelledOrders.size} cancelled orders")
                cancelledOrders
            }
            else -> {
                Log.d(TAG, "Showing ALL orders")
                orders // SHOW_ALL
            }
        }
        
        Log.d(TAG, "Applied filter '$currentFilter': ${filteredOrders.size} orders from ${orders.size} total")
        filteredOrders.forEachIndexed { index, order ->
            Log.d(TAG, "Filtered Order $index [${order.orderId}]: jobStatus='${order.jobOrderStatus}', status='${order.status}'")
        }
        Log.d(TAG, "=== End filter application ===")
        
        displayFilteredOrders(filteredOrders)
    }
    
    private fun isStatusMatch(order: PaymentResponse, vararg statuses: String): Boolean {
        val jobOrderStatus = order.jobOrderStatus?.trim()?.lowercase() ?: ""
        val regularStatus = order.status?.trim()?.lowercase() ?: ""
        // Prioritize jobOrderStatus, fallback to status only if jobOrderStatus is empty
        val effectiveStatus = if (jobOrderStatus.isNotEmpty()) jobOrderStatus else regularStatus
        
        Log.d(TAG, "Checking status match for order ${order.orderId}: jobStatus='${jobOrderStatus}', status='${regularStatus}', effectiveStatus='${effectiveStatus}' against ${statuses.joinToString(", ")}")
        
        val result = statuses.any { status ->
            val statusLower = status.lowercase()
            // Use exact match with effective status to prevent cross-contamination
            val isMatch = effectiveStatus == statusLower
            
            if (isMatch) {
                Log.d(TAG, "✓ Status match found for order ${order.orderId}: '${statusLower}' matches effectiveStatus='${effectiveStatus}'")
            }
            
            isMatch
        }
        
        return result
    }
    
    private fun displayFilteredOrders(orders: List<PaymentResponse>) {
        if (orders.isEmpty()) {
            reminderCard.visibility = View.GONE
            clearAdditionalOrderCards()
            stopCountdownTimer()
            return
        }
        
        // Get the first order for the main card
        val firstOrder = orders.first()
        activeOrderId = firstOrder.orderId
        
        // Show the main reminder card for the first order
        CoroutineScope(Dispatchers.IO).launch {
            updateReminderCard(firstOrder)
            withContext(Dispatchers.Main) {
                reminderCard.visibility = View.VISIBLE
            }
            
            // If there are multiple orders, create additional cards for each
            if (orders.size > 1) {
                displayAdditionalOrderCards(orders.drop(1))
            } else {
                withContext(Dispatchers.Main) {
                    clearAdditionalOrderCards()
                }
            }
        }
        
        // Handle countdown timer only for active orders
        if (currentFilter == "IN_PROGRESS" || currentFilter == "SHOW_ALL") {
            val effectiveStatus = firstOrder.jobOrderStatus.takeIf { it.isNotBlank() } ?: firstOrder.status
            when (effectiveStatus.trim().lowercase()) {
                "accepted" -> {
                    val estimatedArrival = firstOrder.getEffectiveEstimatedArrival()
                    if (estimatedArrival != null) {
                        startCountdownToTime(estimatedArrival.time)
                    } else {
                        startCountdownTimer()
                    }
                }
                else -> {
                    if (isStatusMatch(firstOrder, "available", "accepted", "in-progress")) {
                        startCountdownTimer()
                    } else {
                        stopCountdownTimer()
                    }
                }
            }
        } else {
            stopCountdownTimer()
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
                
                // Store all orders and apply current filter
                allOrders = orders
                withContext(Dispatchers.Main) {
                    applyCurrentFilter()
                }
            } else {
                // No orders found
                Log.d(TAG, "No orders found in API response")
                allOrders = emptyList()
                withContext(Dispatchers.Main) {
                    reminderCard.visibility = View.GONE
                    clearAdditionalOrderCards()
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
            
            // Store all orders and apply current filter
            allOrders = orders
            withContext(Dispatchers.Main) {
                applyCurrentFilter()
            }
        } else {
            Log.d(TAG, "Fallback response unsuccessful: ${response.code()}")
            allOrders = emptyList()
            withContext(Dispatchers.Main) {
                reminderCard.visibility = View.GONE
                clearAdditionalOrderCards()
                stopCountdownTimer()
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
                statusToCheck.contains("avail") || statusToCheck.contains("processing") -> "PICKUP REQUESTED"
                statusToCheck.contains("accept") -> "DRIVER ON THE WAY"
                statusToCheck.contains("progress") -> "PICKUP IN PROGRESS"
                statusToCheck.contains("completed") -> "ORDER COMPLETED"
                statusToCheck.contains("cancelled") -> "ORDER CANCELLED"
                else -> "TRUCK REMINDER"
            }
            Log.d(TAG, "Setting main reminder title to: $titleText for status: $statusToCheck")
            reminderTitle.text = titleText
            
            // Set card background color based on status
            val cardBackgroundColor = when {
                statusToCheck.contains("completed") -> ContextCompat.getColor(this@HomeActivity, R.color.grey)
                statusToCheck.contains("cancelled") -> ContextCompat.getColor(this@HomeActivity, R.color.material_red)
                statusToCheck.contains("accept") || statusToCheck.contains("progress") -> ContextCompat.getColor(this@HomeActivity, R.color.material_yellow)
                else -> ContextCompat.getColor(this@HomeActivity, R.color.secondary) // Default green for available/processing
            }
            reminderCard.setCardBackgroundColor(cardBackgroundColor)
            
            Log.d(TAG, "Setting main reminder card background color for status '$statusToCheck': ${when {
                statusToCheck.contains("completed") -> "grey"
                statusToCheck.contains("cancelled") -> "red"
                statusToCheck.contains("accept") || statusToCheck.contains("progress") -> "yellow"
                else -> "green"
            }}")
            
            // Save the active order ID
            activeOrderId = order.orderId
            Log.d(TAG, "Order ID set to: $activeOrderId")
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
                    
                    // Create a PickupOrder object from the PaymentResponse
                    // Note: selectedTruck field removed as trucks are now assigned by backend
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
                        trashWeight = 0.0, // Default value, actual weight is handled by backend
                        notes = null, // Notes not available in payment response
                        barangayId = paymentResponse.barangayId ?: ""
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

        // Always fetch fresh data when returning to home activity to show latest information
        Log.d(TAG, "onResume - fetching fresh data to show latest information")
        checkForActiveOrders(isManualRefresh = true)
        
        // Always refresh profile data to ensure latest user information
        Log.d(TAG, "onResume - refreshing profile data")
        loadUserData()
    }

    // This is called when the activity is brought back to the foreground
    override fun onRestart() {
        super.onRestart()
        // Always fetch fresh data when activity is brought back to foreground
        Log.d(TAG, "onRestart - fetching fresh data")
        checkForActiveOrders(isManualRefresh = true)
        
        // Always refresh profile data
        Log.d(TAG, "onRestart - refreshing profile data")
        loadUserData()
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
        
        // Set card background color based on status
        val cardBackgroundColor = when {
            statusToCheck.contains("completed") -> ContextCompat.getColor(this, R.color.grey)
            statusToCheck.contains("cancelled") -> ContextCompat.getColor(this, R.color.material_red)
            statusToCheck.contains("accept") || statusToCheck.contains("progress") -> ContextCompat.getColor(this, R.color.material_yellow)
            else -> ContextCompat.getColor(this, R.color.secondary) // Default green for available/processing
        }
        cardView.setCardBackgroundColor(cardBackgroundColor)
        cardView.elevation = 4f.dpToPx().toFloat()
        
        Log.d(TAG, "Setting card background color for status '$statusToCheck': ${when {
            statusToCheck.contains("completed") -> "grey"
            statusToCheck.contains("cancelled") -> "red"
            statusToCheck.contains("accept") || statusToCheck.contains("progress") -> "yellow"
            else -> "green"
        }}")
        
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
            statusToCheck.contains("avail") || statusToCheck.contains("processing") -> "PICKUP REQUESTED"
            statusToCheck.contains("accept") -> "DRIVER ON THE WAY"
            statusToCheck.contains("progress") -> "PICKUP IN PROGRESS"
            statusToCheck.contains("completed") -> "ORDER COMPLETED"
            statusToCheck.contains("cancelled") -> "ORDER CANCELLED"
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
        // Store cached orders and apply current filter
        allOrders = orders
        withContext(Dispatchers.Main) {
            applyCurrentFilter()
        }
    }
}