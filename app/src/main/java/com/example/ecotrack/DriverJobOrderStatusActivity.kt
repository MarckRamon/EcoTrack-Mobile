package com.example.ecotrack

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.ecotrack.models.JobOrder
import com.example.ecotrack.models.JobOrderStatusUpdate
import com.example.ecotrack.models.OrderStatus
import com.example.ecotrack.models.payment.Payment
import com.example.ecotrack.utils.ApiService
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

class DriverJobOrderStatusActivity : BaseActivity() {

    private val TAG = "DriverJobOrderStatus"

    // UI components
    private lateinit var mapView: MapView
    private lateinit var addressTextView: TextView
    private lateinit var fullNameTextView: TextView
    private lateinit var phoneNumberTextView: TextView
    private lateinit var paymentMethodTextView: TextView
    private lateinit var wasteTypeTextView: TextView
    private lateinit var totalTextView: TextView
    private lateinit var statusTextView: TextView
    private lateinit var actionButton: Button
    private lateinit var cancelJobOrderButton: Button
    private lateinit var backButton: ImageView
    private lateinit var progressBar: ProgressBar
    private lateinit var toolbarTitleTextView: TextView
    
    // Additional views for payment details
    private lateinit var amountTextView: TextView
    private lateinit var taxValueTextView: TextView
    
    // Confirmation dialog views (for collection completed)
    private lateinit var confirmationDialog: CardView
    private lateinit var confirmButton: Button
    private lateinit var cancelButton: Button
    private lateinit var dialogOverlay: View
    
    // API Service
    private lateinit var apiService: ApiService
    
    // Payment data
    private var payment: Payment? = null
    
    // Status mode
    private var mode: JobOrderStatusMode = JobOrderStatusMode.ACCEPT
    
    // Location data
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001
    private var customerLatitude = 14.5995 // Default location (Philippines)
    private var customerLongitude = 120.9842 // Default location (Philippines)
    private var customerLocation: GeoPoint? = null

    enum class JobOrderStatusMode {
        ACCEPT,      // For accepting job orders (similar to DriverAcceptJobOrderActivity)
        COMPLETE,    // For completing collections (similar to DriverCollectionCompletedActivity)
        VIEW         // For viewing job order details without actions
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize OpenStreetMap configuration
        Configuration.getInstance().load(
            applicationContext,
            PreferenceManager.getDefaultSharedPreferences(applicationContext)
        )
        
        // Determine mode based on intent
        mode = intent.getSerializableExtra("MODE") as? JobOrderStatusMode ?: JobOrderStatusMode.ACCEPT
        
        // Set the appropriate layout based on mode
        setContentView(
            if (mode == JobOrderStatusMode.ACCEPT) 
                R.layout.activity_driver_accept_job_order
            else 
                R.layout.activity_driver_collection_completed
        )

        // Initialize API service
        apiService = ApiService.create()
        
        // Initialize views
        initViews()
        
        // Set up listeners
        setupListeners()
        
        // Check location permission for map
        checkLocationPermission()
        
        // Load job order details and setup map
        loadPaymentDetails()
    }
    
