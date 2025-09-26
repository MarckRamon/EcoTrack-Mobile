package com.example.grabtrash

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import com.example.grabtrash.models.SecurityQuestion
import com.example.grabtrash.models.UserSecurityQuestionsResponse
import com.example.grabtrash.utils.ApiService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChangePasswordSecurityActivity : BaseActivity() {
    private val TAG = "ChangePasswordSecQues"
    private val apiService = ApiService.create()
    
    // UI Components
    private lateinit var questionText1: TextView
    private lateinit var questionText2: TextView
    private lateinit var questionText3: TextView
    private lateinit var answerInput1: EditText
    private lateinit var answerInput2: EditText
    private lateinit var answerInput3: EditText
    private lateinit var submitButton: Button
    private lateinit var goBackButton: TextView
    
    private var securityQuestions: List<SecurityQuestion> = listOf()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_change_password_security)
        
        initViews()
        setupClickListeners()
        fetchSecurityQuestions()
    }
    
    private fun initViews() {
        questionText1 = findViewById(R.id.securityQuestion1)
        questionText2 = findViewById(R.id.securityQuestion2)
        questionText3 = findViewById(R.id.securityQuestion3)
        answerInput1 = findViewById(R.id.answerInput1)
        answerInput2 = findViewById(R.id.answerInput2)
        answerInput3 = findViewById(R.id.answerInput3)
        submitButton = findViewById(R.id.submitButton)
        goBackButton = findViewById(R.id.goBackButton)
        
        findViewById<ImageButton>(R.id.backButton).setOnClickListener {
            onBackPressed()
        }
    }
    
    private fun setupClickListeners() {
        submitButton.setOnClickListener {
            verifySecurityQuestions()
        }
        
        goBackButton.setOnClickListener {
            onBackPressed()
        }
    }
    
    private fun fetchSecurityQuestions() {
        val token = sessionManager.getToken()
        
        if (token == null) {
            Log.e(TAG, "Token is null")
            Toast.makeText(this, "Session expired. Please login again.", Toast.LENGTH_LONG).show()
            navigateToLogin()
            return
        }

        // Show loading state
        showLoading(true)
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Fetching security questions from /api/users/profile/security-questions")
                val response = apiService.getUserProfileSecurityQuestionsAlternative("Bearer $token")
                
                withContext(Dispatchers.Main) {
                    showLoading(false)
                    
                    if (response.isSuccessful && response.body() != null) {
                        val responseBody = response.body()!!
                        Log.d(TAG, "Security questions fetched successfully: $responseBody")
                        
                        if (responseBody.securityQuestions.isNotEmpty()) {
                            displaySecurityQuestions(responseBody)
                        } else {
                            Log.e(TAG, "No security questions returned in the response")
                            Toast.makeText(
                                this@ChangePasswordSecurityActivity,
                                "No security questions found for your account",
                                Toast.LENGTH_LONG
                            ).show()
                            finish()
                        }
                    } else {
                        val errorCode = response.code()
                        val errorBody = response.errorBody()?.string() ?: "No error body"
                        Log.e(TAG, "Failed to load security questions. Error code: $errorCode, Error body: $errorBody")
                        
                        Toast.makeText(
                            this@ChangePasswordSecurityActivity,
                            "Failed to load security questions (${errorCode}). Please try again later.",
                            Toast.LENGTH_LONG
                        ).show()
                        finish()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception fetching security questions: ${e.message}")
                e.printStackTrace()
                
                withContext(Dispatchers.Main) {
                    showLoading(false)
                    Toast.makeText(
                        this@ChangePasswordSecurityActivity,
                        "Error: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                }
            }
        }
    }
    
    private fun showLoading(isLoading: Boolean) {
        if (isLoading) {
            submitButton.isEnabled = false
            submitButton.text = "Loading..."
        } else {
            submitButton.isEnabled = true
            submitButton.text = "SUBMIT"
        }
    }
    
    private fun displaySecurityQuestions(questionsResponse: UserSecurityQuestionsResponse) {
        val questions = questionsResponse.securityQuestions
        Log.d(TAG, "Displaying ${questions.size} security questions")
        
        for (question in questions) {
            // Log both the question ID and the resolved question text
            val effectiveQuestionId = if (question.questionId.isNotEmpty()) question.questionId else question.id
            val effectiveQuestionText = question.getEffectiveQuestionText()
            
            Log.d(TAG, "Question: $effectiveQuestionId - $effectiveQuestionText, Answer: ${question.answer}")
        }
        
        if (questions.size < 3) {
            Log.e(TAG, "Not enough security questions returned: ${questions.size}")
            Toast.makeText(this, "Security questions not properly setup (need 3, got ${questions.size})", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        
        securityQuestions = questions
        
        // Display the questions using the getEffectiveQuestionText method
        questionText1.text = questions[0].getEffectiveQuestionText()
        questionText2.text = questions[1].getEffectiveQuestionText()
        questionText3.text = questions[2].getEffectiveQuestionText()
        
        // Make sure the questions are visible in the UI
        questionText1.visibility = View.VISIBLE
        questionText2.visibility = View.VISIBLE
        questionText3.visibility = View.VISIBLE
        
        Log.d(TAG, "Questions displayed successfully")
    }
    
    private fun verifySecurityQuestions() {
        if (securityQuestions.isEmpty() || securityQuestions.size < 3) {
            Toast.makeText(this, "Security questions not loaded properly.", Toast.LENGTH_SHORT).show()
            return
        }
        
        val answer1 = answerInput1.text.toString().trim()
        val answer2 = answerInput2.text.toString().trim()
        val answer3 = answerInput3.text.toString().trim()
        
        // Validate inputs
        if (answer1.isEmpty() || answer2.isEmpty() || answer3.isEmpty()) {
            Toast.makeText(this, "Please answer all security questions", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d(TAG, "Checking answers against stored values:")
        Log.d(TAG, "Q1: User entered: '$answer1', Correct: '${securityQuestions[0].answer}'")
        Log.d(TAG, "Q2: User entered: '$answer2', Correct: '${securityQuestions[1].answer}'")
        Log.d(TAG, "Q3: User entered: '$answer3', Correct: '${securityQuestions[2].answer}'")
        
        // Check if answers match
        val isAnswer1Correct = answer1.equals(securityQuestions[0].answer, ignoreCase = true)
        val isAnswer2Correct = answer2.equals(securityQuestions[1].answer, ignoreCase = true)
        val isAnswer3Correct = answer3.equals(securityQuestions[2].answer, ignoreCase = true)
        
        Log.d(TAG, "Answer verification results: Q1=$isAnswer1Correct, Q2=$isAnswer2Correct, Q3=$isAnswer3Correct")
        
        if (isAnswer1Correct && isAnswer2Correct && isAnswer3Correct) {
            // All answers are correct, navigate to change password screen
            Log.d(TAG, "All answers are correct, navigating to password change screen")
            val intent = Intent(this, ChangePasswordActivity::class.java)
            intent.putExtra("email", sessionManager.getUserEmail())
            
            // Pass the security question IDs and answers
            intent.putExtra("questionId1", securityQuestions[0].questionId)
            intent.putExtra("questionId2", securityQuestions[1].questionId)
            intent.putExtra("questionId3", securityQuestions[2].questionId)
            
            // Pass the actual correct answers, not user input (since we've verified they match)
            intent.putExtra("answer1", securityQuestions[0].answer)
            intent.putExtra("answer2", securityQuestions[1].answer)
            intent.putExtra("answer3", securityQuestions[2].answer)
            
            startActivity(intent)
            finish()
        } else {
            // Show error for incorrect answers
            Log.d(TAG, "One or more answers are incorrect")
            Toast.makeText(this, "One or more security answers are incorrect", Toast.LENGTH_LONG).show()
        }
    }
} 