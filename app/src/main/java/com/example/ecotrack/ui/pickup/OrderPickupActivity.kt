package com.example.ecotrack.ui.pickup

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.ecotrack.HomeActivity
import com.example.ecotrack.R
import com.example.ecotrack.api.XenditApiService
import com.example.ecotrack.models.xendit.CreateInvoiceRequest
import com.example.ecotrack.models.xendit.Customer
import com.example.ecotrack.models.xendit.Item
import com.example.ecotrack.ui.pickup.model.PaymentMethod
import com.example.ecotrack.ui.pickup.model.PickupOrder
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.UUID

class OrderPickupActivity : AppCompatActivity() {

    private lateinit var etFullName: EditText
    private lateinit var etEmail: EditText
    private lateinit var btnProceedToPayment: Button
    private lateinit var btnEditLocation: View
    private lateinit var tvLocationAddress: android.widget.TextView
    private lateinit var mapView: MapView
    private var selectedLocation: GeoPoint? = null
    private var selectedAddress: String = ""

    // Add bottom navigation
    private lateinit var navHome: LinearLayout
    private lateinit var navSchedule: LinearLayout
    private lateinit var navLocation: LinearLayout
    private lateinit var navPickup: LinearLayout

    // Xendit API service
    private val xenditApiService by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()

        Retrofit.Builder()
            .baseUrl(XenditApiService.BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(XenditApiService::class.java)
    }

    // Hard-coded values for demo
    private val pickupOrderAmount = 500.0
    private val taxAmount = 50.0
    private val totalAmount = pickupOrderAmount + taxAmount

    // Tag for logging
    private val TAG = "OrderPickupActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize OpenStreetMap configuration
        Configuration.getInstance().load(
            applicationContext,
            PreferenceManager.getDefaultSharedPreferences(applicationContext)
        )

        setContentView(R.layout.activity_order_pickup)

        // Setup back button
        val backButton = findViewById<View>(R.id.backButton)
        backButton.setOnClickListener {
            onBackPressed()
        }

        // Initialize views
        etFullName = findViewById(R.id.et_full_name)
        etEmail = findViewById(R.id.et_email)
        btnProceedToPayment = findViewById(R.id.btn_proceed_to_payment)
        btnEditLocation = findViewById(R.id.btn_edit_location)
        tvLocationAddress = findViewById(R.id.tv_location_address)

