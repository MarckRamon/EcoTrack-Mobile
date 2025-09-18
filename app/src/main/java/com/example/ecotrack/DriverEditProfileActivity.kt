package com.example.ecotrack

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.activity.result.contract.ActivityResultContracts
import com.bumptech.glide.Glide
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import com.example.ecotrack.databinding.ActivityDriverEditProfileBinding
import com.example.ecotrack.models.Barangay
import com.example.ecotrack.models.ProfileUpdateRequest
import com.example.ecotrack.utils.ApiService
import com.example.ecotrack.utils.RealTimeUpdateManager
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
    private var originalBarangayId: String? = null
    private var originalBarangayName: String? = null
    
    // Selected barangay
    private var selectedBarangayId: String? = null
    private var selectedBarangayName: String? = null

    // Barangay data
    private var barangays: List<Barangay> = listOf()
    
    // Count email update attempts to avoid infinite loops
    private var emailUpdateAttempts = 0
    private val MAX_EMAIL_ATTEMPTS = 3
    
    // Real-time update manager
    private lateinit var realTimeUpdateManager: RealTimeUpdateManager

    // Image picking
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            Glide.with(this).load(it).into(binding.profileImage)
            uploadAndSaveProfileImage(it)
        }
    }

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
        
        // Initialize real-time update manager
        realTimeUpdateManager = RealTimeUpdateManager(
            activity = this,
            updateCallback = { loadUserProfile() }
        )

        // Load user profile and barangays
        loadUserProfile()
        loadBarangays()

        binding.saveButton.setOnClickListener {
            updateProfile()
        }

        // Tap to change profile image
        binding.profileImage.setOnClickListener {
            openGallery()
        }
        binding.cameraOverlay?.setOnClickListener {
            openGallery()
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

    private fun openGallery() {
        try {
            pickImageLauncher.launch("image/*")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open gallery", e)
            Toast.makeText(this, "Unable to open gallery", Toast.LENGTH_SHORT).show()
        }
    }

    private fun uploadAndSaveProfileImage(imageUri: Uri) {
        val token = sessionManager.getToken() ?: run {
            Toast.makeText(this, "Not authenticated", Toast.LENGTH_SHORT).show()
            return
        }

        showLoading(true)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val catboxUrl = uploadToCatbox(imageUri)
                if (catboxUrl.isNullOrEmpty()) {
                    withContext(Dispatchers.Main) {
                        showLoading(false)
                        Toast.makeText(this@DriverEditProfileActivity, "Failed to upload image", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val mimeType = contentResolver.getType(imageUri) ?: "image/jpeg"
                val imageType = mimeType.substringAfter('/', "jpeg")

                val saveResp = apiService.updateProfileImage(
                    authToken = "Bearer $token",
                    body = mapOf(
                        "imageUrl" to catboxUrl,
                        "imageType" to imageType
                    )
                )

                withContext(Dispatchers.Main) {
                    showLoading(false)
                    if (saveResp.isSuccessful) {
                        // Persist URL for global access
                        sessionManager.saveProfileImageUrl(catboxUrl)
                        
                        Glide.with(this@DriverEditProfileActivity)
                            .load(catboxUrl)
                            .skipMemoryCache(true)
                            .into(binding.profileImage)
                        Toast.makeText(this@DriverEditProfileActivity, "Profile image updated", Toast.LENGTH_SHORT).show()
                    } else {
                        Log.e(TAG, "Failed to save image URL: ${saveResp.code()} ${saveResp.message()}")
                        Toast.makeText(this@DriverEditProfileActivity, "Failed to save image URL", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error uploading/saving profile image", e)
                withContext(Dispatchers.Main) {
                    showLoading(false)
                    Toast.makeText(this@DriverEditProfileActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun uploadToCatbox(imageUri: Uri): String? {
        return try {
            val contentResolver = contentResolver
            val mimeType = contentResolver.getType(imageUri) ?: "image/jpeg"
            val extension = when (mimeType.substringAfter('/').lowercase()) {
                "jpeg", "jpg" -> ".jpg"
                "png" -> ".png"
                "webp" -> ".webp"
                "gif" -> ".gif"
                else -> ".jpg"
            }
            val fileName = "profile_${System.currentTimeMillis()}${extension}"

            val inputStream = contentResolver.openInputStream(imageUri) ?: return null
            val bytes = inputStream.readBytes()
            inputStream.close()

            val requestBody = okhttp3.MultipartBody.Builder().setType(okhttp3.MultipartBody.FORM)
                .addFormDataPart("reqtype", "fileupload")
                .addFormDataPart("userhash", "9977879e19e2ca7543183dd67")
                .addFormDataPart(
                    "fileToUpload",
                    fileName,
                    okhttp3.RequestBody.create(mimeType.toMediaTypeOrNull(), bytes)
                )
                .build()

            val request = okhttp3.Request.Builder()
                .url("https://catbox.moe/user/api.php")
                .post(requestBody)
                .build()

            val okClient = okhttp3.OkHttpClient()
            okClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Catbox upload failed: ${response.code}")
                    return null
                }
                val bodyStr = response.body?.string()?.trim()
                if (bodyStr.isNullOrBlank()) null else bodyStr
            }
        } catch (e: Exception) {
            Log.e(TAG, "Catbox upload error", e)
            null
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
                            originalBarangayId = it.barangayId
                            originalBarangayName = it.barangayName
                            
                            // Set UI fields
                            binding.firstNameInput.setText(it.firstName)
                            binding.lastNameInput.setText(it.lastName)
                            binding.emailInput.setText(it.email)
                            
                            // Set initial barangay selection
                            if (originalBarangayName != null) {
                                binding.barangayDropdown.setText(originalBarangayName)
                                selectedBarangayId = originalBarangayId
                                selectedBarangayName = originalBarangayName
                            }
                            
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
        val barangayChanged = selectedBarangayId != originalBarangayId
        
        if (!nameChanged && !emailChanged && !barangayChanged) {
            Log.d(TAG, "No changes detected, returning to previous screen")
            Toast.makeText(this, "No changes were made", Toast.LENGTH_SHORT).show()
            showLoading(false)
            finish()
            return
        }

        Log.d(TAG, "Attempting to update profile for userId: $userId")
        Log.d(TAG, "Update data: firstName=$firstName, lastName=$lastName, email=$email")
        Log.d(TAG, "Original values: firstName=$originalFirstName, lastName=$originalLastName, email=$originalEmail")
        Log.d(TAG, "Email changed: $emailChanged, Name changed: $nameChanged, Barangay changed: $barangayChanged")
        Log.d(TAG, "Selected barangay: $selectedBarangayName (ID: $selectedBarangayId)")
        
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
                    firstName = firstName,
                    lastName = lastName,
                    phoneNumber = null,  // No phone number update
                    username = null,     // Username doesn't change
                    location = null,     // Location is null
                    email = newEmail,    // Updated email
                    barangayId = selectedBarangayId,
                    barangayName = selectedBarangayName
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

    private fun loadBarangays() {
        Log.d(TAG, "Starting to load barangays")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Making API call to get barangays")

                // Get the token from session manager
                val token = sessionManager.getToken()

                if (token == null) {
                    Log.e(TAG, "No authentication token available")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@DriverEditProfileActivity, "Authentication error. Please log in again.", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                // Add the Bearer prefix to the token
                val authHeader = "Bearer $token"
                Log.d(TAG, "Using auth token for barangay API")

                val response = apiService.getAllBarangays(authHeader)
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful && response.body() != null) {
                        barangays = response.body()!!.filter { it.isActive }
                        Log.d(TAG, "Barangays loaded successfully: ${barangays.size} barangays")
                        Log.d(TAG, "Filtered barangays: $barangays")

                        // Now that we have the data, set up the dropdown
                        setupBarangayDropdown()
                    } else {
                        Log.e(TAG, "Failed to load barangays: ${response.code()} - ${response.message()}")
                        Toast.makeText(this@DriverEditProfileActivity, "Failed to load barangays. Please try again.", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading barangays", e)
                Log.e(TAG, "Exception details: ${e.message}, cause: ${e.cause}")
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@DriverEditProfileActivity, "Error loading barangays: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupBarangayDropdown() {
        val barangayNames = barangays.map { it.name }
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, barangayNames)

        val barangayDropdown = binding.barangayDropdown
        barangayDropdown.setAdapter(adapter)
        barangayDropdown.threshold = 0  // Show all items immediately when clicked

        // Set the initial selection if we have the original barangay
        if (originalBarangayId != null && originalBarangayName != null) {
            barangayDropdown.setText(originalBarangayName)
            selectedBarangayId = originalBarangayId
            selectedBarangayName = originalBarangayName
        }

        barangayDropdown.setOnItemClickListener { _, _, position, _ ->
            val selectedBarangay = barangays[position]
            selectedBarangayId = selectedBarangay.barangayId
            selectedBarangayName = selectedBarangay.name
            Log.d(TAG, "Selected barangay: $selectedBarangayName (ID: $selectedBarangayId)")
        }
    }
    
    private fun showLoading(isLoading: Boolean) {
        binding.saveButton.isEnabled = !isLoading
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    // Override onResume to start real-time updates
    override fun onResume() {
        super.onResume()
        // Start real-time updates
        realTimeUpdateManager.startRealTimeUpdates()
    }
    
    // Override onPause to stop real-time updates
    override fun onPause() {
        super.onPause()
        // Stop real-time updates
        realTimeUpdateManager.stopRealTimeUpdates()
    }
} 