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
import com.example.ecotrack.models.QuestionAnswer
import com.example.ecotrack.utils.ApiService
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class ForgotPasswordSecurityActivity : BaseActivity() {
    private val TAG = "ForgotPasswordSecurity"
    private val apiService = ApiService.create()
    
    private lateinit var question1Text: TextView
    private lateinit var question2Text: TextView
    private lateinit var question3Text: TextView
    private lateinit var answer1Input: EditText
    private lateinit var answer2Input: EditText
    private lateinit var answer3Input: EditText
    private lateinit var submitButton: Button
    private lateinit var goBackButton: TextView
    private lateinit var progressBar: ProgressBar
    
    private var email: String = ""
    private var securityQuestions = mutableListOf<JSONObject>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password_security)
        
        // Get email from intent
        email = intent.getStringExtra("email") ?: ""
        if (email.isEmpty()) {
            Toast.makeText(this, "Email not provided", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        // Initialize views
        question1Text = findViewById(R.id.securityQuestion1)
        question2Text = findViewById(R.id.securityQuestion2)
        question3Text = findViewById(R.id.securityQuestion3)
        answer1Input = findViewById(R.id.answerInput1)
        answer2Input = findViewById(R.id.answerInput2)
        answer3Input = findViewById(R.id.answerInput3)
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
            verifySecurityAnswers()
        }
        
        // Fetch security questions
        fetchSecurityQuestions(email)
    }
    
    private fun fetchSecurityQuestions(email: String) {
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
                        try {
                            val responseString = response.body()!!.string()
                            Log.d(TAG, "Security questions response: $responseString")
                            
                            val jsonResponse = JSONObject(responseString)
                            val questionsArray = jsonResponse.optJSONArray("securityQuestions")
                            
                            if (questionsArray != null && questionsArray.length() >= 3) {
                                // Parse security questions
                                securityQuestions.clear()
                                for (i in 0 until questionsArray.length()) {
                                    securityQuestions.add(questionsArray.getJSONObject(i))
                                }
                                
                                // Display questions
                                displaySecurityQuestions()
                            } else {
                                Toast.makeText(
                                    this@ForgotPasswordSecurityActivity,
                                    "Not enough security questions found",
                                    Toast.LENGTH_LONG
                                ).show()
                                finish()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing security questions: ${e.message}")
                            Toast.makeText(
                                this@ForgotPasswordSecurityActivity,
                                "Error parsing security questions: ${e.message}",
                                Toast.LENGTH_LONG
                            ).show()
                            finish()
                        }
                    } else {
                        val errorCode = response.code()
                        val errorBody = response.errorBody()?.string() ?: "Unknown error"
                        Log.e(TAG, "Failed to get security questions: $errorCode - $errorBody")
                        
                        Toast.makeText(
                            this@ForgotPasswordSecurityActivity,
                            "Failed to retrieve security questions. Please try again later.",
                            Toast.LENGTH_LONG
                        ).show()
                        finish()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception retrieving security questions: ${e.message}")
                e.printStackTrace()
                
                withContext(Dispatchers.Main) {
                    showLoading(false)
                    Toast.makeText(
                        this@ForgotPasswordSecurityActivity,
                        "Error: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                }
            }
        }
    }
    
    private fun displaySecurityQuestions() {
        if (securityQuestions.size >= 3) {
            question1Text.text = securityQuestions[0].optString("questionText", "Question 1")
            question2Text.text = securityQuestions[1].optString("questionText", "Question 2")
            question3Text.text = securityQuestions[2].optString("questionText", "Question 3")
            
            // Make sure questions are visible
            question1Text.visibility = View.VISIBLE
            question2Text.visibility = View.VISIBLE
            question3Text.visibility = View.VISIBLE
            
            // If questionText is null or empty, try to get a display name from questionId
            if (question1Text.text.isNullOrEmpty()) {
                val questionId = securityQuestions[0].optString("questionId", "")
                question1Text.text = getReadableQuestionText(questionId)
            }
            
            if (question2Text.text.isNullOrEmpty()) {
                val questionId = securityQuestions[1].optString("questionId", "")
                question2Text.text = getReadableQuestionText(questionId)
            }
            
            if (question3Text.text.isNullOrEmpty()) {
                val questionId = securityQuestions[2].optString("questionId", "")
                question3Text.text = getReadableQuestionText(questionId)
            }
        }
    }
    
    private fun getReadableQuestionText(questionId: String): String {
        return when (questionId) {
            "FIRST_PET_NAME" -> "What was the name of your first pet?"
            "BIRTH_CITY" -> "In what city were you born?"
            "FAVORITE_COLOR" -> "What is your favorite color?"
            "MOTHERS_MAIDEN_NAME" -> "What is your mother's maiden name?"
            "FIRST_SCHOOL" -> "What was the name of your first school?"
            "CHILDHOOD_NICKNAME" -> "What was your childhood nickname?"
            else -> "Question: $questionId"
        }
    }
    
    private fun verifySecurityAnswers() {
        if (securityQuestions.size < 3) {
            Toast.makeText(this, "Security questions not loaded properly", Toast.LENGTH_SHORT).show()
            return
        }
        
        val answer1 = answer1Input.text.toString().trim()
        val answer2 = answer2Input.text.toString().trim()
        val answer3 = answer3Input.text.toString().trim()
        
        // Validate inputs
        if (answer1.isEmpty() || answer2.isEmpty() || answer3.isEmpty()) {
            Toast.makeText(this, "Please answer all security questions", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Get question IDs
        val questionId1 = securityQuestions[0].optString("questionId", "")
        val questionId2 = securityQuestions[1].optString("questionId", "")
        val questionId3 = securityQuestions[2].optString("questionId", "")
        
        Log.d(TAG, "Verifying security answers for email: $email")
        Log.d(TAG, "Question IDs: $questionId1, $questionId2, $questionId3")
        
        showLoading(true)
        
        // Create verification request
        val jsonObject = JSONObject()
        jsonObject.put("identifier", email)
        
        val answersArray = JSONArray()
        
        // Add first answer
        val answerObj1 = JSONObject()
        answerObj1.put("questionId", questionId1)
        answerObj1.put("answer", answer1)
        answersArray.put(answerObj1)
        
        // Add second answer
        val answerObj2 = JSONObject()
        answerObj2.put("questionId", questionId2)
        answerObj2.put("answer", answer2)
        answersArray.put(answerObj2)
        
        // Add third answer
        val answerObj3 = JSONObject()
        answerObj3.put("questionId", questionId3)
        answerObj3.put("answer", answer3)
        answersArray.put(answerObj3)
        
        jsonObject.put("answers", answersArray)
        
        val requestBody = jsonObject.toString()
            .toRequestBody("application/json".toMediaTypeOrNull())
        
        // Verify answers with the API before proceeding
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Make API request to verify answers (using the same endpoint for simplicity)
                // In a real app, you might have a dedicated endpoint for verification
                val response = apiService.getSecurityQuestion(requestBody)
                
                withContext(Dispatchers.Main) {
                    showLoading(false)
                    
                    if (response.isSuccessful) {
                        // If verification is successful, proceed to password change screen
                        Log.d(TAG, "Security answers verified successfully")
                        Log.d(TAG, "Navigating to password change screen with email: $email")
                        
                        // Proceed to password change screen
                        val intent = Intent(this@ForgotPasswordSecurityActivity, ChangePasswordActivity::class.java)
                        intent.putExtra("email", email)
                        intent.putExtra("questionId1", questionId1)
                        intent.putExtra("questionId2", questionId2)
                        intent.putExtra("questionId3", questionId3)
                        intent.putExtra("answer1", answer1)
                        intent.putExtra("answer2", answer2)
                        intent.putExtra("answer3", answer3)
                        
                        // Add flag to clear the back stack up to this activity
                        intent.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT)
                        
                        startActivity(intent)
                        finish() // Finish this activity to prevent going back to it
                    } else {
                        val errorCode = response.code()
                        val errorBody = response.errorBody()?.string() ?: "Unknown error"
                        Log.e(TAG, "Failed to verify security answers: $errorCode - $errorBody")
                        
                        val errorMessage = when (errorCode) {
                            400 -> "Incorrect answers. Please try again."
                            401 -> "Unauthorized. Please try again."
                            404 -> "User not found. Please check your email."
                            else -> "Failed to verify answers (Error $errorCode). Please try again later."
                        }
                        
                        Toast.makeText(
                            this@ForgotPasswordSecurityActivity,
                            errorMessage,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception verifying security answers: ${e.message}")
                e.printStackTrace()
                
                withContext(Dispatchers.Main) {
                    showLoading(false)
                    Toast.makeText(
                        this@ForgotPasswordSecurityActivity,
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