    private fun initViews() {
        mapView = findViewById(R.id.mapView)
        addressTextView = findViewById(R.id.addressTextView)
        fullNameTextView = findViewById(R.id.fullNameValueTextView)
        phoneNumberTextView = findViewById(R.id.phoneNumberValueTextView)
        paymentMethodTextView = findViewById(R.id.paymentMethodValueTextView)
        wasteTypeTextView = findViewById(R.id.wasteTypeValueTextView)
        totalTextView = findViewById(R.id.totalValueTextView)
        progressBar = findViewById(R.id.progressBar)
        amountTextView = findViewById(R.id.amountValueTextView)
        taxValueTextView = findViewById(R.id.taxValueTextView)
        
        // Initialize back button for all modes
        try {
            backButton = findViewById(R.id.backButton)
            backButton.setOnClickListener { onBackPressed() }
        } catch (e: Exception) {
            Log.e(TAG, "Back button not found in layout: ${e.message}")
        }
        
        // Initialize mode-specific views
        if (mode == JobOrderStatusMode.ACCEPT) {
            statusTextView = findViewById(R.id.jobOrderStatusTextView)
            actionButton = findViewById(R.id.acceptJobOrderButton)
            cancelJobOrderButton = findViewById(R.id.cancelJobOrderButton)
            toolbarTitleTextView = findViewById(R.id.toolbarTitle)
        } else {
            statusTextView = findViewById(R.id.statusTextView)
            actionButton = findViewById(R.id.collectionCompletedButton)
            toolbarTitleTextView = findViewById(R.id.toolbarTitle)
            
            // Initialize confirmation dialog views
            confirmationDialog = findViewById(R.id.confirmationDialog)
            confirmButton = findViewById(R.id.confirmButton)
            cancelButton = findViewById(R.id.cancelButton)
            dialogOverlay = findViewById(R.id.dialogOverlay)
            
            // If in VIEW mode, disable the action button
            if (mode == JobOrderStatusMode.VIEW) {
                actionButton.isEnabled = false
                actionButton.text = "VIEW ONLY"
                actionButton.alpha = 0.5f
            }
        }
        
        // Configure the map
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(16.0)
    }
    
    private fun setupListeners() {
        // Skip setting action listeners in VIEW mode
        if (mode == JobOrderStatusMode.VIEW) {
            return
        }
        
        if (mode == JobOrderStatusMode.ACCEPT) {
            // Accept Job Order mode
            actionButton.setOnClickListener {
                // Check job status and take appropriate action
                when (payment?.jobOrderStatus) {
                    "In-Progress" -> {
                        // If already in progress, continue to location screen
                        val intent = Intent(this, DriverArrivedAtTheLocationActivity::class.java)
                        intent.putExtra("PAYMENT", payment)
                        startActivity(intent)
                    }
                    "Accepted" -> {
                        // If already accepted, continue to location screen
                        val intent = Intent(this, DriverArrivedAtTheLocationActivity::class.java)
                        intent.putExtra("PAYMENT", payment)
                        startActivity(intent)
                    }
                    else -> {
                        // Check if driver already has an active job
                        checkForActiveJobs()
                    }
                }
            }
            
            // Cancel Job Order button
            cancelJobOrderButton.setOnClickListener {
                // Update job order status to "Cancelled"
                updateJobOrderStatus("Cancelled")
            }
        } else {
            // Collection Completed mode
            actionButton.setOnClickListener {
                showConfirmationDialog()
            }
            
            confirmButton.setOnClickListener {
                hideConfirmationDialog()
                
                // Update job order status to "Completed"
                updateJobOrderStatus("Completed")
            }
            
            cancelButton.setOnClickListener {
                hideConfirmationDialog()
            }
        }
    }
    
