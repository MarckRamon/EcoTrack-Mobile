package com.example.ecotrack

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ProfileActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        supportActionBar?.hide()
        setupClickListeners()
        loadUserData()
    }

    private fun setupClickListeners() {
        findViewById<LinearLayout>(R.id.editInfoButton).setOnClickListener {
            startActivity(Intent(this, EditProfileActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.forgotPasswordButton).setOnClickListener {
            // TODO: Handle forgot password
        }

        findViewById<LinearLayout>(R.id.configureNotificationsButton).setOnClickListener {
            // TODO: Handle notifications settings
        }

        findViewById<MaterialButton>(R.id.logoutButton).setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        // Bottom navigation click listeners
        findViewById<LinearLayout>(R.id.homeNav).setOnClickListener {
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
        }

        findViewById<LinearLayout>(R.id.scheduleNav).setOnClickListener {
            // TODO: Navigate to schedule
        }

        findViewById<LinearLayout>(R.id.pointsNav).setOnClickListener {
            // TODO: Navigate to points
        }

        findViewById<LinearLayout>(R.id.pickupNav).setOnClickListener {
            // TODO: Navigate to pickup
        }
    }

    private fun loadUserData() {
        val currentUser = auth.currentUser
        currentUser?.let { user ->
            firestore.collection("users").document(user.uid).get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        // Update UI with user data
                        val firstName = document.getString("firstName") ?: ""
                        val lastName = document.getString("lastName") ?: ""
                        findViewById<TextView>(R.id.userName).text = "$firstName $lastName"
                        findViewById<TextView>(R.id.userEmail).text = document.getString("email")
                    }
                }
        }
    }

    // Override onResume to refresh data when returning from EditProfileActivity
    override fun onResume() {
        super.onResume()
        loadUserData()
    }
} 