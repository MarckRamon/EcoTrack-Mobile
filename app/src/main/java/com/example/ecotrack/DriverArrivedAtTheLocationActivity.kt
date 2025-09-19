package com.example.ecotrack

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.ecotrack.models.JobOrderStatusUpdate
import com.example.ecotrack.models.payment.Payment
import com.example.ecotrack.utils.ApiService
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

class DriverArrivedAtTheLocationActivity : BaseActivity() {

    private val TAG = "DriverArrivedLocation"
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001

    // UI components
    private lateinit var mapView: MapView
    private lateinit var addressTextView: TextView
    private lateinit var customerNameTextView: TextView
    private lateinit var phoneTextView: TextView
    private lateinit var wasteTypeTextView: TextView
    private lateinit var orderTypeTextView: TextView
    private lateinit var arrivedButton: Button
    private lateinit var confirmationDialog: CardView
    private lateinit var confirmButton: Button
    private lateinit var cancelButton: Button
    private lateinit var dialogOverlay: View
    private lateinit var progressBar: ProgressBar
    private lateinit var locationToggleButton: FloatingActionButton

    // Location data
    private var customerLatitude = 0.0 // Will be set from payment data
    private var customerLongitude = 0.0 // Will be set from payment data
    private var customerLocation: GeoPoint? = null
    private var customerName: String = ""
    private var customerPhone: String = ""
    private var customerAddress: String = ""
    private var wasteType: String = ""
    private lateinit var myLocationOverlay: MyLocationNewOverlay
    private var isLocationEnabled = false
    
    // Payment data
    private var payment: Payment? = null
    
    // API Service
    private lateinit var apiService: ApiService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize OpenStreetMap configuration
        Configuration.getInstance().load(
            applicationContext,
            PreferenceManager.getDefaultSharedPreferences(applicationContext)
        )
        
        setContentView(R.layout.activity_driver_arrived_at_location)

        // Initialize API service
        apiService = ApiService.create()
        
        // Initialize views
        initViews()
        
        // Set up listeners
        setupListeners()
        
        // Get payment data from intent
        getPaymentData()
        
