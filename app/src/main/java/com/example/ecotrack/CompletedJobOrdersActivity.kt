package com.example.ecotrack

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ecotrack.adapters.PaymentOrderAdapter
import com.example.ecotrack.models.payment.Payment
import com.example.ecotrack.utils.ApiService
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

class CompletedJobOrdersActivity : BaseActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var toolbar: Toolbar
    private lateinit var tvNoOrders: TextView
    
    private val apiService = ApiService.create()
    private val TAG = "CompletedJobOrdersActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_completed_job_orders)
        
        // Initialize views
        initViews()
        
        // Load all completed payment orders
        loadCompletedPaymentOrders()
    }
    
    private fun initViews() {
        recyclerView = findViewById(R.id.recyclerViewCompletedJobOrders)
        toolbar = findViewById(R.id.toolbar)
        tvNoOrders = findViewById(R.id.tvNoOrders)
        
        // Set up RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        // Set up toolbar with back button
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.title = "Completed Job Orders"
        
        toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
    }
    
    private fun loadCompletedPaymentOrders() {
        // Get the driver ID from session manager
        val driverId = sessionManager.getUserId()
        
        if (driverId == null) {
            Toast.makeText(this, "Driver ID not found. Please log in again.", Toast.LENGTH_LONG).show()
            navigateToLogin()
            return
        }
        
        // Get the JWT token for authentication
        val token = sessionManager.getToken()
        if (token == null) {
            Toast.makeText(this, "Authentication token not found. Please log in again.", Toast.LENGTH_LONG).show()
            navigateToLogin()
            return
        }
        
        // Show/hide UI elements
        recyclerView.visibility = View.GONE
        tvNoOrders.visibility = View.GONE
        
        // Fetch payment orders assigned to this driver
        lifecycleScope.launch {
            try {
                val response = apiService.getPaymentsByDriverId(
                    driverId = driverId,
                    authToken = "Bearer $token"
                )
                
                if (response.isSuccessful) {
                    val payments = response.body()
                    
                    if (payments != null && payments.isNotEmpty()) {
                        // Filter only completed payments
                        val completedPayments = payments.filter { payment ->
                            // Prioritize jobOrderStatus over regular status field
                            val effectiveStatus = if (!payment.jobOrderStatus.isNullOrBlank()) {
                                payment.jobOrderStatus.trim()
                            } else {
                                payment.status?.trim() ?: ""
                            }
                            
                            // Only include orders that are truly completed
                            effectiveStatus.equals("Completed", ignoreCase = true)
                        }
                        
                        if (completedPayments.isNotEmpty()) {
                            setupCompletedPaymentsAdapter(completedPayments)
                            recyclerView.visibility = View.VISIBLE
                            tvNoOrders.visibility = View.GONE
                        } else {
                            recyclerView.visibility = View.GONE
                            tvNoOrders.visibility = View.VISIBLE
                        }
                    } else {
                        // Show no orders message
                        recyclerView.visibility = View.GONE
                        tvNoOrders.visibility = View.VISIBLE
                    }
                } else {
                    // Handle error response
                    val errorMessage = "Error: ${response.code()} - ${response.message()}"
                    Log.e(TAG, errorMessage)
                    Toast.makeText(this@CompletedJobOrdersActivity, errorMessage, Toast.LENGTH_LONG).show()
                    tvNoOrders.visibility = View.VISIBLE
                }
            } catch (e: IOException) {
                // Handle network error
                Log.e(TAG, "Network error: ${e.message}")
                Toast.makeText(this@CompletedJobOrdersActivity, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
                tvNoOrders.visibility = View.VISIBLE
            } catch (e: HttpException) {
                // Handle HTTP exception
                Log.e(TAG, "HTTP error: ${e.message}")
                Toast.makeText(this@CompletedJobOrdersActivity, "HTTP error: ${e.message}", Toast.LENGTH_LONG).show()
                tvNoOrders.visibility = View.VISIBLE
            } catch (e: Exception) {
                // Handle other exceptions
                Log.e(TAG, "Error: ${e.message}")
                Toast.makeText(this@CompletedJobOrdersActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                tvNoOrders.visibility = View.VISIBLE
            }
        }
    }
    
    private fun setupCompletedPaymentsAdapter(payments: List<Payment>) {
        val adapter = PaymentOrderAdapter(payments, false, "Completed") { payment ->
            // Navigate to the job order details screen
            startActivity(Intent(this, DriverJobOrderStatusActivity::class.java).apply {
                putExtra("PAYMENT", payment)
                putExtra("MODE", DriverJobOrderStatusActivity.JobOrderStatusMode.ACCEPT)
            })
        }
        
        recyclerView.adapter = adapter
    }
} 