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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //ContentView will only be set if needed (i.e. for failure/unknown status)

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
            notes = "Payment from mobile app"
        )

        // Log the payment method being sent to the backend
        Log.d(TAG, "Sending payment to backend with method: ${order.paymentMethod.getDisplayName()}")
        Log.d(TAG, "Full payment request: $paymentRequest")

        // Send payment data to backend
        lifecycleScope.launch {
            try {
                val response = apiService.processPayment(paymentRequest)

                if (response.isSuccessful) {
                    Log.i(TAG, "Payment successfully sent to backend: ${response.body()}")

                    // Remove order from temp storage
                    TempOrderHolder.removeOrder(order.id)

                    // Navigate to success screen
                    val intent = Intent(this@PaymentCallbackActivity, OrderSuccessActivity::class.java)
                    intent.putExtra("ORDER_DATA", order)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                    finish() // Important: Finish PaymentCallbackActivity so user can't go back to it
                } else {
                    // Payment was processed by Xendit but failed to save in our backend
                    // We'll still show success to the user but log the error
                    Log.e(TAG, "Failed to send payment to backend: ${response.errorBody()?.string()}")

                    // Remove order from temp storage
                    TempOrderHolder.removeOrder(order.id)

                    // Navigate to success screen anyway since payment was successful with Xendit
                    val intent = Intent(this@PaymentCallbackActivity, OrderSuccessActivity::class.java)
                    intent.putExtra("ORDER_DATA", order)
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

        // Check multiple possible parameter names that Xendit might use
        val paymentSource = data.getQueryParameter("payment_source") ?:
                           data.getQueryParameter("source") ?:
                           data.getQueryParameter("payment_channel") ?:
                           data.getQueryParameter("payment_method") ?:
                           data.getQueryParameter("payment_method_id") ?:
                           data.getQueryParameter("channel_code") ?:
                           data.getQueryParameter("payment_type") ?:
                           data.getQueryParameter("channel") ?:
                           data.getQueryParameter("method")

        // Also check for payment_method_type which might be more specific
        val paymentMethodType = data.getQueryParameter("payment_method_type") ?:
                               data.getQueryParameter("method_type") ?:
                               data.getQueryParameter("type")

        // Check for payment_status which might contain additional info
        val paymentStatus = data.getQueryParameter("payment_status") ?:
                           data.getQueryParameter("status")

        // Check for Xendit's invoice_id which might be useful for reference
        val invoiceId = data.getQueryParameter("invoice_id") ?:
                       data.getQueryParameter("id")

        // Check for additional parameters that might contain payment method info
        val paymentName = data.getQueryParameter("payment_name") ?:
                         data.getQueryParameter("method_name") ?:
                         data.getQueryParameter("name")

        val paymentDescription = data.getQueryParameter("payment_description") ?:
                                data.getQueryParameter("description")

        Log.d(TAG, "Payment details - source: $paymentSource, type: $paymentMethodType, status: $paymentStatus, invoice: $invoiceId")
        Log.d(TAG, "Additional payment details - name: $paymentName, description: $paymentDescription")

        // Combine all payment method indicators for better detection
        val combinedPaymentInfo = listOfNotNull(
            paymentSource,
            paymentMethodType,
            paymentName,
            paymentDescription
        ).joinToString(" ").lowercase()

        Log.d(TAG, "Combined payment info: $combinedPaymentInfo")

        return when {
            combinedPaymentInfo.isEmpty() -> null // No payment source information

            // GCash
            combinedPaymentInfo.contains("gcash") ->
                com.example.ecotrack.ui.pickup.model.PaymentMethod.GCASH

            // PayMaya/Maya
            combinedPaymentInfo.contains("paymaya") ||
            combinedPaymentInfo.contains("maya") ->
                com.example.ecotrack.ui.pickup.model.PaymentMethod.PAYMAYA

            // GrabPay
            combinedPaymentInfo.contains("grab") ->
                com.example.ecotrack.ui.pickup.model.PaymentMethod.GRABPAY

            // Credit/Debit Card
            combinedPaymentInfo.contains("cc") ||
            combinedPaymentInfo.contains("credit") ||
            combinedPaymentInfo.contains("card") ||
            combinedPaymentInfo.contains("visa") ||
            combinedPaymentInfo.contains("mastercard") ||
            combinedPaymentInfo.contains("amex") ||
            combinedPaymentInfo.contains("jcb") ->
                com.example.ecotrack.ui.pickup.model.PaymentMethod.CREDIT_CARD

            // Bank Transfer
            combinedPaymentInfo.contains("bank") ||
            combinedPaymentInfo.contains("transfer") ||
            combinedPaymentInfo.contains("bpi") ||
            combinedPaymentInfo.contains("bdo") ||
            combinedPaymentInfo.contains("unionbank") ||
            combinedPaymentInfo.contains("instapay") ||
            combinedPaymentInfo.contains("pesonet") ->
                com.example.ecotrack.ui.pickup.model.PaymentMethod.BANK_TRANSFER

            // Over the Counter
            combinedPaymentInfo.contains("otc") ||
            combinedPaymentInfo.contains("counter") ||
            combinedPaymentInfo.contains("7eleven") ||
            combinedPaymentInfo.contains("7-eleven") ||
            combinedPaymentInfo.contains("cebuana") ||
            combinedPaymentInfo.contains("ecpay") ||
            combinedPaymentInfo.contains("palawan") ||
            combinedPaymentInfo.contains("mlhuillier") ||
            combinedPaymentInfo.contains("shopee") ||  // ShopeePay - map to OTC
            combinedPaymentInfo.contains("coin") ||    // Coins.ph - map to OTC
            combinedPaymentInfo.contains("qr") ||      // QR Ph - map to OTC
            combinedPaymentInfo.contains("online") ->  // Online Banking - map to OTC
                com.example.ecotrack.ui.pickup.model.PaymentMethod.OTC

            // Cash
            combinedPaymentInfo.contains("cash") ||
            combinedPaymentInfo.contains("cod") ->
                com.example.ecotrack.ui.pickup.model.PaymentMethod.CASH_ON_HAND

            else -> {
                Log.w(TAG, "Unknown payment source: $combinedPaymentInfo")
                null // Keep the original payment method if unknown
            }
        }
    }
}