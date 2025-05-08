package com.example.ecotrack

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.ecotrack.models.JobOrder
import com.example.ecotrack.models.OrderStatus
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

class DriverAcceptJobOrderActivity : BaseActivity() {

    private lateinit var mapView: MapView
    private lateinit var addressTextView: TextView
    private lateinit var fullNameTextView: TextView
    private lateinit var phoneNumberTextView: TextView
    private lateinit var paymentMethodTextView: TextView
    private lateinit var wasteTypeTextView: TextView
    private lateinit var totalTextView: TextView
    private lateinit var acceptButton: Button
    private lateinit var backButton: ImageView
    
    // Location data
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001
    private var customerLatitude = 14.5995 // Default location (Philippines)
    private var customerLongitude = 120.9842 // Default location (Philippines)
    private var customerLocation: GeoPoint? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize OpenStreetMap configuration
        Configuration.getInstance().load(
            applicationContext,
            PreferenceManager.getDefaultSharedPreferences(applicationContext)
        )
        
        setContentView(R.layout.activity_driver_accept_job_order)

        // Initialize views
        initViews()
        
        // Set up listeners
        setupListeners()
        
        // Check location permission for map
        checkLocationPermission()
        
        // Load job order details and setup map
        loadJobOrderDetails()
    }
    
    private fun initViews() {
        mapView = findViewById(R.id.mapView)
        addressTextView = findViewById(R.id.addressTextView)
        fullNameTextView = findViewById(R.id.fullNameValueTextView)
        phoneNumberTextView = findViewById(R.id.phoneNumberValueTextView)
        paymentMethodTextView = findViewById(R.id.paymentMethodValueTextView)
        wasteTypeTextView = findViewById(R.id.wasteTypeValueTextView)
        totalTextView = findViewById(R.id.totalValueTextView)
        acceptButton = findViewById(R.id.acceptJobOrderButton)
        backButton = findViewById(R.id.backButton)
        
        // Configure the map
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(16.0)
    }
    
    private fun setupListeners() {
        acceptButton.setOnClickListener {
            // In a real implementation, this would call an API to accept the job order
            Toast.makeText(this, "Job Order Accepted!", Toast.LENGTH_SHORT).show()
            finish()
        }
        
        backButton.setOnClickListener {
            onBackPressed()
        }
    }
    
    private fun loadJobOrderDetails() {
        // In a real implementation, this would get the job order ID from the intent
        // and fetch the details from an API
        
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
        // Add my location overlay to show current driver location
        val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), mapView)
        locationOverlay.enableMyLocation()
        mapView.overlays.add(locationOverlay)
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