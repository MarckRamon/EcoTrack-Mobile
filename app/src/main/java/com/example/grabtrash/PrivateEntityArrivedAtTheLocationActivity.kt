package com.example.grabtrash

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
import com.example.grabtrash.models.DeliveryStatusUpdate
import com.example.grabtrash.models.PrivateEntity
import com.example.grabtrash.models.payment.Payment
import com.example.grabtrash.utils.ApiService
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

class PrivateEntityArrivedAtTheLocationActivity : BaseActivity() {

    private val TAG = "PrivateEntityArrived"
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001

    // UI components
    private lateinit var mapView: MapView
    private lateinit var addressTextView: TextView
    private lateinit var entityNameTextView: TextView
    private lateinit var entityWasteTypeTextView: TextView
    private lateinit var entityStatusTextView: TextView
    private lateinit var deliverButton: Button
    private lateinit var confirmationDialog: CardView
    private lateinit var confirmButton: Button
    private lateinit var cancelButton: Button
    private lateinit var dialogOverlay: View
    private lateinit var progressBar: ProgressBar
    private lateinit var locationToggleButton: FloatingActionButton

    // Location data
    private var entityLatitude = 0.0
    private var entityLongitude = 0.0
    private var entityLocation: GeoPoint? = null
    private lateinit var myLocationOverlay: MyLocationNewOverlay
    private var isLocationEnabled = false
    
    // Entity and Payment data
    private var privateEntity: PrivateEntity? = null
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
        
        setContentView(R.layout.activity_private_entity_arrived_at_location)

        // Initialize API service
        apiService = ApiService.create()
        
        // Initialize views
        initViews()
        
        // Set up listeners
        setupListeners()
        
        // Get data from intent
        getIntentData()
        
