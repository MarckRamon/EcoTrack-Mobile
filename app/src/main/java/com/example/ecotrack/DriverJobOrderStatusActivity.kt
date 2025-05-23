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
        COMPLETE     // For completing collections (similar to DriverCollectionCompletedActivity)
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
        
        // Initialize mode-specific views
        if (mode == JobOrderStatusMode.ACCEPT) {
            statusTextView = findViewById(R.id.jobOrderStatusTextView)
            actionButton = findViewById(R.id.acceptJobOrderButton)
            try {
                backButton = findViewById(R.id.backButton)
                backButton.setOnClickListener { onBackPressed() }
            } catch (e: Exception) {
                Log.e(TAG, "Back button not found in layout: ${e.message}")
            }
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
        }
        
        // Configure the map
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(16.0)
    }
    
    private fun setupListeners() {
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
                        // Update job order status to "Accepted"
                        updateJobOrderStatus("Accepted")
                    }
                }
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
    
    private fun showLoading(message: String) {
        progressBar.visibility = View.VISIBLE
        actionButton.isEnabled = false
    }
    
    private fun hideLoading() {
        progressBar.visibility = View.GONE
        
        // Only enable button if status is not "Completed"
        if (payment?.jobOrderStatus != "Completed") {
            actionButton.isEnabled = true
        }
    }
    
    private fun updateJobOrderStatus(status: String) {
        payment?.let { payment ->
            // Show loading indicator
            showLoading("Updating status...")
            
            lifecycleScope.launch {
                try {
                    val token = sessionManager.getToken()
                    if (token != null) {
                        val response = apiService.updateJobOrderStatus(
                            paymentId = payment.id,
                            statusUpdate = JobOrderStatusUpdate(status),
                            authToken = "Bearer $token"
                        )
                        
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
                            // Error updating status
                            hideLoading()
                            Toast.makeText(
                                this@DriverJobOrderStatusActivity,
                                "Failed to update job status: ${response.message()}",
                                Toast.LENGTH_SHORT
                            ).show()
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
                }
                "In-Progress" -> {
                    if (mode == JobOrderStatusMode.ACCEPT) {
                        actionButton.text = "CONTINUE TO LOCATION"
                    } else {
                        actionButton.text = "COLLECTION COMPLETED"
                    }
                }
                "Accepted" -> {
                    if (mode == JobOrderStatusMode.ACCEPT) {
                        actionButton.text = "GO TO LOCATION"
                    }
                }
                else -> {
                    // Default button text is already set in the layout
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
            else -> if (mode == JobOrderStatusMode.ACCEPT) "Available Job Order" else "Collection Completed"
        }
        
        toolbarTitleTextView.text = title
    }
    
    private fun loadFallbackJobOrder() {
        // For now, we'll use static data that matches the image
        val jobOrder = JobOrder(
            id = "JO-001",
            customerName = "Miggy Chan",
            address = "7953 Oakland St.",
            city = "Honolulu",
            state = "HI",
            zipCode = "96815",
            price = "550",
            phoneNumber = "+639127463218",
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
    }
    
    private fun setupMap() {
        customerLocation?.let { location ->
            // Center map on the customer location
            mapView.controller.setCenter(location)
            
            // Add a marker for the customer location
            val marker = Marker(mapView)
            marker.position = location
            marker.title = "Customer Location"
            marker.snippet = "Pickup Address\n${addressTextView.text}"
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            marker.icon = ContextCompat.getDrawable(this, R.drawable.ic_location_pin)
            
            // Create and use a custom info window
            val infoWindow = CustomInfoWindow(R.layout.marker_info_window, mapView)
            marker.infoWindow = infoWindow
            
            // Add click listener to the marker
            marker.setOnMarkerClickListener { clickedMarker, _ ->
                // Close any other open info windows
                mapView.overlays.filterIsInstance<Marker>().forEach { 
                    if (it != clickedMarker && it.isInfoWindowShown) {
                        it.closeInfoWindow()
                    }
                }
                
                // Center map on marker when clicked with an offset for the info window
                val offsetPoint = GeoPoint(clickedMarker.position.latitude - 0.0001, clickedMarker.position.longitude)
                mapView.controller.animateTo(offsetPoint)
                
                // Show the info window
                if (!clickedMarker.isInfoWindowShown) {
                    clickedMarker.showInfoWindow()
                } else {
                    clickedMarker.closeInfoWindow()
                }
                
                true
            }
            
            // Add the marker to the map
            mapView.overlays.add(marker)
            
            // Show info window by default
            marker.showInfoWindow()
            
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
} 