package com.example.ecotrack

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.ecotrack.databinding.ActivityRegisterBinding
import com.example.ecotrack.models.RegistrationRequest
import com.example.ecotrack.models.SecurityQuestion
import com.example.ecotrack.models.SecurityQuestionAnswer
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
    
    // User data to store between steps
    private var firstName = ""
    private var lastName = ""
    private var email = ""
    private var phoneNumber = ""
    private var password = ""
    
    // Security questions data
    private var securityQuestions: List<SecurityQuestion> = listOf()
    private var selectedQuestionIds = mutableListOf<String?>(null, null, null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager.getInstance(this)
        sessionManager.setCurrentActivity(this)

        // Load security questions from API
        loadSecurityQuestions()

        // First page - Next button
        binding.btnNext.setOnClickListener {
            if (validateFirstStep()) {
                saveFirstStepData()
                showSecurityQuestions()
            }
        }
        
        // Security Questions - Back button
        binding.btnBack.setOnClickListener {
            showUserInfoForm()
        }

        // Security Questions - Submit button
        binding.btnSubmit.setOnClickListener {
            if (validateSecurityQuestions()) {
                registerUser()
            }
        }

        binding.btnLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
        
        // Set up the question spinners
        setupQuestionSpinners()
    }

    override fun onResume() {
        super.onResume()
        sessionManager.setCurrentActivity(this)
    }

    override fun onPause() {
        super.onPause()
        sessionManager.setCurrentActivity(null)
    }
    
    private fun setupQuestionSpinners() {
        // We'll set click listeners after the adapters are set in loadSecurityQuestions()
        // This prevents crashes when clicking on spinners before data is loaded
    }
    
    private fun loadSecurityQuestions() {
        Log.d(TAG, "Starting to load security questions")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Making API call to get security questions")
                val response = apiService.getSecurityQuestions()
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful && response.body() != null) {
                        val securityQuestionsResponse = response.body()!!
                        securityQuestions = securityQuestionsResponse.questions ?: emptyList()
                        Log.d(TAG, "Security questions loaded successfully: ${securityQuestions.size} questions")
                        Log.d(TAG, "Raw security questions data: $securityQuestions")
                        
                        if (securityQuestions.isEmpty()) {
                            Log.e(TAG, "No security questions received from API")
                            Toast.makeText(
                                this@RegisterActivity,
                                "Could not load security questions. Please try again later.",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@withContext
                        }
                        
                        Log.d(TAG, "Question IDs: ${securityQuestions.map { it.id }}")
                        
                        // Populate the dropdown menus
                        val questionTexts = securityQuestions
                            .mapNotNull { if (it.questionText.isNullOrBlank()) null else it.questionText }
                            .toList()
                        Log.d(TAG, "Filtered question texts (${questionTexts.size}): $questionTexts")

                        if (questionTexts.isEmpty()) {
                            Log.e(TAG, "No valid security questions found")
                            Toast.makeText(
                                this@RegisterActivity,
                                "Could not load security questions. Please try again later.",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@withContext
                        }
                        
                        // Create a list of non-null strings for safety
                        val safeQuestionTexts = questionTexts.map { it ?: "Unknown Question" }.toList()
                        
                        // Create custom adapters that safely handle null values
                        val adapter1 = object : ArrayAdapter<String>(
                            this@RegisterActivity,
                            android.R.layout.simple_dropdown_item_1line,
                            safeQuestionTexts
                        ) {
                            override fun getItem(position: Int): String {
                                return if (position >= 0 && position < safeQuestionTexts.size) {
                                    safeQuestionTexts[position]
                                } else {
                                    "Unknown Question"
                                }
                            }
                        }
                        binding.question1Spinner.setAdapter(adapter1)
                        
                        val adapter2 = object : ArrayAdapter<String>(
                            this@RegisterActivity,
                            android.R.layout.simple_dropdown_item_1line,
                            safeQuestionTexts
                        ) {
                            override fun getItem(position: Int): String {
                                return if (position >= 0 && position < safeQuestionTexts.size) {
                                    safeQuestionTexts[position]
                                } else {
                                    "Unknown Question"
                                }
                            }
                        }
                        binding.question2Spinner.setAdapter(adapter2)
                        
                        val adapter3 = object : ArrayAdapter<String>(
                            this@RegisterActivity,
                            android.R.layout.simple_dropdown_item_1line,
                            safeQuestionTexts
                        ) {
                            override fun getItem(position: Int): String {
                                return if (position >= 0 && position < safeQuestionTexts.size) {
                                    safeQuestionTexts[position]
                                } else {
                                    "Unknown Question"
                                }
                            }
                        }
                        binding.question3Spinner.setAdapter(adapter3)
                        
                        // Set up the item click listeners after adapters are set
                        binding.question1Spinner.setOnItemClickListener { _, _, position, _ ->
                            try {
                                Log.d(TAG, "Question 1 clicked, position: $position, text: ${binding.question1Spinner.text}")
                                if (position >= 0 && position < safeQuestionTexts.size) {
                                    val selectedText = safeQuestionTexts[position]
                                    // Find the corresponding question in the original list
                                    val questionIndex = securityQuestions.indexOfFirst { 
                                        !it.questionText.isNullOrBlank() && it.questionText == selectedText 
                                    }
                                    
                                    if (questionIndex >= 0) {
                                        selectedQuestionIds[0] = securityQuestions[questionIndex].id
                                        Log.d(TAG, "Selected question 1: $selectedText with ID: ${selectedQuestionIds[0]}")
                                    } else {
                                        Log.e(TAG, "Could not find question with text: $selectedText")
                                    }
                                } else {
                                    Log.e(TAG, "Invalid position for question 1: $position")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error selecting question 1", e)
                            }
                        }
                        
                        binding.question2Spinner.setOnItemClickListener { _, _, position, _ ->
                            try {
                                Log.d(TAG, "Question 2 clicked, position: $position, text: ${binding.question2Spinner.text}")
                                if (position >= 0 && position < safeQuestionTexts.size) {
                                    val selectedText = safeQuestionTexts[position]
                                    // Find the corresponding question in the original list
                                    val questionIndex = securityQuestions.indexOfFirst { 
                                        !it.questionText.isNullOrBlank() && it.questionText == selectedText 
                                    }
                                    
                                    if (questionIndex >= 0) {
                                        selectedQuestionIds[1] = securityQuestions[questionIndex].id
                                        Log.d(TAG, "Selected question 2: $selectedText with ID: ${selectedQuestionIds[1]}")
                                    } else {
                                        Log.e(TAG, "Could not find question with text: $selectedText")
                                    }
                                } else {
                                    Log.e(TAG, "Invalid position for question 2: $position")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error selecting question 2", e)
                            }
                        }
                        
                        binding.question3Spinner.setOnItemClickListener { _, _, position, _ ->
                            try {
                                Log.d(TAG, "Question 3 clicked, position: $position, text: ${binding.question3Spinner.text}")
                                if (position >= 0 && position < safeQuestionTexts.size) {
                                    val selectedText = safeQuestionTexts[position]
                                    // Find the corresponding question in the original list
                                    val questionIndex = securityQuestions.indexOfFirst { 
                                        !it.questionText.isNullOrBlank() && it.questionText == selectedText 
                                    }
                                    
                                    if (questionIndex >= 0) {
                                        selectedQuestionIds[2] = securityQuestions[questionIndex].id
                                        Log.d(TAG, "Selected question 3: $selectedText with ID: ${selectedQuestionIds[2]}")
                                    } else {
                                        Log.e(TAG, "Could not find question with text: $selectedText")
                                    }
                                } else {
                                    Log.e(TAG, "Invalid position for question 3: $position")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error selecting question 3", e)
                            }
                        }
                    } else {
                        Log.e(TAG, "Failed to load security questions: ${response.errorBody()?.string()}")
                        Log.e(TAG, "Response code: ${response.code()}")
                        Log.e(TAG, "Response message: ${response.message()}")
                        Toast.makeText(
                            this@RegisterActivity,
                            "Failed to load security questions. Using default questions.",
                            Toast.LENGTH_LONG
                        ).show()
                        // Use hardcoded questions as fallback
                        loadHardcodedSecurityQuestions()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading security questions", e)
                Log.e(TAG, "Error message: ${e.message}")
                Log.e(TAG, "Stack trace: ${e.stackTraceToString()}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@RegisterActivity,
                        "Error loading security questions. Using default questions.",
                        Toast.LENGTH_SHORT
                    ).show()
                    
                    // Use hardcoded security questions as fallback
                    loadHardcodedSecurityQuestions()
                }
            }
        }
    }

    private fun validateFirstStep(): Boolean {
        val firstName = binding.etFirstName.text.toString().trim()
        val lastName = binding.etLastName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val phoneNumber = binding.etPhone.text.toString().trim()
        val password = binding.etPassword.text.toString()
        val confirmPassword = binding.etConfirmPassword.text.toString()

        if (firstName.isEmpty() || lastName.isEmpty() || email.isEmpty() || 
            phoneNumber.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            return false
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show()
            return false
        }
        
        // Validate phone number format (09XXXXXXXXX) - 11 digits total
        val phoneRegex = Regex("^09\\d{9}$")
        if (!phoneRegex.matches(phoneNumber)) {
            Toast.makeText(this, "Please enter a valid phone number in the format 09XXXXXXXXX", Toast.LENGTH_SHORT).show()
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
    
    private fun saveFirstStepData() {
        firstName = binding.etFirstName.text.toString().trim()
        lastName = binding.etLastName.text.toString().trim()
        email = binding.etEmail.text.toString().trim()
        phoneNumber = binding.etPhone.text.toString().trim()
        password = binding.etPassword.text.toString()
    }
    
    private fun showSecurityQuestions() {
        binding.userInfoContainer.visibility = View.GONE
        binding.securityQuestionsContainer.visibility = View.VISIBLE
    }
    
    private fun showUserInfoForm() {
        binding.securityQuestionsContainer.visibility = View.GONE
        binding.userInfoContainer.visibility = View.VISIBLE
    }
    
    private fun validateSecurityQuestions(): Boolean {
        val answer1 = binding.etAnswer1.text.toString().trim()
        val answer2 = binding.etAnswer2.text.toString().trim()
        val answer3 = binding.etAnswer3.text.toString().trim()
        
        // Check if all questions are selected
        if (selectedQuestionIds[0].isNullOrEmpty() || 
            selectedQuestionIds[1].isNullOrEmpty() || 
            selectedQuestionIds[2].isNullOrEmpty()) {
            
            Log.e(TAG, "Validation failed - missing question IDs: ${selectedQuestionIds}")
            Toast.makeText(this, "Please select all security questions", Toast.LENGTH_SHORT).show()
            return false
        }
        
        // Check for duplicate questions
        val uniqueQuestionIds = selectedQuestionIds.filterNotNull().filter { it.isNotEmpty() }.toSet()
        if (uniqueQuestionIds.size < 3) {
            Log.e(TAG, "Validation failed - duplicate questions: ${selectedQuestionIds}")
            Toast.makeText(this, "Please select different questions for each field", Toast.LENGTH_SHORT).show()
            return false
        }
        
        // Check if all answers are provided
        if (answer1.isEmpty() || answer2.isEmpty() || answer3.isEmpty()) {
            Log.e(TAG, "Validation failed - missing answers")
            Toast.makeText(this, "Please provide an answer for each question", Toast.LENGTH_SHORT).show()
            return false
        }
        
        Log.d(TAG, "Security questions validation successful")
        return true
    }

    private fun registerUser() {
        showLoading(true)
        
        // Create security question answers
        val securityQuestionAnswers = mutableListOf<SecurityQuestionAnswer>()
        
        // Log the selected question IDs for debugging
        Log.d(TAG, "Registration - Selected Question IDs: ${selectedQuestionIds}")
        
        securityQuestionAnswers.add(SecurityQuestionAnswer(
            questionId = selectedQuestionIds[0]!!,
            answer = binding.etAnswer1.text.toString().trim()
        ))
        
        securityQuestionAnswers.add(SecurityQuestionAnswer(
            questionId = selectedQuestionIds[1]!!,
            answer = binding.etAnswer2.text.toString().trim()
        ))
        
        securityQuestionAnswers.add(SecurityQuestionAnswer(
            questionId = selectedQuestionIds[2]!!,
            answer = binding.etAnswer3.text.toString().trim()
        ))
        
        // Log the final security question answers for debugging
        Log.d(TAG, "Registration - Security Question Answers: $securityQuestionAnswers")
        
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
                    phoneNumber = phoneNumber,
                    role = "customer", // Set role as customer for all registrations
                    securityQuestions = securityQuestionAnswers
                )
                
                try {
                    val response = apiService.register(registrationRequest)
                    withContext(Dispatchers.Main) {
                        if (response.isSuccessful) {
                            Log.d(TAG, "Registration successful")
                            
                            // Always navigate to login page instead of auto-login
                            Toast.makeText(this@RegisterActivity, "Registration successful! Please login.", Toast.LENGTH_LONG).show()
                            navigateToLogin()
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
    
    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        startActivity(intent)
        finish()
    }
    
    private fun showLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnNext.isEnabled = !isLoading && binding.userInfoContainer.visibility == View.VISIBLE
        binding.btnSubmit.isEnabled = !isLoading && binding.securityQuestionsContainer.visibility == View.VISIBLE
    }

    private fun findQuestionIdByText(questionText: String): String? {
        if (questionText.isBlank()) return null
        
        return securityQuestions.find { !it.questionText.isNullOrBlank() && it.questionText == questionText }?.id
    }

    private fun loadHardcodedSecurityQuestions() {
        Log.d(TAG, "Loading hardcoded security questions")
        securityQuestions = listOf(
            SecurityQuestion(
                id = "MOTHERS_MAIDEN_NAME",
                questionText = "What is your mother's maiden name?"
            ),
            SecurityQuestion(
                id = "BIRTH_CITY",
                questionText = "In what city were you born?"
            ),
            SecurityQuestion(
                id = "FAVORITE_COLOR",
                questionText = "What is your favorite color?"
            ),
            SecurityQuestion(
                id = "FIRST_PET",
                questionText = "What was the name of your first pet?"
            ),
            SecurityQuestion(
                id = "CHILDHOOD_NICKNAME",
                questionText = "What was your childhood nickname?"
            )
        )
        
        // Populate the dropdown menus
        val questionTexts = securityQuestions.map { it.questionText ?: "Unknown Question" }
        Log.d(TAG, "Hardcoded question texts: $questionTexts")
        
        val adapter1 = object : ArrayAdapter<String>(
            this,
            android.R.layout.simple_dropdown_item_1line,
            questionTexts
        ) {
            override fun getItem(position: Int): String {
                return if (position >= 0 && position < questionTexts.size) {
                    questionTexts[position]
                } else {
                    "Unknown Question"
                }
            }
        }
        binding.question1Spinner.setAdapter(adapter1)
        
        val adapter2 = object : ArrayAdapter<String>(
            this,
            android.R.layout.simple_dropdown_item_1line,
            questionTexts
        ) {
            override fun getItem(position: Int): String {
                return if (position >= 0 && position < questionTexts.size) {
                    questionTexts[position]
                } else {
                    "Unknown Question"
                }
            }
        }
        binding.question2Spinner.setAdapter(adapter2)
        
        val adapter3 = object : ArrayAdapter<String>(
            this,
            android.R.layout.simple_dropdown_item_1line,
            questionTexts
        ) {
            override fun getItem(position: Int): String {
                return if (position >= 0 && position < questionTexts.size) {
                    questionTexts[position]
                } else {
                    "Unknown Question"
                }
            }
        }
        binding.question3Spinner.setAdapter(adapter3)
        
        // Set up listeners
        setupQuestionSpinnerListeners(questionTexts)
    }
    
    private fun setupQuestionSpinnerListeners(questionTexts: List<String>) {
        binding.question1Spinner.setOnItemClickListener { _, _, position, _ ->
            try {
                Log.d(TAG, "Question 1 clicked, position: $position")
                if (position >= 0 && position < securityQuestions.size) {
                    selectedQuestionIds[0] = securityQuestions[position].id
                    Log.d(TAG, "Selected question 1 ID: ${selectedQuestionIds[0]}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error selecting question 1", e)
            }
        }
        
        binding.question2Spinner.setOnItemClickListener { _, _, position, _ ->
            try {
                Log.d(TAG, "Question 2 clicked, position: $position")
                if (position >= 0 && position < securityQuestions.size) {
                    selectedQuestionIds[1] = securityQuestions[position].id
                    Log.d(TAG, "Selected question 2 ID: ${selectedQuestionIds[1]}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error selecting question 2", e)
            }
        }
        
        binding.question3Spinner.setOnItemClickListener { _, _, position, _ ->
            try {
                Log.d(TAG, "Question 3 clicked, position: $position")
                if (position >= 0 && position < securityQuestions.size) {
                    selectedQuestionIds[2] = securityQuestions[position].id
                    Log.d(TAG, "Selected question 3 ID: ${selectedQuestionIds[2]}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error selecting question 3", e)
            }
        }
    }
}