package com.example.grabtrash.ui.pickup

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.grabtrash.HomeActivity
import com.example.grabtrash.R
import com.example.grabtrash.api.XenditApiService
import com.example.grabtrash.utils.ApiService
import com.example.grabtrash.utils.SessionManager
import com.example.grabtrash.models.xendit.CreateInvoiceRequest
import com.example.grabtrash.models.xendit.Customer
import com.example.grabtrash.models.xendit.Item
import com.example.grabtrash.models.payment.PaymentRequest
import com.example.grabtrash.models.quote.QuoteRequest
import com.example.grabtrash.ui.pickup.model.PaymentMethod
import com.example.grabtrash.ui.pickup.model.PickupOrder
import com.example.grabtrash.ui.pickup.model.WasteType
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

class OrderPickupActivity : AppCompatActivity() {

    private lateinit var etFullName: EditText
    private lateinit var etEmail: EditText
    private lateinit var spinnerWasteType: Spinner
    private lateinit var etTrashWeight: EditText
    private lateinit var etNotes: EditText
    private lateinit var btnProceedToPayment: Button
    private lateinit var btnEditLocation: View
    private lateinit var tvLocationAddress: TextView
    private lateinit var mapView: MapView
    
    private var selectedLocation: GeoPoint? = null
    private var selectedAddress: String = ""
    private var selectedWasteType: WasteType = WasteType.MIXED

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
        etTrashWeight = findViewById(R.id.et_trash_weight)
        etNotes = findViewById(R.id.et_notes)
        btnProceedToPayment = findViewById(R.id.btn_proceed_to_payment)
        btnEditLocation = findViewById(R.id.btn_edit_location)
        tvLocationAddress = findViewById(R.id.tv_location_address)

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

