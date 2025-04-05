package com.example.ecotrack

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.ecotrack.databinding.ActivityRegisterBinding
import com.example.ecotrack.models.RegisterRequest
import com.example.ecotrack.utils.ApiService
import com.example.ecotrack.utils.SessionManager
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RegisterActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRegisterBinding
    private lateinit var sessionManager: SessionManager
    private val apiService = ApiService.create()
    private val TAG = "RegisterActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager.getInstance(this)
        sessionManager.setCurrentActivity(this)

        binding.btnRegister.setOnClickListener {
            val email = binding.etEmail.text.toString()
            val password = binding.etPassword.text.toString()
            val confirmPassword = binding.etConfirmPassword.text.toString()
            val username = binding.etUsername.text.toString()
            val firstName = binding.etFirstName.text.toString()
            val lastName = binding.etLastName.text.toString()

            if (email.isNotEmpty() && password.isNotEmpty() && confirmPassword.isNotEmpty() &&
                username.isNotEmpty() && firstName.isNotEmpty() && lastName.isNotEmpty()) {
                if (password == confirmPassword) {
                    showLoading(true)
                    registerUser(email, password, username, firstName, lastName)
                } else {
                    Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        sessionManager.setCurrentActivity(this)
    }

    override fun onPause() {
        super.onPause()
        sessionManager.setCurrentActivity(null)
    }

    private fun registerUser(
        email: String,
        password: String,
        username: String,
        firstName: String,
        lastName: String
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Sign out from Firebase first to prevent interference
                try {
                    FirebaseAuth.getInstance().signOut()
                } catch (e: Exception) {
                    Log.e(TAG, "Error signing out from Firebase", e)
                    // Continue anyway - we'll use our custom backend
                }
                
                Log.d(TAG, "Attempting to register with email: $email, username: $username")
                
                // Clean up inputs
                val cleanEmail = email.trim()
                val cleanUsername = username.trim()
                val cleanFirstName = firstName.trim()
                val cleanLastName = lastName.trim()
                
                val registerRequest = RegisterRequest(
                    cleanEmail, password, cleanUsername, cleanFirstName, cleanLastName
                )
                
                try {
                    val response = apiService.register(registerRequest)
                    withContext(Dispatchers.Main) {
                        if (response.isSuccessful) {
                            val registerResponse = response.body()
                            registerResponse?.let {
                                Log.d(TAG, "Registration successful. UserId: ${it.userId}")
                                sessionManager.saveUserId(it.userId)
                                // Redirect to login instead of showing dialog
                                Toast.makeText(this@RegisterActivity, "Registration successful! Please log in.", Toast.LENGTH_SHORT).show()
                                startActivity(Intent(this@RegisterActivity, LoginActivity::class.java))
                                finish()
                            }
                        } else {
                            val statusCode = response.code()
                            Log.e(TAG, "Registration failed with code: $statusCode, message: ${response.message()}")
                            
                            try {
                                val errorBody = response.errorBody()?.string()
                                Log.e(TAG, "Error body: $errorBody")
                                
                                val errorMessage = when (statusCode) {
                                    400 -> "Invalid registration data. Please check your information."
                                    409 -> "Email or username already exists. Please use a different one."
                                    500 -> "Server error, please try again later"
                                    else -> "Registration failed: ${errorBody ?: response.message()}"
                                }
                                
                                Toast.makeText(this@RegisterActivity, errorMessage, Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Log.e(TAG, "Error parsing error response", e)
                                Toast.makeText(this@RegisterActivity, "Registration failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Network error during registration", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@RegisterActivity, "Network error: Please check your connection", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during registration", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@RegisterActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    showLoading(false)
                }
            }
        }
    }
    
    private fun showLoading(isLoading: Boolean) {
        binding.progressBar?.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnRegister.isEnabled = !isLoading
    }
}