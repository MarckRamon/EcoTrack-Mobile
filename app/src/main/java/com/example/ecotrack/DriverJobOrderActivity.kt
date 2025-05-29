package com.example.ecotrack

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
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

class DriverJobOrderActivity : BaseActivity() {

    private lateinit var inProgressRecyclerView: RecyclerView
    private lateinit var availableRecyclerView: RecyclerView
    private lateinit var completedRecyclerView: RecyclerView
    private lateinit var homeNav: View
    private lateinit var jobOrdersNav: View
    private lateinit var collectionPointsNav: View
    private lateinit var tvNoInProgressOrders: TextView
    private lateinit var tvNoAvailableOrders: TextView
    private lateinit var tvNoCompletedOrders: TextView
    private lateinit var tvInProgressHeader: TextView
    private lateinit var tvAvailableHeader: TextView
    private lateinit var tvCompletedHeader: TextView
    private lateinit var btnViewMoreAvailable: TextView
    private lateinit var btnViewHistory: TextView
    private lateinit var tvAvailableDisabledMessage: TextView
    private lateinit var endCollectionButton: Button
    private lateinit var profileImage: CircleImageView
    private lateinit var notificationIcon: ImageView
    
    // Confirmation dialog components
    private lateinit var confirmationDialog: CardView
    private lateinit var confirmButton: Button
    private lateinit var cancelButton: Button
    private lateinit var dialogOverlay: View

    private val apiService = ApiService.create()
    private val TAG = "DriverJobOrderActivity"
    
    // Real-time update manager
    private lateinit var realTimeUpdateManager: RealTimeUpdateManager
    
