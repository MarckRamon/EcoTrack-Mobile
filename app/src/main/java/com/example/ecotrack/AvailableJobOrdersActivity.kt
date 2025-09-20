package com.example.ecotrack

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
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

class AvailableJobOrdersActivity : BaseActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var toolbar: Toolbar
    private lateinit var tvNoOrders: TextView
    private lateinit var tvActiveJobMessage: TextView
    
    private val apiService = ApiService.create()
    private val TAG = "AvailableJobOrdersActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_available_job_orders)
        
        // Initialize views
        initViews()
        
        // Load all available payment orders
        loadAvailablePaymentOrders()
    }
    
    private fun initViews() {
        recyclerView = findViewById(R.id.recyclerViewAvailableJobOrders)
        toolbar = findViewById(R.id.toolbar)
        tvNoOrders = findViewById(R.id.tvNoOrders)
        tvActiveJobMessage = findViewById(R.id.tvActiveJobMessage)
        
        // Set up RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        // Set up toolbar with back button
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.title = "Available Job Orders"
        
        toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
    }
    
    private fun loadAvailablePaymentOrders() {
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
        tvActiveJobMessage.visibility = View.GONE
        
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
                        // Check if there's an active job
                        val activeJobs = payments.filter { 
                            it.jobOrderStatus == "In-Progress" || it.jobOrderStatus == "Accepted" 
                        }
                        val hasActiveJob = activeJobs.isNotEmpty()
                        
                        // Filter only available payments
                        val availablePayments = payments.filter { payment ->
                            // Prioritize jobOrderStatus over regular status field
                            val effectiveStatus = if (!payment.jobOrderStatus.isNullOrBlank()) {
                                payment.jobOrderStatus.trim()
                            } else {
                                payment.status?.trim() ?: ""
                            }
                            
                            // Only include orders that are truly available
                            effectiveStatus.equals("Available", ignoreCase = true)
                        }
                        
                        if (availablePayments.isNotEmpty()) {
                            // Set up the adapter with the hasActiveJob flag to disable items
                            setupAvailablePaymentsAdapter(availablePayments, hasActiveJob)
                            recyclerView.visibility = View.VISIBLE
                            tvNoOrders.visibility = View.GONE
                            
                            // Hide the message - we'll use toast instead
                            tvActiveJobMessage.visibility = View.GONE
                        } else {
                            recyclerView.visibility = View.GONE
                            tvNoOrders.visibility = View.VISIBLE
                            tvActiveJobMessage.visibility = View.GONE
                        }
                    } else {
                        // Show no orders message
                        recyclerView.visibility = View.GONE
                        tvNoOrders.visibility = View.VISIBLE
                        tvActiveJobMessage.visibility = View.GONE
                    }
                } else {
                    // Handle error response
                    val errorMessage = "Error: ${response.code()} - ${response.message()}"
                    Log.e(TAG, errorMessage)
                    Toast.makeText(this@AvailableJobOrdersActivity, errorMessage, Toast.LENGTH_LONG).show()
                    tvNoOrders.visibility = View.VISIBLE
                    tvActiveJobMessage.visibility = View.GONE
                }
            } catch (e: IOException) {
                // Handle network error
                Log.e(TAG, "Network error: ${e.message}")
                Toast.makeText(this@AvailableJobOrdersActivity, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
                tvNoOrders.visibility = View.VISIBLE
                tvActiveJobMessage.visibility = View.GONE
            } catch (e: HttpException) {
                // Handle HTTP exception
                Log.e(TAG, "HTTP error: ${e.message}")
                Toast.makeText(this@AvailableJobOrdersActivity, "HTTP error: ${e.message}", Toast.LENGTH_LONG).show()
                tvNoOrders.visibility = View.VISIBLE
                tvActiveJobMessage.visibility = View.GONE
            } catch (e: Exception) {
                // Handle other exceptions
                Log.e(TAG, "Error: ${e.message}")
                Toast.makeText(this@AvailableJobOrdersActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                tvNoOrders.visibility = View.VISIBLE
                tvActiveJobMessage.visibility = View.GONE
            }
        }
    }
    
    private fun setupAvailablePaymentsAdapter(payments: List<Payment>, hasActiveJob: Boolean) {
        val adapter = PaymentOrderAdapter(payments, true, "Available") { payment ->
            // Always navigate to the job order details screen
            val intent = Intent(this, DriverJobOrderStatusActivity::class.java)
            intent.putExtra("PAYMENT", payment)
            intent.putExtra("MODE", DriverJobOrderStatusActivity.JobOrderStatusMode.ACCEPT)
            startActivity(intent)
        }
        
        recyclerView.adapter = adapter
    }
} 