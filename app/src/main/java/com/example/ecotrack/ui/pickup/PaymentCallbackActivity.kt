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

        // Log order information
        Log.d(TAG, "Sending payment to backend for order ${order.id} with trash weight: ${order.trashWeight}kg")

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

        // Step 4: Received Xendit payment completion callback
        Log.d(TAG, "Step 4: Received Xendit payment completion callback for order ${order.id}")
        Log.d(TAG, "Payment reference: $paymentReference")

        // Log the payment method being sent to the backend
        Log.d(TAG, "Sending payment to backend with method: ${order.paymentMethod.getDisplayName()}")

        // Perform network calls inside a coroutine
        lifecycleScope.launch {
            try {
                // Get the stored quote data
                val quote = TempOrderHolder.getQuote(order.id)
                if (quote == null) {
                    Log.e(TAG, "Quote data not found for order ${order.id}")
                    throw Exception("Quote data not found for order")
                }
                
                Log.d(TAG, "Creating final payment entry with quote data...")
                Log.d(TAG, "Quote data: quoteId=${quote.quoteId}, amount=₱${quote.estimatedAmount}, total=₱${quote.estimatedTotalAmount}")
                Log.d(TAG, "Truck: ${quote.truckDetails}, Driver: ${quote.driverDetails}")
                
                val paymentRequest = PaymentRequest(
                    orderId = order.id,
                    customerName = order.fullName,
                    customerEmail = order.email,
                    address = order.address,
                    latitude = order.latitude,
                    longitude = order.longitude,
                    amount = quote.estimatedAmount, // Use quote pricing
                    tax = 0.0,
                    totalAmount = quote.estimatedTotalAmount, // Use quote pricing
                    paymentMethod = "ONLINE",
                    paymentReference = paymentReference,
                    notes = order.notes ?: "Online payment via Xendit - completed",
                    wasteType = order.wasteType.name,
                    barangayId = order.barangayId,
                    trashWeight = order.trashWeight,
                    selectedTruckId = null,
                    truckId = quote.assignedTruckId,
                    truckMake = null,
                    truckModel = null,
                    plateNumber = null,
                    quoteId = quote.quoteId
                )
                
                Log.d(TAG, "Full payment request: $paymentRequest")
                Log.d(TAG, "Trash weight being sent: ${paymentRequest.trashWeight}kg, waste type: ${paymentRequest.wasteType}")
                
                // Check if final payment already exists
                try {
                    val existingOrderResponse = apiService.getPaymentByOrderId(order.id, "Bearer $token")
                    if (existingOrderResponse.isSuccessful && existingOrderResponse.body() != null) {
                        val existingOrder = existingOrderResponse.body()!!
                        Log.d(TAG, "Found existing order ${order.id} with payment method: ${existingOrder.paymentMethod}")
                        
                        // If it's an actual payment, not just a quote, order already exists
                        if (existingOrder.paymentMethod != "GET_PRICING_ONLY") {
                            Log.d(TAG, "Final payment already exists, navigating to success directly")
                            
                            // Clean up temporary data since order already exists
                            TempOrderHolder.removeOrder(order.id)
                            
                            val updatedOrder = order.copy(
                                amount = existingOrder.amount ?: order.amount,
                                total = existingOrder.totalAmount ?: order.total
                            )

                            val intent = Intent(this@PaymentCallbackActivity, OrderSuccessActivity::class.java)
                            intent.putExtra("ORDER_DATA", updatedOrder)
                            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                            startActivity(intent)
                            finish()
                            return@launch
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "No existing payment found: ${e.message}")
                    Log.d(TAG, "Continuing with creating final payment entry...")
                }
                
                // Create the payment
                val response = apiService.processPayment(paymentRequest, "Bearer $token")

                if (response.isSuccessful) {
                    Log.i(TAG, "Payment successfully stored in database: ${response.body()}")

                    // Clean up temporary data
                    Log.d(TAG, "Cleaning up temporary data...")
                    TempOrderHolder.removeOrder(order.id)
                    Log.d(TAG, "Temporary data cleaned up successfully")
                    
                    Log.d(TAG, "FLOW COMPLETE: Quote → Xendit → Payment creation with quote data → Cleanup")
                    Log.d(TAG, "SUCCESS: Customer paid quote amount, payment created with full quote details!")

                    // Update order with backend response if needed
                    val paymentResponse = response.body()
                    val finalOrder = if (paymentResponse != null) {
                        Log.d(TAG, "Final order details from database: ${paymentResponse}")
                        // Use the final values from database
                        order.copy(
                            amount = paymentResponse.amount ?: order.amount,
                            total = paymentResponse.totalAmount ?: order.total
                        )
                    } else {
                        order
                    }

                    // Navigate to success screen
                    val intent = Intent(this@PaymentCallbackActivity, OrderSuccessActivity::class.java)
                    intent.putExtra("ORDER_DATA", finalOrder)
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
                    intent.putExtra("ORDER_DATA", order) // Use original order since response failed
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                    finish()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending payment to backend: ${e.message}", e)
                // Even if backend call fails, navigate to success to avoid blocking the user
                TempOrderHolder.removeOrder(order.id)
                val intent = Intent(this@PaymentCallbackActivity, OrderSuccessActivity::class.java)
                intent.putExtra("ORDER_DATA", order)
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