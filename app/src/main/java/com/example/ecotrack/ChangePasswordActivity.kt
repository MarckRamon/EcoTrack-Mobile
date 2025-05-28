package com.example.ecotrack

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import com.example.ecotrack.models.ForgotPasswordRequest
import com.example.ecotrack.models.QuestionAnswer
import com.example.ecotrack.utils.ApiService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChangePasswordActivity : BaseActivity() {
    private val TAG = "ChangePasswordActivity"
    private val apiService = ApiService.create()
    
    // UI Components
    private lateinit var newPasswordInput: EditText
    private lateinit var confirmPasswordInput: EditText
    private lateinit var submitButton: Button
    private lateinit var goBackButton: TextView
    
    // For forgot password flow
    private var isForgotPasswordFlow = false
    private var email: String = ""
    private var questionId1: String = ""
    private var questionId2: String = ""
    private var questionId3: String = ""
    private var answer1: String = ""
    private var answer2: String = ""
    private var answer3: String = ""
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_change_password)
        
        Log.d(TAG, "ChangePasswordActivity onCreate called")
        
        // Check if we're coming from the forgot password flow
        email = intent.getStringExtra("email") ?: ""
        Log.d(TAG, "Email from intent: $email")
        
        // If these extras exist, we're in the forgot password flow
        if (intent.hasExtra("questionId1") && intent.hasExtra("answer1")) {
            Log.d(TAG, "Found question and answer extras, setting isForgotPasswordFlow to true")
            isForgotPasswordFlow = true
            questionId1 = intent.getStringExtra("questionId1") ?: ""
            questionId2 = intent.getStringExtra("questionId2") ?: ""
            questionId3 = intent.getStringExtra("questionId3") ?: ""
            answer1 = intent.getStringExtra("answer1") ?: ""
            answer2 = intent.getStringExtra("answer2") ?: ""
            answer3 = intent.getStringExtra("answer3") ?: ""
            Log.d(TAG, "Received questionIds: $questionId1, $questionId2, $questionId3")
            Log.d(TAG, "Received answers: $answer1, $answer2, $answer3")
        } else {
            Log.d(TAG, "No question and answer extras found")
        }
        
        initViews()
        setupClickListeners()
    }
    
    private fun initViews() {
        newPasswordInput = findViewById(R.id.newPasswordInput)
        confirmPasswordInput = findViewById(R.id.confirmPasswordInput)
        submitButton = findViewById(R.id.submitButton)
        goBackButton = findViewById(R.id.goBackButton)
        
        findViewById<ImageButton>(R.id.backButton).setOnClickListener {
            onBackPressed()
        }
    }
    
    private fun setupClickListeners() {
        submitButton.setOnClickListener {
            resetPassword()
        }
        
        goBackButton.setOnClickListener {
            onBackPressed()
        }
        
        // Toggle password visibility
        findViewById<ImageButton>(R.id.togglePassword1).setOnClickListener {
            togglePasswordVisibility(newPasswordInput)
        }
        
        findViewById<ImageButton>(R.id.togglePassword2).setOnClickListener {
            togglePasswordVisibility(confirmPasswordInput)
        }
    }
    
    private fun togglePasswordVisibility(editText: EditText) {
        if (editText.inputType and android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD > 0) {
            // Show password
            editText.inputType = android.text.InputType.TYPE_CLASS_TEXT or 
                                android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        } else {
            // Hide password
            editText.inputType = android.text.InputType.TYPE_CLASS_TEXT or 
                                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        // Move cursor to the end of text
        editText.setSelection(editText.text.length)
    }
    
    private fun resetPassword() {
        val newPassword = newPasswordInput.text.toString().trim()
        val confirmPassword = confirmPasswordInput.text.toString().trim()
        
        // Validate passwords
        if (newPassword.isEmpty() || confirmPassword.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (newPassword != confirmPassword) {
            Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Get user email
        if (email.isEmpty()) {
            email = sessionManager.getUserEmail() ?: ""
            if (email.isEmpty()) {
                Toast.makeText(this, "Could not retrieve user information. Please login again.", Toast.LENGTH_LONG).show()
                navigateToLogin()
                return
            }
        }
        
        showLoading(true)
        
        if (isForgotPasswordFlow) {
            // Create a list of security question answers
            val securityAnswers = listOf(
                QuestionAnswer(questionId = questionId1, answer = answer1),
                QuestionAnswer(questionId = questionId2, answer = answer2),
                QuestionAnswer(questionId = questionId3, answer = answer3)
            )
            
            // Create the request for forgot password flow
            val forgotPasswordRequest = ForgotPasswordRequest(
                identifier = email,
                newPassword = newPassword,
                answers = securityAnswers
            )
            
            Log.d(TAG, "Sending password reset request for email: $email with security answers")
            
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val response = apiService.resetPassword(forgotPasswordRequest)
                    
                    withContext(Dispatchers.Main) {
                        showLoading(false)
                        
                        if (response.isSuccessful) {
                            Log.d(TAG, "Password reset successful")
                            Toast.makeText(
                                this@ChangePasswordActivity,
                                "Password changed successfully!",
                                Toast.LENGTH_LONG
                            ).show()
                            
                            // Return to login page using our custom method
                            handlePostResetNavigation()
                        } else {
                            val errorCode = response.code()
                            val errorBody = response.errorBody()?.string() ?: "Unknown error"
                            Log.e(TAG, "Failed to reset password: $errorCode - $errorBody")
                            
                            // Handle specific error cases
                            val errorMessage = when (errorCode) {
                                400 -> "Invalid request format or security answers are incorrect. Please try again."
                                401 -> "Unauthorized. Please try again."
                                404 -> "User not found. Please check your email."
                                else -> "Failed to change password (Error $errorCode). Please try again later."
                            }
                            
                            Toast.makeText(
                                this@ChangePasswordActivity,
                                errorMessage,
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Exception during password reset: ${e.message}")
                    e.printStackTrace()
                    
                    withContext(Dispatchers.Main) {
                        showLoading(false)
                        Toast.makeText(
                            this@ChangePasswordActivity,
                            "Error: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        } else {
            // Use dummy answers for verification - these won't actually be checked
            // since we're already authenticated in this flow
            val dummyAnswers = listOf(
                QuestionAnswer(questionId = "FIRST_PET_NAME", answer = "Fluffy"),
                QuestionAnswer(questionId = "BIRTH_CITY", answer = "New York"),
                QuestionAnswer(questionId = "FAVORITE_COLOR", answer = "Black")
            )
            
            // Create the request with the format that matches the backend's expectation
            val forgotPasswordRequest = ForgotPasswordRequest(
                identifier = email,
                newPassword = newPassword,
                answers = dummyAnswers
            )
            
            Log.d(TAG, "Sending password reset request for email: $email")
            
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val response = apiService.resetPassword(forgotPasswordRequest)
                    
                    withContext(Dispatchers.Main) {
                        showLoading(false)
                        
                        if (response.isSuccessful) {
                            Log.d(TAG, "Password reset successful")
                            Toast.makeText(
                                this@ChangePasswordActivity,
                                "Password changed successfully!",
                                Toast.LENGTH_LONG
                            ).show()
                            
                            // Return to profile page
                            finish()
                        } else {
                            val errorCode = response.code()
                            val errorBody = response.errorBody()?.string() ?: "Unknown error"
                            Log.e(TAG, "Failed to reset password: $errorCode - $errorBody")
                            
                            // Handle specific error cases
                            val errorMessage = when (errorCode) {
                                400 -> "Invalid request format. Please try again."
                                401 -> "Unauthorized. Please log in again."
                                404 -> "User not found. Please check your email."
                                else -> "Failed to change password (Error $errorCode). Please try again later."
                            }
                            
                            Toast.makeText(
                                this@ChangePasswordActivity,
                                errorMessage,
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Exception during password reset: ${e.message}")
                    e.printStackTrace()
                    
                    withContext(Dispatchers.Main) {
                        showLoading(false)
                        Toast.makeText(
                            this@ChangePasswordActivity,
                            "Error: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }
    
    private fun showLoading(isLoading: Boolean) {
        if (isLoading) {
            submitButton.isEnabled = false
            submitButton.text = "Updating..."
        } else {
            submitButton.isEnabled = true
            submitButton.text = "SUBMIT"
        }
    }
    
    // Custom method to handle navigation after password reset
    private fun handlePostResetNavigation() {
        Log.d(TAG, "Handling post-reset navigation. isForgotPasswordFlow: $isForgotPasswordFlow")
        
        // Clear activity stack and go back to login screen
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
    
    // Override to allow access without authentication in forgot password flow
    override fun requiresAuthentication(): Boolean {
        return !isForgotPasswordFlow
    }
} 