        // Check location permission for map
        checkLocationPermission()
    }
    
    private fun initViews() {
        mapView = findViewById(R.id.mapView)
        addressTextView = findViewById(R.id.addressTextView)
        entityNameTextView = findViewById(R.id.entityNameTextView)
        entityWasteTypeTextView = findViewById(R.id.entityWasteTypeTextView)
        entityStatusTextView = findViewById(R.id.entityStatusTextView)
        deliverButton = findViewById(R.id.deliverButton)
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
        deliverButton.setOnClickListener {
            showConfirmationDialog()
        }
        
        confirmButton.setOnClickListener {
            hideConfirmationDialog()
            
            // Update delivery status to true
            updateDeliveryStatus(true)
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
        deliverButton.isEnabled = false
    }
    
    private fun hideLoading() {
        progressBar.visibility = View.GONE
        deliverButton.isEnabled = true
    }
    
    private fun updateDeliveryStatus(isDelivered: Boolean) {
        payment?.let { payment ->
            // Show loading indicator
            showLoading("Updating delivery status...")
            
            lifecycleScope.launch {
                try {
                    val token = sessionManager.getToken()
                    if (token != null) {
                        try {
                            // Create the delivery status update object
                            val deliveryStatusUpdate = DeliveryStatusUpdate(isDelivered)
                            Log.d(TAG, "Sending delivery status update: $deliveryStatusUpdate")
                            Log.d(TAG, "Payment ID: ${payment.id}")
                            Log.d(TAG, "Auth token: Bearer $token")
                            
                            val response = apiService.updateDeliveryStatus(
                                paymentId = payment.id,
                                deliveryStatus = deliveryStatusUpdate,
                                authToken = "Bearer $token"
                            )
                            
                            Log.d(TAG, "Response code: ${response.code()}")
                            Log.d(TAG, "Response message: ${response.message()}")
                            Log.d(TAG, "Response body: ${response.body()}")
                            
                            if (response.isSuccessful && response.body() != null) {
                                // Update successful
                                hideLoading()
                                
                                // Show success message
                                Toast.makeText(this@PrivateEntityArrivedAtTheLocationActivity, "Delivery confirmed!", Toast.LENGTH_SHORT).show()
                                
                                // Navigate back to the job orders screen
                                val intent = Intent(this@PrivateEntityArrivedAtTheLocationActivity, DriverJobOrderActivity::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                                startActivity(intent)
                                finish()
                            } else {
                                // Error updating status
                                hideLoading()
                                Log.e(TAG, "Server error: ${response.code()} - ${response.message()}")
                                Toast.makeText(
                                    this@PrivateEntityArrivedAtTheLocationActivity,
                                    "Failed to update delivery status: ${response.message()}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        } catch (e: Exception) {
                            hideLoading()
                            Log.e(TAG, "Exception during API call: ${e.message}")
                            Toast.makeText(
                                this@PrivateEntityArrivedAtTheLocationActivity,
                                "Error: ${e.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } else {
                        hideLoading()
                        Toast.makeText(
                            this@PrivateEntityArrivedAtTheLocationActivity,
                            "Authentication token not found. Please log in again.",
                            Toast.LENGTH_LONG
                        ).show()
                        navigateToLogin()
                    }
                } catch (e: Exception) {
                    hideLoading()
                    Log.e(TAG, "Exception: ${e.message}")
                    Toast.makeText(
                        this@PrivateEntityArrivedAtTheLocationActivity,
                        "Error: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        } ?: run {
            Toast.makeText(this, "Payment information not found", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun getIntentData() {
        // Get the private entity from intent
        privateEntity = intent.getSerializableExtra("SELECTED_ENTITY") as? PrivateEntity
        
        // Get the payment from intent
        payment = intent.getSerializableExtra("PAYMENT") as? Payment
        
        if (privateEntity == null) {
            Toast.makeText(this, "Private entity information not found", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        
        if (payment == null) {
            Toast.makeText(this, "Payment information not found", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        
        // Update UI with entity information
        updateEntityInfo()
        
        // Update map with entity location
        updateMapWithEntityLocation()
    }
    
    private fun updateEntityInfo() {
        privateEntity?.let { entity ->
            entityNameTextView.text = entity.entityName
            addressTextView.text = entity.address
            entityWasteTypeTextView.text = entity.entityWasteType
            
            // Set status with appropriate color
            entityStatusTextView.text = entity.entityStatus
            if (entity.entityStatus.equals("OPEN", ignoreCase = true)) {
                entityStatusTextView.setTextColor(ContextCompat.getColor(this, R.color.secondary))
            } else {
                entityStatusTextView.setTextColor(ContextCompat.getColor(this, R.color.error))
            }
            
            // Store location data
            entityLatitude = entity.latitude
            entityLongitude = entity.longitude
            entityLocation = GeoPoint(entityLatitude, entityLongitude)
            
            // Display phone number if available
            try {
                val phoneTextView = findViewById<TextView>(R.id.phoneTextView)
                // Check if the phoneNumber property exists in the entity
                val phoneNumber = entity.phoneNumber
                
                if (phoneNumber != null && phoneNumber.isNotEmpty()) {
                    phoneTextView.text = phoneNumber
                } else {
                    // Try to get phone number from the payment object as fallback
                    val paymentPhoneNumber = payment?.phoneNumber
                    if (!paymentPhoneNumber.isNullOrEmpty()) {
                        phoneTextView.text = paymentPhoneNumber
                    } else {
                        phoneTextView.text = "No phone number available"
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Phone TextView not found or error setting phone: ${e.message}")
            }
        }
    }
    
    private fun updateMapWithEntityLocation() {
        privateEntity?.let { entity ->
            // Create a marker for the entity location
            val marker = Marker(mapView)
            marker.position = GeoPoint(entity.latitude, entity.longitude)
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            marker.title = entity.entityName
            
            // Set marker icon with appropriate color
            val drawable = ContextCompat.getDrawable(this, R.drawable.ic_location_pin)?.mutate()
            if (entity.entityStatus.equals("OPEN", ignoreCase = true)) {
                drawable?.setColorFilter(ContextCompat.getColor(this, R.color.secondary), android.graphics.PorterDuff.Mode.SRC_IN)
            } else {
                drawable?.setColorFilter(ContextCompat.getColor(this, R.color.error), android.graphics.PorterDuff.Mode.SRC_IN)
            }
            marker.icon = drawable
            
            // Add marker to map
            mapView.overlays.add(marker)
            
            // Center map on entity location
            mapView.controller.setZoom(18.0)
            mapView.controller.setCenter(GeoPoint(entity.latitude, entity.longitude))
            
            // Force map refresh
            mapView.invalidate()
        }
    }
    
    private fun showConfirmationDialog() {
        // Show the dialog and overlay
        dialogOverlay.visibility = View.VISIBLE
        confirmationDialog.visibility = View.VISIBLE
    }
    
    private fun hideConfirmationDialog() {
        // Hide the dialog and overlay
        dialogOverlay.visibility = View.GONE
        confirmationDialog.visibility = View.GONE
    }
    
    private fun checkLocationPermission() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
            return
        }
        
        // Permission already granted, set up the map
        setupMap()
    }
    
    private fun setupMap() {
        // Initialize the location overlay
        myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), mapView)
        myLocationOverlay.enableMyLocation()
        mapView.overlays.add(myLocationOverlay)
        
        // Update map with entity location
        updateMapWithEntityLocation()
    }
    
    private fun enableMyLocation() {
        if (::myLocationOverlay.isInitialized) {
            myLocationOverlay.enableMyLocation()
            myLocationOverlay.enableFollowLocation()
        }
    }
    
    private fun disableMyLocation() {
        if (::myLocationOverlay.isInitialized) {
            myLocationOverlay.disableMyLocation()
            myLocationOverlay.disableFollowLocation()
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission was granted, set up the map
                    setupMap()
                } else {
                    // Permission denied, show a message
                    Toast.makeText(
                        this,
                        "Location permission is required to show your location on the map",
                        Toast.LENGTH_LONG
                    ).show()
                }
                return
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
        disableMyLocation()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        disableMyLocation()
    }
    
    override fun onBackPressed() {
        // Simply prevent back navigation in this activity
        Toast.makeText(this, "Please complete the delivery process", Toast.LENGTH_SHORT).show()
    }
} 