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
import com.example.ecotrack.models.PrivateEntitiesResponse
import com.example.ecotrack.models.PrivateEntity
import com.example.ecotrack.models.payment.Payment
import com.example.ecotrack.utils.ApiService
import com.example.ecotrack.utils.RealTimeUpdateManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import de.hdodenhof.circleimageview.CircleImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

class DriverMapActivity : BaseActivity() {
    private val TAG = "DriverMapActivity"
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001
    
    private lateinit var mapView: MapView
    private lateinit var progressBar: ProgressBar
    private lateinit var infoCardView: CardView
    private lateinit var titleTextView: TextView
    private lateinit var addressTextView: TextView
    private lateinit var wasteTypeTextView: TextView
    private lateinit var jobOrderOrPrivateEntityTextView: TextView
    private lateinit var closeButton: Button
    private lateinit var previousButton: Button
    private lateinit var nextButton: Button
    private lateinit var profileImage: CircleImageView
    private lateinit var homeNav: View
    private lateinit var jobOrdersNav: View
    private lateinit var collectionPointsNav: View
    private lateinit var locationToggleButton: FloatingActionButton
    
    private val apiService = ApiService.create()
    
    // Data lists
    private var jobOrders: List<Payment> = emptyList()
    private var privateEntities: List<PrivateEntity> = emptyList()
    
    // Location data
    private lateinit var myLocationOverlay: MyLocationNewOverlay
    private var isLocationEnabled = false
    
    // Real-time update manager
    private lateinit var realTimeUpdateManager: RealTimeUpdateManager
    
    // For navigation between markers
    private var allMarkers = mutableListOf<Marker>()
    private var currentMarkerIndex = -1
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Load the OpenStreetMap configuration
        Configuration.getInstance().load(
            applicationContext,
            PreferenceManager.getDefaultSharedPreferences(applicationContext)
        )
        
        setContentView(R.layout.activity_driver_map)
        supportActionBar?.hide()
        
        initializeViews()
        setupListeners()
        checkLocationPermission()
        
        // Initialize real-time update manager
        realTimeUpdateManager = RealTimeUpdateManager(
            activity = this,
            updateCallback = { fetchLocationsData() }
        )
        
