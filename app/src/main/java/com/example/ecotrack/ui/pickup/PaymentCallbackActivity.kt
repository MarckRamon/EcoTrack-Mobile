package com.example.ecotrack.ui.pickup

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.ecotrack.R
import com.example.ecotrack.models.payment.PaymentRequest
import com.example.ecotrack.ui.pickup.model.PickupOrder
import com.example.ecotrack.utils.ApiService
import com.example.ecotrack.utils.SessionManager
import kotlinx.coroutines.launch

/**
 * Activity for handling payment callbacks from Xendit
 * This activity is launched when the user is redirected back from the Xendit Checkout API
 */
class PaymentCallbackActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnAction: Button

    private val TAG = "PaymentCallbackActivity"
    private var retrievedOrder: PickupOrder? = null

    // API service for backend communication
    private val apiService by lazy { ApiService.create() }
    
    // Session manager for user data
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //ContentView will only be set if needed (i.e. for failure/unknown status)

        // Initialize session manager
        sessionManager = SessionManager.getInstance(this)

        val data = intent.data
        if (data != null) {
            Log.i(TAG, "Received callback URI: $data")
            val orderId = data.getQueryParameter("order_id")
            val redirectStatus = data.getQueryParameter("redirect_status")
            // Log other Xendit params if needed for debugging, but prioritize redirectStatus
            val xenditExternalId = data.getQueryParameter("external_id")
            val effectiveOrderId = orderId ?: xenditExternalId

            // Log all query parameters for debugging
            data.queryParameterNames.forEach { param ->
                val value = data.getQueryParameter(param)
                Log.d(TAG, "Xendit callback param: $param = $value")
            }

            Log.i(TAG, "Callback params: effective_order_id='$effectiveOrderId', redirect_status='$redirectStatus'")

            if (effectiveOrderId != null) {
                retrievedOrder = TempOrderHolder.getOrder(effectiveOrderId)
                if (retrievedOrder == null) {
                    Log.e(TAG, "Order with ID '$effectiveOrderId' not found in TempOrderHolder.")
                }
            }

            if (redirectStatus == "success" && retrievedOrder != null) {
                // --- DIRECT NAVIGATION ON SUCCESS ---
                Log.i(TAG, "Payment success, redirect_status='success'. Navigating directly to OrderSuccessActivity.")

                // Update the payment method in the order based on the URL parameters
                val updatedPaymentMethod = getUpdatedPaymentMethod(data)
                if (updatedPaymentMethod != null) {
                    Log.i(TAG, "Updating payment method from ${retrievedOrder!!.paymentMethod.getDisplayName()} to ${updatedPaymentMethod.getDisplayName()}")
                    retrievedOrder = retrievedOrder!!.copy(paymentMethod = updatedPaymentMethod)
                    // Store updated order back in case it's accessed elsewhere before processing completes
                    TempOrderHolder.updateOrder(retrievedOrder!!)

                    // Log the updated order to verify it was changed
                    Log.d(TAG, "Order after payment method update: ${retrievedOrder!!}")
                    Log.d(TAG, "Payment method after update: ${retrievedOrder!!.paymentMethod.name}, display name: ${retrievedOrder!!.paymentMethod.getDisplayName()}")
                } else {
                    Log.i(TAG, "Keeping original payment method: ${retrievedOrder!!.paymentMethod.getDisplayName()}")

                    // Try to determine why payment method detection failed
                    Log.d(TAG, "Payment method detection failed. All query parameters:")
                    data.queryParameterNames.forEach { param ->
                        val value = data.getQueryParameter(param)
                        Log.d(TAG, "  $param = $value")
                    }
                }

                navigateToSuccessScreen(retrievedOrder!!)
                return // Finish this activity, skip its UI
            } else {
                // --- SHOW UI FOR FAILURE/CANCELLED/UNKNOWN OR IF ORDER IS MISSING ---
                setContentView(R.layout.activity_payment_callback) // Set layout only now
                tvStatus = findViewById(R.id.tv_status)
                progressBar = findViewById(R.id.progress_bar)
                btnAction = findViewById(R.id.btn_continue)
                progressBar.visibility = View.GONE // No long verification needed for these paths
                btnAction.visibility = View.VISIBLE

                if (retrievedOrder == null && effectiveOrderId != null) {
                    // Critical issue: Order was paid (or tried) but can't be found locally
                    Log.e(TAG, "CRITICAL: Order ID '$effectiveOrderId' was in callback, but order not in TempOrderHolder.")
                    tvStatus.text = "Error: Order details lost. Please contact support with order ID '$effectiveOrderId'."
                    btnAction.text = "GO HOME"
                    btnAction.setOnClickListener { navigateToOrderPickup() } // Or HomeActivity
                } else if (redirectStatus == "failure" || redirectStatus == "cancelled") {
                    tvStatus.text = "Payment Failed or Cancelled."
                    btnAction.text = "TRY AGAIN"
                    btnAction.setOnClickListener { navigateToOrderPickup() }
                    effectiveOrderId?.let { TempOrderHolder.removeOrder(it) } // Clean up failed attempt
                } else {
                    // Unknown status or successful redirect but order was null (should be caught by above)
                    Log.w(TAG, "Payment status unclear or order missing. redirect_status: $redirectStatus, retrievedOrder is null: ${retrievedOrder == null}")
                    tvStatus.text = "Payment status unclear. Please check your orders."
                    btnAction.text = "BACK TO ORDERS"
                    btnAction.setOnClickListener { navigateToOrderPickup() }
                    effectiveOrderId?.let { TempOrderHolder.removeOrder(it) }
                }
            }
        } else {
            // --- SHOW UI FOR INVALID CALLBACK ---
            setContentView(R.layout.activity_payment_callback) // Set layout only now
            tvStatus = findViewById(R.id.tv_status)
            progressBar = findViewById(R.id.progress_bar)
            btnAction = findViewById(R.id.btn_continue)
            progressBar.visibility = View.GONE
            btnAction.visibility = View.VISIBLE

            Log.e(TAG, "No callback data (intent.data is null).")
            tvStatus.text = "Error: Invalid payment callback. No data."
            btnAction.text = "GO HOME"
            btnAction.setOnClickListener { navigateToOrderPickup() } // Or HomeActivity
        }
    }

    private fun navigateToSuccessScreen(order: PickupOrder) {
        // Send payment data to backend before navigating to success screen
        sendPaymentToBackend(order, intent.data)
    }

    /**
     * Send payment data to the backend
     * This method sends the payment information to the backend after a successful payment
     *
     * @param order The pickup order to send to the backend
     * @param data The URI data from the Xendit callback, containing payment details
     */
    private fun sendPaymentToBackend(order: PickupOrder, data: android.net.Uri?) {
        // Show progress indicator if we're still on this screen
        if (::progressBar.isInitialized) {
            progressBar.visibility = View.VISIBLE
            tvStatus.text = "Processing payment..."
        }
        
        val token = sessionManager.getToken()

        if (token == null) {
            Log.e(TAG, "Authentication token is missing. Cannot send payment to backend.")
            // Handle the case where the token is missing, maybe show an error to the user
            if (::tvStatus.isInitialized) {
                progressBar.visibility = View.GONE
                tvStatus.text = "Error: Authentication failed. Please log in again."
                Toast.makeText(this, "Authentication failed. Please log in again.", Toast.LENGTH_LONG).show()
                btnAction.visibility = View.VISIBLE
                btnAction.text = "GO HOME"
                btnAction.setOnClickListener { navigateToOrderPickup() }
            }
            // Do not proceed with API call if token is null
            return
        }

        // Log truck information from the order
        Log.d(TAG, "Sending payment to backend for order ${order.id} with truck: ID=${order.selectedTruck?.truckId}, make=${order.selectedTruck?.make}, model=${order.selectedTruck?.model}")

        // Get payment reference from Xendit callback or generate one
        val paymentReference = if (data != null) {
            // Try to get the Xendit invoice ID or payment ID
            data.getQueryParameter("invoice_id") ?:
            data.getQueryParameter("id") ?:
            data.getQueryParameter("payment_id") ?:
            data.getQueryParameter("xendit_id") ?:
            data.getQueryParameter("xendit_invoice_id") ?:
            data.getQueryParameter("transaction_id") ?:
            "xnd_order_${order.id}" // Fallback
        } else {
            "xnd_order_${order.id}" // Fallback if no data
        }

        // Log the payment method before creating the request
        Log.d(TAG, "Payment method before creating request: ${order.paymentMethod.name}, display name: ${order.paymentMethod.getDisplayName()}")

        // Create payment request
        val paymentRequest = PaymentRequest(
            orderId = order.id,
            customerName = order.fullName,
            customerEmail = order.email,
            address = order.address,
            latitude = order.latitude,
            longitude = order.longitude,
            amount = order.amount,
            tax = order.tax,
            totalAmount = order.total,
            paymentMethod = order.paymentMethod.getDisplayName(), // Use display name instead of enum name
            paymentReference = paymentReference,
            notes = "Payment from mobile app",
            wasteType = order.wasteType.name, // Include the waste type here
            barangayId = order.barangayId, // Include the barangayId here
            selectedTruckId = order.selectedTruck?.truckId, // Use the truck ID instead of number of sacks
            truckId = order.selectedTruck?.truckId, // Add truckId field with the same value as selectedTruckId
            truckMake = order.selectedTruck?.make, // Add truck make
            truckModel = order.selectedTruck?.model, // Add truck model
            plateNumber = order.selectedTruck?.plateNumber // Add plate number
        )

        // Log the payment method being sent to the backend
        Log.d(TAG, "Sending payment to backend with method: ${order.paymentMethod.getDisplayName()}")
        Log.d(TAG, "Full payment request: $paymentRequest")
        Log.d(TAG, "Selected truck ID being sent: ${paymentRequest.selectedTruckId}, truckId: ${paymentRequest.truckId}, make: ${paymentRequest.truckMake}, model: ${paymentRequest.truckModel}, plateNumber: ${paymentRequest.plateNumber}")

        // Send payment data to backend
        lifecycleScope.launch {
            // Create a variable for the order we'll use (either original or updated)
            var orderToUse = order
            
            try {
                val response = apiService.processPayment(paymentRequest, "Bearer $token")

                if (response.isSuccessful) {
                    Log.i(TAG, "Payment successfully sent to backend: ${response.body()}")

                    // Remove order from temp storage
                    TempOrderHolder.removeOrder(order.id)

                    // Check if the response contains updated truck information
                    val paymentResponse = response.body()
                    
                    if (paymentResponse != null) {
                        Log.d(TAG, "Got payment response from backend: ${paymentResponse}")
                        
                        // Check if we have a truckId but missing truck details
                        if (paymentResponse.truckId != null && 
                            (paymentResponse.truckMake.isNullOrBlank() || 
                             paymentResponse.truckModel.isNullOrBlank() || 
                             paymentResponse.plateNumber.isNullOrBlank())) {
                            
                            // Try to fetch complete truck details
                            try {
                                Log.d(TAG, "Fetching detailed truck information for truckId: ${paymentResponse.truckId}")
                                val trucksResponse = apiService.getTrucks("Bearer $token")
                                
                                if (trucksResponse.isSuccessful && trucksResponse.body() != null) {
                                    val trucks = trucksResponse.body()!!
                                    Log.d(TAG, "Fetched ${trucks.size} trucks from API")
                                    
                                    // Find the truck with matching ID (try case-insensitive matching if exact match fails)
                                    var matchingTruck = trucks.find { it.truckId == paymentResponse.truckId }
                                    
                                    // If no exact match, try case-insensitive comparison
                                    if (matchingTruck == null) {
                                        Log.d(TAG, "No exact match found for truck ID: ${paymentResponse.truckId}, trying case-insensitive match")
                                        matchingTruck = trucks.find { it.truckId.equals(paymentResponse.truckId, ignoreCase = true) }
                                        if (matchingTruck != null) {
                                            Log.d(TAG, "Found truck with case-insensitive match: ${matchingTruck.truckId} for requested ID: ${paymentResponse.truckId}")
                                        } else {
                                            Log.d(TAG, "No matching truck found among ${trucks.size} trucks. Available IDs: ${trucks.map { it.truckId }.take(5).joinToString()}")
                                        }
                                    }
                                    
                                    if (matchingTruck != null) {
                                        Log.d(TAG, "Found matching truck: ${matchingTruck.make} ${matchingTruck.model}, plate: ${matchingTruck.plateNumber}")
                                        
                                        // Create an updated truck with complete info
                                        val updatedTruck = com.example.ecotrack.models.Truck(
                                            truckId = matchingTruck.truckId,
                                            size = matchingTruck.size,
                                            wasteType = paymentResponse.wasteType ?: matchingTruck.wasteType,
                                            status = matchingTruck.status,
                                            make = matchingTruck.make,
                                            model = matchingTruck.model,
                                            plateNumber = matchingTruck.plateNumber,
                                            truckPrice = matchingTruck.truckPrice,
                                            createdAt = matchingTruck.createdAt
                                        )
                                        
                                        // Update our order with the complete truck information
                                        orderToUse = order.copy(selectedTruck = updatedTruck)
                                        Log.d(TAG, "Updated order with complete truck information from trucks API: ${updatedTruck}")
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error fetching complete truck details", e)
                                // Continue with original paymentResponse data
                            }
                        }
                        
                        // Check for truck info in payment response (if we didn't already update via trucks API)
                        if (orderToUse == order && 
                            (paymentResponse.truckId != null || 
                            !paymentResponse.truckMake.isNullOrBlank() ||
                            !paymentResponse.truckModel.isNullOrBlank() ||
                            !paymentResponse.plateNumber.isNullOrBlank())) {
                            
                            // Get the current truck
                            val currentTruck = order.selectedTruck
                            
                            // Create updated truck
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
                            
                            // Update our order with the new truck information
                            orderToUse = order.copy(selectedTruck = updatedTruck)
                            Log.d(TAG, "Updated order with truck information from API response: ${updatedTruck}")
                        }
                    }

                    // Navigate to success screen
                    val intent = Intent(this@PaymentCallbackActivity, OrderSuccessActivity::class.java)
                    intent.putExtra("ORDER_DATA", orderToUse)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                    finish() // Important: Finish PaymentCallbackActivity so user can't go back to it
                } else {
                    // Payment was processed by Xendit but failed to save in our backend
                    // We'll still show success to the user but log the error
                    val errorBody = response.errorBody()?.string()
                    Log.e(TAG, "Failed to send payment to backend: ${response.code()} - ${response.message()} - $errorBody")

                    // Remove order from temp storage
                    TempOrderHolder.removeOrder(order.id)

                    // Navigate to success screen anyway since payment was successful with Xendit
                    val intent = Intent(this@PaymentCallbackActivity, OrderSuccessActivity::class.java)
                    intent.putExtra("ORDER_DATA", orderToUse)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                    finish()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending payment to backend", e)

                // Handle error but still navigate to success since Xendit payment was successful
                if (::tvStatus.isInitialized) {
                    progressBar.visibility = View.GONE
                    tvStatus.text = "Payment successful but failed to sync with our system."
                    Toast.makeText(
                        this@PaymentCallbackActivity,
                        "Payment successful but failed to sync with our system.",
                        Toast.LENGTH_LONG
                    ).show()
                }

                // Remove order from temp storage
                TempOrderHolder.removeOrder(order.id)

                // Navigate to success screen anyway since payment was successful with Xendit
                val intent = Intent(this@PaymentCallbackActivity, OrderSuccessActivity::class.java)
                intent.putExtra("ORDER_DATA", orderToUse)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                finish()
            }
        }
    }

    private fun navigateToOrderPickup() {
        val intent = Intent(this, OrderPickupActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        finish()
    }

    /**
     * Determine the actual payment method used based on the URL parameters
     * from the Xendit redirect
     */
    private fun getUpdatedPaymentMethod(data: android.net.Uri): com.example.ecotrack.ui.pickup.model.PaymentMethod? {
        // Log all parameters for debugging
        Log.d(TAG, "All Xendit callback parameters:")
        data.queryParameterNames.forEach { param ->
            val value = data.getQueryParameter(param)
            Log.d(TAG, "  $param = $value")
        }

        // Always return XENDIT_PAYMENT_GATEWAY for payments made through Xendit
        Log.d(TAG, "Setting payment method to XENDIT_PAYMENT_GATEWAY")
        return com.example.ecotrack.ui.pickup.model.PaymentMethod.XENDIT_PAYMENT_GATEWAY
    }
}