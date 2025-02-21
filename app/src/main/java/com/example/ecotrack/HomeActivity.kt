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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import android.content.Intent
import android.app.AlertDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import android.util.Log


class HomeActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var greetingText: TextView

    public override fun onStart() {
        super.onStart()
        // Check if user is signed in (non-null) and update UI accordingly.
        val currentUser = auth.currentUser
        if (currentUser != null) {
            // User is signed in, update UI
            updateUI(currentUser)
        } else {
            // User is signed out, handle accordingly
        }
    }

    private fun updateUI(user: FirebaseUser?) {
        // TODO: Implement UI update based on user state
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_home)

            auth = FirebaseAuth.getInstance()
            firestore = FirebaseFirestore.getInstance()

            supportActionBar?.hide()

            // Set up toolbar
            val toolbar = findViewById<Toolbar>(R.id.toolbar)
            setSupportActionBar(toolbar)
            supportActionBar?.setDisplayShowTitleEnabled(false)

            // Initialize views
            val menuIcon = findViewById<ImageButton>(R.id.menu_icon)
            val profileIcon = findViewById<CircleImageView>(R.id.profile_icon)
            greetingText = findViewById<TextView>(R.id.greeting)
            val trackingNumberInput = findViewById<TextInputEditText>(R.id.tracking_number_input)
            val servicesRecyclerView = findViewById<RecyclerView>(R.id.services_recycler_view)

            // Set up click listeners
            menuIcon.setOnClickListener {
                // Handle menu click
                // TODO: Implement navigation drawer or menu functionality
            }

            profileIcon.setOnClickListener {
                // You can implement profile functionality here instead
            }

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

            // Setup logout button
            findViewById<FloatingActionButton>(R.id.btn_logout).setOnClickListener {
                showLogoutConfirmationDialog()
            }

            // Fetch and display username
            fetchAndDisplayUsername()

        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun fetchAndDisplayUsername() {
        val userId = auth.currentUser?.uid
        if (userId != null) {
            firestore.collection("users")
                .document(userId)
                .get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val username = document.getString("username") ?: "User"
                        greetingText.text = "Hello, $username"
                    } else {
                        greetingText.text = "Hello, User"
                    }
                }
                .addOnFailureListener { e ->
                    greetingText.text = "Hello, User"
                    Log.e("HomeActivity", "Error fetching username", e)
                }
        } else {
            greetingText.text = "Hello, User"
        }
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

    private fun showLogoutConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Yes") { dialog, _ ->
                signOut()
                dialog.dismiss()
            }
            .setNegativeButton("No") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun signOut() {
        try {
            // Sign out from Firebase
            Firebase.auth.signOut()
            
            // Show success message
            Toast.makeText(this, "Successfully logged out", Toast.LENGTH_SHORT).show()
            
            // Navigate back to LoginActivity and clear the back stack
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            Toast.makeText(this, "Logout failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

// Data class for service items
data class ServiceItem(
    val title: String,
    val description: String,
    val iconResId: Int
)