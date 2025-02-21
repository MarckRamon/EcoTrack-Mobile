package com.example.ecotrack

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator

class DriverLoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private var isUserMode = false
    private lateinit var userLoginText: TextView
    private lateinit var driverLoginText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_driver_login)

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        // This Line of code removes the title bar from the screen
        supportActionBar?.hide()

        // Initialize views - make sure to find them after setContentView
        val loginToggle = findViewById<View>(R.id.loginToggle)
        userLoginText = loginToggle.findViewById(R.id.userLoginText)
        driverLoginText = loginToggle.findViewById(R.id.driverLoginText)

        // Set initial state
        updateToggleState()

        // Set up click listeners for toggle
        userLoginText.setOnClickListener { switchToUserMode() }
        driverLoginText.setOnClickListener { switchToDriverMode() }

        val email = findViewById<EditText>(R.id.et_email)
        val password = findViewById<EditText>(R.id.et_password)
        val btnLogin = findViewById<Button>(R.id.btn_login)

        btnLogin.setOnClickListener {
            val emailText = email.text.toString()
            val passwordText = password.text.toString()

            if (emailText.isNotEmpty() && passwordText.isNotEmpty()) {
                signIn(emailText, passwordText)
            } else {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateToggleState() {
        userLoginText.setBackgroundColor(if (isUserMode) getColor(R.color.green) else Color.WHITE)
        userLoginText.setTextColor(if (isUserMode) Color.WHITE else getColor(R.color.green))
        driverLoginText.setBackgroundColor(if (!isUserMode) getColor(R.color.green) else Color.WHITE)
        driverLoginText.setTextColor(if (!isUserMode) Color.WHITE else getColor(R.color.green))
    }

    private fun switchToUserMode() {
        if (!isUserMode) {
            isUserMode = true
            animateToggle(true)
            // Switch to LoginActivity with reverse animation
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
            finish()
        }
    }

    private fun switchToDriverMode() {
        if (isUserMode) {
            isUserMode = false
            animateToggle(false)
            // No need to switch activities as we're already in DriverLoginActivity
        }
    }

    private fun animateToggle(toUserMode: Boolean) {
        val colorFrom = if (toUserMode) Color.WHITE else getColor(R.color.green)
        val colorTo = if (toUserMode) getColor(R.color.green) else Color.WHITE
        val textColorFrom = if (toUserMode) getColor(R.color.green) else Color.WHITE
        val textColorTo = if (toUserMode) Color.WHITE else getColor(R.color.green)

        // Background color animation for user text
        ValueAnimator.ofObject(ArgbEvaluator(), colorFrom, colorTo).apply {
            duration = 300
            addUpdateListener { animator ->
                userLoginText.setBackgroundColor(animator.animatedValue as Int)
            }
            start()
        }

        // Text color animation for user text
        ValueAnimator.ofObject(ArgbEvaluator(), textColorFrom, textColorTo).apply {
            duration = 300
            addUpdateListener { animator ->
                userLoginText.setTextColor(animator.animatedValue as Int)
            }
            start()
        }

        // Background color animation for driver text
        ValueAnimator.ofObject(ArgbEvaluator(), colorTo, colorFrom).apply {
            duration = 300
            addUpdateListener { animator ->
                driverLoginText.setBackgroundColor(animator.animatedValue as Int)
            }
            start()
        }

        // Text color animation for driver text
        ValueAnimator.ofObject(ArgbEvaluator(), textColorTo, textColorFrom).apply {
            duration = 300
            addUpdateListener { animator ->
                driverLoginText.setTextColor(animator.animatedValue as Int)
            }
            start()
        }
    }

    private fun signIn(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Sign in success, show success message and update UI
                    Toast.makeText(baseContext, "Driver login successful!", Toast.LENGTH_SHORT).show()
                    val user = auth.currentUser
                    updateUI(user)
                } else {
                    // If sign in fails, display a message to the user.
                    Toast.makeText(baseContext, "Authentication failed: ${task.exception?.message}",
                        Toast.LENGTH_SHORT).show()
                    updateUI(null)
                }
            }
    }

    private fun updateUI(user: FirebaseUser?) {
        if (user != null) {
            // Navigate to DriverHomeActivity (you'll need to create this)
            startActivity(Intent(this, HomeActivity::class.java))
            finish() // Close DriverLoginActivity
        }
    }

    public override fun onStart() {
        super.onStart()
        // Check if user is signed in (non-null) and update UI accordingly.
        val currentUser = auth.currentUser
        if(currentUser != null){
            updateUI(currentUser)
        }
    }
}