        // Initialize map
        mapView = findViewById(R.id.map_view)
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)

        // Setup map with default location
        setupMap()

        // Initialize bottom navigation
        navHome = findViewById(R.id.nav_home)
        navSchedule = findViewById(R.id.nav_schedule)
        navLocation = findViewById(R.id.nav_location)
        navPickup = findViewById(R.id.nav_pickup)

        // Set click listeners for bottom navigation
        navHome.setOnClickListener {
            navigateToHome()
        }

        navSchedule.setOnClickListener {
            // Navigate to schedule screen
            // This would be implemented in a real app
            // For now, just show a quick message or navigate to home
            navigateToHome()
        }

        navLocation.setOnClickListener {
            // Navigate to location screen
            // This would be implemented in a real app
            // For now, just show a quick message or navigate to home
            navigateToHome()
        }

        // Pickup is the current screen, no need to navigate

        // Handle proceed to payment button click
        btnProceedToPayment.setOnClickListener {
            if (validateForm()) {
                btnProceedToPayment.isEnabled = false
                btnProceedToPayment.text = "Processing..."

                val fullName = etFullName.text.toString()
                val email = etEmail.text.toString()

                // Ensure PickupOrder has a unique ID, matching Xendit's external_id
                val uniqueOrderId = "order_${System.currentTimeMillis()}_${UUID.randomUUID()}"

                // For testing different payment methods, you can change this to any of the enum values
                // Options: GCASH, CASH_ON_HAND, CREDIT_CARD, PAYMAYA, GRABPAY, BANK_TRANSFER, OTC
                val testPaymentMethod = PaymentMethod.GCASH

                val order = PickupOrder(
                    id = uniqueOrderId, // Set the unique ID here
                    fullName = fullName,
                    email = email,
                    address = selectedAddress,
                    latitude = selectedLocation?.latitude ?: 0.0,
                    longitude = selectedLocation?.longitude ?: 0.0,
                    amount = pickupOrderAmount,
                    tax = taxAmount,
                    total = totalAmount,
                    paymentMethod = testPaymentMethod // Default, will be updated by Xendit choice
                )

                // Log the initial payment method
                Log.d(TAG, "Initial payment method: ${testPaymentMethod.name}, display name: ${testPaymentMethod.getDisplayName()}")

                // Save the order temporarily
                TempOrderHolder.saveOrder(order)

                createXenditInvoice(order) // Pass the order object
            }
        }

        // Handle edit location button click
        btnEditLocation.setOnClickListener {
            // Launch location picker activity
            val intent = Intent(this, MapPickerActivity::class.java)
            if (selectedLocation != null) {
                intent.putExtra("CURRENT_LAT", selectedLocation!!.latitude)
                intent.putExtra("CURRENT_LNG", selectedLocation!!.longitude)
            }
            startActivityForResult(intent, LOCATION_PICKER_REQUEST)
        }
    }

    private fun calculateTotalAmount(): Double {
        return totalAmount
    }

    // Renamed for clarity and to accept PickupOrder
    private fun createXenditInvoice(order: PickupOrder) {
        lifecycleScope.launch {
            try {
                // Use a clear query parameter for status, as path segments might be handled differently by gateways/browsers
                val successRedirectUrl = "ecotrack://payment-callback?redirect_status=success&order_id=${order.id}"
                val failureRedirectUrl = "ecotrack://payment-callback?redirect_status=failure&order_id=${order.id}" // also handle cancelled here

                val request = CreateInvoiceRequest(
                    externalId = order.id, // Use the order's unique ID as external_id
                    amount = calculateTotalAmount(),
                    description = "EcoTrack Trash Pickup Service for order ${order.id}",
                    customer = Customer(
                        givenNames = order.fullName,
                        email = order.email,
                        mobileNumber = "09123456789" // Placeholder, ideally get from user profile
                    ),
                    successRedirectUrl = successRedirectUrl,
                    failureRedirectUrl = failureRedirectUrl,
                    items = listOf(
                        Item(
                            name = "Trash Pickup Service",
                            quantity = 1,
                            price = calculateTotalAmount()
                        )
                    )
                )

                val response = xenditApiService.createInvoice(
                    authorization = XenditApiService.getAuthHeader(),
                    requestBody = request
                )

                if (response.isSuccessful) {
                    response.body()?.let {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(it.invoiceUrl))
                        startActivity(intent)
                        // Keep button disabled until user returns or cancels
                    } ?: run {
                        throw Exception("Empty response body from Xendit")
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    throw Exception("Failed to create Xendit invoice: $errorBody")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error creating Xendit invoice", e)
                runOnUiThread {
                    btnProceedToPayment.isEnabled = true
                    btnProceedToPayment.text = "PROCEED TO PAYMENT"
                    Toast.makeText(
                        this@OrderPickupActivity,
                        "Error creating invoice: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun setupMap() {
        // Default location (can be user's current location in production)
        val defaultLocation = GeoPoint(14.6091, 121.0223) // Manila, Philippines
        selectedLocation = defaultLocation
        selectedAddress = "Lagtang Talisay, 6045 Talisay City"

        // Configure map zoom
        mapView.controller.setZoom(15.0)
        mapView.controller.setCenter(defaultLocation)

        // Add marker
        addMarkerToMap(defaultLocation)

        // Update the location text
        tvLocationAddress.setText(selectedAddress)
    }

    private fun addMarkerToMap(geoPoint: GeoPoint) {
        // Clear previous markers
        mapView.overlays.clear()

        // Add new marker
        val marker = Marker(mapView)
        marker.position = geoPoint
        marker.title = "Pickup Location"
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        marker.icon = ContextCompat.getDrawable(this, R.drawable.ic_location_pin)
        mapView.overlays.add(marker)

        // Refresh map
        mapView.invalidate()
    }

    private fun navigateToHome() {
        val intent = Intent(this, HomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        startActivity(intent)
    }

    private fun validateForm(): Boolean {
        var isValid = true

        // Validate full name
        if (etFullName.text.toString().trim().isEmpty()) {
            etFullName.error = "Full name is required"
            isValid = false
        }

        // Validate email
        val email = etEmail.text.toString().trim()
        if (email.isEmpty()) {
            etEmail.error = "Email is required"
            isValid = false
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.error = "Please enter a valid email address"
            isValid = false
        }

        // Validate location
        if (selectedLocation == null) {
            // Show error for location
            tvLocationAddress.error = "Please select a location"
            isValid = false
        }

        return isValid
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == LOCATION_PICKER_REQUEST && resultCode == RESULT_OK && data != null) {
            // Get selected location from the map picker
            val latitude = data.getDoubleExtra("LATITUDE", 0.0)
            val longitude = data.getDoubleExtra("LONGITUDE", 0.0)
            selectedLocation = GeoPoint(latitude, longitude)
            selectedAddress = data.getStringExtra("ADDRESS") ?: ""

            // Update the map
            mapView.controller.setCenter(selectedLocation)
            addMarkerToMap(selectedLocation!!)

            // Update the location text
            tvLocationAddress.setText(selectedAddress)
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

    companion object {
        private const val LOCATION_PICKER_REQUEST = 1001
    }
}