package com.example.ecotrack

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import android.util.Log
import com.google.android.material.button.MaterialButton

class DriverLoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private var isUserMode = false
    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var loginButton: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            setContentView(R.layout.activity_driver_login)

            // Initialize Firebase Auth
            auth = FirebaseAuth.getInstance()  // Changed to getInstance()

            // Initialize views
            initializeViews()
            setupClickListeners()

        } catch (e: Exception) {
            Log.e("DriverLoginActivity", "Error in onCreate: ${e.message}")
            e.printStackTrace()
            Toast.makeText(this, "Error initializing app: ${e.message}", Toast.LENGTH_LONG).show()
            finish() // Close the activity if initialization fails
        }
    }

    private fun initializeViews() {
        try {
            emailInput = findViewById(R.id.emailInput)
            passwordInput = findViewById(R.id.passwordInput)
            loginButton = findViewById(R.id.loginButton)
        } catch (e: Exception) {
            Log.e("DriverLoginActivity", "Error in initializeViews: ${e.message}")
            throw Exception("Failed to initialize views: ${e.message}")
        }
    }

    private fun setupClickListeners() {
        try {
            // Change Button to TextView for customer toggle
            findViewById<TextView>(R.id.customerButton).setOnClickListener {
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }

            // Set up login button click listener
            loginButton.setOnClickListener {
                val email = emailInput.text.toString()
                val password = passwordInput.text.toString()
                
                if (email.isNotEmpty() && password.isNotEmpty()) {
                    loginDriver(email, password)
                } else {
                    Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                }
            }

            // Change Button to TextView for driver button
            findViewById<TextView>(R.id.driverButton).isEnabled = false
        } catch (e: Exception) {
            throw Exception("Failed to setup click listeners: ${e.message}")
        }
    }

    private fun loginDriver(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    startActivity(Intent(this, HomeActivity::class.java))
                    finish()
                } else {
                    Toast.makeText(this, "Authentication failed: ${task.exception?.message}", 
                        Toast.LENGTH_SHORT).show()
                }
            }
    }

    public override fun onStart() {
        super.onStart()
        try {
            val currentUser = auth.currentUser
            if(currentUser != null){
                startActivity(Intent(this, HomeActivity::class.java))
                finish()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error checking login state: ${e.message}", 
                Toast.LENGTH_LONG).show()
        }
    }
}