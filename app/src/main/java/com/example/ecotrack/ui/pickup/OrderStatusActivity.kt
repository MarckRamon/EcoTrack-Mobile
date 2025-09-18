package com.example.ecotrack.ui.pickup

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PorterDuff
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.preference.PreferenceManager
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.ecotrack.HomeActivity
import com.example.ecotrack.R
import com.example.ecotrack.models.JobOrderStatusUpdate
import com.example.ecotrack.models.payment.PaymentResponse
import com.example.ecotrack.ui.pickup.model.OrderStatus
import com.example.ecotrack.ui.pickup.model.PaymentMethod
import com.example.ecotrack.ui.pickup.model.PickupOrder
import com.example.ecotrack.utils.ApiService
import com.example.ecotrack.utils.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.*
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.app.AlertDialog
import android.Manifest
import android.content.pm.PackageManager
import android.provider.MediaStore
import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream
import com.google.android.material.button.MaterialButton
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.bumptech.glide.Glide
import android.widget.ImageButton

class OrderStatusActivity : AppCompatActivity() {

    private lateinit var tvStatusHeader: TextView
    private lateinit var tvStatusDescription: TextView
    private lateinit var tvWasteType: TextView
    private lateinit var tvSacks: TextView
    private lateinit var tvTruckSize: TextView
    private lateinit var tvAmount: TextView
    private lateinit var tvPaymentMethod: TextView
    private lateinit var tvLocation: TextView
    private lateinit var btnCancel: TextView
    private lateinit var backButton: ImageView
    private lateinit var order: PickupOrder
    private lateinit var apiService: ApiService
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var sessionManager: SessionManager
    
    // For status polling
    private val statusHandler = Handler(Looper.getMainLooper())
    private var statusRunnable: Runnable? = null
    // Set polling interval to 30 seconds to significantly reduce server load
    private val STATUS_POLL_INTERVAL = 30000L // 30 seconds
    
    // Add variables to track last request time and prevent redundant calls
    private val lastRequestTimes = mutableMapOf<String, Long>()
    private val MINIMUM_REQUEST_INTERVAL = 30000L // 30 seconds between requests for the same order

    // Status bar icons
    private lateinit var iconDriverAssigned: ImageView
    private lateinit var iconPickup: ImageView
    private lateinit var iconCompleted: ImageView

    // Status bar progress lines
    private lateinit var progressLine1: View
    private lateinit var progressLine2: View
    private lateinit var progressLine3: View

    // Bottom navigation
    private lateinit var navHome: LinearLayout
    private lateinit var navSchedule: LinearLayout
    private lateinit var navLocation: LinearLayout
    private lateinit var navPickup: LinearLayout

