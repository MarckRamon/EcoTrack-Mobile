package com.example.ecotrack

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import com.example.ecotrack.models.PrivateEntitiesResponse
import com.example.ecotrack.models.PrivateEntity
import com.example.ecotrack.models.UserProfile
import com.example.ecotrack.utils.ApiService
import com.example.ecotrack.utils.RealTimeUpdateManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

class PrivateEntityMapActivity : BaseActivity() {

    private val TAG = "PrivateEntityMap"
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001

    // UI components
    private lateinit var mapView: MapView
    private lateinit var backButton: ImageView
    private lateinit var toolbarTitle: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var entityInfoCard: CardView
    private lateinit var addressCard: CardView
    private lateinit var addressTextView: TextView
    private lateinit var entityNameTextView: TextView
    private lateinit var entityStatusTextView: TextView
    private lateinit var entityWasteTypeTextView: TextView
    private lateinit var entityPhoneTextView: TextView
    private lateinit var confirmLocationButton: Button
    private lateinit var prevEntityButton: ImageView
    private lateinit var nextEntityButton: ImageView
    private lateinit var locationToggleButton: FloatingActionButton
    
    // UI components for confirmation dialog
    private lateinit var confirmationDialog: androidx.cardview.widget.CardView
    private lateinit var dialogOverlay: View
    private lateinit var confirmDialogButton: Button
    private lateinit var cancelDialogButton: Button
    private lateinit var confirmDialogMessage: TextView

    // Location data
    private lateinit var myLocationOverlay: MyLocationNewOverlay
    private var isLocationEnabled = false
    
    // Private entities data
    private var privateEntities: List<PrivateEntity> = emptyList()
    private var selectedEntity: PrivateEntity? = null
    private var currentEntityIndex = 0
    
    // API Service
    private lateinit var apiService: ApiService
    
    // Real-time update manager
    private lateinit var realTimeUpdateManager: RealTimeUpdateManager
    
    // Cache for user profiles
    private val userProfileCache = mutableMapOf<String, UserProfile>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize OpenStreetMap configuration
        Configuration.getInstance().load(
            applicationContext,
            PreferenceManager.getDefaultSharedPreferences(applicationContext)
        )
        
        setContentView(R.layout.activity_private_entity_map)

        // Initialize API service
        apiService = ApiService.create()
        
        // Initialize views
        initViews()
        
        // Check location permission for map
        checkLocationPermission()
        
        // Initialize real-time update manager
        realTimeUpdateManager = RealTimeUpdateManager(
            activity = this,
            updateCallback = { fetchUpdatedEntities() }
        )
        