        // Initialize map
        mapView = findViewById(R.id.map_view)
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)

        // Initialize session manager
        sessionManager = SessionManager.getInstance(this)
        
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

                val order = createPickupOrder(selectedPaymentMethod)

                // Log the selected payment method
                Log.d(TAG, "Selected payment method: ${selectedPaymentMethod.name}, display name: ${selectedPaymentMethod.getDisplayName()}")

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

    // Removed truck loading and selection methods as per new requirements

    // Process cash on hand payment directly with backend
    private fun processCashOnHandPayment(order: PickupOrder) {
        lifecycleScope.launch {
            try {
                // Get auth token
                val token = sessionManager.getToken()
                if (token == null) {
                    throw Exception("Authentication token not available")
                }
                
                // Log order information
                Log.d(TAG, "Processing cash payment with trash weight: ${order.trashWeight} kg")
                
                // Create a payment request for the backend using the new structure
                val paymentRequest = PaymentRequest(
                    orderId = order.id,
                    customerName = order.fullName,
                    customerEmail = order.email,
                    address = order.address,
                    latitude = order.latitude,
                    longitude = order.longitude,
                    amount = 0.0, // Backend will calculate
                    tax = 0.0,
                    totalAmount = 0.0, // Backend will calculate
                    paymentMethod = "CASH_ON_HAND",
                    paymentReference = "COH-${order.id}",
                    notes = order.notes ?: "Cash on Hand payment",
                    wasteType = order.wasteType.name,
                    barangayId = order.barangayId,
                    trashWeight = order.trashWeight,
                    selectedTruckId = null,
                    truckId = null,
                    truckMake = null,
                    truckModel = null,
                    plateNumber = null
                )
                
                // Log the payment request details
                Log.d(TAG, "Payment request details: trashWeight=${paymentRequest.trashWeight}, paymentMethod=${paymentRequest.paymentMethod}, notes=${paymentRequest.notes}")
                
                // Make an API call to the backend to process the cash payment
                // This uses the same API endpoint that processes orders after Xendit payment
                val response = apiService.processPayment(paymentRequest, "Bearer $token")
                
                if (response.isSuccessful && response.body() != null) {
                    val paymentResult = response.body()!!
                    Log.d(TAG, "Cash on hand payment processed successfully for order ${order.id}")
                    Log.d(TAG, "Backend returned amount: ${paymentResult.amount}, total: ${paymentResult.totalAmount}")
                    
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
                // Log order information
                Log.d(TAG, "Creating Xendit invoice with trash weight: ${order.trashWeight} kg")
                
                // Step 1: Get quote from backend
                val token = sessionManager.getToken()
                if (token == null) {
                    throw Exception("Authentication token not available")
                }
                
                Log.d(TAG, "Step 1: Getting quote from backend...")
                
                // Create request to get quote from backend
                val quoteRequest = QuoteRequest(
                    customerEmail = order.email,
                    address = order.address,
                    latitude = order.latitude,
                    longitude = order.longitude,
                    wasteType = order.wasteType.name,
                    trashWeight = order.trashWeight
                )
                
                // Get quote from backend
                val quoteResponse = apiService.getQuote(quoteRequest, "Bearer $token")
                
                if (!quoteResponse.isSuccessful || quoteResponse.body() == null) {
                    val errorBody = quoteResponse.errorBody()?.string()
                    throw Exception("Failed to get quote from backend: $errorBody")
                }
                
                val quote = quoteResponse.body()!!
                val quoteAmount = quote.estimatedAmount
                val quoteTotal = quote.estimatedTotalAmount
                
                Log.d(TAG, "Quote received: quoteId=${quote.quoteId}, amount=₱$quoteAmount, total=₱$quoteTotal")
                Log.d(TAG, "Assigned truck: ${quote.truckDetails}")
                Log.d(TAG, "Assigned driver: ${quote.driverDetails}")
                
                // Validate quote pricing
                if (quoteAmount <= 0 && quoteTotal <= 0) {
                    throw Exception("Quote pricing calculation failed")
                }
                
                // Use a clear query parameter for status, as path segments might be handled differently by gateways/browsers
                val successRedirectUrl = "ecotrack://payment-callback?redirect_status=success&order_id=${order.id}"
                val failureRedirectUrl = "ecotrack://payment-callback?redirect_status=failure&order_id=${order.id}" // also handle cancelled here

                // Step 2: Save quote reply in frontend as temporary data (DON'T send to database)
                Log.d(TAG, "Step 2: Saving quote data in frontend temporary data (NOT in database)...")
                
                val orderWithQuoteData = order.copy(
                    paymentMethod = PaymentMethod.XENDIT_PAYMENT_GATEWAY,
                    amount = quoteAmount,
                    total = quoteTotal
                )
                
                // Store the order with quote data and quote response in frontend temporary storage ONLY
                TempOrderHolder.updateOrder(orderWithQuoteData)
                TempOrderHolder.saveQuote(order.id, quote) // Store quote for later use in payment callback
                Log.d(TAG, "Saved quote data in frontend temp storage: amount=₱$quoteAmount, total=₱$quoteTotal")
                Log.d(TAG, "IMPORTANT: Quote data saved ONLY in frontend memory, NOT in database!")
                
                // Step 3: Send to Xendit with quote pricing
                Log.d(TAG, "Step 3: Creating Xendit invoice with quote pricing...")
                Log.d(TAG, "Order details: Trash weight: ${orderWithQuoteData.trashWeight} kg, Amount: ₱${orderWithQuoteData.amount}, Total: ₱${orderWithQuoteData.total}")
                
                val request = CreateInvoiceRequest(
                    externalId = orderWithQuoteData.id, // Use the order's unique ID as external_id
                    amount = orderWithQuoteData.total,
                    description = "EcoTrack Trash Pickup Service - ${orderWithQuoteData.trashWeight}kg ${orderWithQuoteData.wasteType.getDisplayName()}",
                    customer = Customer(
                        givenNames = orderWithQuoteData.fullName,
                        email = orderWithQuoteData.email,
                        mobileNumber = "09123456789" // Placeholder, ideally get from user profile
                    ),
                    successRedirectUrl = successRedirectUrl,
                    failureRedirectUrl = failureRedirectUrl,
                    items = listOf(
                        Item(
                                name = "Trash Pickup Service (${orderWithQuoteData.trashWeight}kg)",
                                quantity = 1,
                                price = orderWithQuoteData.total
                            )
                    )
                )

                val response = xenditApiService.createInvoice(
                    authorization = XenditApiService.getAuthHeader(),
                    requestBody = request
                )

                if (response.isSuccessful) {
                    response.body()?.let {
                        Log.d(TAG, "Step 4: Successfully created Xendit invoice with quote pricing!")
                        Log.d(TAG, "Xendit invoice URL: ${it.invoiceUrl}, invoice ID: ${it.id}")
                        Log.d(TAG, "Invoice amount: ₱${orderWithQuoteData.total} (Quote pricing from backend)")
                        Log.d(TAG, "Next: User will pay quote amount, then callback will create payment with quote data")
                        
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(it.invoiceUrl))
                        startActivity(intent)
                        // Keep button disabled until user returns or cancels
                        // Note: PaymentCallbackActivity will handle final database storage and cleanup
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
        val trashWeight = etTrashWeight.text.toString().trim()

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
        
        if (trashWeight.isEmpty()) {
            etTrashWeight.error = "Please enter trash weight"
            return false
        }
        
        try {
            val weight = trashWeight.toDouble()
            if (weight <= 0) {
                etTrashWeight.error = "Weight must be greater than 0"
                return false
            }
        } catch (e: NumberFormatException) {
            etTrashWeight.error = "Please enter a valid weight"
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
        val trashWeight = etTrashWeight.text.toString().trim().toDoubleOrNull() ?: 0.0
        val notes = etNotes.text.toString().trim().takeIf { it.isNotEmpty() }
        
        return PickupOrder(
            fullName = etFullName.text.toString().trim(),
            email = etEmail.text.toString().trim(),
            address = selectedAddress,
            latitude = selectedLocation?.latitude ?: 0.0,
            longitude = selectedLocation?.longitude ?: 0.0,
            amount = 0.0, // Amount will be calculated by backend
            tax = 0.0,
            total = 0.0, // Total will be calculated by backend
            paymentMethod = paymentMethod,
            wasteType = selectedWasteType,
            trashWeight = trashWeight,
            notes = notes,
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