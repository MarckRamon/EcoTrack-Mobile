package com.example.ecotrack.ui.pickup

import android.content.Intent
import android.location.Address
import android.location.Geocoder
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.MotionEvent
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import com.example.ecotrack.R
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.util.*

class MapPickerActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private var selectedLocation: GeoPoint? = null
    private var currentAddress: String = ""
    private lateinit var btnConfirm: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize OpenStreetMap configuration
        Configuration.getInstance().load(
            applicationContext,
            PreferenceManager.getDefaultSharedPreferences(applicationContext)
        )
        
        setContentView(R.layout.activity_map_picker)

        // Setup toolbar with back button
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.title = "Select Location"

        // Initialize map
        mapView = findViewById(R.id.map_view)
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)

        // Get any existing location from the intent extras
        val currentLat = intent.getDoubleExtra("CURRENT_LAT", 0.0)
        val currentLng = intent.getDoubleExtra("CURRENT_LNG", 0.0)
        if (currentLat != 0.0 && currentLng != 0.0) {
            selectedLocation = GeoPoint(currentLat, currentLng)
        }

        // Setup confirm button
        btnConfirm = findViewById(R.id.btn_confirm)
        btnConfirm.setOnClickListener {
            if (selectedLocation != null) {
                val resultIntent = Intent()
                resultIntent.putExtra("LATITUDE", selectedLocation!!.latitude)
                resultIntent.putExtra("LONGITUDE", selectedLocation!!.longitude)
                resultIntent.putExtra("ADDRESS", currentAddress)
                setResult(RESULT_OK, resultIntent)
                finish()
            }
        }
        
        // Setup map
        setupMap()
    }

    private fun setupMap() {
        // Setup default location if no location was passed
        if (selectedLocation == null) {
            // Default to Manila, Philippines
            selectedLocation = GeoPoint(14.6091, 121.0223)
        }
        
        // Move camera to selected location and add a marker
        mapView.controller.setZoom(15.0)
        mapView.controller.setCenter(selectedLocation)
        updateMarkerAndAddress(selectedLocation!!)
        
        // Set map click listener to update the selected location
        mapView.overlays.add(object : org.osmdroid.views.overlay.Overlay() {
            override fun onSingleTapConfirmed(e: MotionEvent?, mapView: MapView?): Boolean {
                val projection = mapView?.projection
                val iGeoPoint = projection?.fromPixels(e?.x?.toInt() ?: 0, e?.y?.toInt() ?: 0)
                iGeoPoint?.let {
                    // Convert IGeoPoint to GeoPoint
                    val geoPoint = GeoPoint(it.latitude, it.longitude)
                    selectedLocation = geoPoint
                    updateMarkerAndAddress(geoPoint)
                }
                return true
            }
        })
    }
    
    private fun updateMarkerAndAddress(geoPoint: GeoPoint) {
        // Clear existing markers
        mapView.overlays.removeAll { it is Marker }
        
        // Add new marker
        val marker = Marker(mapView)
        marker.position = geoPoint
        marker.title = "Pickup Location"
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        marker.icon = ContextCompat.getDrawable(this, R.drawable.ic_location_pin)
        mapView.overlays.add(marker)
        
        // Refresh map
        mapView.invalidate()
        
        // Get address from coordinates
        try {
            val geocoder = Geocoder(this, Locale.getDefault())
            val addresses: List<Address> = geocoder.getFromLocation(geoPoint.latitude, geoPoint.longitude, 1) as List<Address>
            
            if (addresses.isNotEmpty()) {
                val address = addresses[0]
                val addressStringBuilder = StringBuilder()
                
                for (i in 0..address.maxAddressLineIndex) {
                    addressStringBuilder.append(address.getAddressLine(i))
                    if (i < address.maxAddressLineIndex) {
                        addressStringBuilder.append(", ")
                    }
                }
                
                currentAddress = addressStringBuilder.toString()
            } else {
                currentAddress = "Unknown location"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            currentAddress = "Could not determine address"
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

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
} 