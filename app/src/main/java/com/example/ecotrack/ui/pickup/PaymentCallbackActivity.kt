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
import com.example.ecotrack.R
import com.example.ecotrack.ui.pickup.model.PickupOrder

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
                } else {
                    Log.i(TAG, "Keeping original payment method: ${retrievedOrder!!.paymentMethod.getDisplayName()}")
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
        TempOrderHolder.removeOrder(order.id) 
        val intent = Intent(this, OrderSuccessActivity::class.java)
        intent.putExtra("ORDER_DATA", order)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        finish() // Important: Finish PaymentCallbackActivity so user can't go back to it
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
        val paymentSource = data.getQueryParameter("payment_source") ?: 
                           data.getQueryParameter("source") ?: 
                           data.getQueryParameter("payment_channel") ?:
                           data.getQueryParameter("payment_method")
        
        Log.d(TAG, "Payment source/method detected: $paymentSource")
        
        return when {
            paymentSource == null -> null // No payment source information
            paymentSource.contains("gcash", ignoreCase = true) -> 
                com.example.ecotrack.ui.pickup.model.PaymentMethod.GCASH
            paymentSource.contains("paymaya", ignoreCase = true) || 
            paymentSource.contains("maya", ignoreCase = true) -> 
                com.example.ecotrack.ui.pickup.model.PaymentMethod.PAYMAYA
            paymentSource.contains("grab", ignoreCase = true) -> 
                com.example.ecotrack.ui.pickup.model.PaymentMethod.GRABPAY
            paymentSource.contains("cc", ignoreCase = true) || 
            paymentSource.contains("credit", ignoreCase = true) || 
            paymentSource.contains("card", ignoreCase = true) -> 
                com.example.ecotrack.ui.pickup.model.PaymentMethod.CREDIT_CARD
            paymentSource.contains("bank", ignoreCase = true) || 
            paymentSource.contains("transfer", ignoreCase = true) -> 
                com.example.ecotrack.ui.pickup.model.PaymentMethod.BANK_TRANSFER
            paymentSource.contains("otc", ignoreCase = true) || 
            paymentSource.contains("counter", ignoreCase = true) || 
            paymentSource.contains("7eleven", ignoreCase = true) || 
            paymentSource.contains("cebuana", ignoreCase = true) || 
            paymentSource.contains("ecpay", ignoreCase = true) -> 
                com.example.ecotrack.ui.pickup.model.PaymentMethod.OTC
            paymentSource.contains("cash", ignoreCase = true) || 
            paymentSource.contains("cod", ignoreCase = true) -> 
                com.example.ecotrack.ui.pickup.model.PaymentMethod.CASH_ON_HAND
            else -> {
                Log.w(TAG, "Unknown payment source: $paymentSource")
                null // Keep the original payment method if unknown
            }
        }
    }
} 