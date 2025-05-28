package com.example.ecotrack

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import com.example.ecotrack.utils.ApiService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody

class ForgotPasswordActivity : BaseActivity() {
    private val TAG = "ForgotPasswordActivity"
    private val apiService = ApiService.create()
    
    private lateinit var emailInput: EditText
    private lateinit var submitButton: Button
    private lateinit var goBackButton: TextView
    private lateinit var progressBar: ProgressBar
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)
        
        // Initialize views
        emailInput = findViewById(R.id.emailInput)
        submitButton = findViewById(R.id.submitButton)
        goBackButton = findViewById(R.id.goBackButton)
        progressBar = findViewById(R.id.progressBar)
        
        // Set up click listeners
        findViewById<ImageButton>(R.id.backButton).setOnClickListener {
            onBackPressed()
        }
        
        goBackButton.setOnClickListener {
            onBackPressed()
        }
        
        submitButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            if (email.isEmpty()) {
                Toast.makeText(this, "Please enter your email", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            submitEmail(email)
        }
    }
    
    private fun submitEmail(email: String) {
        showLoading(true)
        
        // Create the JSON request
        val jsonObject = JSONObject()
        jsonObject.put("identifier", email)
        
        val requestBody = jsonObject.toString()
            .toRequestBody("application/json".toMediaTypeOrNull())
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Make API request to get security questions
                val response = apiService.getSecurityQuestion(requestBody)
                
                withContext(Dispatchers.Main) {
                    showLoading(false)
                    
                    if (response.isSuccessful && response.body() != null) {
                        Log.d(TAG, "Security questions retrieved successfully")
                        
                        // Navigate to security questions screen
                        val intent = Intent(this@ForgotPasswordActivity, ForgotPasswordSecurityActivity::class.java)
                        intent.putExtra("email", email)
                        startActivity(intent)
                    } else {
                        val errorCode = response.code()
                        val errorBody = response.errorBody()?.string() ?: "Unknown error"
                        Log.e(TAG, "Failed to get security questions: $errorCode - $errorBody")
                        
                        val errorMessage = when (errorCode) {
                            400 -> "Invalid email format. Please try again."
                            404 -> "Email not found. Please check your email."
                            else -> "Failed to retrieve security questions (Error $errorCode). Please try again later."
                        }
                        
                        Toast.makeText(
                            this@ForgotPasswordActivity,
                            errorMessage,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception retrieving security questions: ${e.message}")
                e.printStackTrace()
                
                withContext(Dispatchers.Main) {
                    showLoading(false)
                    Toast.makeText(
                        this@ForgotPasswordActivity,
                        "Error: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    
    private fun showLoading(isLoading: Boolean) {
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        submitButton.isEnabled = !isLoading
        submitButton.text = if (isLoading) "Loading..." else "SUBMIT"
    }
    
    // Override to allow access without authentication
    override fun requiresAuthentication(): Boolean {
        return false
    }
} 