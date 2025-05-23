package com.example.ecotrack

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ecotrack.adapters.PaymentOrderAdapter
import com.example.ecotrack.models.payment.Payment
import com.example.ecotrack.utils.ApiService
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

class DriverJobOrderActivity : BaseActivity() {

    private lateinit var availableRecyclerView: RecyclerView
    private lateinit var completedRecyclerView: RecyclerView
    private lateinit var homeNav: View
    private lateinit var jobOrdersNav: View
    private lateinit var collectionPointsNav: View
    private lateinit var tvNoAvailableOrders: TextView
    private lateinit var tvNoCompletedOrders: TextView
    private lateinit var tvAvailableHeader: TextView
    private lateinit var tvCompletedHeader: TextView
    private lateinit var endCollectionButton: Button
    
    private val apiService = ApiService.create()
    private val TAG = "DriverJobOrderActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driver_job_order)
        
        // Initialize views
        initViews()
        
        // Setup navigation
        setupNavigation()
        
        // Load payment orders for the current driver
        loadPaymentOrders()
    }
    
    private fun initViews() {
        availableRecyclerView = findViewById(R.id.recyclerViewAvailableJobOrders)
        completedRecyclerView = findViewById(R.id.recyclerViewCompletedJobOrders)
        homeNav = findViewById(R.id.homeNav)
        jobOrdersNav = findViewById(R.id.jobOrdersNav)
        collectionPointsNav = findViewById(R.id.collectionPointsNav)
        tvNoAvailableOrders = findViewById(R.id.tvNoAvailableOrders)
        tvNoCompletedOrders = findViewById(R.id.tvNoCompletedOrders)
        tvAvailableHeader = findViewById(R.id.tvAvailableHeader)
        tvCompletedHeader = findViewById(R.id.tvCompletedHeader)
        endCollectionButton = findViewById(R.id.endCollectionButton)
        
        // Set up RecyclerViews
        availableRecyclerView.layoutManager = LinearLayoutManager(this)
        completedRecyclerView.layoutManager = LinearLayoutManager(this)
        
        // Set up End Collection button
        endCollectionButton.setOnClickListener {
            Toast.makeText(this, "End Collection clicked", Toast.LENGTH_SHORT).show()
            // This is static for now as requested
        }
    }
    
    private fun setupNavigation() {
        homeNav.setOnClickListener {
            startActivity(Intent(this, DriverHomeActivity::class.java))
            finish()
        }
        
        // Already on job orders screen
        
        collectionPointsNav.setOnClickListener {
            startActivity(Intent(this, DriverMapActivity::class.java))
            finish()
        }
    }
    
    private fun loadPaymentOrders() {
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
        availableRecyclerView.visibility = View.GONE
        completedRecyclerView.visibility = View.GONE
        tvNoAvailableOrders.visibility = View.GONE
        tvNoCompletedOrders.visibility = View.GONE
        
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
                        // Split payments into available and completed
                        val availablePayments = payments.filter { it.jobOrderStatus == "Available" ||  it.jobOrderStatus == "In-Progress" ||  it.jobOrderStatus == "Accepted"}
                        val completedPayments = payments.filter { it.jobOrderStatus == "Completed" }
                        
                        // Handle available payments
                        if (availablePayments.isNotEmpty()) {
                            setupAvailablePaymentsAdapter(availablePayments)
                            availableRecyclerView.visibility = View.VISIBLE
                            tvNoAvailableOrders.visibility = View.GONE
                            tvAvailableHeader.visibility = View.VISIBLE
                        } else {
                            availableRecyclerView.visibility = View.GONE
                            tvNoAvailableOrders.visibility = View.VISIBLE
                            tvAvailableHeader.visibility = View.VISIBLE
                        }
                        
                        // Handle completed payments
                        if (completedPayments.isNotEmpty()) {
                            setupCompletedPaymentsAdapter(completedPayments)
                            completedRecyclerView.visibility = View.VISIBLE
                            tvNoCompletedOrders.visibility = View.GONE
                            tvCompletedHeader.visibility = View.VISIBLE
                        } else {
                            completedRecyclerView.visibility = View.GONE
                            tvNoCompletedOrders.visibility = View.VISIBLE
                            tvCompletedHeader.visibility = View.VISIBLE
                        }
                    } else {
                        // Show no orders message for both sections
                        availableRecyclerView.visibility = View.GONE
                        completedRecyclerView.visibility = View.GONE
                        tvNoAvailableOrders.visibility = View.VISIBLE
                        tvNoCompletedOrders.visibility = View.VISIBLE
                        tvAvailableHeader.visibility = View.VISIBLE
                        tvCompletedHeader.visibility = View.VISIBLE
                    }
                } else {
                    // Handle error response
                    val errorMessage = "Error: ${response.code()} - ${response.message()}"
                    Log.e(TAG, errorMessage)
                    Toast.makeText(this@DriverJobOrderActivity, errorMessage, Toast.LENGTH_LONG).show()
                    tvNoAvailableOrders.visibility = View.VISIBLE
                    tvNoCompletedOrders.visibility = View.VISIBLE
                }
            } catch (e: IOException) {
                // Handle network error
                Log.e(TAG, "Network error: ${e.message}")
                Toast.makeText(this@DriverJobOrderActivity, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
                tvNoAvailableOrders.visibility = View.VISIBLE
                tvNoCompletedOrders.visibility = View.VISIBLE
            } catch (e: HttpException) {
                // Handle HTTP exception
                Log.e(TAG, "HTTP error: ${e.message}")
                Toast.makeText(this@DriverJobOrderActivity, "HTTP error: ${e.message}", Toast.LENGTH_LONG).show()
                tvNoAvailableOrders.visibility = View.VISIBLE
                tvNoCompletedOrders.visibility = View.VISIBLE
            } catch (e: Exception) {
                // Handle other exceptions
                Log.e(TAG, "Error: ${e.message}")
                Toast.makeText(this@DriverJobOrderActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                tvNoAvailableOrders.visibility = View.VISIBLE
                tvNoCompletedOrders.visibility = View.VISIBLE
            }
        }
    }
    
    private fun setupAvailablePaymentsAdapter(payments: List<Payment>) {
        val adapter = PaymentOrderAdapter(payments, true) { payment ->
            // Navigate to the appropriate screen based on job order status
            when (payment.jobOrderStatus) {
                "In-Progress" -> {
                    // If the job is in progress, go directly to the collection completed screen
                    val intent = Intent(this, DriverJobOrderStatusActivity::class.java)
                    intent.putExtra("PAYMENT", payment)
                    intent.putExtra("MODE", DriverJobOrderStatusActivity.JobOrderStatusMode.COMPLETE)
                    startActivity(intent)
                }
                "Accepted" -> {
                    // If the job is accepted, go directly to the arrived at location screen
                    val intent = Intent(this, DriverArrivedAtTheLocationActivity::class.java)
                    intent.putExtra("PAYMENT", payment)
                    startActivity(intent)
                }
                else -> {
                    // For available or other statuses, go to the accept job order screen
                    val intent = Intent(this, DriverJobOrderStatusActivity::class.java)
                    intent.putExtra("PAYMENT", payment)
                    intent.putExtra("MODE", DriverJobOrderStatusActivity.JobOrderStatusMode.ACCEPT)
                    startActivity(intent)
                }
            }
        }
        
        availableRecyclerView.adapter = adapter
    }
    
    private fun setupCompletedPaymentsAdapter(payments: List<Payment>) {
        val adapter = PaymentOrderAdapter(payments, false) { payment ->
            // Navigate to the job order details screen
            startActivity(Intent(this, DriverJobOrderStatusActivity::class.java).apply {
                putExtra("PAYMENT", payment)
                putExtra("MODE", DriverJobOrderStatusActivity.JobOrderStatusMode.ACCEPT)
            })
        }
        
        completedRecyclerView.adapter = adapter
    }
} 