package com.example.ecotrack

import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import com.example.ecotrack.models.SecurityQuestion
import com.example.ecotrack.models.SecurityQuestionsResponse
import com.example.ecotrack.models.UserSecurityQuestionsResponse
import com.example.ecotrack.utils.ApiService
import com.example.ecotrack.utils.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Response

class SecurityQuestionsActivity : BaseActivity() {
    private val apiService = ApiService.create()
    private val TAG = "SecurityQuestionsActivity"

    // UI components
    private lateinit var question1Text: TextView
    private lateinit var question2Text: TextView
    private lateinit var question3Text: TextView
    private lateinit var answer1Text: TextView
    private lateinit var answer2Text: TextView
    private lateinit var answer3Text: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_security_questions)

        // Initialize views
        initializeViews()

        // Set up back button
        findViewById<ImageButton>(R.id.backButton).setOnClickListener {
            onBackPressed()
        }

        // Load security questions
        loadSecurityQuestions()
    }

    private fun initializeViews() {
        question1Text = findViewById(R.id.question1Text)
        question2Text = findViewById(R.id.question2Text)
        question3Text = findViewById(R.id.question3Text)
        answer1Text = findViewById(R.id.answer1Text)
        answer2Text = findViewById(R.id.answer2Text)
        answer3Text = findViewById(R.id.answer3Text)
    }

    private fun loadSecurityQuestions() {
        val token = sessionManager.getToken()

        if (token == null) {
            Log.e(TAG, "Token is null")
            Toast.makeText(this, "Session expired. Please login again.", Toast.LENGTH_LONG).show()
            navigateToLogin()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // First try the original endpoint
                Log.d(TAG, "Calling API endpoint: api/users/profile/security-question")
                var response = apiService.getUserProfileSecurityQuestions("Bearer $token")
                
                // If the first endpoint fails with 404, try the alternative endpoint
                if (response.code() == 404) {
                    Log.d(TAG, "First endpoint returned 404, trying alternative endpoint: api/users/profile/security-questions")
                    response = apiService.getUserProfileSecurityQuestionsAlternative("Bearer $token")
                }
                
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful && response.body() != null) {
                        val responseBody = response.body()!!
                        Log.d(TAG, "API response successful: $responseBody")
                        displaySecurityQuestions(responseBody)
                    } else {
                        // Detailed logging for error investigation
                        val errorCode = response.code()
                        val errorBody = response.errorBody()?.string() ?: "No error body"
                        Log.e(TAG, "Failed to load security questions from both endpoints")
                        Log.e(TAG, "Error code: $errorCode")
                        Log.e(TAG, "Error body: $errorBody")
                        
                        Toast.makeText(
                            this@SecurityQuestionsActivity,
                            "The security questions feature is not available yet (Error $errorCode)",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception calling security questions API: ${e.message}")
                e.printStackTrace()
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@SecurityQuestionsActivity,
                        "Error loading security questions: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun createAndDisplayMockSecurityQuestions(email: String? = null) {
        // Get the token directly to ensure we're using the current user's information
        val token = sessionManager.getToken()
        val actualEmail = if (email.isNullOrEmpty() && token != null) {
            sessionManager.extractEmailFromToken(token)
        } else {
            email
        }
        
        Log.d(TAG, "Creating personalized security questions for email: $actualEmail")
        
        // Create personalized mock answers based on the user's email
        val username = actualEmail?.split("@")?.get(0) ?: "User"
        
        // Generate answers unique to this user by using parts of their email/username
        val firstPet = if (username.length > 3) username.substring(0, 3).capitalize() + "y" else "Buddy"
        val city = "Manila"
        val mothersMaiden = if (username.length > 2) username.capitalize() + "Mom" else "Smith"
        
        // Create mock security questions with embedded answers to match API format
        val mockQuestions = listOf(
            SecurityQuestion(
                id = "FIRST_PET", 
                questionText = "What was your first pet's name?",
                answer = firstPet
            ),
            SecurityQuestion(
                id = "BIRTH_CITY", 
                questionText = "In what city were you born?",
                answer = city
            ),
            SecurityQuestion(
                id = "MOTHERS_MAIDEN_NAME", 
                questionText = "What is your mother's maiden name?",
                answer = mothersMaiden
            )
        )
        
        val mockResponse = UserSecurityQuestionsResponse(mockQuestions)
        
        Log.d(TAG, "Created personalized mock data for user: $actualEmail with username: $username")
        displaySecurityQuestions(mockResponse)
        
        // Show message to indicate these are mock questions
        Toast.makeText(
            this,
            "Security questions feature is not yet available on the backend.\nShowing example questions for your account.",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun displaySecurityQuestions(questionsResponse: UserSecurityQuestionsResponse) {
        // Log the entire response for debugging
        Log.d(TAG, "Security questions response: $questionsResponse")
        Log.d(TAG, "Questions size: ${questionsResponse.securityQuestions.size}")
        
        if (questionsResponse.securityQuestions.size < 3) {
            Log.e(TAG, "Not enough security questions returned: ${questionsResponse.securityQuestions.size}")
            Toast.makeText(this, "Security questions not properly setup.", Toast.LENGTH_LONG).show()
            return
        }

        // Display the questions
        question1Text.text = questionsResponse.securityQuestions[0].questionText
        question2Text.text = questionsResponse.securityQuestions[1].questionText
        question3Text.text = questionsResponse.securityQuestions[2].questionText

        // Display the masked answers - now the answers are in each question object
        for (i in 0..2) {
            val question = questionsResponse.securityQuestions[i]
            val answer = question.answer
            Log.d(TAG, "Question ${i+1} (${question.id}): ${question.questionText}")
            Log.d(TAG, "Answer ${i+1} before masking: $answer")
            
            val maskedAnswer = maskAnswer(answer ?: "")
            when (i) {
                0 -> answer1Text.text = maskedAnswer
                1 -> answer2Text.text = maskedAnswer
                2 -> answer3Text.text = maskedAnswer
            }
        }
    }

    private fun maskAnswer(answer: String): String {
        if (answer.isEmpty()) return "No answer saved"
        
        // Show only the first character followed by asterisks
        val firstChar = answer.first()
        val asterisks = "*".repeat(minOf(answer.length - 1, 8))  // Limit to 8 asterisks for privacy
        
        return "$firstChar$asterisks"
    }
} 