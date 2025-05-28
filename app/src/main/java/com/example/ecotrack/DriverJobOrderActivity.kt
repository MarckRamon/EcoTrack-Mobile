package com.example.ecotrack

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ecotrack.adapters.PaymentOrderAdapter
import com.example.ecotrack.models.payment.Payment
import com.example.ecotrack.utils.ApiService
import de.hdodenhof.circleimageview.CircleImageView
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

class DriverJobOrderActivity : BaseActivity() {

    private lateinit var inProgressRecyclerView: RecyclerView
    private lateinit var availableRecyclerView: RecyclerView
    private lateinit var completedRecyclerView: RecyclerView
    private lateinit var homeNav: View
    private lateinit var jobOrdersNav: View
    private lateinit var collectionPointsNav: View
    private lateinit var tvNoInProgressOrders: TextView
    private lateinit var tvNoAvailableOrders: TextView
    private lateinit var tvNoCompletedOrders: TextView
    private lateinit var tvInProgressHeader: TextView
    private lateinit var tvAvailableHeader: TextView
    private lateinit var tvCompletedHeader: TextView
    private lateinit var btnViewMoreAvailable: TextView
    private lateinit var btnViewHistory: TextView
    private lateinit var tvAvailableDisabledMessage: TextView
    private lateinit var endCollectionButton: Button
    private lateinit var profileImage: CircleImageView
    private lateinit var notificationIcon: ImageView
    
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
        inProgressRecyclerView = findViewById(R.id.recyclerViewInProgressJobOrders)
        availableRecyclerView = findViewById(R.id.recyclerViewAvailableJobOrders)
        completedRecyclerView = findViewById(R.id.recyclerViewCompletedJobOrders)
        homeNav = findViewById(R.id.homeNav)
        jobOrdersNav = findViewById(R.id.jobOrdersNav)
        collectionPointsNav = findViewById(R.id.collectionPointsNav)
        tvNoInProgressOrders = findViewById(R.id.tvNoInProgressOrders)
        tvNoAvailableOrders = findViewById(R.id.tvNoAvailableOrders)
        tvNoCompletedOrders = findViewById(R.id.tvNoCompletedOrders)
        tvInProgressHeader = findViewById(R.id.tvInProgressHeader)
        tvAvailableHeader = findViewById(R.id.tvAvailableHeader)
        tvCompletedHeader = findViewById(R.id.tvCompletedHeader)
        btnViewMoreAvailable = findViewById(R.id.btnViewMoreAvailable)
        btnViewHistory = findViewById(R.id.btnViewHistory)
        tvAvailableDisabledMessage = findViewById(R.id.tvAvailableDisabledMessage)
        endCollectionButton = findViewById(R.id.endCollectionButton)
        profileImage = findViewById(R.id.profileImage)
        notificationIcon = findViewById(R.id.iconLeft)
        
        // Set up RecyclerViews
        inProgressRecyclerView.layoutManager = LinearLayoutManager(this)
        availableRecyclerView.layoutManager = LinearLayoutManager(this)
        completedRecyclerView.layoutManager = LinearLayoutManager(this)
        
        // Set up End Collection button
        endCollectionButton.setOnClickListener {
            Toast.makeText(this, "End Collection clicked", Toast.LENGTH_SHORT).show()
            // This is static for now as requested
        }
        
        // Set up profile image click
        profileImage.setOnClickListener {
            startActivity(Intent(this, DriverProfileActivity::class.java))
        }
        
        // Set up notification icon click
        notificationIcon.setOnClickListener {
            Toast.makeText(this, "Notifications feature coming soon", Toast.LENGTH_SHORT).show()
        }
        
        // Set up view more buttons
        btnViewMoreAvailable.setOnClickListener {
            val intent = Intent(this, AvailableJobOrdersActivity::class.java)
            startActivity(intent)
        }
        