        fetchLocationsData()
    }
    
    private fun initializeViews() {
        mapView = findViewById(R.id.mapView)
        progressBar = findViewById(R.id.progressBar)
        infoCardView = findViewById(R.id.infoCardView)
        titleTextView = findViewById(R.id.siteName)
        addressTextView = findViewById(R.id.address)
        wasteTypeTextView = findViewById(R.id.garbageType)
        jobOrderOrPrivateEntityTextView = findViewById(R.id.jobOrderOrPrivateEntity)
        closeButton = findViewById(R.id.closeButton)
        previousButton = findViewById(R.id.previousButton)
        nextButton = findViewById(R.id.nextButton)
        profileImage = findViewById(R.id.profileImage)
        homeNav = findViewById(R.id.homeNav)
        jobOrdersNav = findViewById(R.id.jobOrdersNav)
        collectionPointsNav = findViewById(R.id.collectionPointsNav)
        
        // Add the location toggle button
        locationToggleButton = findViewById(R.id.locationToggleButton)
        if (locationToggleButton == null) {
            // If the button doesn't exist in the layout, let's log it - you'll need to add it to the layout
            Log.e(TAG, "locationToggleButton not found in layout. Make sure to add it.")
        }
        
        // Configure the map
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        
        // Initial zoom level
        mapView.controller.setZoom(5.0)
    }
    
    private fun setupListeners() {
        closeButton.setOnClickListener {
            infoCardView.visibility = View.GONE
        }
        
        // Set up previous and next buttons
        previousButton.setOnClickListener {
            navigateToPreviousMarker()
        }
        
        nextButton.setOnClickListener {
            navigateToNextMarker()
        }
        
        // Remove or hide the refresh button since we have real-time updates
        findViewById<View>(R.id.refreshButton)?.visibility = View.GONE

        profileImage.setOnClickListener {
            startActivity(Intent(this, DriverProfileActivity::class.java))
        }
        
        // Set up location toggle button
        locationToggleButton?.setOnClickListener {
            toggleLocationTracking()
        }
        
        // Update location toggle button UI
        updateLocationToggleButton()
        
        // Set up bottom navigation
        homeNav.setOnClickListener {
            startActivity(Intent(this, DriverHomeActivity::class.java))
            finish()
        }
        
        jobOrdersNav.setOnClickListener {
            startActivity(Intent(this, DriverJobOrderActivity::class.java))
            finish()
        }
        
        // collectionPointsNav is the current screen
    }
    
    private fun navigateToPreviousMarker() {
        if (allMarkers.isEmpty() || currentMarkerIndex <= 0) return
        
        currentMarkerIndex--
        if (currentMarkerIndex < 0) currentMarkerIndex = allMarkers.size - 1
        
        handleMarkerClick(allMarkers[currentMarkerIndex])
    }
    
    private fun navigateToNextMarker() {
        if (allMarkers.isEmpty()) return
        
        currentMarkerIndex++
        if (currentMarkerIndex >= allMarkers.size) currentMarkerIndex = 0
        
        handleMarkerClick(allMarkers[currentMarkerIndex])
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
        locationToggleButton?.let { button ->
            if (isLocationEnabled) {
                button.setImageResource(R.drawable.ic_location_on)
                button.backgroundTintList = ContextCompat.getColorStateList(this, R.color.secondary)
                button.imageTintList = ContextCompat.getColorStateList(this, R.color.white)
            } else {
                button.setImageResource(R.drawable.ic_location_on)
                button.backgroundTintList = ContextCompat.getColorStateList(this, R.color.white)
                button.imageTintList = ContextCompat.getColorStateList(this, R.color.secondary)
            }
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
    
    private fun fetchLocationsData() {
        progressBar.visibility = View.VISIBLE
        
        // Get the driver ID from session manager
        val driverId = sessionManager.getUserId()
        val token = sessionManager.getToken()
        
        if (driverId == null || token == null) {
            Log.e(TAG, "Missing credentials - token: $token, userId: $driverId")
            Toast.makeText(this, "Authentication required", Toast.LENGTH_SHORT).show()
            progressBar.visibility = View.GONE
            return
        }
        
        // Use coroutines to fetch both job orders and private entities
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Fetch job orders
                val jobOrdersResponse = apiService.getPaymentsByDriverId(driverId, "Bearer $token")
                
                // Fetch private entities
                val privateEntitiesResponse = apiService.getAllPrivateEntities("Bearer $token")
                
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    
                    // Process job orders
                    if (jobOrdersResponse.isSuccessful && jobOrdersResponse.body() != null) {
                        jobOrders = jobOrdersResponse.body() ?: emptyList()
                        Log.d(TAG, "Loaded ${jobOrders.size} job orders")
                    } else {
                        Log.e(TAG, "Failed to load job orders: ${jobOrdersResponse.code()}")
                        jobOrders = emptyList()
                    }
                    
                    // Process private entities
                    if (privateEntitiesResponse.isSuccessful && privateEntitiesResponse.body() != null) {
                        privateEntities = privateEntitiesResponse.body()?.entities ?: emptyList()
                        Log.d(TAG, "Loaded ${privateEntities.size} private entities")
                    } else {
                        Log.e(TAG, "Failed to load private entities: ${privateEntitiesResponse.code()}")
                        privateEntities = emptyList()
                    }
                    
                    // Now add all markers to the map
                    addMarkersToMap()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(
                        this@DriverMapActivity,
                        "Error loading map data: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    Log.e(TAG, "Error loading map data", e)
                }
            }
        }
    }
    
    private fun addMarkersToMap() {
        // Clear existing markers but keep location overlay
        val locationOverlay = mapView.overlays.find { it is MyLocationNewOverlay }
        mapView.overlays.clear()
        locationOverlay?.let { mapView.overlays.add(it) }
        
        // Clear the markers list
        allMarkers.clear()
        currentMarkerIndex = -1
        
        val allMarkerPoints = mutableListOf<GeoPoint>()
        
        // Add job order markers (green)
        for (jobOrder in jobOrders) {
            val marker = Marker(mapView)
            val location = GeoPoint(jobOrder.latitude, jobOrder.longitude)
            
            marker.position = location
            marker.title = jobOrder.customerName
            marker.snippet = "${jobOrder.address}\n${jobOrder.wasteType}"
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            
            // Set green color for job orders - using a lighter green for better visibility
            val drawable = ContextCompat.getDrawable(this, R.drawable.ic_location_pin)?.mutate()
            drawable?.setColorFilter(
                ContextCompat.getColor(this, R.color.secondary),
                android.graphics.PorterDuff.Mode.SRC_IN
            )
            marker.icon = drawable
            
            // Store the marker type and ID
            marker.id = "job_${jobOrder.id}"
            
            // Disable the built-in info window
            marker.infoWindow = null
            
            // Set click listener
            marker.setOnMarkerClickListener { clickedMarker, _ ->
                handleMarkerClick(clickedMarker)
                true
            }
            
            mapView.overlays.add(marker)
            allMarkers.add(marker)
            allMarkerPoints.add(location)
        }
        
        // Add private entity markers (red)
        for (entity in privateEntities) {
            val marker = Marker(mapView)
            val location = GeoPoint(entity.latitude, entity.longitude)
            
            marker.position = location
            marker.title = entity.entityName
            marker.snippet = "${entity.address}\n${entity.entityWasteType}"
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            
            // Set red color for private entities
            val drawable = ContextCompat.getDrawable(this, R.drawable.ic_location_pin)?.mutate()
            drawable?.setColorFilter(
                ContextCompat.getColor(this, R.color.error),
                android.graphics.PorterDuff.Mode.SRC_IN
            )
            marker.icon = drawable
            
            // Store the marker type and ID
            marker.id = "entity_${entity.entityId}"
            
            // Disable the built-in info window
            marker.infoWindow = null
            
            // Set click listener
            marker.setOnMarkerClickListener { clickedMarker, _ ->
                handleMarkerClick(clickedMarker)
                true
            }
            
            mapView.overlays.add(marker)
            allMarkers.add(marker)
            allMarkerPoints.add(location)
        }
        
        // Focus on the first marker if available
        if (allMarkers.isNotEmpty()) {
            // Select the first marker
            handleMarkerClick(allMarkers[0])
        } else {
            // If no markers, just center the map on a default location (Philippines)
            val defaultLocation = GeoPoint(14.5995, 120.9842)
            mapView.controller.setCenter(defaultLocation)
            mapView.controller.setZoom(10.0)
        }
        
        mapView.invalidate()
    }
    
    private fun handleMarkerClick(marker: Marker) {
        // Update current marker index
        currentMarkerIndex = allMarkers.indexOf(marker)
        
        // Store the previously selected marker to reset its appearance
        val previouslySelectedMarker = mapView.overlays.filterIsInstance<Marker>()
            .find { it.id != marker.id && it.id?.contains("selected_") == true }
            
        // Reset previously selected marker appearance
        previouslySelectedMarker?.let {
            val oldId = it.id?.replace("selected_", "") ?: ""
            if (oldId.startsWith("job_")) {
                val drawable = ContextCompat.getDrawable(this, R.drawable.ic_location_pin)?.mutate()
                drawable?.setColorFilter(
                    ContextCompat.getColor(this, R.color.secondary),
                    android.graphics.PorterDuff.Mode.SRC_IN
                )
                it.icon = drawable
            } else if (oldId.startsWith("entity_")) {
                val drawable = ContextCompat.getDrawable(this, R.drawable.ic_location_pin)?.mutate()
                drawable?.setColorFilter(
                    ContextCompat.getColor(this, R.color.error),
                    android.graphics.PorterDuff.Mode.SRC_IN
                )
                it.icon = drawable
            }
            it.id = oldId
        }
        
        // Highlight the selected marker
        val originalId = marker.id ?: ""
        marker.id = "selected_$originalId"
        
        // Make the selected marker lighter green
        val drawable = ContextCompat.getDrawable(this, R.drawable.ic_location_pin)?.mutate()
        if (originalId.startsWith("job_")) {
            // Using a much lighter green color for selected job order pins
            drawable?.setColorFilter(
                android.graphics.Color.rgb(0, 255, 0),
                android.graphics.PorterDuff.Mode.SRC_IN
            )
        } else if (originalId.startsWith("entity_")) {
            drawable?.setColorFilter(
                android.graphics.Color.rgb(255, 0, 0),
                android.graphics.PorterDuff.Mode.SRC_IN
            )
        }
        marker.icon = drawable
        
        // Center map on selected pin with slight offset for the info window
        val mapController = mapView.controller
        val offsetPoint = GeoPoint(marker.position.latitude - 0.0005, marker.position.longitude)
        mapController.animateTo(offsetPoint)
        mapController.setZoom(15.0)
        
        // Show the marker info in the card
        val markerId = originalId
        
        if (markerId.startsWith("job_")) {
            // Find the job order by ID
            val jobId = markerId.substringAfter("job_")
            val jobOrder = jobOrders.find { it.id == jobId } ?: return
            
            // Update the info card
            titleTextView.text = jobOrder.customerName
            addressTextView.text = "Address: ${jobOrder.address}"
            wasteTypeTextView.text = "Waste Type: ${jobOrder.wasteType}"
            jobOrderOrPrivateEntityTextView.text = "Job Order"
            
            // Make sure the info card is visible
            infoCardView.visibility = View.VISIBLE
        } else if (markerId.startsWith("entity_")) {
            // Find the private entity by ID
            val entityId = markerId.substringAfter("entity_")
            val entity = privateEntities.find { it.entityId == entityId } ?: return
            
            // Update the info card
            titleTextView.text = entity.entityName
            addressTextView.text = "Address: ${entity.address}"
            wasteTypeTextView.text = "Waste Type: ${entity.entityWasteType}"
            jobOrderOrPrivateEntityTextView.text = "Private Entity"
            
            // Make sure the info card is visible
            infoCardView.visibility = View.VISIBLE
        }
        
        // Update button states based on marker index
        updateNavigationButtonStates()
        
        mapView.invalidate()
    }
    
    private fun updateNavigationButtonStates() {
        // Update previous/next button states based on current marker index
        previousButton.isEnabled = allMarkers.size > 1
        nextButton.isEnabled = allMarkers.size > 1
    }
    
    private fun centerMapOnPoints(points: List<GeoPoint>) {
        if (points.isEmpty()) return
        
        // For efficiency, if there's only one point, just center on it
        if (points.size == 1) {
            mapView.controller.setCenter(points[0])
            mapView.controller.setZoom(15.0)
            return
        }
        
        // Calculate the center and bounds
        val minLat = points.minOf { it.latitude }
        val maxLat = points.maxOf { it.latitude }
        val minLon = points.minOf { it.longitude }
        val maxLon = points.maxOf { it.longitude }
        
        val centerLat = (minLat + maxLat) / 2
        val centerLon = (minLon + maxLon) / 2
        
        // Determine appropriate zoom level based on the area size
        val latSpan = maxLat - minLat
        val lonSpan = maxLon - minLon
        val zoomLevel = when {
            latSpan > 1 || lonSpan > 1 -> 10.0
            latSpan > 0.1 || lonSpan > 0.1 -> 13.0
            else -> 15.0
        }
        
        // Set map center and zoom
        val mapController = mapView.controller
        mapController.setCenter(GeoPoint(centerLat, centerLon))
        mapController.setZoom(zoomLevel)
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
        // Start real-time updates
        realTimeUpdateManager.startRealTimeUpdates()
    }
    
    override fun onPause() {
        super.onPause()
        mapView.onPause()
        // Stop real-time updates
        realTimeUpdateManager.stopRealTimeUpdates()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        mapView.onDetach()
    }
} 