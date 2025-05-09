package com.example.ecotrack.ui.pickup

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.ecotrack.HomeActivity
import com.example.ecotrack.R
import com.example.ecotrack.ui.pickup.model.OrderStatus
import com.example.ecotrack.ui.pickup.model.PaymentMethod
import com.example.ecotrack.ui.pickup.model.PickupOrder
import java.util.*

class OrderStatusActivity : AppCompatActivity() {

    private lateinit var tvStatusHeader: TextView
    private lateinit var tvStatusDescription: TextView
    private lateinit var tvAmount: TextView
    private lateinit var tvPaymentMethod: TextView
    private lateinit var tvLocation: TextView
    private lateinit var btnCancel: TextView
    private lateinit var backButton: ImageView
    private lateinit var order: PickupOrder

    // Bottom navigation
    private lateinit var navHome: LinearLayout
    private lateinit var navSchedule: LinearLayout
    private lateinit var navLocation: LinearLayout
    private lateinit var navPickup: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_order_status)

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
        tvAmount = findViewById(R.id.tv_amount)
        tvPaymentMethod = findViewById(R.id.tv_payment_method)
        tvLocation = findViewById(R.id.tv_location)
        btnCancel = findViewById(R.id.btn_cancel)

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
        tvAmount.text = "₱${order.total.toInt()}"
        tvPaymentMethod.text = order.paymentMethod.getDisplayName()
        tvLocation.text = order.address

        // Log payment method for debugging
        Log.d("OrderStatusActivity", "Payment method: ${order.paymentMethod.name}, display name: ${order.paymentMethod.getDisplayName()}")

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

            // Show toast
            Toast.makeText(this, "Payment method changed to: ${order.paymentMethod.getDisplayName()}", Toast.LENGTH_SHORT).show()

            true
        }

        // For demo purposes, simulate that the order has been accepted after 5 seconds
        updateOrderStatus(order.status)

        // If order is in PROCESSING status, show the ability to cancel
        if (order.status == OrderStatus.PROCESSING) {
            btnCancel.visibility = View.VISIBLE
            btnCancel.setOnClickListener {
                // Update the order status to cancelled
                // In a real app, we would call the API here
                order = order.copy(status = OrderStatus.CANCELLED)
                updateOrderStatus(OrderStatus.CANCELLED)
                btnCancel.visibility = View.GONE
            }

            // Simulate driver accepting the order after 5 seconds
            btnCancel.postDelayed({
                if (!isFinishing) { // Check if activity is still active
                    // Update with an estimated arrival time (now + 30 minutes)
                    val calendar = Calendar.getInstance()
                    calendar.add(Calendar.MINUTE, 30)
                    order = order.copy(
                        status = OrderStatus.ACCEPTED,
                        estimatedArrival = calendar.time
                    )
                    updateOrderStatus(OrderStatus.ACCEPTED)
                    btnCancel.visibility = View.GONE
                }
            }, 5000)
        } else {
            btnCancel.visibility = View.GONE
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

    private fun updateOrderStatus(status: OrderStatus) {
        when (status) {
            OrderStatus.PROCESSING -> {
                tvStatusHeader.text = "Processing"
                tvStatusDescription.text = "Waiting for driver to accept order"
            }
            OrderStatus.ACCEPTED -> {
                tvStatusHeader.text = "Arriving by ${order.getFormattedArrivalTime()}"
                tvStatusDescription.text = "We've got your pickup order!"
            }
            OrderStatus.COMPLETED -> {
                tvStatusHeader.text = "Completed"
                tvStatusDescription.text = "Your trash has been picked up"
            }
            OrderStatus.CANCELLED -> {
                tvStatusHeader.text = "Cancelled"
                tvStatusDescription.text = "This order has been cancelled"
            }
        }
    }
}