        btnViewHistory.setOnClickListener {
            val intent = Intent(this, CompletedJobOrdersActivity::class.java)
            startActivity(intent)
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
        inProgressRecyclerView.visibility = View.GONE
        availableRecyclerView.visibility = View.GONE
        completedRecyclerView.visibility = View.GONE
        tvNoInProgressOrders.visibility = View.GONE
        tvNoAvailableOrders.visibility = View.GONE
        tvNoCompletedOrders.visibility = View.GONE
        tvAvailableDisabledMessage.visibility = View.GONE
        btnViewMoreAvailable.visibility = View.GONE
        btnViewHistory.visibility = View.GONE
        
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
                        // Split payments into in-progress, available and completed
                        val inProgressPayments = payments.filter { 
                            it.jobOrderStatus == "In-Progress" || it.jobOrderStatus == "Accepted" 
                        }
                        val availablePayments = payments.filter { it.jobOrderStatus == "Available" }
                        val completedPayments = payments.filter { it.jobOrderStatus == "Completed" }
                        
                        // Check if there's an active job (In-Progress or Accepted)
                        val hasActiveJob = inProgressPayments.isNotEmpty()
                        
                        // Handle in-progress payments - show all of them
                        if (inProgressPayments.isNotEmpty()) {
                            // Show all in-progress/accepted payments
                            setupInProgressPaymentsAdapter(inProgressPayments)
                            inProgressRecyclerView.visibility = View.VISIBLE
                            tvNoInProgressOrders.visibility = View.GONE
                            tvInProgressHeader.visibility = View.VISIBLE
                        } else {
                            inProgressRecyclerView.visibility = View.GONE
                            tvNoInProgressOrders.visibility = View.VISIBLE
                            tvInProgressHeader.visibility = View.VISIBLE
                        }
                        
                        // Handle available payments - limit to 4 and disable if there's an active job
                        if (availablePayments.isNotEmpty()) {
                            // Limit to 4 available payments for the main screen
                            val displayAvailablePayments = availablePayments.take(4)
                            
                            // Set up the adapter with the hasActiveJob flag to disable items
                            setupAvailablePaymentsAdapter(displayAvailablePayments, hasActiveJob)
                            
                            // Always show the items
                            availableRecyclerView.visibility = View.VISIBLE
                            tvNoAvailableOrders.visibility = View.GONE
                            
                            // Hide the disabled message
                            tvAvailableDisabledMessage.visibility = View.GONE
                            
                            // Show View More button if there are more than 4 available payments
                            btnViewMoreAvailable.visibility = if (availablePayments.size > 4) View.VISIBLE else View.GONE
                            tvAvailableHeader.visibility = View.VISIBLE
                        } else {
                            availableRecyclerView.visibility = View.GONE
                            tvNoAvailableOrders.visibility = View.VISIBLE
                            tvAvailableDisabledMessage.visibility = View.GONE
                            tvAvailableHeader.visibility = View.VISIBLE
                            btnViewMoreAvailable.visibility = View.GONE
                        }
                        
                        // Handle completed payments - limit to 4
                        if (completedPayments.isNotEmpty()) {
                            // Log the completed payments for debugging
                            completedPayments.forEach { payment ->
                                Log.d(TAG, "Completed payment: ${payment.id}, updatedAt: ${payment.updatedAt}, createdAt: ${payment.createdAt}, status: ${payment.jobOrderStatus}")
                            }
                            
                            // Limit to 4 completed payments for the main screen
                            val displayCompletedPayments = completedPayments.take(4)
                            setupCompletedPaymentsAdapter(displayCompletedPayments)
                            completedRecyclerView.visibility = View.VISIBLE
                            tvNoCompletedOrders.visibility = View.GONE
                            tvCompletedHeader.visibility = View.VISIBLE
                            
                            // Show View History button if there are more than 4 completed payments
                            btnViewHistory.visibility = if (completedPayments.size > 4) View.VISIBLE else View.GONE
                        } else {
                            completedRecyclerView.visibility = View.GONE
                            tvNoCompletedOrders.visibility = View.VISIBLE
                            tvCompletedHeader.visibility = View.VISIBLE
                            btnViewHistory.visibility = View.GONE
                        }
                    } else {
                        // Show no orders message for all sections
                        inProgressRecyclerView.visibility = View.GONE
                        availableRecyclerView.visibility = View.GONE
                        completedRecyclerView.visibility = View.GONE
                        tvNoInProgressOrders.visibility = View.VISIBLE
                        tvNoAvailableOrders.visibility = View.VISIBLE
                        tvNoCompletedOrders.visibility = View.VISIBLE
                        tvAvailableDisabledMessage.visibility = View.GONE
                        tvInProgressHeader.visibility = View.VISIBLE
                        tvAvailableHeader.visibility = View.VISIBLE
                        tvCompletedHeader.visibility = View.VISIBLE
                        btnViewMoreAvailable.visibility = View.GONE
                        btnViewHistory.visibility = View.GONE
                    }
                } else {
                    // Handle error response
                    val errorMessage = "Error: ${response.code()} - ${response.message()}"
                    Log.e(TAG, errorMessage)
                    Toast.makeText(this@DriverJobOrderActivity, errorMessage, Toast.LENGTH_LONG).show()
                    tvNoInProgressOrders.visibility = View.VISIBLE
                    tvNoAvailableOrders.visibility = View.VISIBLE
                    tvNoCompletedOrders.visibility = View.VISIBLE
                }
            } catch (e: IOException) {
                // Handle network error
                Log.e(TAG, "Network error: ${e.message}")
                Toast.makeText(this@DriverJobOrderActivity, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
                tvNoInProgressOrders.visibility = View.VISIBLE
                tvNoAvailableOrders.visibility = View.VISIBLE
                tvNoCompletedOrders.visibility = View.VISIBLE
            } catch (e: HttpException) {
                // Handle HTTP exception
                Log.e(TAG, "HTTP error: ${e.message}")
                Toast.makeText(this@DriverJobOrderActivity, "HTTP error: ${e.message}", Toast.LENGTH_LONG).show()
                tvNoInProgressOrders.visibility = View.VISIBLE
                tvNoAvailableOrders.visibility = View.VISIBLE
                tvNoCompletedOrders.visibility = View.VISIBLE
            } catch (e: Exception) {
                // Handle other exceptions
                Log.e(TAG, "Error: ${e.message}")
                Toast.makeText(this@DriverJobOrderActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                tvNoInProgressOrders.visibility = View.VISIBLE
                tvNoAvailableOrders.visibility = View.VISIBLE
                tvNoCompletedOrders.visibility = View.VISIBLE
            }
        }
    }
    
    private fun setupInProgressPaymentsAdapter(payments: List<Payment>) {
        val adapter = PaymentOrderAdapter(payments, true, "In-Progress") { payment ->
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
            }
        }
        
        inProgressRecyclerView.adapter = adapter
    }
    
    private fun setupAvailablePaymentsAdapter(payments: List<Payment>, hasActiveJob: Boolean) {
        val adapter = PaymentOrderAdapter(payments, true, "Available") { payment ->
            // Always navigate to the job order details screen
            val intent = Intent(this, DriverJobOrderStatusActivity::class.java)
            intent.putExtra("PAYMENT", payment)
            intent.putExtra("MODE", DriverJobOrderStatusActivity.JobOrderStatusMode.ACCEPT)
            startActivity(intent)
        }
        
        availableRecyclerView.adapter = adapter
    }
    
    private fun setupCompletedPaymentsAdapter(payments: List<Payment>) {
        val adapter = PaymentOrderAdapter(payments, false, "Completed") { payment ->
            // Navigate to the job order details screen
            startActivity(Intent(this, DriverJobOrderStatusActivity::class.java).apply {
                putExtra("PAYMENT", payment)
                putExtra("MODE", DriverJobOrderStatusActivity.JobOrderStatusMode.ACCEPT)
            })
        }
        
        completedRecyclerView.adapter = adapter
    }
} 