package com.example.ecotrack.ui.pickup

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.ecotrack.HomeActivity
import com.example.ecotrack.R
import com.example.ecotrack.ui.pickup.model.PaymentMethod
import com.example.ecotrack.ui.pickup.model.PickupOrder

class OrderReceiptActivity : AppCompatActivity() {

    private lateinit var tvReceiptNumber: TextView
    private lateinit var tvPaymentType: TextView
    private lateinit var tvCustomerName: TextView
    private lateinit var tvAddress: TextView
    private lateinit var tvEmail: TextView
    private lateinit var tvAmount: TextView
    private lateinit var tvTax: TextView
    private lateinit var tvTotal: TextView
    private lateinit var btnBackToHome: Button
    private lateinit var order: PickupOrder
    
    // Bottom navigation
    private lateinit var navHome: LinearLayout
    private lateinit var navSchedule: LinearLayout
    private lateinit var navLocation: LinearLayout
    private lateinit var navPickup: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_order_receipt)

        // Get order data from intent
        order = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("ORDER_DATA", PickupOrder::class.java) ?: throw IllegalStateException("No order data provided")
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("ORDER_DATA") ?: throw IllegalStateException("No order data provided")
        }

        // Initialize views
        tvReceiptNumber = findViewById(R.id.tv_receipt_number)
        tvPaymentType = findViewById(R.id.tv_payment_type)
        tvCustomerName = findViewById(R.id.tv_customer_name)
        tvAddress = findViewById(R.id.tv_address)
        tvEmail = findViewById(R.id.tv_email)
        tvAmount = findViewById(R.id.tv_amount)
        tvTax = findViewById(R.id.tv_tax)
        tvTotal = findViewById(R.id.tv_total)
        btnBackToHome = findViewById(R.id.btn_back_to_home)

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
        tvReceiptNumber.text = order.referenceNumber
        tvPaymentType.text = order.paymentMethod.getDisplayName()
        tvCustomerName.text = order.fullName
        tvAddress.text = order.address
        tvEmail.text = order.email
        tvAmount.text = "₱${order.amount.toInt()}"
        tvTax.text = "₱${order.tax.toInt()}"
        tvTotal.text = "₱${order.total.toInt()}"

        // Set button click listener
        btnBackToHome.setOnClickListener {
            navigateToHome()
        }
    }
    
    private fun navigateToHome() {
        val intent = Intent(this, HomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
    
    private fun navigateToPickup() {
        val intent = Intent(this, OrderPickupActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        startActivity(intent)
    }
} 