    private var lastKnownStatus: String? = null
    private lateinit var btnUploadProof: MaterialButton
    private lateinit var cardProof: androidx.cardview.widget.CardView
    private lateinit var ivProof: ImageView
    private lateinit var btnRemoveProof: ImageButton
    private var latestPaymentResponse: PaymentResponse? = null
    private var cachedProofUrl: String? = null
    private val CAMERA_REQUEST_CODE = 1001
    private val CAMERA_PERMISSION_CODE = 2001
    private lateinit var requestCameraPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var takePicturePreviewLauncher: ActivityResultLauncher<Void?>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_order_status)
        
        // Initialize API service
        apiService = ApiService.create()
        
        // Initialize shared preferences and session manager
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        sessionManager = SessionManager.getInstance(this)

        // Setup back button
        backButton = findViewById(R.id.backButton)
        backButton.setOnClickListener {
            onBackPressed()
        }

        // Get order data from intent
        order = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("ORDER_DATA", PickupOrder::class.java) ?: throw IllegalStateException("No order data provided")
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("ORDER_DATA") ?: throw IllegalStateException("No order data provided")
        }

        // Initialize views
        tvStatusHeader = findViewById(R.id.tv_status_header)
        tvStatusDescription = findViewById(R.id.tv_status_description)
        tvWasteType = findViewById(R.id.tv_waste_type)
        tvSacks = findViewById(R.id.tv_sacks)
        tvTruckSize = findViewById(R.id.tv_truck_size)
        tvAmount = findViewById(R.id.tv_amount)
        tvPaymentMethod = findViewById(R.id.tv_payment_method)
        tvLocation = findViewById(R.id.tv_location)
        btnCancel = findViewById(R.id.btn_cancel)
        btnUploadProof = findViewById(R.id.btn_upload_proof)
        btnUploadProof.setOnClickListener { maybeStartCamera() }
        cardProof = findViewById(R.id.card_proof_image)
        ivProof = findViewById(R.id.iv_proof_image)
        btnRemoveProof = findViewById(R.id.btn_remove_proof)
        btnRemoveProof.setOnClickListener { confirmRetakeProof() }

        // Initialize activity result launchers
        requestCameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                takePicturePreviewLauncher.launch(null)
            } else {
                Toast.makeText(this, "Camera permission is required to take a photo", Toast.LENGTH_SHORT).show()
            }
        }

        takePicturePreviewLauncher = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
            if (bitmap != null) {
                val tempFile = File.createTempFile("proof_", ".jpg", cacheDir)
                FileOutputStream(tempFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                uploadToCatboxAndSave(tempFile)
            } else {
                Toast.makeText(this, "No image captured", Toast.LENGTH_SHORT).show()
            }
        }

        // Initialize status bar icons and lines
        iconDriverAssigned = findViewById(R.id.icon_driver_assigned)
        iconPickup = findViewById(R.id.icon_pickup)
        iconCompleted = findViewById(R.id.icon_completed)

        val progressContainer = findViewById<LinearLayout>(R.id.progress_container)
        progressLine1 = progressContainer.getChildAt(0) as View
        progressLine2 = progressContainer.getChildAt(2) as View
        progressLine3 = progressContainer.getChildAt(4) as View

        // Initialize bottom navigation if it exists in this layout
        try {
            navHome = findViewById(R.id.nav_home)
            navSchedule = findViewById(R.id.nav_schedule)
            navLocation = findViewById(R.id.nav_location)
            navPickup = findViewById(R.id.nav_pickup)

            // Set click listeners for bottom navigation
            navHome.setOnClickListener {
                navigateToHome()
            }

            navSchedule.setOnClickListener {
                navigateToHome()
            }

            navLocation.setOnClickListener {
                navigateToHome()
            }

            navPickup.setOnClickListener {
                navigateToPickup()
            }
        } catch (e: Exception) {
            // Bottom navigation might not be in this layout
        }

        // Set values
        tvWasteType.text = order.wasteType.getDisplayName()
        tvSacks.text = order.selectedTruck?.plateNumber ?: "N/A" // Display plate number instead of sacks
        tvTruckSize.text = "${order.selectedTruck?.make ?: "N/A"} ${order.selectedTruck?.model ?: ""}" // Display make and model
        tvAmount.text = "₱${order.total.toInt()}"
        tvPaymentMethod.text = order.paymentMethod.getDisplayName()
        tvLocation.text = order.address

        // Log payment method for debugging
        Log.d("OrderStatusActivity", "Payment method: ${order.paymentMethod.name}, display name: ${order.paymentMethod.getDisplayName()}")

        // Initially, set the status based on local order status
        val initialStatus = when(order.status) {
            OrderStatus.PROCESSING -> "Processing"
            OrderStatus.ACCEPTED -> "Accepted"
            OrderStatus.COMPLETED -> "Completed"
            OrderStatus.CANCELLED -> "Cancelled"
        }
        updateOrderStatus(initialStatus)
        // Try to render any existing proof image early (before network) using cached or last known response
        try {
            // 1) Intent extra from navigator (Home banner)
            val intentProof = intent.getStringExtra("PROOF_IMAGE_URL")
            if (!intentProof.isNullOrBlank()) {
                cachedProofUrl = intentProof
                showOrHideProof(intentProof)
            } else {
                // 2) Last known API response value
                latestPaymentResponse?.getEffectiveConfirmationImageUrl()?.let {
                    cachedProofUrl = it
                    showOrHideProof(it)
                }
                // 3) Local cache by order id
                if (cardProof.visibility != View.VISIBLE) {
                    val cached = sharedPreferences.getString("proof_order_${order.id}", null)
                    if (!cached.isNullOrBlank()) {
                        cachedProofUrl = cached
                        showOrHideProof(cached)
                    }
                }
            }
        } catch (_: Exception) {}
        
        // Check if we should force a refresh (coming from OrderSuccessActivity)
        val forceRefresh = intent.getBooleanExtra("FORCE_REFRESH", false)
        if (forceRefresh) {
            Log.d("OrderStatusActivity", "Force refresh requested - fetching latest data immediately")
            fetchLatestStatus(forceRefresh = true)
        } else {
            // Fetch the latest status normally
            fetchLatestStatus()
        }
        
        // Set up periodic status checking
        startStatusPolling()

        // Initialize cancel button: hidden by default until backend confirms Processing
        btnCancel.visibility = View.GONE
        btnCancel.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Cancel Order")
                .setMessage("Are you sure you want to cancel this order?")
                .setPositiveButton("Yes") { _, _ ->
                    cancelOrder()
                }
                .setNegativeButton("No", null)
                .show()
        }

        // Add debug functionality - long press on payment method text to cycle through payment methods
        tvPaymentMethod.setOnLongClickListener {
            // Cycle through payment methods for testing
            val methods = PaymentMethod.values()
            val currentIndex = methods.indexOf(order.paymentMethod)
            val nextIndex = (currentIndex + 1) % methods.size
            val newMethod = methods[nextIndex]

            // Update the order with the new payment method
            order = order.copy(paymentMethod = newMethod)

            // Update the UI
            tvPaymentMethod.text = order.paymentMethod.getDisplayName()

            // Log the change
            Log.d("OrderStatusActivity", "Changed payment method to: ${order.paymentMethod.name}, display name: ${order.paymentMethod.getDisplayName()}")

            return@setOnLongClickListener true
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Fetch the latest status immediately when resuming
        fetchLatestStatus()
        // Restart polling
        startStatusPolling()
    }
    
    override fun onPause() {
        super.onPause()
        // Stop polling when activity is paused
        stopStatusPolling()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Ensure polling is stopped when activity is destroyed
        stopStatusPolling()
    }
    
    private fun startStatusPolling() {
        // Stop any existing polling
        stopStatusPolling()
        
        statusRunnable = object : Runnable {
            override fun run() {
                fetchLatestStatus()
                statusHandler.postDelayed(this, STATUS_POLL_INTERVAL)
            }
        }
        // Start polling with initial delay to avoid immediate duplicate request after onCreate/onResume fetch
        statusHandler.postDelayed(statusRunnable!!, STATUS_POLL_INTERVAL)
    }
    
    private fun stopStatusPolling() {
        statusRunnable?.let {
            statusHandler.removeCallbacks(it)
            statusRunnable = null
        }
    }
    
    private fun fetchLatestStatus(forceRefresh: Boolean = false) {
        // Use the payment reference number to fetch the latest status
        val orderId = order.id // Assuming this is the orderId used in the backend
        val referenceNumber = order.referenceNumber // Try using the reference number as an alternative
        
        // Skip if we recently made a request for this order (avoid redundant calls)
        // Unless forceRefresh is true, in which case always make the request
        val currentTime = System.currentTimeMillis()
        val lastRequestTime = lastRequestTimes[orderId] ?: 0L
        
        if (!forceRefresh && currentTime - lastRequestTime < MINIMUM_REQUEST_INTERVAL) {
            Log.d("OrderStatusActivity", "Skipping redundant request for orderId: $orderId (last request was ${currentTime - lastRequestTime}ms ago)")
            return
        }
        
        // Update the last request time
        lastRequestTimes[orderId] = currentTime
        
        Log.d("OrderStatusActivity", "Fetching status for orderId: $orderId, referenceNumber: $referenceNumber" + if (forceRefresh) " (forced refresh)" else "")
        
        // Get the JWT token from the SessionManager
        val jwtToken = sessionManager.getToken()
        
        var bearerToken = if (!jwtToken.isNullOrBlank()) {
            if (jwtToken.startsWith("Bearer ")) jwtToken else "Bearer $jwtToken"
        } else null
        
        if (bearerToken != null) {
            Log.d("OrderStatusActivity", "Using JWT token from SessionManager")
        } else {
            Log.e("OrderStatusActivity", "No JWT token available from SessionManager - this will likely cause a 403 error")
            
            // Fall back to SharedPreferences if SessionManager doesn't have the token
            val fallbackToken = sharedPreferences.getString("jwt_token", null) 
                ?: sharedPreferences.getString("auth_token", null)
                ?: sharedPreferences.getString("token", null)
                
            if (fallbackToken != null) {
                Log.d("OrderStatusActivity", "Found fallback token in SharedPreferences")
                bearerToken = if (fallbackToken.startsWith("Bearer ")) fallbackToken else "Bearer $fallbackToken"
            } else {
                // List available keys for debugging
                val allKeys = sharedPreferences.all.keys
                Log.d("OrderStatusActivity", "Available SharedPreferences keys: $allKeys")
                
                // DEBUGGING ONLY: Try a hardcoded token to see if it helps with the 403 error
                // REMOVE THIS IN PRODUCTION
                bearerToken = "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VySWQiOiI2NTkzZmRlOTg3Mjc4ZWNkYTVjNWRmNmUiLCJlbWFpbCI6InVzZXJAZXhhbXBsZS5jb20iLCJyb2xlIjoiQ1VTVE9NRVIiLCJpYXQiOjE3MjA0OTk4NTAsImV4cCI6MTcyMDUwMzQ1MH0.uoRqcwx5WKbK5XLQPEyOlw3WFnqxIJ8MlGJLSfm0zJo"
                Log.d("OrderStatusActivity", "*** USING DEBUG TOKEN FOR TESTING - REMOVE IN PRODUCTION ***")
            }
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // First try with order ID
                var response = if (bearerToken != null) {
                    apiService.getPaymentByOrderId(orderId, bearerToken)
                } else {
                    // This will likely fail with 403 Forbidden if JWT is required
                    apiService.getPaymentByOrderId(orderId)
                }
                
                // If first attempt fails, try with reference number
                if (!response.isSuccessful) {
                    Log.d("OrderStatusActivity", "First attempt failed with code ${response.code()}, trying with reference number")
                    response = if (bearerToken != null) {
                        apiService.getPaymentByOrderId(referenceNumber, bearerToken)
                    } else {
                        apiService.getPaymentByOrderId(referenceNumber)
                    }
                }
                
                withContext(Dispatchers.Main) {
                    processApiResponse(response, bearerToken)
                }
            } catch (e: Exception) {
                Log.e("OrderStatusActivity", "Error fetching status", e)
            }
        }
    }
    
    private suspend fun processApiResponse(response: Response<PaymentResponse>, bearerToken: String?) {
        if (response.isSuccessful && response.body() != null) {
            val paymentResponse = response.body()!!
            latestPaymentResponse = paymentResponse
            Log.d("OrderStatusActivity", "API Response: ${response.code()}")
            Log.d("OrderStatusActivity", "Fetched payment: id=${paymentResponse.id}, orderId=${paymentResponse.orderId}")
            Log.d("OrderStatusActivity", "Fetched status: ${paymentResponse.jobOrderStatus}, status: ${paymentResponse.status}")
            Log.d("OrderStatusActivity", "All payment data: $paymentResponse")
            
            // Check if we should use jobOrderStatus or regular status
            val effectiveStatus = paymentResponse.jobOrderStatus.takeIf { status -> status.isNotBlank() } ?: paymentResponse.status
            
            // Only update UI if status has changed
            if (lastKnownStatus != effectiveStatus) {
                Log.d("OrderStatusActivity", "Status change detected: $lastKnownStatus -> $effectiveStatus")
                
                // If there's a truckId in the response, try to fetch complete truck details
                if (!paymentResponse.truckId.isNullOrBlank() && bearerToken != null) {
                    fetchTruckDetails(paymentResponse.truckId, bearerToken, paymentResponse)
                } else {
                    // Update UI with the latest status if we can't fetch truck details
                    updateOrderStatus(effectiveStatus, paymentResponse)
                    
                    // Update order details from API response
                    updateOrderDetails(paymentResponse)
                    // Always update proof image and refresh cache regardless of status change
                    paymentResponse.getEffectiveConfirmationImageUrl()?.let { cachedProofUrl = it }
                    showOrHideProof(paymentResponse.getEffectiveConfirmationImageUrl() ?: cachedProofUrl)
                    
                    // Hide cancel button unless status is Processing or Available
                    if (effectiveStatus != "Processing" && effectiveStatus != "Available") {
                        btnCancel.visibility = View.GONE
                    }
                }
            } else {
                Log.d("OrderStatusActivity", "Status unchanged, ensuring proof preview is updated")
                // Ensure proof image is shown even if status didn't change
                paymentResponse.getEffectiveConfirmationImageUrl()?.let { cachedProofUrl = it }
                showOrHideProof(paymentResponse.getEffectiveConfirmationImageUrl() ?: cachedProofUrl ?: run {
                    // fallback: try local cache by id
                    try {
                        val pid = paymentResponse.id
                        sharedPreferences.getString("proof_${pid}", null)
                            ?: sharedPreferences.getString("proof_order_${paymentResponse.orderId}", null)
                    } catch (_: Exception) { null }
                })
            }
        } else {
            Log.e("OrderStatusActivity", "Failed to fetch status: ${response.code()} - ${response.message()}")
            try {
                val errorBody = response.errorBody()?.string() ?: "No error body"
                Log.e("OrderStatusActivity", "Error body: $errorBody")
                
                // If we get a 403 Forbidden error, log additional debugging info but don't show a toast
                if (response.code() == 403) {
                    // For debugging, check if a hardcoded token works
                    if (bearerToken?.startsWith("Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9") == true) {
                        Log.d("OrderStatusActivity", "Used hardcoded token but still got 403 - backend may require a valid token")
                    } else {
                        Log.d("OrderStatusActivity", "Not using debug token")
                    }
                }
            } catch (e: Exception) {
                Log.e("OrderStatusActivity", "Error reading error body", e)
            }
        }
    }

    /**
     * Fetch complete truck details by ID from the trucks API
     */
    private suspend fun fetchTruckDetails(truckId: String, bearerToken: String, paymentResponse: PaymentResponse) {
        try {
            Log.d("OrderStatusActivity", "Fetching detailed truck information for truckId: $truckId")
            val response = apiService.getTrucks(bearerToken)
            
            if (response.isSuccessful && response.body() != null) {
                val trucks = response.body()!!
                Log.d("OrderStatusActivity", "Fetched ${trucks.size} trucks from API")
                
                // Find the truck with matching ID (try case-insensitive matching if exact match fails)
                var matchingTruck = trucks.find { it.truckId == truckId }
                
                // If no exact match, try case-insensitive comparison
                if (matchingTruck == null) {
                    matchingTruck = trucks.find { it.truckId.equals(truckId, ignoreCase = true) }
                    if (matchingTruck != null) {
                        Log.d("OrderStatusActivity", "Found truck with case-insensitive match: ${matchingTruck.truckId} for requested ID: $truckId")
                    }
                }
                
                if (matchingTruck != null) {
                    Log.d("OrderStatusActivity", "Found matching truck: ${matchingTruck.make} ${matchingTruck.model}, plate: ${matchingTruck.plateNumber}")
                    
                    // Create an enhanced payment response with the complete truck info
                    val enhancedResponse = paymentResponse.copy(
                        truckMake = matchingTruck.make,
                        truckModel = matchingTruck.model,
                        plateNumber = matchingTruck.plateNumber
                    )
                    
                    // Now update UI with the enhanced response
                    withContext(Dispatchers.Main) {
                        updateOrderStatus(enhancedResponse.jobOrderStatus, enhancedResponse)
                        updateOrderDetails(enhancedResponse)
                        
                        // Hide cancel button unless status is Processing or Available
                        if (enhancedResponse.jobOrderStatus != "Processing" && enhancedResponse.jobOrderStatus != "Available") {
                            btnCancel.visibility = View.GONE
                        }
                    }
                    
                    return
                } else {
                    Log.w("OrderStatusActivity", "No matching truck found for ID: $truckId")
                }
            } else {
                Log.e("OrderStatusActivity", "Failed to fetch trucks: ${response.code()}")
            }
            
            // Fall back to original update if truck details couldn't be found
            withContext(Dispatchers.Main) {
                updateOrderStatus(paymentResponse.jobOrderStatus, paymentResponse)
                updateOrderDetails(paymentResponse)
                showOrHideProof(paymentResponse.getEffectiveConfirmationImageUrl()
                    ?: sharedPreferences.getString("proof_order_${paymentResponse.orderId}", null))
                
                // Hide cancel button unless status is Processing or Available
                if (paymentResponse.jobOrderStatus != "Processing" && paymentResponse.jobOrderStatus != "Available") {
                    btnCancel.visibility = View.GONE
                }
            }
        } catch (e: Exception) {
            Log.e("OrderStatusActivity", "Error fetching truck details", e)
            
            // Fall back to original update
            withContext(Dispatchers.Main) {
                updateOrderStatus(paymentResponse.jobOrderStatus, paymentResponse)
                updateOrderDetails(paymentResponse)
                showOrHideProof(paymentResponse.getEffectiveConfirmationImageUrl()
                    ?: sharedPreferences.getString("proof_order_${paymentResponse.orderId}", null))
                
                // Hide cancel button unless status is Processing or Available
                if (paymentResponse.jobOrderStatus != "Processing" && paymentResponse.jobOrderStatus != "Available") {
                    btnCancel.visibility = View.GONE
                }
            }
        }
    }

    private fun navigateToHome() {
        val intent = Intent(this, HomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        startActivity(intent)
    }

    private fun navigateToPickup() {
        val intent = Intent(this, OrderPickupActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        startActivity(intent)
    }

    private fun maybeStartCamera() {
        val hasCameraApp = packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
        if (!hasCameraApp) {
            Toast.makeText(this, "No camera available on this device", Toast.LENGTH_SHORT).show()
            return
        }
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            takePicturePreviewLauncher.launch(null)
        }
    }

    // Old permission and activity result overrides are no longer used; kept out for clarity

    private fun uploadToCatboxAndSave(imageFile: File) {
        // Upload to Catbox using multipart form
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = OkHttpClient()
                val mediaType = "image/jpeg".toMediaTypeOrNull()
                val fileBody = imageFile.asRequestBody(mediaType)
                val multipart = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("reqtype", "fileupload")
                    .addFormDataPart("userhash", "9977879e19e2ca7543183dd67")
                    .addFormDataPart("fileToUpload", imageFile.name, fileBody)
                    .build()

                val request = Request.Builder()
                    .url("https://catbox.moe/user/api.php")
                    .post(multipart)
                    .build()

                val response = client.newCall(request).execute()
                val bodyString = response.body?.string()?.trim()
                if (!response.isSuccessful || bodyString.isNullOrBlank()) {
                    Log.e("OrderStatusActivity", "Catbox upload failed: ${response.code} ${response.message}")
                    withContext(Dispatchers.Main) { Toast.makeText(this@OrderStatusActivity, "Failed to upload image", Toast.LENGTH_SHORT).show() }
                    return@launch
                }

                val imageUrl = bodyString // Catbox returns the file URL directly

                // Save to backend
                val jwtToken = sessionManager.getToken()
                if (jwtToken.isNullOrBlank()) {
                    withContext(Dispatchers.Main) { Toast.makeText(this@OrderStatusActivity, "Missing auth token", Toast.LENGTH_SHORT).show() }
                    return@launch
                }
                val bearerToken = if (jwtToken.startsWith("Bearer ")) jwtToken else "Bearer $jwtToken"

                // We need the paymentId; prefer latest response id, otherwise fetch by order id
                val paymentId = latestPaymentResponse?.id ?: run {
                    val resp = apiService.getPaymentByOrderId(order.id, bearerToken)
                    if (resp.isSuccessful && resp.body() != null) resp.body()!!.id else null
                }

                if (paymentId == null) {
                    withContext(Dispatchers.Main) { Toast.makeText(this@OrderStatusActivity, "Unable to resolve payment ID", Toast.LENGTH_SHORT).show() }
                    return@launch
                }

                val payload = mapOf("imageUrl" to imageUrl)
                val saveResp = apiService.uploadPaymentConfirmationImage(paymentId, payload, bearerToken)
                withContext(Dispatchers.Main) {
                    if (saveResp.isSuccessful) {
                        Toast.makeText(this@OrderStatusActivity, "Proof uploaded", Toast.LENGTH_SHORT).show()
                        showOrHideProof(imageUrl)
                        // Persist locally as fallback
                        try {
                            sharedPreferences.edit()
                                .putString("proof_${paymentId}", imageUrl)
                                .putString("proof_order_${order.id}", imageUrl)
                                .apply()
                        } catch (_: Exception) {}
                    } else {
                        Toast.makeText(this@OrderStatusActivity, "Failed to save proof", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("OrderStatusActivity", "Error uploading image", e)
                withContext(Dispatchers.Main) { Toast.makeText(this@OrderStatusActivity, "Error uploading image", Toast.LENGTH_SHORT).show() }
            } finally {
                imageFile.delete()
            }
        }
    }

    private fun showOrHideProof(url: String?) {
        if (!this::cardProof.isInitialized) return
        val effectiveUrl = url ?: cachedProofUrl
        if (!effectiveUrl.isNullOrBlank()) {
            cardProof.visibility = View.VISIBLE
            try {
                com.bumptech.glide.Glide.with(this)
                    .load(effectiveUrl)
                    .centerCrop()
                    .into(ivProof)
            } catch (_: Exception) { }
        } else {
            cardProof.visibility = View.GONE
        }
    }

private fun confirmRetakeProof() {
    AlertDialog.Builder(this)
        .setTitle("Retake proof photo?")
        .setMessage("This will let you capture a new proof photo to replace the current one.")
        .setPositiveButton("Retake") { _, _ -> maybeStartCamera() }
        .setNegativeButton("Cancel", null)
        .show()
}

// Removing proof via API is disabled due to server limits; user can retake instead.

    // Add animation utils with more subtle parameters
    private fun animateColorChange(view: View, fromColor: Int, toColor: Int, duration: Long = 800) {
        val colorAnimation = ValueAnimator.ofObject(ArgbEvaluator(), fromColor, toColor)
        colorAnimation.duration = duration
        colorAnimation.addUpdateListener { animator ->
            view.setBackgroundColor(animator.animatedValue as Int)
        }
        colorAnimation.start()
    }
    
    private fun animateIconColorFilter(icon: ImageView, fromColor: Int, toColor: Int, duration: Long = 800) {
        val colorAnimation = ValueAnimator.ofObject(ArgbEvaluator(), fromColor, toColor)
        colorAnimation.duration = duration
        colorAnimation.addUpdateListener { animator ->
            icon.setColorFilter(animator.animatedValue as Int)
        }
        colorAnimation.start()
    }
    
    private fun pulseAnimation(view: View) {
        // More subtle pulse with smaller scale change
        view.animate()
            .scaleX(1.1f)
            .scaleY(1.1f)
            .setDuration(400)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                view.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(300)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
            .start()
    }
    
    private fun showStatusChangeNotification(newStatus: String) {
        // Visual feedback for status change - only animate if there's an actual status change
        runOnUiThread {
            // Find the active icon to animate
            val iconToAnimate = when (newStatus) {
                "Processing", "Available" -> null // No icon to animate for processing
                "Accepted" -> iconDriverAssigned
                "In-Progress" -> iconPickup
                "Completed" -> iconCompleted
                else -> null
            }
            
            // Apply a subtle pulse animation to the icon - only if we're not in the initial load
            if (lastKnownStatus != null && iconToAnimate != null) {
                pulseAnimation(iconToAnimate)
            }
        }
    }

    private fun updateOrderStatus(jobOrderStatus: String, paymentResponse: PaymentResponse? = null) {
        Log.d("OrderStatusActivity", "Updating UI status to: $jobOrderStatus")
        
        // Detect status change and provide visual feedback, but only if this isn't the first load
        val isStatusChange = lastKnownStatus != null && lastKnownStatus != jobOrderStatus
        if (isStatusChange) {
            showStatusChangeNotification(jobOrderStatus)
        }
        
        // Store current status for next comparison
        lastKnownStatus = jobOrderStatus
        
        val greenColor = ContextCompat.getColor(this, R.color.green)
        val greyColor = ContextCompat.getColor(this, R.color.stroke_color)

        when (jobOrderStatus) {
            "Processing", "Available" -> {
                tvStatusHeader.text = "Processing"
                tvStatusDescription.text = "Waiting for driver to accept order"
                
                // If this is initial load, just set colors without animation
                if (!isStatusChange) {
                    iconDriverAssigned.setColorFilter(greyColor)
                    iconPickup.setColorFilter(greyColor)
                    iconCompleted.setColorFilter(greyColor)
                    
                    progressLine1.setBackgroundColor(greyColor)
                    progressLine2.setBackgroundColor(greyColor)
                    progressLine3.setBackgroundColor(greyColor)
                } else {
                    // All icons and lines should be grey - with animation for changes
                    animateIconColorFilter(iconDriverAssigned, greenColor, greyColor)
                    animateIconColorFilter(iconPickup, greenColor, greyColor)
                    animateIconColorFilter(iconCompleted, greenColor, greyColor)
                    
                    animateColorChange(progressLine1, greenColor, greyColor)
                    animateColorChange(progressLine2, greenColor, greyColor)
                    animateColorChange(progressLine3, greenColor, greyColor)
                }
                btnCancel.visibility = View.VISIBLE
                btnUploadProof.visibility = View.GONE
                showOrHideProof(null)
            }
            "Accepted" -> {
                // Get estimated arrival time from backend if available
                // If not, calculate a reasonable estimate (e.g., 30 minutes from now)
                val estimatedTime = if (paymentResponse != null) {
                    val arrivalTime = paymentResponse.getEffectiveEstimatedArrival()
                    if (arrivalTime != null) {
                        formatEstimatedTime(arrivalTime)
                    } else {
                        // Default to a 30-minute estimate if no time provided
                        val calendar = Calendar.getInstance()
                        calendar.add(Calendar.MINUTE, 30)
                        formatEstimatedTime(calendar.time)
                    }
                } else {
                    // Default to a 30-minute estimate if no payment response
                    val calendar = Calendar.getInstance()
                    calendar.add(Calendar.MINUTE, 30)
                    formatEstimatedTime(calendar.time)
                }
                
                tvStatusHeader.text = "Arriving by $estimatedTime"
                tvStatusDescription.text = "We've got your pickup order!"
                
                // If this is initial load, just set colors without animation
                if (!isStatusChange) {
                    iconDriverAssigned.setColorFilter(greenColor)
                    progressLine1.setBackgroundColor(greenColor)
                    iconPickup.setColorFilter(greenColor)
                    iconCompleted.setColorFilter(greyColor)
                    progressLine2.setBackgroundColor(greyColor)
                    progressLine3.setBackgroundColor(greyColor)
                } else {
                    // Animate only if there's a status change
                    // Current icon is green
                    animateIconColorFilter(iconDriverAssigned, greyColor, greenColor)
                    // Current icon's line is green
                    animateColorChange(progressLine1, greyColor, greenColor)
                    // Next icon is green but its line is not
                    animateIconColorFilter(iconPickup, greyColor, greenColor)
                    // Make sure other items are grey
                    animateIconColorFilter(iconCompleted, greenColor, greyColor)
                    animateColorChange(progressLine2, greenColor, greyColor)
                    animateColorChange(progressLine3, greenColor, greyColor)
                }
                btnCancel.visibility = View.GONE
                btnUploadProof.visibility = View.GONE
                if (paymentResponse?.getEffectiveConfirmationImageUrl().isNullOrBlank()) {
                    showOrHideProof(cachedProofUrl)
                } else {
                    paymentResponse?.getEffectiveConfirmationImageUrl()?.let { cachedProofUrl = it }
                    showOrHideProof(paymentResponse?.getEffectiveConfirmationImageUrl())
                }
            }
            "In-Progress" -> {
                tvStatusHeader.text = "Pickup in Progress"
                tvStatusDescription.text = "Driver is on the way to your location."
                
                // If this is initial load, just set colors without animation
                if (!isStatusChange) {
                    iconDriverAssigned.setColorFilter(greenColor)
                    progressLine1.setBackgroundColor(greenColor)
                    iconPickup.setColorFilter(greenColor)
                    progressLine2.setBackgroundColor(greenColor)
                    iconCompleted.setColorFilter(greenColor)
                    progressLine3.setBackgroundColor(greyColor)
                } else {
                    // Animate only if there's a status change
                    // Previous icon and its line stay green
                    animateIconColorFilter(iconDriverAssigned, greyColor, greenColor)
                    animateColorChange(progressLine1, greyColor, greenColor)
                    
                    // Current icon is green
                    animateIconColorFilter(iconPickup, greyColor, greenColor)
                    // Current icon's line is green
                    animateColorChange(progressLine2, greyColor, greenColor)
                    
                    // Next icon is green but its line is not
                    animateIconColorFilter(iconCompleted, greyColor, greenColor)
                    // Last line stays grey
                    animateColorChange(progressLine3, greenColor, greyColor)
                }
                btnCancel.visibility = View.GONE
                btnUploadProof.visibility = View.VISIBLE
                if (paymentResponse?.getEffectiveConfirmationImageUrl().isNullOrBlank()) {
                    showOrHideProof(cachedProofUrl)
                } else {
                    paymentResponse?.getEffectiveConfirmationImageUrl()?.let { cachedProofUrl = it }
                    showOrHideProof(paymentResponse?.getEffectiveConfirmationImageUrl())
                }
            }
            "Completed" -> {
                tvStatusHeader.text = "Completed"
                tvStatusDescription.text = "Your trash has been picked up"
                
                // If this is initial load, just set colors without animation
                if (!isStatusChange) {
                    iconDriverAssigned.setColorFilter(greenColor)
                    progressLine1.setBackgroundColor(greenColor)
                    iconPickup.setColorFilter(greenColor)
                    progressLine2.setBackgroundColor(greenColor)
                    iconCompleted.setColorFilter(greenColor)
                    progressLine3.setBackgroundColor(greenColor)
                } else {
                    // Animate only if there's a status change
                    // All previous icons and lines stay green
                    animateIconColorFilter(iconDriverAssigned, greyColor, greenColor)
                    animateColorChange(progressLine1, greyColor, greenColor)
                    animateIconColorFilter(iconPickup, greyColor, greenColor)
                    animateColorChange(progressLine2, greyColor, greenColor)
                    
                    // Current icon is green
                    animateIconColorFilter(iconCompleted, greyColor, greenColor)
                    // Current icon's line is green
                    animateColorChange(progressLine3, greyColor, greenColor)
                }
                btnCancel.visibility = View.GONE
                btnUploadProof.visibility = View.VISIBLE
                if (paymentResponse?.getEffectiveConfirmationImageUrl().isNullOrBlank()) {
                    showOrHideProof(cachedProofUrl)
                } else {
                    paymentResponse?.getEffectiveConfirmationImageUrl()?.let { cachedProofUrl = it }
                    showOrHideProof(paymentResponse?.getEffectiveConfirmationImageUrl())
                }
            }
            "Cancelled" -> {
                tvStatusHeader.text = "Cancelled"
                tvStatusDescription.text = "This order has been cancelled"
                
                // If this is initial load, just set colors without animation
                if (!isStatusChange) {
                    iconDriverAssigned.setColorFilter(greyColor)
                    progressLine1.setBackgroundColor(greyColor)
                    iconPickup.setColorFilter(greyColor)
                    progressLine2.setBackgroundColor(greyColor)
                    iconCompleted.setColorFilter(greyColor)
                    progressLine3.setBackgroundColor(greyColor)
                } else {
                    // All icons and lines revert to grey
                    animateIconColorFilter(iconDriverAssigned, greenColor, greyColor)
                    animateIconColorFilter(iconPickup, greenColor, greyColor)
                    animateIconColorFilter(iconCompleted, greenColor, greyColor)
                    
                    animateColorChange(progressLine1, greenColor, greyColor)
                    animateColorChange(progressLine2, greenColor, greyColor)
                    animateColorChange(progressLine3, greenColor, greyColor)
                }
                btnCancel.visibility = View.GONE
                btnUploadProof.visibility = View.GONE
                showOrHideProof(null)
            }
            else -> {
                // Handle unknown status
                tvStatusHeader.text = "Unknown Status: $jobOrderStatus"
                tvStatusDescription.text = "An unknown status was received."
            }
        }
    }
    
    private fun formatEstimatedTime(date: Date): String {
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        return timeFormat.format(date)
    }

    private fun cancelOrder() {
        // Get the JWT token from the SessionManager
        val jwtToken = sessionManager.getToken()
        
        if (jwtToken.isNullOrBlank()) {
            Log.e("OrderStatusActivity", "No JWT token available to cancel order")
            return
        }
        
        val bearerToken = if (jwtToken.startsWith("Bearer ")) jwtToken else "Bearer $jwtToken"
        
        // Show loading state
        btnCancel.isEnabled = false
        btnCancel.text = "Cancelling..."
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // First get the payment ID for this order
                val response = apiService.getPaymentByOrderId(order.id, bearerToken)
                
                if (response.isSuccessful && response.body() != null) {
                    val paymentResponse = response.body()!!
                    val paymentId = paymentResponse.id
                    
                    // Now update the job order status to "Cancelled" using customer API
                    val statusUpdate = JobOrderStatusUpdate("Cancelled")
                    val updateResponse = apiService.updatePaymentJobOrderStatus(paymentId, statusUpdate, bearerToken)
                    
                    withContext(Dispatchers.Main) {
                        if (updateResponse.isSuccessful) {
                            // Update UI to show cancelled state
                            updateOrderStatus("Cancelled")
                            btnCancel.visibility = View.GONE
                            
                            // Stop polling when cancelled
                            stopStatusPolling()
                        } else {
                            Log.e("OrderStatusActivity", "Failed to cancel order: ${updateResponse.code()} - ${updateResponse.message()}")
                            // Show error and reset button
                            btnCancel.isEnabled = true
                            btnCancel.text = "CANCEL ORDER"
                        }
                    }
                } else {
                    Log.e("OrderStatusActivity", "Failed to get payment details: ${response.code()} - ${response.message()}")
                    withContext(Dispatchers.Main) {
                        // Show error and reset button
                        btnCancel.isEnabled = true
                        btnCancel.text = "CANCEL ORDER"
                    }
                }
            } catch (e: Exception) {
                Log.e("OrderStatusActivity", "Error cancelling order", e)
                withContext(Dispatchers.Main) {
                    // Show error and reset button
                    btnCancel.isEnabled = true
                    btnCancel.text = "CANCEL ORDER"
                }
            }
        }
    }

    // Add new method to update order details from API response
    private fun updateOrderDetails(paymentResponse: PaymentResponse) {
        Log.d("OrderStatusActivity", "Updating order details from API: truckId=${paymentResponse.truckId}, wasteType=${paymentResponse.wasteType}")
        
        // Update the internal order object with the latest data from the API
        if (paymentResponse.truckId != null || 
            !paymentResponse.truckMake.isNullOrBlank() || 
            !paymentResponse.truckModel.isNullOrBlank() || 
            !paymentResponse.plateNumber.isNullOrBlank()) {
            
            // Get the current truck or null
            val currentTruck = order.selectedTruck
            
            // Create a new Truck object with the API data
            val updatedTruck = if (currentTruck != null) {
                currentTruck.copy(
                    truckId = paymentResponse.truckId ?: currentTruck.truckId,
                    make = paymentResponse.truckMake ?: currentTruck.make,
                    model = paymentResponse.truckModel ?: currentTruck.model,
                    plateNumber = paymentResponse.plateNumber ?: currentTruck.plateNumber
                )
            } else {
                com.example.ecotrack.models.Truck(
                    truckId = paymentResponse.truckId ?: "truck_${paymentResponse.orderId}",
                    size = paymentResponse.truckSize ?: "MEDIUM",
                    wasteType = paymentResponse.wasteType ?: "MIXED",
                    status = "ACTIVE",
                    make = paymentResponse.truckMake ?: "EcoTrack",
                    model = paymentResponse.truckModel ?: "Standard",
                    plateNumber = paymentResponse.plateNumber ?: "ECO-${paymentResponse.orderId.takeLast(4)}",
                    truckPrice = paymentResponse.amount ?: 0.0,
                    createdAt = paymentResponse.createdAt.toString()
                )
            }
            
            // Update our internal order object
            order = order.copy(selectedTruck = updatedTruck)
            
            Log.d("OrderStatusActivity", "Updated internal order with new truck data: " +
                "ID=${updatedTruck.truckId}, Make=${updatedTruck.make}, " +
                "Model=${updatedTruck.model}, Plate=${updatedTruck.plateNumber}")
        }
        
        // Update plate number if it's provided in the API response
        if (!paymentResponse.plateNumber.isNullOrBlank()) {
            runOnUiThread {
                tvSacks.text = paymentResponse.plateNumber
                Log.d("OrderStatusActivity", "Updated plate number display to: ${paymentResponse.plateNumber}")
            }
        } else {
            // Get the current truck
            val currentTruck = order.selectedTruck
            
            if (currentTruck != null && !currentTruck.plateNumber.isNullOrBlank()) {
                // Use the plate number from our updated order object
                runOnUiThread {
                    tvSacks.text = currentTruck.plateNumber
                    Log.d("OrderStatusActivity", "Updated plate number display to: ${currentTruck.plateNumber}")
                }
            } else if (paymentResponse.numberOfSacks > 0) {
                // If we don't have a plate number but have number of sacks, show that
                runOnUiThread {
                    tvSacks.text = paymentResponse.numberOfSacks.toString()
                    Log.d("OrderStatusActivity", "Updated sacks display to: ${paymentResponse.numberOfSacks}")
                }
            } else {
                // If we don't have either, create a plate number
                val plateNumber = "ECO-${paymentResponse.orderId.takeLast(4)}"
                runOnUiThread {
                    tvSacks.text = plateNumber
                    Log.d("OrderStatusActivity", "Updated plate number display to: $plateNumber")
                }
            }
        }
        
        // Update waste type if it's provided in the API response
        if (!paymentResponse.wasteType.isNullOrBlank()) {
            runOnUiThread {
                tvWasteType.text = paymentResponse.wasteType
                Log.d("OrderStatusActivity", "Updated waste type display to: ${paymentResponse.wasteType}")
            }
        }
        
        // Update truck make & model if they're provided in the API response
        if (!paymentResponse.truckMake.isNullOrBlank() || !paymentResponse.truckModel.isNullOrBlank()) {
            runOnUiThread {
                // Format as "Make & Model" using actual values if available
                val truckDisplay = "${paymentResponse.truckMake ?: ""} ${paymentResponse.truckModel ?: ""}".trim()
                tvTruckSize.text = truckDisplay
                Log.d("OrderStatusActivity", "Updated truck display to: $truckDisplay")
            }
        } else {
            // Get the current truck
            val currentTruck = order.selectedTruck
            
            if (currentTruck != null) {
                // Use the truck from our updated order object
                runOnUiThread {
                    val truckDisplay = "${currentTruck.make} ${currentTruck.model}".trim()
                    tvTruckSize.text = truckDisplay
                    Log.d("OrderStatusActivity", "Updated truck display to: $truckDisplay")
                }
            } else if (!paymentResponse.truckSize.isNullOrBlank()) {
                // If we only have truck size, use that
                runOnUiThread {
                    val truckDisplay = "${paymentResponse.truckSize} Standard"
                    tvTruckSize.text = truckDisplay
                    Log.d("OrderStatusActivity", "Updated truck display to: $truckDisplay")
                }
            }
        }
        
        // Update payment method if it's provided in the API response
        if (!paymentResponse.paymentMethod.isNullOrBlank()) {
            runOnUiThread {
                tvPaymentMethod.text = paymentResponse.paymentMethod
                Log.d("OrderStatusActivity", "Updated payment method display to: ${paymentResponse.paymentMethod}")
            }
        }
    }
}