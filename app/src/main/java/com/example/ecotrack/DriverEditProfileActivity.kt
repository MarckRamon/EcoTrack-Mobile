package com.example.ecotrack

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.ecotrack.databinding.ActivityDriverEditProfileBinding
import com.example.ecotrack.models.ProfileUpdateRequest
import com.example.ecotrack.utils.ApiService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DriverEditProfileActivity : BaseActivity() {
    private lateinit var binding: ActivityDriverEditProfileBinding
    private val apiService = ApiService.create()
    private val TAG = "DriverEditProfileActivity"
    
    // Store original profile values
    private var originalEmail = ""
    private var originalFirstName = ""
    private var originalLastName = ""
    
    // Count email update attempts to avoid infinite loops
    private var emailUpdateAttempts = 0
    private val MAX_EMAIL_ATTEMPTS = 3

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDriverEditProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // BaseActivity handles session checks
        
        // Verify the user is a driver
        validateDriverRole()
        
        binding.backButton.setOnClickListener {
            finish()
        }

        loadUserProfile()

        binding.saveButton.setOnClickListener {
            updateProfile()
        }
    }
    
    private fun validateDriverRole() {
        val userType = sessionManager.getUserType()
        if (userType != "driver") {
            Log.w(TAG, "Non-driver account detected: $userType")
            Toast.makeText(this, "This section is for drivers only. Redirecting...", Toast.LENGTH_LONG).show()
            finish()
            return
        }
    }

    private fun loadUserProfile() {
        val token = sessionManager.getToken()
        val userId = sessionManager.getUserId()

        if (token == null || userId == null) {
            Log.e(TAG, "loadUserProfile - Missing credentials - token: $token, userId: $userId")
            // BaseActivity will handle the redirect to login if needed
            return
        }

        Log.d(TAG, "Attempting to load profile for userId: $userId")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = apiService.getProfile(userId, "Bearer $token")
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        val profile = response.body()
                        profile?.let {
                            Log.d(TAG, "Profile loaded successfully: ${it.firstName} ${it.lastName}, email: ${it.email}")
                            
                            // Store original values
                            originalFirstName = it.firstName ?: ""
                            originalLastName = it.lastName ?: ""
                            originalEmail = it.email ?: ""
                            
                            // Set UI fields
                            binding.firstNameInput.setText(it.firstName)
                            binding.lastNameInput.setText(it.lastName)
                            binding.emailInput.setText(it.email)
                            
                            // Make email field editable again (with warning)
                            binding.emailInput.isEnabled = true
                            binding.emailInputLayout.helperText = "Note: Changing your email will require you to login again"
                        }
                    } else {
                        Log.e(TAG, "Failed to load profile: ${response.code()} - ${response.message()}")
                        val errorBody = response.errorBody()?.string()
                        Log.e(TAG, "Error body: $errorBody")
                        
                        // Show appropriate error message
                        Toast.makeText(this@DriverEditProfileActivity, "Failed to load profile", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading profile", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@DriverEditProfileActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateProfile() {
        val token = sessionManager.getToken()
        val userId = sessionManager.getUserId()

        if (token == null || userId == null) {
            Log.e(TAG, "updateProfile - Missing credentials - token: $token, userId: $userId")
            // BaseActivity will handle the redirect to login if needed
            return
        }

        val firstName = binding.firstNameInput.text.toString().trim()
        val lastName = binding.lastNameInput.text.toString().trim()
        val email = binding.emailInput.text.toString().trim()

        if (firstName.isEmpty() || lastName.isEmpty() || email.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            return
        }

        // Show progress indicator
        showLoading(true)

        // Check if any field actually changed
        val nameChanged = firstName != originalFirstName || lastName != originalLastName
        val emailChanged = email != originalEmail
        
        if (!nameChanged && !emailChanged) {
            Log.d(TAG, "No changes detected, returning to previous screen")
            Toast.makeText(this, "No changes were made", Toast.LENGTH_SHORT).show()
            showLoading(false)
            finish()
            return
        }

        Log.d(TAG, "Attempting to update profile for userId: $userId")
        Log.d(TAG, "Update data: firstName=$firstName, lastName=$lastName, email=$email")
        Log.d(TAG, "Original values: firstName=$originalFirstName, lastName=$originalLastName, email=$originalEmail")
        Log.d(TAG, "Email changed: $emailChanged, Name changed: $nameChanged")
        
        updateProfileWithNewEmail(userId, token, firstName, lastName, email)
    }
    
    private fun updateProfileWithNewEmail(
        userId: String, 
        token: String, 
        firstName: String, 
        lastName: String, 
        newEmail: String
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Create request with all fields
                val updateRequest = ProfileUpdateRequest(
                    firstName,
                    lastName,
                    null,  // phoneNumber is null
                    null,  // username doesn't change
                    null,  // location is null
                    newEmail  // Updated email
                )
                
                Log.d(TAG, "Sending profile update request: $updateRequest")
                
                val response = apiService.updateProfile(
                    userId,
                    "Bearer $token",
                    updateRequest
                )
                
                withContext(Dispatchers.Main) {
                    showLoading(false)
                    
                    if (response.isSuccessful) {
                        Log.d(TAG, "Profile updated successfully")
                        val emailChanged = newEmail != originalEmail
                        
                        if (emailChanged) {
                            // If email was changed, need to logout and relogin
                            Toast.makeText(
                                this@DriverEditProfileActivity,
                                "Profile updated successfully. Please login again with your new email.",
                                Toast.LENGTH_LONG
                            ).show()
                            
                            // Logout and navigate to login
                            sessionManager.logout()
                            navigateToLogin()
                        } else {
                            Toast.makeText(
                                this@DriverEditProfileActivity,
                                "Profile updated successfully", 
                                Toast.LENGTH_SHORT
                            ).show()
                            finish()
                        }
                    } else {
                        val errorCode = response.code()
                        Log.e(TAG, "Failed to update profile: $errorCode - ${response.message()}")
                        
                        try {
                            val errorBody = response.errorBody()?.string()
                            Log.e(TAG, "Error body: $errorBody")
                            
                            // Check if this is the common "Email is already in use" error
                            val isEmailInUseError = errorBody?.contains("Email is already in use") == true
                            
                            if (isEmailInUseError && emailUpdateAttempts < MAX_EMAIL_ATTEMPTS) {
                                handleEmailInUseError(userId, token, firstName, lastName, newEmail)
                            } else {
                                val userMessage = when {
                                    isEmailInUseError -> "This email appears to be already in use. Please try another email address."
                                    errorCode == 400 -> "Invalid data format. Please check your inputs."
                                    errorCode == 401 -> "Session expired. Please login again."
                                    errorCode == 403 -> "You don't have permission to update this profile."
                                    errorCode == 404 -> "User profile not found."
                                    else -> "Failed to update profile: ${errorBody ?: "Unknown error"}"
                                }
                                
                                Toast.makeText(this@DriverEditProfileActivity, userMessage, Toast.LENGTH_LONG).show()
                                
                                if (errorCode == 401 || errorCode == 403) {
                                    sessionManager.logout()
                                    navigateToLogin()
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing error response", e)
                            Toast.makeText(this@DriverEditProfileActivity, "Failed to update profile", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating profile", e)
                withContext(Dispatchers.Main) {
                    showLoading(false)
                    Toast.makeText(this@DriverEditProfileActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun handleEmailInUseError(
        userId: String,
        token: String,
        firstName: String,
        lastName: String,
        attemptedEmail: String
    ) {
        // Generate alternative email suggestions
        val username = attemptedEmail.substringBefore('@')
        val domain = attemptedEmail.substringAfter('@')
        
        val options = arrayOf(
            // Try with name only changes, keep email the same
            "Continue with original email ($originalEmail)",
            // Try with a timestamp
            "${username}_${System.currentTimeMillis()}@$domain",
            // Try with a different domain
            "$username@outlook.com",
            // Try another variation
            "${username}.alt@$domain"
        )
        
        AlertDialog.Builder(this)
            .setTitle("Email Already Exists")
            .setMessage(
                "The server reports that '$attemptedEmail' is already in use.\n\n" +
                "Would you like to:\n" +
                "1. Keep your current email and update only your name\n" +
                "2. Try a different email format"
            )
            .setItems(options) { _, which ->
                emailUpdateAttempts++
                
                when (which) {
                    0 -> {
                        // Use original email, update only names
                        showLoading(true)
                        updateProfileWithNewEmail(userId, token, firstName, lastName, originalEmail)
                    }
                    else -> {
                        val selectedEmail = options[which]
                        binding.emailInput.setText(selectedEmail)
                        showLoading(true)
                        updateProfileWithNewEmail(userId, token, firstName, lastName, selectedEmail)
                    }
                }
            }
            .setNeutralButton("Cancel") { _, _ ->
                // Reset to original email
                binding.emailInput.setText(originalEmail)
            }
            .setCancelable(false)
            .show()
    }
    
    private fun showLoading(isLoading: Boolean) {
        binding.saveButton.isEnabled = !isLoading
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
    }
} 