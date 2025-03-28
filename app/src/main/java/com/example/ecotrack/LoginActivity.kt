package com.example.ecotrack

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.android.material.button.MaterialButton
import android.util.Log

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private var isUserMode = true

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_login)

            // Initialize Firebase Auth
            auth = FirebaseAuth.getInstance()

            // This Line of code removes the title bar from the screen
            supportActionBar?.hide()

            // Initialize views with error checking
            val emailInput = findViewById<EditText>(R.id.et_email) 
                ?: throw Exception("Email input not found")
            val passwordInput = findViewById<EditText>(R.id.et_password) 
                ?: throw Exception("Password input not found")
            val loginButton = findViewById<Button>(R.id.btn_login) 
                ?: throw Exception("Login button not found")
            val driverToggle = findViewById<TextView>(R.id.driverToggle) 
                ?: throw Exception("Driver toggle not found")
            val signupButton = findViewById<TextView>(R.id.btn_register)
                ?: throw Exception("Signup button not found")

            // Set up click listeners
            driverToggle.setOnClickListener {
                startActivity(Intent(this, DriverLoginActivity::class.java))
                finish()
            }

            // Add signup button click listener
            signupButton.setOnClickListener {
                startActivity(Intent(this, RegisterActivity::class.java))
            }

            loginButton.setOnClickListener {
                val email = emailInput.text.toString()
                val password = passwordInput.text.toString()
                
                if (email.isNotEmpty() && password.isNotEmpty()) {
                    loginUser(email, password)
                } else {
                    Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                }
            }

        } catch (e: Exception) {
            Log.e("LoginActivity", "Error in onCreate: ${e.message}")
            Toast.makeText(this, "Error initializing app: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
            finish()
        }
    }

    private fun loginUser(email: String, password: String) {
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

    override fun onStart() {
        super.onStart()
        val currentUser = auth.currentUser
        if (currentUser != null) {
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
        }
    }
}