        // Check location permission for map
        checkLocationPermission()
    }
    
    private fun initViews() {
        mapView = findViewById(R.id.mapView)
        addressTextView = findViewById(R.id.addressTextView)
        customerNameTextView = findViewById(R.id.customerNameTextView)
        phoneTextView = findViewById(R.id.phoneTextView)
        wasteTypeTextView = findViewById(R.id.wasteTypeTextView)
        orderTypeTextView = findViewById(R.id.orderTypeTextView)
        arrivedButton = findViewById(R.id.arrivedButton)
        confirmationDialog = findViewById(R.id.confirmationDialog)
        confirmButton = findViewById(R.id.confirmButton)
        cancelButton = findViewById(R.id.cancelButton)
        dialogOverlay = findViewById(R.id.dialogOverlay)
        progressBar = findViewById(R.id.progressBar)
        locationToggleButton = findViewById(R.id.locationToggleButton)
        
        // Configure the map
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        
        // Update location toggle button UI
        updateLocationToggleButton()
    }
    
    private fun setupListeners() {
        arrivedButton.setOnClickListener {
            showConfirmationDialog()
        }
        
        confirmButton.setOnClickListener {
            hideConfirmationDialog()
            
            // Update job order status to "In-Progress"
            updateJobOrderStatus("In-Progress")
        }
        
        cancelButton.setOnClickListener {
            hideConfirmationDialog()
        }
        
        // Set up location toggle button
        locationToggleButton.setOnClickListener {
            toggleLocationTracking()
        }
    }
    
    private fun toggleLocationTracking() {
        isLocationEnabled = !isLocationEnabled
        
        if (isLocationEnabled) {
            enableMyLocation()
        } else {
            disableMyLocation()
        }
        
        updateLocationToggleButton()
    }
    
    private fun updateLocationToggleButton() {
        if (isLocationEnabled) {
            locationToggleButton.setImageResource(R.drawable.ic_location_on)
            locationToggleButton.backgroundTintList = ContextCompat.getColorStateList(this, R.color.secondary)
            locationToggleButton.imageTintList = ContextCompat.getColorStateList(this, R.color.white)
        } else {
            locationToggleButton.setImageResource(R.drawable.ic_location_on)
            locationToggleButton.backgroundTintList = ContextCompat.getColorStateList(this, R.color.white)
            locationToggleButton.imageTintList = ContextCompat.getColorStateList(this, R.color.secondary)
        }
    }
    
    private fun showLoading(message: String) {
        progressBar.visibility = View.VISIBLE
        arrivedButton.isEnabled = false
    }
    
    private fun hideLoading() {
        progressBar.visibility = View.GONE
        arrivedButton.isEnabled = true
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
                                
                                // Show success message
                                Toast.makeText(this@DriverArrivedAtTheLocationActivity, "Arrival confirmed!", Toast.LENGTH_SHORT).show()
                                
                                // Navigate to the collection completed screen
                                val intent = Intent(this@DriverArrivedAtTheLocationActivity, DriverJobOrderStatusActivity::class.java)
                                
                                // Pass the updated payment data to the next activity
                                intent.putExtra("PAYMENT", updatedPayment)
                                intent.putExtra("MODE", DriverJobOrderStatusActivity.JobOrderStatusMode.COMPLETE)
                                
                                startActivity(intent)
                                finish()
                            } else {
                                // Error updating status but we'll proceed with local update
                                Log.e(TAG, "Server error: ${response.code()} - ${response.message()}")
                                
                                // Create a new payment object with updated status instead of using copy()
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
                                    updatedAt = payment.updatedAt,
                                    jobOrderStatus = status,
                                    wasteType = payment.wasteType
                                )
                                
                                hideLoading()
                                
                                // Show warning message
                                Toast.makeText(
                                    this@DriverArrivedAtTheLocationActivity,
                                    "Server error, proceeding with local update",
                                    Toast.LENGTH_SHORT
                                ).show()
                                
                                // Navigate to the collection completed screen with locally updated payment
                                val intent = Intent(this@DriverArrivedAtTheLocationActivity, DriverJobOrderStatusActivity::class.java)
                                intent.putExtra("PAYMENT", updatedPayment)
                                intent.putExtra("MODE", DriverJobOrderStatusActivity.JobOrderStatusMode.COMPLETE)
                                
                                startActivity(intent)
                                finish()
                            }
                        } catch (e: Exception) {
                            // Handle network error but still proceed
                            Log.e(TAG, "Network error: ${e.message}", e)
                            
                            // Create a new payment object with updated status instead of using copy()
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
                                updatedAt = payment.updatedAt,
                                jobOrderStatus = status,
                                wasteType = payment.wasteType
                            )
                            
                            hideLoading()
                            
                            Toast.makeText(
                                this@DriverArrivedAtTheLocationActivity,
                                "Network error, proceeding with local update",
                                Toast.LENGTH_SHORT
                            ).show()
                            
                            // Navigate to the collection completed screen with locally updated payment
                            val intent = Intent(this@DriverArrivedAtTheLocationActivity, DriverJobOrderStatusActivity::class.java)
                            intent.putExtra("PAYMENT", updatedPayment)
                            intent.putExtra("MODE", DriverJobOrderStatusActivity.JobOrderStatusMode.COMPLETE)
                            
                            startActivity(intent)
                            finish()
                        }
                    } else {
                        hideLoading()
                        Toast.makeText(
                            this@DriverArrivedAtTheLocationActivity,
                            "Authentication token not found",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: Exception) {
                    hideLoading()
                    Log.e(TAG, "Error updating job status: ${e.message}", e)
                    Toast.makeText(
                        this@DriverArrivedAtTheLocationActivity,
                        "Error updating job status: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        } ?: run {
            // If payment is null, just navigate to the next screen with static data
            Toast.makeText(this, "Arrival confirmed!", Toast.LENGTH_SHORT).show()
            
            // Navigate to the collection completed screen
            val intent = Intent(this, DriverJobOrderStatusActivity::class.java)
            intent.putExtra("PAYMENT", payment)
            intent.putExtra("MODE", DriverJobOrderStatusActivity.JobOrderStatusMode.COMPLETE)
            
            startActivity(intent)
            finish()
        }
    }
    
    private fun getPaymentData() {
        payment = intent.getSerializableExtra("PAYMENT") as? Payment
        
        if (payment != null) {
            // Use the payment data
            customerLatitude = payment?.latitude ?: 0.0
            customerLongitude = payment?.longitude ?: 0.0
            customerName = payment?.customerName ?: ""
            customerPhone = payment?.phoneNumber ?: ""
            customerAddress = payment?.address ?: ""
            wasteType = payment?.wasteType ?: ""
            
            Log.d(TAG, "Payment coordinates: Lat=$customerLatitude, Long=$customerLongitude")
            
            // Update UI
            addressTextView.text = customerAddress
            customerNameTextView.text = customerName
            phoneTextView.text = customerPhone
            wasteTypeTextView.text = wasteType
            orderTypeTextView.text = "Job Order"
        } else {
            // Fallback to demo data
            customerLatitude = 21.3069 // Honolulu latitude
            customerLongitude = -157.8583 // Honolulu longitude
            customerName = "Customer Name"
            customerPhone = "+639127463218"
            customerAddress = "7953 Oakland St. Honolulu, HI 96815"
            wasteType = "Biodegradable"
            
            Log.d(TAG, "Using fallback coordinates: Lat=$customerLatitude, Long=$customerLongitude")
            
            // Update UI
            addressTextView.text = customerAddress
            customerNameTextView.text = customerName
            phoneTextView.text = customerPhone
            wasteTypeTextView.text = wasteType
            orderTypeTextView.text = "Job Order"
        }
        
        // Create the GeoPoint with the coordinates
        customerLocation = GeoPoint(customerLatitude, customerLongitude)
        Log.d(TAG, "Customer GeoPoint created: $customerLocation")
        
        // Setup map with customer location after coordinates are set
        setupMap()
    }
    
    private fun setupMap() {
        customerLocation?.let { location ->
            Log.d(TAG, "Setting up map with location: $location")
            
            // Set zoom level first (more appropriate for street-level view)
            mapView.controller.setZoom(18.0)
            
            // Center map on the customer location
            mapView.controller.setCenter(location)
            
            // Add a marker for the customer location
            val marker = Marker(mapView)
            marker.position = location
            marker.title = customerName
            marker.snippet = "Phone: $customerPhone\nAddress: $customerAddress\nWaste Type: $wasteType\nType: Job Order"
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            
            // Set marker icon with green color for job order
            val drawable = ContextCompat.getDrawable(this, R.drawable.ic_location_pin)?.mutate()
            drawable?.setColorFilter(ContextCompat.getColor(this, R.color.secondary), android.graphics.PorterDuff.Mode.SRC_IN)
            marker.icon = drawable
            
            // Store the job order ID in the marker
            marker.id = payment?.id ?: "default"
            
            // Disable info window
            marker.infoWindow = null
            
            // Add the marker to the map
            mapView.overlays.add(marker)
            
            // Refresh the map
            mapView.invalidate()
            
            Log.d(TAG, "Map setup complete")
        } ?: run {
            Log.e(TAG, "Customer location is null, cannot setup map")
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
            setupLocationOverlay()
        }
    }
    
    private fun setupLocationOverlay() {
        try {
            // Create my location overlay but don't enable it yet
            myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), mapView)
            // Location is disabled by default
            disableMyLocation()
            Log.d(TAG, "Location overlay created but disabled by default")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up location overlay: ${e.message}")
        }
    }
    
    private fun enableMyLocation() {
        if (::myLocationOverlay.isInitialized) {
            myLocationOverlay.enableMyLocation()
            myLocationOverlay.enableFollowLocation()
            
            // Add overlay to map if not already added
            if (!mapView.overlays.contains(myLocationOverlay)) {
            mapView.overlays.add(myLocationOverlay)
            }
            
            Log.d(TAG, "My location enabled")
        }
    }
    
    private fun disableMyLocation() {
        if (::myLocationOverlay.isInitialized) {
            myLocationOverlay.disableMyLocation()
            myLocationOverlay.disableFollowLocation()
            Log.d(TAG, "My location disabled")
        }
    }
    
    private fun showConfirmationDialog() {
        dialogOverlay.visibility = View.VISIBLE
        confirmationDialog.visibility = View.VISIBLE
    }
    
    private fun hideConfirmationDialog() {
        dialogOverlay.visibility = View.GONE
        confirmationDialog.visibility = View.GONE
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setupLocationOverlay()
                Toast.makeText(
                    this,
                    "Location permission granted. Click the location button to enable tracking.",
                    Toast.LENGTH_SHORT
                ).show()
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
        
        // If location was enabled before, re-enable it
        if (isLocationEnabled && ::myLocationOverlay.isInitialized) {
            enableMyLocation()
        }
    }
    
    override fun onPause() {
        super.onPause()
        mapView.onPause()
        
        // Disable location when activity is paused to save battery
        if (::myLocationOverlay.isInitialized) {
            disableMyLocation()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        mapView.onDetach()
    }
    
    override fun onBackPressed() {
        // If confirmation dialog is showing, hide it instead of going back
        if (::confirmationDialog.isInitialized && confirmationDialog.visibility == View.VISIBLE) {
            hideConfirmationDialog()
            return
        }
        
        // Prevent going back if this is an accepted job
        if (payment?.jobOrderStatus == "Accepted" || payment?.jobOrderStatus == "In-Progress") {
            val statusText = payment?.jobOrderStatus ?: "active"
            Toast.makeText(this, "Cannot go back during an $statusText job", Toast.LENGTH_SHORT).show()
            return
        }
        
        super.onBackPressed()
    }
} 