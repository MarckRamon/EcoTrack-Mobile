package com.example.ecotrack

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.firestore.FirebaseFirestore
import com.example.ecotrack.models.User
import com.example.ecotrack.utils.PasswordUtils
import com.example.ecotrack.utils.DateUtils
import android.widget.TextView

class RegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        // Initialize Firebase Auth and Firestore
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        supportActionBar?.hide()

        val username = findViewById<TextInputEditText>(R.id.et_username)
        val firstName = findViewById<TextInputEditText>(R.id.et_first_name)
        val lastName = findViewById<TextInputEditText>(R.id.et_last_name)
        val email = findViewById<TextInputEditText>(R.id.et_email)
        val password = findViewById<TextInputEditText>(R.id.et_password)
        val confirmPassword = findViewById<TextInputEditText>(R.id.et_confirm_password)
        val btnRegister = findViewById<MaterialButton>(R.id.btn_register)
        val btnLogin = findViewById<TextView>(R.id.btn_login)

        btnRegister.setOnClickListener {
            when {
                username.text.toString().isEmpty() -> {
                    showToast("Please enter a username")
                }
                firstName.text.toString().isEmpty() -> {
                    showToast("Please enter your first name")
                }
                lastName.text.toString().isEmpty() -> {
                    showToast("Please enter your last name")
                }
                email.text.toString().isEmpty() -> {
                    showToast("Please enter your email")
                }
                password.text.toString().isEmpty() -> {
                    showToast("Please enter a password")
                }
                confirmPassword.text.toString().isEmpty() -> {
                    showToast("Please confirm your password")
                }
                password.text.toString() != confirmPassword.text.toString() -> {
                    showToast("Passwords do not match")
                }
                else -> {
                    createAccount(
                        username.text.toString(),
                        firstName.text.toString(),
                        lastName.text.toString(),
                        email.text.toString(),
                        password.text.toString()
                    )
                }
            }
        }

        btnLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun createAccount(username: String, firstName: String, lastName: String, email: String, password: String) {
        // Show loading indicator
        // You might want to add a ProgressBar in your layout
        
        // Hash the password before creating the account
        val hashedPassword = PasswordUtils.hashPassword(password)
        
        auth.createUserWithEmailAndPassword(email, password)  // Original password for Firebase Auth
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val firebaseUser = auth.currentUser
                    firebaseUser?.let { user ->
                        val userData = User(
                            uid = user.uid,
                            username = username,
                            firstName = firstName,
                            lastName = lastName,
                            email = email,
                            password = hashedPassword,  // Store hashed password in Firestore
                            createdAt = DateUtils.getCurrentFormattedDateTime()  // Add timestamp
                        )

                        firestore.collection("users")
                            .document(user.uid)
                            .set(userData)
                            .addOnSuccessListener {
                                Log.d("RegisterActivity", "User data saved successfully")
                                Toast.makeText(this, "Registration successful!", Toast.LENGTH_SHORT).show()
                                val intent = Intent(this, LoginActivity::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                                startActivity(intent)
                                finish()
                            }
                            .addOnFailureListener { e ->
                                Log.e("RegisterActivity", "Error saving user data", e)
                                Toast.makeText(this, "Failed to save user data: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    }
                } else {
                    // Handle specific authentication errors
                    val errorMessage = when (task.exception) {
                        is FirebaseAuthWeakPasswordException -> "Password is too weak"
                        is FirebaseAuthInvalidCredentialsException -> "Invalid email format"
                        is FirebaseAuthUserCollisionException -> "This email is already registered"
                        else -> "Registration failed: ${task.exception?.message}"
                    }
                    Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}