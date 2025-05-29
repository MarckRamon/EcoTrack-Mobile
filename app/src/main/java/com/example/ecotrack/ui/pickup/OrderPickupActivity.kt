package com.example.ecotrack.ui.pickup

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.ecotrack.HomeActivity
import com.example.ecotrack.R
import com.example.ecotrack.api.XenditApiService
import com.example.ecotrack.utils.ApiService
import com.example.ecotrack.utils.SessionManager
import com.example.ecotrack.models.xendit.CreateInvoiceRequest
import com.example.ecotrack.models.xendit.Customer
import com.example.ecotrack.models.xendit.Item
import com.example.ecotrack.models.payment.PaymentRequest
import com.example.ecotrack.ui.pickup.model.PaymentMethod
import com.example.ecotrack.ui.pickup.model.PickupOrder
import com.example.ecotrack.ui.pickup.model.WasteType
import com.example.ecotrack.models.Truck
import com.example.ecotrack.ui.pickup.dialogs.TruckSelectionDialog
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
    private lateinit var spinnerWasteType: Spinner
    private lateinit var btnProceedToPayment: Button
    private lateinit var btnEditLocation: View
    private lateinit var tvLocationAddress: TextView
    private lateinit var mapView: MapView
    private lateinit var btnSelectTruck: Button
    private lateinit var tvSelectedTruck: TextView
    
    private var selectedLocation: GeoPoint? = null
    private var selectedAddress: String = ""
    private var selectedWasteType: WasteType = WasteType.MIXED
    private var selectedTruck: Truck? = null
    private var availableTrucks: List<Truck> = emptyList()

    // Session manager for user data
    private lateinit var sessionManager: SessionManager
    private val apiService = ApiService.create()

    // User's barangay information
    private var userBarangayId: String? = null
    private var userBarangayName: String? = null

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

    // Pricing variables
    private var totalAmount: Double = 0.0

    // Tag for logging
    private val TAG = "OrderPickupActivity"

    // Replace startActivityForResult with ActivityResultLauncher
    private val locationPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            if (data != null) {
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
    }

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
        spinnerWasteType = findViewById(R.id.spinner_waste_type)
        btnProceedToPayment = findViewById(R.id.btn_proceed_to_payment)
        btnEditLocation = findViewById(R.id.btn_edit_location)
        tvLocationAddress = findViewById(R.id.tv_location_address)
        btnSelectTruck = findViewById(R.id.btn_select_truck)
        tvSelectedTruck = findViewById(R.id.tv_selected_truck)

        // Setup waste type spinner
        val wasteTypes = WasteType.values()
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            wasteTypes.map { it.getDisplayName() }
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerWasteType.adapter = adapter
        spinnerWasteType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedWasteType = wasteTypes[position]
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedWasteType = WasteType.MIXED
            }
        }

        // Setup truck selection button
        btnSelectTruck.setOnClickListener {
            if (availableTrucks.isEmpty()) {
                Toast.makeText(this, "Loading trucks, please wait...", Toast.LENGTH_SHORT).show()
                loadTrucks()
            } else {
                showTruckSelectionDialog()
            }
        }

        // Initialize map
        mapView = findViewById(R.id.map_view)
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)

        // Initialize session manager
        sessionManager = SessionManager.getInstance(this)

        // Load trucks from API
        loadTrucks()
        
        // Load user profile to get barangay information
        loadUserProfile()

        // Map will be set up after loading user profile

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

        // Initialize radio buttons for payment methods
        val radioCashOnHand = findViewById<RadioButton>(R.id.radio_cash_on_hand)
        
        // Handle proceed to payment button click
        btnProceedToPayment.setOnClickListener {
            if (validateForm()) {
                btnProceedToPayment.isEnabled = false
                btnProceedToPayment.text = "Processing..."

                // Determine payment method based on radio button selection
                val selectedPaymentMethod = if (radioCashOnHand.isChecked) {
                    PaymentMethod.CASH_ON_HAND
                } else {
                    PaymentMethod.GCASH // Default online payment method
                }

                calculateTotalAmount()
                val order = createPickupOrder(selectedPaymentMethod)

                // Log the selected payment method and truck information
                Log.d(TAG, "Selected payment method: ${selectedPaymentMethod.name}, display name: ${selectedPaymentMethod.getDisplayName()}")
                Log.d(TAG, "Selected truck: ${selectedTruck?.truckId}, make: ${selectedTruck?.make}, model: ${selectedTruck?.model}")

                // Save the order temporarily
                TempOrderHolder.saveOrder(order)

                if (selectedPaymentMethod == PaymentMethod.CASH_ON_HAND) {
                    // For cash on hand, send directly to backend
                    processCashOnHandPayment(order)
                } else {
                    // For online payment, use Xendit
                    createXenditInvoice(order)
                }
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
            locationPickerLauncher.launch(intent)
        }
    }

    private fun loadTrucks() {
        lifecycleScope.launch {
            try {
                // Get auth token
                val token = sessionManager.getToken()
                if (token == null) {
                    Log.e(TAG, "Authentication token not available")
                    Toast.makeText(
                        this@OrderPickupActivity,
                        "Authentication error, please log in again",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }
                
                // Add bearer token to the request
                val response = apiService.getTrucks("Bearer $token")
                
                if (response.isSuccessful && response.body() != null) {
                    availableTrucks = response.body()!!
                    Log.d(TAG, "Loaded ${availableTrucks.size} trucks")
                    
                    if (availableTrucks.isNotEmpty()) {
                        // Show the first available truck by default
                        val availableTruck = availableTrucks.firstOrNull { it.status.equals("AVAILABLE", ignoreCase = true) }
                        if (availableTruck != null) {
                            selectedTruck = availableTruck
                            updateSelectedTruckDisplay()
                        }
                    }
                } else {
                    Log.e(TAG, "Failed to load trucks: ${response.code()} - ${response.message()}")
                    Toast.makeText(
                        this@OrderPickupActivity,
                        "Failed to load trucks. Please try again.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading trucks", e)
                Toast.makeText(
                    this@OrderPickupActivity,
                    "Error loading trucks: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun showTruckSelectionDialog() {
        val availableTrucksFiltered = availableTrucks.filter { it.status.equals("AVAILABLE", ignoreCase = true) }
        
        if (availableTrucksFiltered.isEmpty()) {
            Toast.makeText(this, "No available trucks found", Toast.LENGTH_SHORT).show()
            return
        }
        
        val dialog = TruckSelectionDialog(this, availableTrucksFiltered) { truck ->
            selectedTruck = truck
            updateSelectedTruckDisplay()
            calculateTotalAmount()
        }
        
        dialog.show()
    }
    
    private fun updateSelectedTruckDisplay() {
        selectedTruck?.let {
            tvSelectedTruck.text = "${it.make} ${it.model} (₱${it.truckPrice})"
            tvSelectedTruck.setTextColor(ContextCompat.getColor(this, android.R.color.black))
        } ?: run {
            tvSelectedTruck.text = "No truck selected"
            tvSelectedTruck.setTextColor(ContextCompat.getColor(this, R.color.gray))
        }
    }

    private fun calculateTotalAmount(): Double {
        // Calculate the total amount (truck cost only, no tax)
        totalAmount = selectedTruck?.truckPrice ?: 0.0
        
        return totalAmount
    }

    // Process cash on hand payment directly with backend
    private fun processCashOnHandPayment(order: PickupOrder) {
        lifecycleScope.launch {
            try {
                // Get auth token
                val token = sessionManager.getToken()
                if (token == null) {
                    throw Exception("Authentication token not available")
                }
                
                // Log truck information from the order
                Log.d(TAG, "Processing cash payment with truck: ID=${order.selectedTruck?.truckId}, make=${order.selectedTruck?.make}, model=${order.selectedTruck?.model}")
                
                // Create a payment request for the backend
                val paymentRequest = PaymentRequest(
                    orderId = order.id,
                    customerName = order.fullName,
                    customerEmail = order.email,
                    address = order.address,
                    latitude = order.latitude,
                    longitude = order.longitude,
                    amount = order.amount,
                    tax = 0.0,
                    totalAmount = order.total,
                    paymentMethod = order.paymentMethod.getDisplayName(),
                    paymentReference = "cash_${order.id}",
                    notes = "Cash on Hand payment",
                    wasteType = order.wasteType.name,
                    barangayId = order.barangayId,
                    selectedTruckId = order.selectedTruck?.truckId,
                    truckId = order.selectedTruck?.truckId, // Add truckId field with the same value as selectedTruckId
                    truckMake = order.selectedTruck?.make, // Add truck make
                    truckModel = order.selectedTruck?.model, // Add truck model
                    plateNumber = order.selectedTruck?.plateNumber // Add plate number
                )
                
                // Log the payment request details
                Log.d(TAG, "Payment request details: selectedTruckId=${paymentRequest.selectedTruckId}, truckId=${paymentRequest.truckId}, make=${paymentRequest.truckMake}, model=${paymentRequest.truckModel}, plateNumber=${paymentRequest.plateNumber}, paymentMethod=${paymentRequest.paymentMethod}")
                
                // Make an API call to the backend to process the cash payment
                // This uses the same API endpoint that processes orders after Xendit payment
                val response = apiService.processPayment(paymentRequest, "Bearer $token")
                
                if (response.isSuccessful && response.body() != null) {
                    val paymentResult = response.body()!!
                    Log.d(TAG, "Cash on hand payment processed successfully for order ${order.id}")
                    
                    // Navigate to success page
                    runOnUiThread {
                        // Navigate to OrderSuccessActivity - same as would happen after Xendit payment
                        val intent = Intent(this@OrderPickupActivity, OrderSuccessActivity::class.java)
                        intent.putExtra("ORDER_DATA", order)
                        startActivity(intent)
                        finish() // Close this activity
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    throw Exception("Failed to process payment: $errorBody")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing cash payment", e)
                runOnUiThread {
                    btnProceedToPayment.isEnabled = true
                    btnProceedToPayment.text = "PROCEED TO PAYMENT"
                    Toast.makeText(
                        this@OrderPickupActivity,
                        "Error processing order: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    
    // Process online payment via Xendit
    private fun createXenditInvoice(order: PickupOrder) {
        lifecycleScope.launch {
            try {
                // Log truck information from the original order
                Log.d(TAG, "Creating Xendit invoice with original truck: ID=${order.selectedTruck?.truckId}, make=${order.selectedTruck?.make}, model=${order.selectedTruck?.model}")
                
                // Use a clear query parameter for status, as path segments might be handled differently by gateways/browsers
                val successRedirectUrl = "ecotrack://payment-callback?redirect_status=success&order_id=${order.id}"
                val failureRedirectUrl = "ecotrack://payment-callback?redirect_status=failure&order_id=${order.id}" // also handle cancelled here

                // Update the order with XENDIT_PAYMENT_GATEWAY payment method
                val updatedOrder = order.copy(paymentMethod = PaymentMethod.XENDIT_PAYMENT_GATEWAY)
                // Update the order in TempOrderHolder
                TempOrderHolder.updateOrder(updatedOrder)
                
                // Log truck information from the updated order
                Log.d(TAG, "Updated order with Xendit payment method. Truck: ID=${updatedOrder.selectedTruck?.truckId}, make=${updatedOrder.selectedTruck?.make}, model=${updatedOrder.selectedTruck?.model}")
                
                val request = CreateInvoiceRequest(
                    externalId = updatedOrder.id, // Use the order's unique ID as external_id
                    amount = updatedOrder.total,
                    description = "EcoTrack Trash Pickup Service for order ${updatedOrder.id}",
                    customer = Customer(
                        givenNames = updatedOrder.fullName,
                        email = updatedOrder.email,
                        mobileNumber = "09123456789" // Placeholder, ideally get from user profile
                    ),
                    successRedirectUrl = successRedirectUrl,
                    failureRedirectUrl = failureRedirectUrl,
                    items = listOf(
                        Item(
                            name = "Trash Pickup Service",
                            quantity = 1,
                            price = updatedOrder.total
                        )
                    )
                )

                val response = xenditApiService.createInvoice(
                    authorization = XenditApiService.getAuthHeader(),
                    requestBody = request
                )

                if (response.isSuccessful) {
                    response.body()?.let {
                        Log.d(TAG, "Successfully created Xendit invoice, URL: ${it.invoiceUrl}, invoice ID: ${it.id}")
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

    private fun loadUserProfile() {
        val token = sessionManager.getToken()
        val userId = sessionManager.getUserId()

        if (token == null || userId == null) {
            Log.e(TAG, "Missing credentials - token: $token, userId: $userId")
            // Use default location if user is not logged in
            setupMap()
            return
        }

        lifecycleScope.launch {
            try {
                val response = apiService.getProfile(userId, "Bearer $token")
                if (response.isSuccessful && response.body() != null) {
                    val profile = response.body()!!

                    // Get user's barangay information
                    userBarangayId = profile.barangayId
                    userBarangayName = profile.barangayName

                    Log.d(TAG, "User barangay: $userBarangayName (ID: $userBarangayId)")

                    // Set up map with user's barangay location
                    setupMap()

                    // Pre-fill user information
                    etFullName.setText("${profile.firstName ?: ""} ${profile.lastName ?: ""}")
                    etEmail.setText(profile.email)
                } else {
                    Log.e(TAG, "Failed to load profile: ${response.code()} - ${response.message()}")
                    // Use default location if profile loading fails
                    setupMap()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading profile", e)
                // Use default location if profile loading fails
                setupMap()
            }
        }
    }

    private fun setupMap() {
        // Default location (can be user's current location in production)
        val defaultLocation = GeoPoint(14.6091, 121.0223) // Manila, Philippines

        // If user has a barangay, use a location based on that barangay
        if (userBarangayName != null) {
            // In a real app, you would have a database of barangay coordinates
            // For now, we'll use a simple mapping for demonstration
            when (userBarangayName) {
                "Lagtang" -> {
                    selectedLocation = GeoPoint(10.2573, 123.8414) // Lagtang, Talisay coordinates
                    selectedAddress = "Lagtang, Talisay City"
                }
                "Tabunok" -> {
                    selectedLocation = GeoPoint(10.2667, 123.8333) // Tabunok, Talisay coordinates
                    selectedAddress = "Tabunok, Talisay City"
                }
                "Bulacao" -> {
                    selectedLocation = GeoPoint(10.2833, 123.8500) // Bulacao coordinates
                    selectedAddress = "Bulacao, Talisay City"
                }
                else -> {
                    // Use default location if barangay is not recognized
                    selectedLocation = defaultLocation
                    selectedAddress = userBarangayName ?: "Talisay City"
                }
            }
        } else {
            // Use default location if user has no barangay
            selectedLocation = defaultLocation
            selectedAddress = "Talisay City"
        }

        // Configure map zoom
        mapView.controller.setZoom(15.0)
        mapView.controller.setCenter(selectedLocation)

        // Add marker
        addMarkerToMap(selectedLocation!!)

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
        val fullName = etFullName.text.toString().trim()
        val email = etEmail.text.toString().trim()

        if (fullName.isEmpty()) {
            etFullName.error = "Please enter your full name"
            return false
        }

        if (email.isEmpty()) {
            etEmail.error = "Please enter your email"
            return false
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.error = "Please enter a valid email address"
            return false
        }
        
        if (selectedTruck == null) {
            Toast.makeText(this, "Please select a truck", Toast.LENGTH_SHORT).show()
            return false
        }

        if (selectedLocation == null) {
            Toast.makeText(this, "Please select a pickup location", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    private fun createPickupOrder(paymentMethod: PaymentMethod): PickupOrder {
        return PickupOrder(
            fullName = etFullName.text.toString().trim(),
            email = etEmail.text.toString().trim(),
            address = selectedAddress,
            latitude = selectedLocation?.latitude ?: 0.0,
            longitude = selectedLocation?.longitude ?: 0.0,
            amount = selectedTruck?.truckPrice ?: 0.0,
            tax = 0.0,
            total = totalAmount,
            paymentMethod = paymentMethod,
            wasteType = selectedWasteType,
            selectedTruck = selectedTruck,
            barangayId = userBarangayId
        )
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }
}