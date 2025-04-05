package com.example.ecotrack

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.ecotrack.databinding.ActivityRegisterBinding
import com.example.ecotrack.models.RegistrationRequest
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
            val firstName = binding.etFirstName.text.toString().trim()
            val lastName = binding.etLastName.text.toString().trim()
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString()
            val confirmPassword = binding.etConfirmPassword.text.toString()

            if (validateInputs(firstName, lastName, email, password, confirmPassword)) {
                showLoading(true)
                registerUser(firstName, lastName, email, password)
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

    private fun validateInputs(
        firstName: String,
        lastName: String,
        email: String,
        password: String,
        confirmPassword: String
    ): Boolean {
        if (firstName.isEmpty() || lastName.isEmpty() || email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            return false
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show()
            return false
        }

        if (password.length < 6) {
            Toast.makeText(this, "Password should be at least 6 characters", Toast.LENGTH_SHORT).show()
            return false
        }

        if (password != confirmPassword) {
            Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    private fun registerUser(firstName: String, lastName: String, email: String, password: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Sign out from Firebase first to prevent interference
                try {
                    FirebaseAuth.getInstance().signOut()
                } catch (e: Exception) {
                    Log.e(TAG, "Error signing out from Firebase", e)
                    // Continue anyway - we'll use our custom backend
                }
                
                // Create registration request
                val registrationRequest = RegistrationRequest(
                    username = null, // Username is optional or auto-generated
                    firstName = firstName,
                    lastName = lastName,
                    email = email,
                    password = password,
                    role = "customer" // Set role as customer for all registrations
                )
                
                try {
                    val response = apiService.register(registrationRequest)
                    withContext(Dispatchers.Main) {
                        if (response.isSuccessful) {
                            Log.d(TAG, "Registration successful")
                            val responseBody = response.body()
                            Log.d(TAG, "Response: $responseBody")
                            
                            responseBody?.let {
                                val userId = it["userId"] as? String
                                Log.d(TAG, "UserId from response: $userId")
                                
                                if (userId != null) {
                                    // Auto-login after registration
                                    loginAfterRegistration(email, password)
                                } else {
                                    Toast.makeText(this@RegisterActivity, "Registration successful! Please login.", Toast.LENGTH_LONG).show()
                                    navigateToLogin()
                                }
                            } ?: run {
                                Toast.makeText(this@RegisterActivity, "Registration successful! Please login.", Toast.LENGTH_LONG).show()
                                navigateToLogin()
                            }
                        } else {
                            val errorBody = response.errorBody()?.string()
                            Log.e(TAG, "Registration failed: ${response.code()} - $errorBody")
                            
                            var errorMessage = "Registration failed"
                            if (errorBody?.contains("User with this email already exists") == true) {
                                errorMessage = "This email is already registered"
                            }
                            
                            Toast.makeText(this@RegisterActivity, errorMessage, Toast.LENGTH_LONG).show()
                            showLoading(false)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Network error during registration", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@RegisterActivity, "Network error: Please check your connection", Toast.LENGTH_SHORT).show()
                        showLoading(false)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during registration", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@RegisterActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    showLoading(false)
                }
            }
        }
    }
    
    private fun loginAfterRegistration(email: String, password: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val loginRequest = com.example.ecotrack.models.LoginRequest(email, password)
                val response = apiService.login(loginRequest)
                
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        val loginResponse = response.body()
                        loginResponse?.let {
                            Log.d(TAG, "Auto-login successful. Token: ${it.token}, UserId: ${it.userId}")
                            sessionManager.saveToken(it.token)
                            sessionManager.saveUserId(it.userId ?: "")
                            sessionManager.saveUserType("customer") // Always customer for new registrations
                            
                            Toast.makeText(this@RegisterActivity, "Registration successful!", Toast.LENGTH_LONG).show()
                            
                            // Navigate to main screen
                            val intent = Intent(this@RegisterActivity, HomeActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                            finish()
                        } ?: run {
                            // Empty response body, just navigate to login
                            Toast.makeText(this@RegisterActivity, "Registration successful! Please login.", Toast.LENGTH_LONG).show()
                            navigateToLogin()
                        }
                    } else {
                        // Failed to auto-login, navigate to login screen
                        Log.d(TAG, "Auto-login failed, redirecting to login")
                        Toast.makeText(this@RegisterActivity, "Registration successful! Please login.", Toast.LENGTH_LONG).show()
                        navigateToLogin()
                    }
                    
                    showLoading(false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during auto-login", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@RegisterActivity, "Registration successful! Please login.", Toast.LENGTH_LONG).show()
                    navigateToLogin()
                    showLoading(false)
                }
            }
        }
    }
    
    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        startActivity(intent)
        finish()
    }
    
    private fun showLoading(isLoading: Boolean) {
        binding.progressBar?.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnRegister.isEnabled = !isLoading
    }
}