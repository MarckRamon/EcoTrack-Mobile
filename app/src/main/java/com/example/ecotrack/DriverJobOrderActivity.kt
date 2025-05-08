package com.example.ecotrack

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ecotrack.adapters.JobOrderAdapter
import com.example.ecotrack.models.JobOrder
import com.example.ecotrack.models.OrderStatus

class DriverJobOrderActivity : BaseActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var homeNav: View
    private lateinit var jobOrdersNav: View
    private lateinit var collectionPointsNav: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driver_job_order)
        
        // Initialize views
        initViews()
        
        // Setup navigation
        setupNavigation()
        
        // Load job orders
        loadJobOrders()
    }
    
    private fun initViews() {
        recyclerView = findViewById(R.id.recyclerViewJobOrders)
        homeNav = findViewById(R.id.homeNav)
        jobOrdersNav = findViewById(R.id.jobOrdersNav)
        collectionPointsNav = findViewById(R.id.collectionPointsNav)
        
        // Set up RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(this)
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
    
    private fun loadJobOrders() {
        // In a real app, this would come from an API
        // For now, we'll create static demo data that matches the image
        val jobOrders = listOf(
            JobOrder(
                id = "JO-001",
                customerName = "Miggy Chan",
                address = "7953 Oakland St.",
                city = "Honolulu",
                state = "HI",
                zipCode = "96815",
                price = "550",
                phoneNumber = "+639127463218",
                paymentMethod = "Cash on Hand",
                wasteType = "Plastic",
                status = OrderStatus.PENDING
            ),
            JobOrder(
                id = "JO-002",
                customerName = "Miggy Chan",
                address = "7953 Oakland St.",
                city = "Honolulu",
                state = "HI",
                zipCode = "96815",
                price = "550",
                phoneNumber = "+639127463218",
                paymentMethod = "Cash on Hand",
                wasteType = "Plastic",
                status = OrderStatus.PENDING
            )
        )
        
        // Set up adapter
        val adapter = JobOrderAdapter(jobOrders) { jobOrder ->
            // Navigate to the job order details screen
            startActivity(Intent(this, DriverAcceptJobOrderActivity::class.java).apply {
                // In a real implementation, you would pass the job order ID
                // putExtra("JOB_ORDER_ID", jobOrder.id)
            })
        }
        
        recyclerView.adapter = adapter
    }
} 