        // Load private entities data
        loadPrivateEntities()
    }
    
    private fun initViews() {
        backButton = findViewById(R.id.backButton)
        toolbarTitle = findViewById(R.id.toolbarTitle)
        mapView = findViewById(R.id.mapView)
        progressBar = findViewById(R.id.progressBar)
        entityInfoCard = findViewById(R.id.entityInfoCard)
        addressCard = findViewById(R.id.addressCard)
        addressTextView = findViewById(R.id.addressTextView)
        entityNameTextView = findViewById(R.id.entityNameTextView)
        entityStatusTextView = findViewById(R.id.entityStatusTextView)
        entityWasteTypeTextView = findViewById(R.id.entityWasteTypeTextView)
        entityPhoneTextView = findViewById(R.id.entityPhoneTextView)
        confirmLocationButton = findViewById(R.id.confirmLocationButton)
        prevEntityButton = findViewById(R.id.prevEntityButton)
        nextEntityButton = findViewById(R.id.nextEntityButton)
        locationToggleButton = findViewById(R.id.locationToggleButton)
        
        // Initialize confirmation dialog components
        confirmationDialog = findViewById(R.id.confirmationDialog)
        dialogOverlay = findViewById(R.id.dialogOverlay)
        confirmDialogButton = findViewById(R.id.confirmDialogButton)
        cancelDialogButton = findViewById(R.id.cancelDialogButton)
        confirmDialogMessage = findViewById(R.id.confirmDialogMessage)
        
        // Configure the map
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        
        // Set default information
        setDefaultInformation()
        
        // Set up back button
        backButton.setOnClickListener {
            onBackPressed()
        }
        
        // Set up confirm location button
        confirmLocationButton.setOnClickListener {
            showConfirmationDialog()
        }
        
        // Set up confirmation dialog buttons
        confirmDialogButton.setOnClickListener {
            hideConfirmationDialog()
            confirmSelectedLocation()
        }
        
        cancelDialogButton.setOnClickListener {
            hideConfirmationDialog()
        }
        
        // Set up previous entity button
        prevEntityButton.setOnClickListener {
            showPreviousEntity()
        }
        
        // Set up next entity button
        nextEntityButton.setOnClickListener {
            showNextEntity()
        }
        
        // Set up location toggle button
        locationToggleButton.setOnClickListener {
            toggleLocationTracking()
        }
        
        // Set initial location toggle button state
        updateLocationToggleButton()
    }
    
    private fun showPreviousEntity() {
        if (privateEntities.isEmpty()) return
        
        currentEntityIndex--
        if (currentEntityIndex < 0) {
            currentEntityIndex = privateEntities.size - 1
        }
        
        selectedEntity = privateEntities[currentEntityIndex]
        updateEntityInfoCard(selectedEntity!!)
        centerMapOnSelectedEntity()
    }
    
    private fun showNextEntity() {
        if (privateEntities.isEmpty()) return
        
        currentEntityIndex++
        if (currentEntityIndex >= privateEntities.size) {
            currentEntityIndex = 0
        }
        
        selectedEntity = privateEntities[currentEntityIndex]
        updateEntityInfoCard(selectedEntity!!)
        centerMapOnSelectedEntity()
    }
    
    private fun centerMapOnSelectedEntity() {
        selectedEntity?.let { entity ->
            val point = GeoPoint(entity.latitude, entity.longitude)
            mapView.controller.animateTo(point)
            
            // Highlight the marker
            highlightSelectedMarker(entity.entityId)
        }
    }
    
    private fun highlightSelectedMarker(entityId: String) {
        // Reset all markers to default colors based on status
        for (overlay in mapView.overlays) {
            if (overlay is Marker) {
                val entity = privateEntities.find { it.entityId == overlay.id } ?: continue
                
                // Color the marker based on entity status
                val drawable = ContextCompat.getDrawable(this, R.drawable.ic_location_pin)?.mutate()
                if (entity.entityStatus.equals("OPEN", ignoreCase = true)) {
                    // Green for open entities
                    drawable?.setColorFilter(ContextCompat.getColor(this, R.color.secondary), android.graphics.PorterDuff.Mode.SRC_IN)
                } else {
                    // Red for closed entities
                    drawable?.setColorFilter(ContextCompat.getColor(this, R.color.error), android.graphics.PorterDuff.Mode.SRC_IN)
                }
                overlay.icon = drawable
            }
        }
        
        // Highlight the selected marker (make it larger or brighter)
        for (overlay in mapView.overlays) {
            if (overlay is Marker && overlay.id == entityId) {
                // Get a mutable copy of the drawable
                val drawable = ContextCompat.getDrawable(this, R.drawable.ic_location_pin)?.mutate()
                
                // Get the entity to determine base color
                val entity = privateEntities.find { it.entityId == entityId }
                
                if (entity != null) {
                    // Use a brighter version of the status color for the selected marker
                    if (entity.entityStatus.equals("OPEN", ignoreCase = true)) {
                        // Bright green for selected open entity
                        drawable?.setColorFilter(android.graphics.Color.rgb(0, 255, 0), android.graphics.PorterDuff.Mode.SRC_IN)
                    } else {
                        // Bright red for selected closed entity
                        drawable?.setColorFilter(android.graphics.Color.rgb(255, 0, 0), android.graphics.PorterDuff.Mode.SRC_IN)
                    }
                    
                    // Make the selected marker slightly larger
                    overlay.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    overlay.icon = drawable
                    overlay.setInfoWindowAnchor(Marker.ANCHOR_CENTER, 0f)
                }
                break
            }
        }
        
        // Refresh the map
        mapView.invalidate()
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
    
    private fun setDefaultInformation() {
        // Show the cards with default information
        addressTextView.text = "Select a private entity on the map"
        addressCard.visibility = View.VISIBLE
        
        entityNameTextView.text = "Not selected"
        entityStatusTextView.text = "N/A"
        entityStatusTextView.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        entityWasteTypeTextView.text = "N/A"
        entityPhoneTextView.text = "N/A"
        
        entityInfoCard.visibility = View.VISIBLE
        
        // Disable confirm button until an entity is selected
        confirmLocationButton.isEnabled = false
        confirmLocationButton.text = "SELECT A LOCATION"
        confirmLocationButton.visibility = View.VISIBLE
    }
    
    private fun showConfirmationDialog() {
        selectedEntity?.let { entity ->
            // Update dialog message with entity name
            confirmDialogMessage.text = "Are you sure you want to select ${entity.entityName} for waste delivery?"
            
            // Show the dialog and overlay
            dialogOverlay.visibility = View.VISIBLE
            confirmationDialog.visibility = View.VISIBLE
            
            // Dim UI elements (set alpha to 0.2)
            val dimAlpha = 0.2f
            findViewById<View>(R.id.toolbar).alpha = dimAlpha
            findViewById<View>(R.id.bottomContainer).alpha = dimAlpha
            findViewById<View>(R.id.mapContainer).alpha = dimAlpha
            
            // Disable interactions with UI elements
            findViewById<View>(R.id.toolbar).isEnabled = false
            findViewById<View>(R.id.bottomContainer).isEnabled = false
            findViewById<View>(R.id.mapContainer).isEnabled = false
            backButton.isClickable = false
            prevEntityButton.isClickable = false
            nextEntityButton.isClickable = false
            locationToggleButton.isClickable = false
            confirmLocationButton.isEnabled = false
            
            // Disable map interactions
            mapView.isClickable = false
            mapView.setMultiTouchControls(false)
        } ?: run {
            Toast.makeText(this, "No entity selected", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun hideConfirmationDialog() {
        // Hide the dialog and overlay
        dialogOverlay.visibility = View.GONE
        confirmationDialog.visibility = View.GONE
        
        // Restore UI elements (set alpha back to 1.0)
        val fullAlpha = 1.0f
        findViewById<View>(R.id.toolbar).alpha = fullAlpha
        findViewById<View>(R.id.bottomContainer).alpha = fullAlpha
        findViewById<View>(R.id.mapContainer).alpha = fullAlpha
        
        // Re-enable interactions with UI elements
        findViewById<View>(R.id.toolbar).isEnabled = true
        findViewById<View>(R.id.bottomContainer).isEnabled = true
        findViewById<View>(R.id.mapContainer).isEnabled = true
        backButton.isClickable = true
        prevEntityButton.isClickable = true
        nextEntityButton.isClickable = true
        locationToggleButton.isClickable = true
        
        // Re-enable confirm button based on entity status
        selectedEntity?.let { entity ->
            confirmLocationButton.isEnabled = entity.entityStatus.equals("OPEN", ignoreCase = true)
        }
        
        // Re-enable map interactions
        mapView.isClickable = true
        mapView.setMultiTouchControls(true)
    }
    
    private fun confirmSelectedLocation() {
        selectedEntity?.let { entity ->
            // Create an intent to return the selected entity
            val resultIntent = Intent()
            resultIntent.putExtra("SELECTED_ENTITY", entity)
            
            // Set the result and finish
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        } ?: run {
            Toast.makeText(this, "No entity selected", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun loadPrivateEntities() {
        showLoading("Loading private entities...")
        
        lifecycleScope.launch {
            try {
                val token = sessionManager.getToken()
                if (token != null) {
                    val response = apiService.getAllPrivateEntities("Bearer $token")
                    
                    if (response.isSuccessful && response.body() != null) {
                        val privateEntitiesResponse = response.body()
                        privateEntities = privateEntitiesResponse?.entities ?: emptyList()
                        
                        Log.d(TAG, "Loaded ${privateEntities.size} private entities")
                        
                        // Setup map with private entity locations
                        setupMap()
                        hideLoading()
                        
                        // Start real-time updates
                        realTimeUpdateManager.startRealTimeUpdates()
                    } else {
                        Log.e(TAG, "Error loading private entities: ${response.code()} - ${response.message()}")
                        Toast.makeText(this@PrivateEntityMapActivity, "Failed to load private entities", Toast.LENGTH_SHORT).show()
                        hideLoading()
                        
                        // Load dummy data for testing if API fails
                        loadDummyData()
                    }
                } else {
                    Log.e(TAG, "No authentication token found")
                    Toast.makeText(this@PrivateEntityMapActivity, "Authentication required", Toast.LENGTH_SHORT).show()
                    hideLoading()
                    
                    // Load dummy data for testing if no token
                    loadDummyData()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception loading private entities: ${e.message}", e)
                Toast.makeText(this@PrivateEntityMapActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                hideLoading()
                
                // Load dummy data for testing
                loadDummyData()
            }
        }
    }
    
    private fun loadDummyData() {
        // Create some dummy private entities for testing
        privateEntities = listOf(
            PrivateEntity(
                entityId = "1",
                userId = "user1",
                entityName = "Dong Junk Private Company",
                address = "7953 Oakland St. Honolulu, HI 96815",
                entityStatus = "OPEN",
                entityWasteType = "Plastic",
                latitude = 21.3069,
                longitude = -157.8583
            ),
            PrivateEntity(
                entityId = "2",
                userId = "user2",
                entityName = "Recyclable Materials Inc.",
                address = "8100 Waialae Ave, Honolulu, HI 96816",
                entityStatus = "OPEN",
                entityWasteType = "METAL",
                latitude = 21.2897,
                longitude = -157.8005
            ),
            PrivateEntity(
                entityId = "3",
                userId = "user3",
                entityName = "Green Earth Recyclers",
                address = "1234 Kapiolani Blvd, Honolulu, HI 96814",
                entityStatus = "CLOSED",
                entityWasteType = "PAPER",
                latitude = 21.2954,
                longitude = -157.8400
            )
        )
        
        // Setup map with dummy data
        setupMap()
        hideLoading()
        
        // Start real-time updates with dummy data
        realTimeUpdateManager.startRealTimeUpdates()
    }
    
    private fun setupMap() {
        if (privateEntities.isEmpty()) {
            Toast.makeText(this, "No private entities found", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Clear existing overlays except for location overlay
        val locationOverlay = mapView.overlays.find { it is MyLocationNewOverlay }
        mapView.overlays.clear()
        locationOverlay?.let { mapView.overlays.add(it) }
        
        // Add markers for each private entity
        for (entity in privateEntities) {
            val marker = Marker(mapView)
            val location = GeoPoint(entity.latitude, entity.longitude)
            
            marker.position = location
            marker.title = entity.entityName
            marker.snippet = entity.address
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            
            // Color the marker based on entity status
            val drawable = ContextCompat.getDrawable(this, R.drawable.ic_location_pin)?.mutate()
            if (entity.entityStatus.equals("OPEN", ignoreCase = true)) {
                // Green for open entities
                drawable?.setColorFilter(ContextCompat.getColor(this, R.color.secondary), android.graphics.PorterDuff.Mode.SRC_IN)
            } else {
                // Red for closed entities
                drawable?.setColorFilter(ContextCompat.getColor(this, R.color.error), android.graphics.PorterDuff.Mode.SRC_IN)
            }
            marker.icon = drawable
            
            // Store the entity ID in the marker
            marker.id = entity.entityId
            
            // Add click listener to the marker
            marker.setOnMarkerClickListener { clickedMarker, _ ->
                // Find the entity associated with this marker
                val selectedEntityId = clickedMarker.id
                val entityIndex = privateEntities.indexOfFirst { it.entityId == selectedEntityId }
                
                if (entityIndex != -1) {
                    currentEntityIndex = entityIndex
                    selectedEntity = privateEntities[currentEntityIndex]
                    
                    // Update the entity info card
                    updateEntityInfoCard(selectedEntity!!)
                    
                    // Highlight the selected marker
                    highlightSelectedMarker(selectedEntityId)
                }
                
                true
            }
            
            // Add the marker to the map
            mapView.overlays.add(marker)
        }
        
        // If we have entities, select the first one by default
        if (privateEntities.isNotEmpty()) {
            currentEntityIndex = 0
            selectedEntity = privateEntities[currentEntityIndex]
            updateEntityInfoCard(selectedEntity!!)
            
            // Center the map on the first entity
            val firstEntityLocation = GeoPoint(selectedEntity!!.latitude, selectedEntity!!.longitude)
            mapView.controller.setZoom(16.0) // Closer zoom for better visibility
            mapView.controller.setCenter(firstEntityLocation)
            
            // Highlight the selected marker
            highlightSelectedMarker(selectedEntity!!.entityId)
        }
        
        // Refresh the map
        mapView.invalidate()
    }
    
    private fun updateEntityInfoCard(entity: PrivateEntity) {
        // Update the address card
        addressTextView.text = entity.address
        
        // Update entity info
        entityNameTextView.text = entity.entityName
        
        // Format status text with proper capitalization
        val formattedStatus = entity.entityStatus.lowercase().replaceFirstChar { it.uppercase() }
        entityStatusTextView.text = formattedStatus
        
        // Set status color
        if (entity.entityStatus.equals("OPEN", ignoreCase = true)) {
            entityStatusTextView.setTextColor(ContextCompat.getColor(this, R.color.secondary))
            
            // Update button state for open entity
            confirmLocationButton.isEnabled = true
            confirmLocationButton.text = "CONFIRM LOCATION"
            confirmLocationButton.backgroundTintList = ContextCompat.getColorStateList(this, R.color.secondary)
        } else {
            entityStatusTextView.setTextColor(ContextCompat.getColor(this, R.color.error))
            
            // Update button state for closed entity
            confirmLocationButton.isEnabled = false
            confirmLocationButton.text = "NOT AVAILABLE"
            confirmLocationButton.backgroundTintList = ContextCompat.getColorStateList(this, R.color.text_secondary)
        }
        
        // Update waste type
        entityWasteTypeTextView.text = entity.entityWasteType
        
        // Fetch user profile for phone number
        fetchUserProfile(entity.userId)
    }
    
    private fun fetchUserProfile(userId: String) {
        // Check if we already have this profile cached
        if (userProfileCache.containsKey(userId)) {
            updatePhoneNumber(userProfileCache[userId])
            return
        }
        
        // Show loading indicator for phone number
        entityPhoneTextView.text = "Loading..."
        
        lifecycleScope.launch {
            try {
                val token = sessionManager.getToken()
                if (token != null) {
                    val response = apiService.getProfile(userId, "Bearer $token")
                    
                    if (response.isSuccessful && response.body() != null) {
                        val userProfile = response.body()!!
                        
                        // Cache the profile
                        userProfileCache[userId] = userProfile
                        
                        // Update UI with phone number
                        updatePhoneNumber(userProfile)
                    } else {
                        Log.e(TAG, "Error fetching user profile: ${response.code()}")
                        entityPhoneTextView.text = "Not available"
                    }
                } else {
                    entityPhoneTextView.text = "Not available"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception fetching user profile: ${e.message}", e)
                entityPhoneTextView.text = "Not available"
            }
        }
    }
    
    private fun updatePhoneNumber() {
        // Update phone number for the currently selected entity
        selectedEntity?.let { entity ->
            fetchUserProfile(entity.userId)
        }
    }
    
    private fun updatePhoneNumber(userProfile: UserProfile?) {
        userProfile?.let { profile ->
            // Display phone number if available without checking role
            if (!profile.phoneNumber.isNullOrEmpty()) {
                entityPhoneTextView.text = profile.phoneNumber
            } else {
                entityPhoneTextView.text = "No phone number provided"
            }
        } ?: run {
            entityPhoneTextView.text = "Not available"
        }
    }
    
    private fun showLoading(message: String) {
        progressBar.visibility = View.VISIBLE
    }
    
    private fun hideLoading() {
        progressBar.visibility = View.GONE
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
            // Location is disabled by default, so we don't enable it here
            // User must click the toggle button to enable it
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
    
    private fun fetchUpdatedEntities() {
        lifecycleScope.launch {
            try {
                val token = sessionManager.getToken()
                if (token != null) {
                    val response = apiService.getAllPrivateEntities("Bearer $token")
                    
                    if (response.isSuccessful && response.body() != null) {
                        val updatedEntities = response.body()?.entities ?: emptyList()
                        
                        // Remember the currently selected entity ID
                        val currentEntityId = selectedEntity?.entityId
                        
                        // Update the entities list
                        privateEntities = updatedEntities
                        
                        // Find the index of the previously selected entity
                        if (currentEntityId != null) {
                            val newIndex = privateEntities.indexOfFirst { it.entityId == currentEntityId }
                            if (newIndex != -1) {
                                currentEntityIndex = newIndex
                                selectedEntity = privateEntities[currentEntityIndex]
                            } else if (privateEntities.isNotEmpty()) {
                                // If previously selected entity is no longer available, select the first one
                                currentEntityIndex = 0
                                selectedEntity = privateEntities[0]
                            }
                        }
                        
                        // Update UI on the main thread
                        runOnUiThread {
                            updateMapWithEntities()
                            selectedEntity?.let { 
                                updateEntityInfoCard(it)
                                // Explicitly update phone number
                                fetchUserProfile(it.userId)
                            }
                            
                            // Clear cache to force refresh of phone numbers
                            userProfileCache.clear()
                        }
                        
                        Log.d(TAG, "Entities updated in real-time: ${privateEntities.size} entities")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating entities: ${e.message}", e)
            }
        }
    }
    
    private fun updateMapWithEntities() {
        // Clear existing markers
        val markersToRemove = mapView.overlays.filter { it is Marker }
        mapView.overlays.removeAll(markersToRemove)
        
        // Add updated markers
        for (entity in privateEntities) {
            val marker = Marker(mapView)
            val location = GeoPoint(entity.latitude, entity.longitude)
            
            marker.position = location
            marker.title = entity.entityName
            marker.snippet = entity.address
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            
            // Color the marker based on entity status
            val drawable = ContextCompat.getDrawable(this, R.drawable.ic_location_pin)?.mutate()
            if (entity.entityStatus.equals("OPEN", ignoreCase = true)) {
                // Green for open entities
                drawable?.setColorFilter(ContextCompat.getColor(this, R.color.secondary), android.graphics.PorterDuff.Mode.SRC_IN)
            } else {
                // Red for closed entities
                drawable?.setColorFilter(ContextCompat.getColor(this, R.color.error), android.graphics.PorterDuff.Mode.SRC_IN)
            }
            marker.icon = drawable
            
            // Store the entity ID in the marker
            marker.id = entity.entityId
            
            // Add click listener to the marker
            marker.setOnMarkerClickListener { clickedMarker, _ ->
                // Find the entity associated with this marker
                val selectedEntityId = clickedMarker.id
                val entityIndex = privateEntities.indexOfFirst { it.entityId == selectedEntityId }
                
                if (entityIndex != -1) {
                    currentEntityIndex = entityIndex
                    selectedEntity = privateEntities[currentEntityIndex]
                    
                    // Update the entity info card
                    updateEntityInfoCard(selectedEntity!!)
                    
                    // Highlight the selected marker
                    highlightSelectedMarker(selectedEntityId)
                }
                
                true
            }
            
            // Add the marker to the map
            mapView.overlays.add(marker)
        }
        
        // Highlight the selected entity if any
        selectedEntity?.let { highlightSelectedMarker(it.entityId) }
        
        // Refresh the map
        mapView.invalidate()
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
                // Still don't enable location by default, just set up the overlay
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
        
        // Restart real-time updates if needed
        if (privateEntities.isNotEmpty()) {
            realTimeUpdateManager.startRealTimeUpdates()
            updatePhoneNumber()
        }
    }
    
    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }
    
    override fun onDestroy() {
        // Stop real-time updates
        realTimeUpdateManager.stopRealTimeUpdates()
        
        super.onDestroy()
        mapView.onDetach()
    }

    companion object {
        const val REQUEST_CODE_SELECT_ENTITY = 1001
    }
} 