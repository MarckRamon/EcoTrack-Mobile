package com.example.ecotrack

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import de.hdodenhof.circleimageview.CircleImageView
import android.widget.TextView
import android.widget.Toast
import android.view.inputmethod.EditorInfo
import androidx.recyclerview.widget.LinearLayoutManager

class HomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_home)

            supportActionBar?.hide()

            // Set up toolbar
            val toolbar = findViewById<Toolbar>(R.id.toolbar)
            setSupportActionBar(toolbar)
            supportActionBar?.setDisplayShowTitleEnabled(false)

            // Initialize views
            val menuIcon = findViewById<ImageButton>(R.id.menu_icon)
            val profileIcon = findViewById<CircleImageView>(R.id.profile_icon)
            val greetingText = findViewById<TextView>(R.id.greeting)
            val trackingNumberInput = findViewById<TextInputEditText>(R.id.tracking_number_input)
            val servicesRecyclerView = findViewById<RecyclerView>(R.id.services_recycler_view)

            // Set up click listeners
            menuIcon.setOnClickListener {
                // Handle menu click
                // TODO: Implement navigation drawer or menu functionality
            }

            profileIcon.setOnClickListener {
                // Handle profile click
                // TODO: Navigate to profile screen
            }

            // Set up greeting text
            greetingText.text = getString(R.string.welcome_message) + " ${getUserName()}"

            // Set up tracking input
            trackingNumberInput.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    handleTrackingSearch(trackingNumberInput.text.toString())
                    true
                } else {
                    false
                }
            }

            // Set up RecyclerView for services
            setupServicesRecyclerView(servicesRecyclerView)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun getUserName(): String {
        // TODO: Get actual user name from preferences or backend
        return "User"
    }

    private fun handleTrackingSearch(trackingNumber: String) {
        if (trackingNumber.isNotEmpty()) {
            // TODO: Implement tracking search functionality
            Toast.makeText(this, "Searching for tracking number: $trackingNumber", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupServicesRecyclerView(recyclerView: RecyclerView) {
        val services = listOf(
            ServiceItem("Courier", "70K+ Couriers", R.drawable.ic_garbage_truck),
            ServiceItem("Express", "Next Day Delivery", R.drawable.ic_garbage_truck),
            ServiceItem("Economy", "3-5 Days Delivery", R.drawable.ic_garbage_truck)
        )
        
        recyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recyclerView.adapter = ServicesAdapter(services)
    }
}

// Data class for service items
data class ServiceItem(
    val title: String,
    val description: String,
    val iconResId: Int
)