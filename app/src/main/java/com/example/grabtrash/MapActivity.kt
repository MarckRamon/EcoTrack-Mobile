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
import com.example.grabtrash.models.PickupSite
import com.example.grabtrash.utils.ApiService
import com.example.grabtrash.utils.ProfileImageLoader
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

class MapActivity : BaseActivity() {
    private lateinit var mapView: MapView
    private lateinit var progressBar: ProgressBar
    private lateinit var infoCardView: CardView
    private lateinit var siteName: TextView
    private lateinit var garbageType: TextView
    private lateinit var closeButton: Button
    private lateinit var profileImage: CircleImageView
    private lateinit var homeNav: View
    private lateinit var scheduleNav: View
    private lateinit var pointsNav: View
    private lateinit var pickupNav: View
    
    private val apiService = ApiService.create()
    private val profileImageLoader = ProfileImageLoader(this)
    private val pickupSites = mutableListOf<PickupSite>()
    
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Load the OpenStreetMap configuration
        Configuration.getInstance().load(
            applicationContext,
            PreferenceManager.getDefaultSharedPreferences(applicationContext)
        )
        
        setContentView(R.layout.activity_map)
        supportActionBar?.hide()
        
        initializeViews()
        setupListeners()
        checkLocationPermission()
        fetchPickupSites()
    }
    
    private fun initializeViews() {
        mapView = findViewById(R.id.mapView)
        progressBar = findViewById(R.id.progressBar)
        infoCardView = findViewById(R.id.infoCardView)
        siteName = findViewById(R.id.siteName)
        garbageType = findViewById(R.id.garbageType)
        closeButton = findViewById(R.id.closeButton)
        profileImage = findViewById(R.id.profileImage)
        // Load cached profile image immediately if available
        try {
            val cachedUrl = sessionManager.getProfileImageUrl()
            if (!cachedUrl.isNullOrBlank()) {
                loadProfileImage(cachedUrl)
            }
        } catch (_: Exception) {}
        homeNav = findViewById(R.id.homeNav)
        scheduleNav = findViewById(R.id.scheduleNav)
        pointsNav = findViewById(R.id.pointsNav)
        pickupNav = findViewById(R.id.pickupNav)
        
        // Hide the old info card - we'll use the new InfoWindow instead
        infoCardView.visibility = View.GONE
        
        // Configure the map
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        
        // Initial zoom level - we'll center on pickup sites later
        mapView.controller.setZoom(15.0)
    }
    
    private fun setupListeners() {
        closeButton.setOnClickListener {
            infoCardView.visibility = View.GONE
        }
        
        findViewById<View>(R.id.refreshButton).setOnClickListener {
            // Clear any open info windows
            mapView.overlays.filterIsInstance<Marker>().forEach { 
                if (it.isInfoWindowShown) {
                    it.closeInfoWindow()
                }
            }
            // Show loading progress
            progressBar.visibility = View.VISIBLE
            // Refresh pickup sites from server
            fetchPickupSites()
            // Show a small toast message
            Toast.makeText(this, "Refreshing map data...", Toast.LENGTH_SHORT).show()
        }

        profileImage.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
        
        // Set up navigation
        homeNav.setOnClickListener {
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
        }
        
        scheduleNav.setOnClickListener {
            // Navigate to schedule screen
            startActivity(Intent(this, ScheduleActivity::class.java))
            finish()
        }
        
        // pointsNav is the current screen (Location)
        
        pickupNav.setOnClickListener {
            // Navigate to Order Pickup activity
            startActivity(Intent(this, com.example.grabtrash.ui.pickup.OrderPickupActivity::class.java))
        }
    }
    
    private fun loadProfileImage(url: String) {
        profileImageLoader.loadProfileImageUltraFast(
            url = url,
            imageView = profileImage,
            placeholderResId = R.drawable.raph,
            errorResId = R.drawable.raph
        )
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
        // Add my location overlay
        val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), mapView)
        locationOverlay.enableMyLocation()
        mapView.overlays.add(locationOverlay)
    }
    
    private fun fetchPickupSites() {
        progressBar.visibility = View.VISIBLE
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = apiService.getPickupSites()
                
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    
                    pickupSites.clear()
                    if (response.isSuccessful && response.body() != null) {
                        val result = response.body()!!
                        if (result.success && result.pickupSites.isNotEmpty()) {
                            pickupSites.addAll(result.pickupSites)
                            addMarkersToMap(result.pickupSites)
                        } else {
                            Toast.makeText(
                                this@MapActivity,
                                result.message ?: "No pickup sites found",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        Toast.makeText(
                            this@MapActivity,
                            "Failed to load pickup sites: ${response.errorBody()?.string()}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(
                        this@MapActivity,
                        "Error loading pickup sites: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    Log.e("MapActivity", "Error loading pickup sites", e)
                }
            }
        }
    }
    
    private fun addMarkersToMap(sites: List<PickupSite>) {
        mapView.overlays.removeAll { it is Marker }
        
        if (sites.isEmpty()) return
        
        // For centering the map on all markers
        val minLat = sites.minOf { it.latitude }
        val maxLat = sites.maxOf { it.latitude }
        val minLon = sites.minOf { it.longitude }
        val maxLon = sites.maxOf { it.longitude }
        
        // Create the custom info window
        val infoWindow = CustomInfoWindow(R.layout.marker_info_window, mapView)
        
        for (site in sites) {
            val marker = Marker(mapView)
            marker.position = GeoPoint(site.latitude, site.longitude)
            marker.title = site.name
            marker.snippet = "${site.garbageType}\n${site.address}"
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            marker.icon = ContextCompat.getDrawable(this, R.drawable.ic_location_pin)
            
            // Set the custom info window
            marker.infoWindow = infoWindow
            
            // Use OSMDroid's built-in InfoWindow which positions directly under the pin
            marker.setOnMarkerClickListener { clickedMarker, _ ->
                // Close any other open info windows
                mapView.overlays.filterIsInstance<Marker>().forEach { 
                    if (it != clickedMarker && it.isInfoWindowShown) {
                        it.closeInfoWindow()
                    }
                }
                
                // Center map on selected pin
                val mapController = mapView.controller
                // Offset slightly to account for info window display
                val offsetPoint = GeoPoint(clickedMarker.position.latitude - 0.0001, clickedMarker.position.longitude)
                mapController.animateTo(offsetPoint)
                mapController.setZoom(17.0)
                
                // Show the info window
                if (!clickedMarker.isInfoWindowShown) {
                    clickedMarker.showInfoWindow()
                } else {
                    clickedMarker.closeInfoWindow()
                }
                
                true
            }
            
            mapView.overlays.add(marker)
        }
        
        // Center map on markers
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
        
        mapView.invalidate()
    }
    
    private fun showSiteDetails(site: PickupSite) {
        // Hide any previously showing info card
        infoCardView.visibility = View.GONE
        
        // Animate to the marker position with slight offset to show popup below pin
        val geoPoint = GeoPoint(site.latitude, site.longitude)
        val mapController = mapView.controller
        
        // Offset the center point slightly upward so the pin is in the upper portion of the screen
        // This leaves room for the popup below
        val offsetPoint = GeoPoint(site.latitude - 0.0005, site.longitude)
        mapController.animateTo(offsetPoint)
        
        // Zoom level to see the pin and surrounding area clearly
        mapController.setZoom(17.0)
        
        // Small delay to allow animation to complete before showing the card
        infoCardView.postDelayed({
            // Set the details from the database
            siteName.text = site.name
            garbageType.text = site.garbageType
            
            // Show the address
            findViewById<TextView>(R.id.address)?.text = site.address
            
            infoCardView.visibility = View.VISIBLE
        }, 300) // 300ms delay for smooth animation
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