    private fun checkForActiveJobs() {
        // Show loading indicator
        showLoading("Checking active jobs...")
        
        lifecycleScope.launch {
            try {
                val driverId = sessionManager.getUserId()
                val token = sessionManager.getToken()
                
                if (driverId != null && token != null) {
                    val response = apiService.getPaymentsByDriverId(
                        driverId = driverId,
                        authToken = "Bearer $token"
                    )

                    if (response.isSuccessful) {
                        val payments = response.body()

                        // Check if there are any active jobs (In-Progress or Accepted)
                        val activeJobs = payments?.filter {
                            it.jobOrderStatus == "In-Progress" || it.jobOrderStatus == "Accepted"
                        } ?: emptyList()
                        
                        if (activeJobs.isNotEmpty()) {
                            // Driver has active jobs, show message and don't allow accepting new job
                            hideLoading()
                            Toast.makeText(
                                this@DriverJobOrderStatusActivity, 
                                "Complete your active job order before accepting a new one", 
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            // No active jobs, proceed with accepting this job
                            hideLoading()
                            updateJobOrderStatus("Accepted")
                        }
                    }
                } else {
                    // Missing driver ID or token
                    hideLoading()
                    Toast.makeText(
                        this@DriverJobOrderStatusActivity,
                        "Authentication error. Please log in again.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                // Handle network error
                Log.e(TAG, "Error checking active jobs: ${e.message}", e)
                hideLoading()
                
                // Show warning but allow accepting the job
                Toast.makeText(
                    this@DriverJobOrderStatusActivity,
                    "Network error. Proceeding with caution.",
                    Toast.LENGTH_SHORT
                ).show()
                updateJobOrderStatus("Accepted")
            }
        }
    }
    
    private fun showLoading(message: String) {
        progressBar.visibility = View.VISIBLE
        actionButton.isEnabled = false
    }
    
    private fun hideLoading() {
        progressBar.visibility = View.GONE
        
        // Only enable button if status is not "Completed" or "Cancelled"
        if (payment?.jobOrderStatus != "Completed" && payment?.jobOrderStatus != "Cancelled") {
            actionButton.isEnabled = true
        }
    }
    
    private fun updateJobOrderStatus(status: String) {
        if (status.isBlank()) {
            Toast.makeText(this, "Status cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }
        
        payment?.let { payment ->
            // Show loading indicator
            showLoading("Updating status...")
            
            lifecycleScope.launch {
                try {
                    val token = sessionManager.getToken()
                    if (token != null) {
                        try {
                            // Create the status update object with the non-empty status
                            val statusUpdate = JobOrderStatusUpdate(status)
                            Log.d(TAG, "Sending status update: $statusUpdate with status: $status")
                            Log.d(TAG, "Payment ID: ${payment.id}")
                            Log.d(TAG, "Auth token: Bearer $token")
                            
                            val response = apiService.updateJobOrderStatus(
                                paymentId = payment.id,
                                statusUpdate = statusUpdate,
                                authToken = "Bearer $token"
                            )
                            
                            Log.d(TAG, "Response code: ${response.code()}")
                            Log.d(TAG, "Response message: ${response.message()}")
                            Log.d(TAG, "Response body: ${response.body()}")
                            
                            if (response.isSuccessful && response.body() != null) {
                                // Update successful
                                val updatedPayment = response.body()
                                hideLoading()
                                
                                // Update UI
                                statusTextView.text = status
                                
                                if (mode == JobOrderStatusMode.ACCEPT) {
                                    // For Accept Job Order mode
                                    when (status) {
                                        "Accepted", "In-Progress" -> {
                                            // Navigate to the arrived at location screen
                                            val intent = Intent(this@DriverJobOrderStatusActivity, DriverArrivedAtTheLocationActivity::class.java)
                                            intent.putExtra("PAYMENT", updatedPayment)
                                            startActivity(intent)
                                        }
                                        "Cancelled" -> {
                                            // Show cancellation message and navigate back to job orders
                                            Toast.makeText(this@DriverJobOrderStatusActivity, "Job order cancelled successfully!", Toast.LENGTH_SHORT).show()
                                            navigateToJobOrders()
                                            statusTextView.setTextColor(ContextCompat.getColor(this@DriverJobOrderStatusActivity, R.color.material_red))
                                        }
                                        else -> {
                                            // Handle other statuses if needed
                                            val intent = Intent(this@DriverJobOrderStatusActivity, DriverArrivedAtTheLocationActivity::class.java)
                                            intent.putExtra("PAYMENT", updatedPayment)
                                            startActivity(intent)
                                        }
                                    }
                                } else {
                                    // Collection completed mode
                                    statusTextView.setTextColor(ContextCompat.getColor(this@DriverJobOrderStatusActivity, R.color.green))
                                    
                                    // Disable the button
                                    actionButton.isEnabled = false
                                    actionButton.alpha = 0.5f
                                    
                                    // Show success message
                                    Toast.makeText(this@DriverJobOrderStatusActivity, "Collection completed successfully!", Toast.LENGTH_SHORT).show()
                                    
                                    // Navigate back to DriverJobOrderActivity
                                    navigateToJobOrders()
                                }
                            } else {
                                // Error updating status but we'll proceed with local update
                                Log.e(TAG, "Server error: ${response.code()} - ${response.message()}")
                                
                                // Create a new payment object with updated status instead of using copy()
                                val currentDate = Date() // Get current date
                                val updatedPayment = Payment(
                                    id = payment.id,
                                    orderId = payment.orderId,
                                    customerName = payment.customerName,
                                    customerEmail = payment.customerEmail,
                                    address = payment.address,
                                    phoneNumber = payment.phoneNumber,
                                    paymentMethod = payment.paymentMethod,
                                    amount = payment.amount,
                                    tax = payment.tax,
                                    totalAmount = payment.totalAmount,
                                    notes = payment.notes,
                                    status = payment.status,
                                    paymentReference = payment.paymentReference,
                                    barangayId = payment.barangayId,
                                    latitude = payment.latitude,
                                    longitude = payment.longitude,
                                    driverId = payment.driverId,
                                    createdAt = payment.createdAt,
                                    updatedAt = if (status == "Completed") currentDate else payment.updatedAt,
                                    jobOrderStatus = status,
                                    wasteType = payment.wasteType
                                )
                                
                                hideLoading()
                                
                                // Show warning message
                                Toast.makeText(
                                    this@DriverJobOrderStatusActivity,
                                    "Server error, proceeding with local update",
                                    Toast.LENGTH_SHORT
                                ).show()
                                
                                // Continue with the flow using the locally updated payment
                                if (mode == JobOrderStatusMode.ACCEPT) {
                                    // For Accept Job Order mode
                                    when (status) {
                                        "Cancelled" -> {
                                            // Show cancellation message and navigate back to job orders
                                            Toast.makeText(this@DriverJobOrderStatusActivity, "Job order cancelled successfully!", Toast.LENGTH_SHORT).show()
                                            navigateToJobOrders()
                                        }
                                        else -> {
                                            val intent = Intent(this@DriverJobOrderStatusActivity, DriverArrivedAtTheLocationActivity::class.java)
                                            intent.putExtra("PAYMENT", updatedPayment)
                                            startActivity(intent)
                                        }
                                    }
                                } else {
                                    // Collection completed mode
                                    statusTextView.text = status
                                    statusTextView.setTextColor(ContextCompat.getColor(this@DriverJobOrderStatusActivity, R.color.green))
                                    
                                    // Disable the button
                                    actionButton.isEnabled = false
                                    actionButton.alpha = 0.5f
                                    
                                    // Navigate back to DriverJobOrderActivity
                                    navigateToJobOrders()
                                }
                            }
                        } catch (e: Exception) {
                            // Handle network error but still proceed
                            Log.e(TAG, "Network error: ${e.message}", e)
                            
                            // Create a new payment object with updated status instead of using copy()
                            val currentDate = Date() // Get current date
                            val updatedPayment = Payment(
                                id = payment.id,
                                orderId = payment.orderId,
                                customerName = payment.customerName,
                                customerEmail = payment.customerEmail,
                                address = payment.address,
                                phoneNumber = payment.phoneNumber,
                                paymentMethod = payment.paymentMethod,
                                amount = payment.amount,
                                tax = payment.tax,
                                totalAmount = payment.totalAmount,
                                notes = payment.notes,
                                status = payment.status,
                                paymentReference = payment.paymentReference,
                                barangayId = payment.barangayId,
                                latitude = payment.latitude,
                                longitude = payment.longitude,
                                driverId = payment.driverId,
                                createdAt = payment.createdAt,
                                updatedAt = if (status == "Completed") currentDate else payment.updatedAt,
                                jobOrderStatus = status,
                                wasteType = payment.wasteType
                            )
                            
                            hideLoading()
                            
                            Toast.makeText(
                                this@DriverJobOrderStatusActivity,
                                "Network error, proceeding with local update",
                                Toast.LENGTH_SHORT
                            ).show()
                            
                            // Continue with the flow using the locally updated payment
                            if (mode == JobOrderStatusMode.ACCEPT) {
                                // For Accept Job Order mode
                                when (status) {
                                    "Cancelled" -> {
                                        // Show cancellation message and navigate back to job orders
                                        Toast.makeText(this@DriverJobOrderStatusActivity, "Job order cancelled successfully!", Toast.LENGTH_SHORT).show()
                                        navigateToJobOrders()
                                    }
                                    else -> {
                                        val intent = Intent(this@DriverJobOrderStatusActivity, DriverArrivedAtTheLocationActivity::class.java)
                                        intent.putExtra("PAYMENT", updatedPayment)
                                        startActivity(intent)
                                    }
                                }
                            } else {
                                // Collection completed mode
                                statusTextView.text = status
                                statusTextView.setTextColor(ContextCompat.getColor(this@DriverJobOrderStatusActivity, R.color.green))
                                
                                // Disable the button
                                actionButton.isEnabled = false
                                actionButton.alpha = 0.5f
                                
                                // Navigate back to DriverJobOrderActivity
                                navigateToJobOrders()
                            }
                        }
                    } else {
                        hideLoading()
                        Toast.makeText(
                            this@DriverJobOrderStatusActivity,
                            "Authentication token not found",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: Exception) {
                    hideLoading()
                    Log.e(TAG, "Error updating job status: ${e.message}", e)
                    Toast.makeText(
                        this@DriverJobOrderStatusActivity,
                        "Error updating job status: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        } ?: run {
            // If payment is null, just handle based on mode
            if (mode == JobOrderStatusMode.ACCEPT) {
                // Navigate to the arrived at location screen with static data
                val intent = Intent(this, DriverArrivedAtTheLocationActivity::class.java)
                intent.putExtra("PAYMENT", payment)
                startActivity(intent)
            } else {
                // Collection completed mode - update UI
                statusTextView.text = status
                statusTextView.setTextColor(ContextCompat.getColor(this, R.color.green))
                
                // Disable the button
                actionButton.isEnabled = false
                actionButton.alpha = 0.5f
                
                // Show success message
                Toast.makeText(this, "Collection completed successfully!", Toast.LENGTH_SHORT).show()
                
                // Navigate back to DriverJobOrderActivity
                navigateToJobOrders()
            }
        }
    }
    
    private fun navigateToJobOrders() {
        // Add a small delay to show the success message before navigating
        actionButton.postDelayed({
            val intent = Intent(this, DriverJobOrderActivity::class.java)
            startActivity(intent)
            finish()
        }, 1500) // 1.5 seconds delay
    }
    
    private fun showConfirmationDialog() {
        // Show the dialog and overlay
        dialogOverlay.visibility = View.VISIBLE
        confirmationDialog.visibility = View.VISIBLE
        
        // Apply grayscale effect to background content
        mapView.alpha = 0.1f
        addressTextView.alpha = 0.1f
        fullNameTextView.alpha = 0.1f
        phoneNumberTextView.alpha = 0.1f
        paymentMethodTextView.alpha = 0.1f
        wasteTypeTextView.alpha = 0.1f
        totalTextView.alpha = 0.1f
        statusTextView.alpha = 0.1f
        actionButton.alpha = 0.1f
        amountTextView.alpha = 0.1f
        taxValueTextView.alpha = 0.1f
        
        // Find all parent views that contain the content and reduce their alpha
        findViewById<View>(R.id.addressCard)?.alpha = 0.1f
        findViewById<View>(R.id.orderDetailsCard)?.alpha = 0.1f
    }
    
    private fun hideConfirmationDialog() {
        // Hide the dialog and overlay
        dialogOverlay.visibility = View.GONE
        confirmationDialog.visibility = View.GONE
        
        // Restore normal appearance to background content
        mapView.alpha = 1.0f
        addressTextView.alpha = 1.0f
        fullNameTextView.alpha = 1.0f
        phoneNumberTextView.alpha = 1.0f
        paymentMethodTextView.alpha = 1.0f
        wasteTypeTextView.alpha = 1.0f
        totalTextView.alpha = 1.0f
        statusTextView.alpha = 1.0f
        actionButton.alpha = 1.0f
        amountTextView.alpha = 1.0f
        taxValueTextView.alpha = 1.0f
        
        // Restore alpha for parent views
        findViewById<View>(R.id.addressCard)?.alpha = 1.0f
        findViewById<View>(R.id.orderDetailsCard)?.alpha = 1.0f
        
        // If job order is completed, keep the action button at reduced alpha
        if (payment?.jobOrderStatus == "Completed") {
            actionButton.alpha = 0.5f
        }
    }
    
    private fun loadPaymentDetails() {
        // Get the payment from the intent
        payment = intent.getSerializableExtra("PAYMENT") as? Payment
        
        if (payment != null) {
            // Use the payment data
            customerLatitude = payment?.latitude ?: 0.0
            customerLongitude = payment?.longitude ?: 0.0
            customerLocation = GeoPoint(customerLatitude, customerLongitude)
            
            // Setup map with customer location
            setupMap()
            
            // Format currency
            val formatter = NumberFormat.getCurrencyInstance(Locale("en", "PH"))
            
            // Populate the views with the payment details
            addressTextView.text = payment?.address
            fullNameTextView.text = payment?.customerName
            phoneNumberTextView.text = payment?.phoneNumber
            paymentMethodTextView.text = payment?.paymentMethod
            wasteTypeTextView.text = payment?.wasteType ?: "N/A"
            totalTextView.text = formatter.format(payment?.totalAmount)
            
            // Set job order status
            val jobOrderStatus = payment?.jobOrderStatus ?: if (mode == JobOrderStatusMode.ACCEPT) "Available" else "In-Progress"
            statusTextView.text = jobOrderStatus
            
            // Update toolbar title based on status
            updateToolbarTitle(jobOrderStatus)
            
            // Set additional payment details
            amountTextView.text = formatter.format(payment?.amount)
            taxValueTextView.text = formatter.format(payment?.tax)
            
            // Update button text and state based on status
            when (payment?.jobOrderStatus) {
                "Completed" -> {
                    actionButton.isEnabled = false
                    actionButton.text = if (mode == JobOrderStatusMode.ACCEPT) "JOB ORDER COMPLETED" else "COLLECTION ALREADY COMPLETED"
                    actionButton.alpha = 0.5f
                    
                    // Hide cancel button and center the accept button with 400dp width
                    if (mode == JobOrderStatusMode.ACCEPT && ::cancelJobOrderButton.isInitialized) {
                        cancelJobOrderButton.visibility = View.GONE
                        
                        // Update accept button constraints to center it with 400dp width
                        val layoutParams = actionButton.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
                        layoutParams.width = (400 * resources.displayMetrics.density).toInt() // Convert 400dp to pixels
                        layoutParams.marginEnd = 0 // Remove end margin
                        layoutParams.startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
                        layoutParams.endToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
                        layoutParams.endToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
                        actionButton.layoutParams = layoutParams
                    }
                }
                "Cancelled" -> {
                    actionButton.isEnabled = false
                    actionButton.text = if (mode == JobOrderStatusMode.ACCEPT) "JOB ORDER CANCELLED" else "JOB ORDER CANCELLED"
                    actionButton.alpha = 0.5f
                    
                    // Hide cancel button and center the accept button with 400dp width
                    if (mode == JobOrderStatusMode.ACCEPT && ::cancelJobOrderButton.isInitialized) {
                        cancelJobOrderButton.visibility = View.GONE
                        
                        // Update accept button constraints to center it with 400dp width
                        val layoutParams = actionButton.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
                        layoutParams.width = (400 * resources.displayMetrics.density).toInt() // Convert 400dp to pixels
                        layoutParams.marginEnd = 0 // Remove end margin
                        layoutParams.startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
                        layoutParams.endToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
                        layoutParams.endToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
                        actionButton.layoutParams = layoutParams
                    }
                }
                "In-Progress", "Accepted" -> {
                    if (mode == JobOrderStatusMode.ACCEPT) {
                        actionButton.text = if (payment?.jobOrderStatus == "In-Progress") "CONTINUE TO LOCATION" else "GO TO LOCATION"
                        
                        // Make sure cancel button is visible and restore original button layout
                        if (::cancelJobOrderButton.isInitialized) {
                            cancelJobOrderButton.visibility = View.VISIBLE
                            
                            // Restore original accept button constraints (side by side layout)
                            val layoutParams = actionButton.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
                            layoutParams.width = 0 // 0dp for match_constraint
                            layoutParams.marginEnd = (8 * resources.displayMetrics.density).toInt() // 8dp margin
                            layoutParams.startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
                            layoutParams.endToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
                            layoutParams.endToStart = cancelJobOrderButton.id
                            actionButton.layoutParams = layoutParams
                        }
                    } else {
                        actionButton.text = "COLLECTION COMPLETED"
                    }
                    
                    // Hide and disable back button for In-Progress and Accepted status
                    if (::backButton.isInitialized) {
                        backButton.visibility = View.INVISIBLE
                        backButton.isEnabled = false
                    }
                }
                else -> {
                    // Default button text is already set in the layout
                    // Make sure cancel button is visible and restore original layout for default statuses (like "Available")
                    if (mode == JobOrderStatusMode.ACCEPT && ::cancelJobOrderButton.isInitialized) {
                        cancelJobOrderButton.visibility = View.VISIBLE
                        
                        // Restore original accept button constraints (side by side layout)
                        val layoutParams = actionButton.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
                        layoutParams.width = 0 // 0dp for match_constraint
                        layoutParams.marginEnd = (8 * resources.displayMetrics.density).toInt() // 8dp margin
                        layoutParams.startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
                        layoutParams.endToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
                        layoutParams.endToStart = cancelJobOrderButton.id
                        actionButton.layoutParams = layoutParams
                    }
                }
            }
        } else {
            // Fallback to static data if no payment is provided
            loadFallbackJobOrder()
        }
    }
    
    private fun updateToolbarTitle(status: String) {
        val title = when (status) {
            "Available" -> "Available Job Order"
            "In-Progress" -> "In-Progress Job Order"
            "Completed" -> "Completed Job Order"
            "Cancelled" -> "Cancelled Job Order"
            else -> if (mode == JobOrderStatusMode.ACCEPT) "Available Job Order" else "Collection Completed"
        }
        
        toolbarTitleTextView.text = title
    }
    
    private fun loadFallbackJobOrder() {
        // For now, we'll use static data that matches the image
        val jobOrder = JobOrder(
            id = "JO-001",
            customerName = "Name",
            address = "7953 Oakland St.",
            city = "Honolulu",
            state = "HI",
            zipCode = "96815",
            price = "0",
            phoneNumber = "+63121212121",
            paymentMethod = "Cash on Hand",
            wasteType = "Plastic",
            status = OrderStatus.PENDING
        )
        
        // For the demo, let's hardcode a location in Honolulu, Hawaii
        // In a real implementation, you would geocode the address or get coordinates from API
        customerLatitude = 21.3069 // Honolulu latitude
        customerLongitude = -157.8583 // Honolulu longitude
        customerLocation = GeoPoint(customerLatitude, customerLongitude)
        
        // Setup map with customer location
        setupMap()
        
        // Populate the views with the job order details
        addressTextView.text = "${jobOrder.address} ${jobOrder.city}, ${jobOrder.state} ${jobOrder.zipCode}"
        fullNameTextView.text = jobOrder.customerName
        phoneNumberTextView.text = jobOrder.phoneNumber
        paymentMethodTextView.text = jobOrder.paymentMethod
        wasteTypeTextView.text = jobOrder.wasteType
        totalTextView.text = "₱${jobOrder.price}"
        
        // Set amount to same as total for consistency
        amountTextView.text = "₱${jobOrder.price}"
        
        // Set collection fee (tax) to a default value
        taxValueTextView.text = "₱50"
        
        // Set default status and update toolbar title
        val defaultStatus = if (mode == JobOrderStatusMode.ACCEPT) "Available" else "In-Progress"
        statusTextView.text = defaultStatus
        updateToolbarTitle(defaultStatus)
        
        // Ensure cancel button is visible and restore original layout for default fallback status
        if (mode == JobOrderStatusMode.ACCEPT && ::cancelJobOrderButton.isInitialized) {
            cancelJobOrderButton.visibility = View.VISIBLE
            
            // Restore original accept button constraints (side by side layout)
            val layoutParams = actionButton.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            layoutParams.width = 0 // 0dp for match_constraint
            layoutParams.marginEnd = (8 * resources.displayMetrics.density).toInt() // 8dp margin
            layoutParams.startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            layoutParams.endToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
            layoutParams.endToStart = cancelJobOrderButton.id
            actionButton.layoutParams = layoutParams
        }
    }
    
    private fun setupMap() {
        customerLocation?.let { location ->
            // Center map on the customer location
            mapView.controller.setCenter(location)
            
            // Add a marker for the customer location
            val marker = Marker(mapView)
            marker.position = location
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            
            // Get the drawable and apply green tint
            val pinDrawable = ContextCompat.getDrawable(this, R.drawable.ic_location_pin)?.mutate()
            pinDrawable?.setColorFilter(ContextCompat.getColor(this, R.color.green), android.graphics.PorterDuff.Mode.SRC_IN)
            marker.icon = pinDrawable
            
            // Add click listener to the marker
            marker.setOnMarkerClickListener { clickedMarker, _ ->

                // Center map on marker when clicked with an offset for the info window
                val offsetPoint = GeoPoint(clickedMarker.position.latitude - 0.0001, clickedMarker.position.longitude)
                mapView.controller.animateTo(offsetPoint)

                true
            }
            
            // Add the marker to the map
            mapView.overlays.add(marker)
            
            // Refresh the map
            mapView.invalidate()
        }
    }
    
    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        } else {
            enableMyLocation()
        }
    }
    
    private fun enableMyLocation() {
        try {
            // Add my location overlay to show current driver location
            val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), mapView)
            locationOverlay.enableMyLocation()
            mapView.overlays.add(locationOverlay)
        } catch (e: Exception) {
            Log.e(TAG, "Error enabling location overlay: ${e.message}")
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableMyLocation()
            } else {
                Toast.makeText(
                    this,
                    "Location permission denied. Some features may not work.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }
    
    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        mapView.onDetach()
    }
    
    override fun onBackPressed() {
        // If confirmation dialog is showing, hide it instead of going back
        if (mode != JobOrderStatusMode.ACCEPT && 
            ::confirmationDialog.isInitialized && 
            confirmationDialog.visibility == View.VISIBLE) {
            hideConfirmationDialog()
            return
        }
        
        // Prevent going back if status is In-Progress or Accepted
        if (payment?.jobOrderStatus == "In-Progress" || payment?.jobOrderStatus == "Accepted") {
            val statusText = payment?.jobOrderStatus ?: "active"
            Toast.makeText(this, "Cannot go back during an $statusText job", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Handle back navigation based on mode
        when (mode) {
            JobOrderStatusMode.ACCEPT -> {
                // Go back to job orders screen
                navigateToJobOrdersScreen()
            }
            JobOrderStatusMode.COMPLETE -> {
                // If job is already completed, go back to job orders screen
                if (payment?.jobOrderStatus == "Completed") {
                    navigateToJobOrdersScreen()
                } else {
                    // Otherwise just finish the activity
                    super.onBackPressed()
                }
            }
            JobOrderStatusMode.VIEW -> {
                // In view mode, just go back
                super.onBackPressed()
            }
        }
    }
    
    private fun navigateToJobOrdersScreen() {
        val intent = Intent(this, DriverJobOrderActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        startActivity(intent)
        finish()
    }
} 