    // Track if there are any completed orders
    private var hasCompletedOrders = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driver_job_order)
        
        // Initialize views
        initViews()
        
        // Setup navigation
        setupNavigation()
        
        // Initialize real-time update manager
        realTimeUpdateManager = RealTimeUpdateManager(
            activity = this,
            updateCallback = { loadPaymentOrders() }
        )
        
        // Load payment orders for the current driver
        loadPaymentOrders()
    }

    private fun initViews() {
        inProgressRecyclerView = findViewById(R.id.recyclerViewInProgressJobOrders)
        availableRecyclerView = findViewById(R.id.recyclerViewAvailableJobOrders)
        completedRecyclerView = findViewById(R.id.recyclerViewCompletedJobOrders)
        homeNav = findViewById(R.id.homeNav)
        jobOrdersNav = findViewById(R.id.jobOrdersNav)
        collectionPointsNav = findViewById(R.id.collectionPointsNav)
        tvNoInProgressOrders = findViewById(R.id.tvNoInProgressOrders)
        tvNoAvailableOrders = findViewById(R.id.tvNoAvailableOrders)
        tvNoCompletedOrders = findViewById(R.id.tvNoCompletedOrders)
        tvInProgressHeader = findViewById(R.id.tvInProgressHeader)
        tvAvailableHeader = findViewById(R.id.tvAvailableHeader)
        tvCompletedHeader = findViewById(R.id.tvCompletedHeader)
        btnViewMoreAvailable = findViewById(R.id.btnViewMoreAvailable)
        btnViewHistory = findViewById(R.id.btnViewHistory)
        tvAvailableDisabledMessage = findViewById(R.id.tvAvailableDisabledMessage)
        endCollectionButton = findViewById(R.id.endCollectionButton)
        profileImage = findViewById(R.id.profileImage)
        
        // Set initial button state to disabled (dark gray)
        updateEndCollectionButtonState(false)
        
        // Confirmation dialog components
        confirmationDialog = findViewById(R.id.confirmationDialog)
        confirmButton = findViewById(R.id.confirmButton)
        cancelButton = findViewById(R.id.cancelButton)
        dialogOverlay = findViewById(R.id.dialogOverlay)
        
        // Set up RecyclerViews
        inProgressRecyclerView.layoutManager = LinearLayoutManager(this)
        availableRecyclerView.layoutManager = LinearLayoutManager(this)
        completedRecyclerView.layoutManager = LinearLayoutManager(this)
        
        // Set up End Collection button
        endCollectionButton.setOnClickListener {
            if (hasCompletedOrders) {
                showConfirmationDialog()
            } else {
                Toast.makeText(this, "Complete at least one job order first", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Set up confirmation dialog buttons
        confirmButton.setOnClickListener {
            hideConfirmationDialog()
            navigateToPrivateEntityMap()
        }
        
        cancelButton.setOnClickListener {
            hideConfirmationDialog()
        }
        
        // Set up profile image click
        profileImage.setOnClickListener {
            startActivity(Intent(this, DriverProfileActivity::class.java))
        }
        
        // Set up view more buttons
        btnViewMoreAvailable.setOnClickListener {
            val intent = Intent(this, AvailableJobOrdersActivity::class.java)
            startActivity(intent)
        }
        
        btnViewHistory.setOnClickListener {
            val intent = Intent(this, CompletedJobOrdersActivity::class.java)
            startActivity(intent)
        }
    }
    
    /**
     * Updates the End Collection button state based on whether there are completed orders
     */
    private fun updateEndCollectionButtonState(enabled: Boolean) {
        hasCompletedOrders = enabled
        
        if (enabled) {
            // Active state - green color
            endCollectionButton.backgroundTintList = ContextCompat.getColorStateList(this, R.color.secondary)
        } else {
            // Inactive state - dark gray color
            endCollectionButton.backgroundTintList = ContextCompat.getColorStateList(this, R.color.dark_gray)
        }
        
        // Update button text
        endCollectionButton.text = "WASTE DELIVER"
    }
    
    private fun showConfirmationDialog() {
        // Show the dialog and overlay
        dialogOverlay.visibility = View.VISIBLE
        confirmationDialog.visibility = View.VISIBLE
        
        // Dim UI elements
        findViewById<View>(R.id.header)?.alpha = 0.2f
        findViewById<View>(R.id.appLogo)?.alpha = 0.1f
        endCollectionButton.alpha = 0.8f
        findViewById<View>(R.id.bottomNavigation)?.alpha = 0.15f

        // Disable interactions with UI elements
        findViewById<View>(R.id.btnView)?.let { it.isEnabled = false }
        findViewById<View>(R.id.header)?.isEnabled = false
        endCollectionButton.isEnabled = false
        findViewById<View>(R.id.bottomNavigation)?.isEnabled = false
        findViewById<View>(R.id.homeNav)?.isClickable = false
        findViewById<View>(R.id.jobOrdersNav)?.isClickable = false
        findViewById<View>(R.id.collectionPointsNav)?.isClickable = false
        findViewById<View>(R.id.profileImage)?.isClickable = false
        findViewById<View>(R.id.btnViewMoreAvailable)?.isClickable = false
        findViewById<View>(R.id.btnViewHistory)?.isClickable = false
        
        // Disable scrolling in recycler views
        inProgressRecyclerView.suppressLayout(true)
        availableRecyclerView.suppressLayout(true)
        completedRecyclerView.suppressLayout(true)
    }
    
    private fun hideConfirmationDialog() {
        // Hide the dialog and overlay
        dialogOverlay.visibility = View.GONE
        confirmationDialog.visibility = View.GONE
        
        // Restore UI elements (set alpha back to 1.0)
        val fullAlpha = 1.0f
        findViewById<View>(R.id.header)?.alpha = fullAlpha
        findViewById<View>(R.id.appLogo)?.alpha = fullAlpha
        endCollectionButton.alpha = fullAlpha
        findViewById<View>(R.id.bottomNavigation)?.alpha = fullAlpha
        
        // Re-enable interactions with UI elements
        findViewById<View>(R.id.btnView)?.let { it.isEnabled = true }
        findViewById<View>(R.id.header)?.isEnabled = true
        endCollectionButton.isEnabled = true
        findViewById<View>(R.id.bottomNavigation)?.isEnabled = true
        findViewById<View>(R.id.homeNav)?.isClickable = true
        findViewById<View>(R.id.jobOrdersNav)?.isClickable = true
        findViewById<View>(R.id.collectionPointsNav)?.isClickable = true
        findViewById<View>(R.id.profileImage)?.isClickable = true
        findViewById<View>(R.id.btnViewMoreAvailable)?.isClickable = true
        findViewById<View>(R.id.btnViewHistory)?.isClickable = true
        
        // Re-enable scrolling in recycler views
        inProgressRecyclerView.suppressLayout(false)
        availableRecyclerView.suppressLayout(false)
        completedRecyclerView.suppressLayout(false)
    }
    
    private fun navigateToPrivateEntityMap() {
        // Find the first completed payment that hasn't been delivered yet
        val completedPayment = lifecycleScope.launch {
            try {
                val token = sessionManager.getToken()
                val driverId = sessionManager.getUserId()
                
                if (token != null && driverId != null) {
                    val response = apiService.getPaymentsByDriverId(
                        driverId = driverId,
                        authToken = "Bearer $token"
                    )
                    
                    if (response.isSuccessful && response.body() != null) {
                        val payments = response.body()
                        // Filter for completed payments that haven't been delivered yet
                        val completedPayments = payments?.filter { 
                            it.jobOrderStatus == "Completed" && !it.isDelivered 
                        }
                        
                        if (!completedPayments.isNullOrEmpty()) {
                            // Launch the PrivateEntityMapActivity with the first completed payment
                            val intent = Intent(this@DriverJobOrderActivity, PrivateEntityMapActivity::class.java)
                            intent.putExtra("PAYMENT", completedPayments.first())
                            startActivity(intent)
                        } else {
                            Toast.makeText(this@DriverJobOrderActivity, "No completed payments found that need delivery", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this@DriverJobOrderActivity, "Failed to fetch payments", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching payments: ${e.message}")
                Toast.makeText(this@DriverJobOrderActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == PrivateEntityMapActivity.REQUEST_CODE_SELECT_ENTITY && resultCode == RESULT_OK) {
            val selectedEntity = data?.getSerializableExtra("SELECTED_ENTITY")
            if (selectedEntity != null) {
                Toast.makeText(this, "Selected entity: ${selectedEntity}", Toast.LENGTH_SHORT).show()
                // Here you would handle the selected entity, perhaps update the job order status
            }
        }
    }
    
    private fun setupNavigation() {
        homeNav.setOnClickListener {
            startActivity(Intent(this, DriverHomeActivity::class.java))
            finish()
        }
        
        // Already on job orders screen
        
        collectionPointsNav.setOnClickListener {
            startActivity(Intent(this, DriverMapActivity::class.java))
            finish()
        }
    }
    
    private fun loadPaymentOrders() {
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
        
        // Show/hide UI elements
        inProgressRecyclerView.visibility = View.GONE
        availableRecyclerView.visibility = View.GONE
        completedRecyclerView.visibility = View.GONE
        tvNoInProgressOrders.visibility = View.GONE
        tvNoAvailableOrders.visibility = View.GONE
        tvNoCompletedOrders.visibility = View.GONE
        tvAvailableDisabledMessage.visibility = View.GONE
        btnViewMoreAvailable.visibility = View.GONE
        btnViewHistory.visibility = View.GONE
        
        // Fetch payment orders assigned to this driver
        lifecycleScope.launch {
            try {
                val response = apiService.getPaymentsByDriverId(
                    driverId = driverId,
                    authToken = "Bearer $token"
                )
                
                if (response.isSuccessful) {
                    val payments = response.body()
                    
                    if (payments != null && payments.isNotEmpty()) {
                        // Split payments into in-progress, available and completed
                        val inProgressPayments = payments.filter { 
                            it.jobOrderStatus == "In-Progress" || it.jobOrderStatus == "Accepted" 
                        }
                        val availablePayments = payments.filter { it.jobOrderStatus == "Available" }
                        
                        // Filter completed payments to exclude those that are already delivered
                        val completedPayments = payments.filter { 
                            it.jobOrderStatus == "Completed" && !it.isDelivered
                        }
                        
                        // Log filtered payments for debugging
                        Log.d(TAG, "Total payments: ${payments.size}")
                        Log.d(TAG, "In-progress payments: ${inProgressPayments.size}")
                        Log.d(TAG, "Available payments: ${availablePayments.size}")
                        Log.d(TAG, "Completed payments (not delivered): ${completedPayments.size}")
                        
                        // Check if there's an active job (In-Progress or Accepted)
                        val hasActiveJob = inProgressPayments.isNotEmpty()
                        
                        // Update End Collection button state based on completed payments
                        updateEndCollectionButtonState(completedPayments.isNotEmpty())
                        
                        // Handle in-progress payments - show all of them
                        if (inProgressPayments.isNotEmpty()) {
                            // Show all in-progress/accepted payments
                            setupInProgressPaymentsAdapter(inProgressPayments)
                            inProgressRecyclerView.visibility = View.VISIBLE
                            tvNoInProgressOrders.visibility = View.GONE
                            tvInProgressHeader.visibility = View.VISIBLE
                        } else {
                            inProgressRecyclerView.visibility = View.GONE
                            tvNoInProgressOrders.visibility = View.VISIBLE
                            tvInProgressHeader.visibility = View.VISIBLE
                        }
                        
                        // Handle available payments - limit to 4 and disable if there's an active job
                        if (availablePayments.isNotEmpty()) {
                            // Limit to 4 available payments for the main screen
                            val displayAvailablePayments = availablePayments.take(4)
                            
                            // Set up the adapter with the hasActiveJob flag to disable items
                            setupAvailablePaymentsAdapter(displayAvailablePayments, hasActiveJob)
                            
                            // Always show the items
                            availableRecyclerView.visibility = View.VISIBLE
                            tvNoAvailableOrders.visibility = View.GONE
                            
                            // Hide the disabled message
                            tvAvailableDisabledMessage.visibility = View.GONE
                            
                            // Show View More button if there are more than 4 available payments
                            btnViewMoreAvailable.visibility = if (availablePayments.size > 4) View.VISIBLE else View.GONE
                            tvAvailableHeader.visibility = View.VISIBLE
                        } else {
                            availableRecyclerView.visibility = View.GONE
                            tvNoAvailableOrders.visibility = View.VISIBLE
                            tvAvailableDisabledMessage.visibility = View.GONE
                            tvAvailableHeader.visibility = View.VISIBLE
                            btnViewMoreAvailable.visibility = View.GONE
                        }
                        
                        // Handle completed payments - limit to 4
                        if (completedPayments.isNotEmpty()) {
                            // Log the completed payments for debugging
                            completedPayments.forEach { payment ->
                                Log.d(TAG, "Completed payment: ${payment.id}, updatedAt: ${payment.updatedAt}, createdAt: ${payment.createdAt}, status: ${payment.jobOrderStatus}, isDelivered: ${payment.isDelivered}")
                            }
                            
                            // Limit to 4 completed payments for the main screen
                            val displayCompletedPayments = completedPayments.take(4)
                            setupCompletedPaymentsAdapter(displayCompletedPayments)
                            completedRecyclerView.visibility = View.VISIBLE
                            tvNoCompletedOrders.visibility = View.GONE
                            tvCompletedHeader.visibility = View.VISIBLE
                            
                            // Show View History button if there are more than 4 completed payments
                            btnViewHistory.visibility = if (completedPayments.size > 4) View.VISIBLE else View.GONE
                        } else {
                            completedRecyclerView.visibility = View.GONE
                            tvNoCompletedOrders.visibility = View.VISIBLE
                            tvCompletedHeader.visibility = View.VISIBLE
                            btnViewHistory.visibility = View.GONE
                        }
                    } else {
                        // Show no orders message for all sections
                        inProgressRecyclerView.visibility = View.GONE
                        availableRecyclerView.visibility = View.GONE
                        completedRecyclerView.visibility = View.GONE
                        tvNoInProgressOrders.visibility = View.VISIBLE
                        tvNoAvailableOrders.visibility = View.VISIBLE
                        tvNoCompletedOrders.visibility = View.VISIBLE
                        tvAvailableDisabledMessage.visibility = View.GONE
                        tvInProgressHeader.visibility = View.VISIBLE
                        tvAvailableHeader.visibility = View.VISIBLE
                        tvCompletedHeader.visibility = View.VISIBLE
                        btnViewMoreAvailable.visibility = View.GONE
                        btnViewHistory.visibility = View.GONE
                        
                        // Disable End Collection button
                        updateEndCollectionButtonState(false)
                    }
                } else {
                    // Handle error response
                    val errorMessage = "Error: ${response.code()} - ${response.message()}"
                    Log.e(TAG, errorMessage)
                    Toast.makeText(this@DriverJobOrderActivity, errorMessage, Toast.LENGTH_LONG).show()
                    tvNoInProgressOrders.visibility = View.VISIBLE
                    tvNoAvailableOrders.visibility = View.VISIBLE
                    tvNoCompletedOrders.visibility = View.VISIBLE
                    
                    // Disable End Collection button
                    updateEndCollectionButtonState(false)
                }
            } catch (e: IOException) {
                // Handle network error
                Log.e(TAG, "Network error: ${e.message}")
                Toast.makeText(this@DriverJobOrderActivity, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
                tvNoInProgressOrders.visibility = View.VISIBLE
                tvNoAvailableOrders.visibility = View.VISIBLE
                tvNoCompletedOrders.visibility = View.VISIBLE
                
                // Disable End Collection button
                updateEndCollectionButtonState(false)
            } catch (e: HttpException) {
                // Handle HTTP exception
                Log.e(TAG, "HTTP error: ${e.message}")
                Toast.makeText(this@DriverJobOrderActivity, "HTTP error: ${e.message}", Toast.LENGTH_LONG).show()
                tvNoInProgressOrders.visibility = View.VISIBLE
                tvNoAvailableOrders.visibility = View.VISIBLE
                tvNoCompletedOrders.visibility = View.VISIBLE
                
                // Disable End Collection button
                updateEndCollectionButtonState(false)
            } catch (e: Exception) {
                // Handle other exceptions
                Log.e(TAG, "Error: ${e.message}")
                Toast.makeText(this@DriverJobOrderActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                tvNoInProgressOrders.visibility = View.VISIBLE
                tvNoAvailableOrders.visibility = View.VISIBLE
                tvNoCompletedOrders.visibility = View.VISIBLE
                
                // Disable End Collection button
                updateEndCollectionButtonState(false)
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
    
    private fun setupAvailablePaymentsAdapter(payments: List<Payment>, hasActiveJob: Boolean) {
        val adapter = PaymentOrderAdapter(payments, true, "Available") { payment ->
            // Always navigate to the job order details screen
            val intent = Intent(this, DriverJobOrderStatusActivity::class.java)
            intent.putExtra("PAYMENT", payment)
            intent.putExtra("MODE", DriverJobOrderStatusActivity.JobOrderStatusMode.ACCEPT)
            startActivity(intent)
        }
        
        availableRecyclerView.adapter = adapter
    }
    
    private fun setupCompletedPaymentsAdapter(payments: List<Payment>) {
        val adapter = PaymentOrderAdapter(payments, false, "Completed") { payment ->
            // Navigate to the job order details screen
            startActivity(Intent(this, DriverJobOrderStatusActivity::class.java).apply {
                putExtra("PAYMENT", payment)
                putExtra("MODE", DriverJobOrderStatusActivity.JobOrderStatusMode.ACCEPT)
            })
        }
        
        completedRecyclerView.adapter = adapter
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