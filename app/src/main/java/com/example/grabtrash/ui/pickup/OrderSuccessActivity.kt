package com.example.grabtrash.ui.pickup

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.example.grabtrash.HomeActivity
import com.example.grabtrash.R
import com.example.grabtrash.ui.pickup.model.PickupOrder

class OrderSuccessActivity : AppCompatActivity() {

    private lateinit var btnSeeStatus: Button
    private lateinit var btnSeeReceipt: Button
    private lateinit var btnBackToHome: Button
    private lateinit var order: PickupOrder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_order_success)

        // Get order data from intent
        order = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("ORDER_DATA", PickupOrder::class.java) ?: throw IllegalStateException("No order data provided")
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("ORDER_DATA") ?: throw IllegalStateException("No order data provided")
        }

        // Initialize views
        btnSeeStatus = findViewById(R.id.btn_see_status)
        btnSeeReceipt = findViewById(R.id.btn_see_receipt)
        btnBackToHome = findViewById(R.id.btn_back_to_home)

        // Set button click listeners
        btnSeeStatus.setOnClickListener {
            val intent = Intent(this, OrderStatusActivity::class.java)
            intent.putExtra("ORDER_DATA", order)
            intent.putExtra("FORCE_REFRESH", true)
            startActivity(intent)
        }

        btnSeeReceipt.setOnClickListener {
            val intent = Intent(this, OrderReceiptActivity::class.java)
            intent.putExtra("ORDER_DATA", order)
            startActivity(intent)
        }

        btnBackToHome.setOnClickListener {
            // Navigate to home screen, clear backstack
            val intent = Intent(this, HomeActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
} 