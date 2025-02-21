package com.example.ecotrack

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.android.material.button.MaterialButton

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private var isUserMode = true
    private lateinit var userLoginText: TextView
    private lateinit var driverLoginText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        // This Line of code removes the title bar from the screen
        supportActionBar?.hide()

        // Initialize views
        userLoginText = findViewById(R.id.userLoginText)
        driverLoginText = findViewById(R.id.driverLoginText)

        // Set up click listeners for toggle
        userLoginText.setOnClickListener { switchToUserMode() }
        driverLoginText.setOnClickListener { switchToDriverMode() }

        val email = findViewById<EditText>(R.id.et_email)
        val password = findViewById<EditText>(R.id.et_password)
        val btnLogin = findViewById<Button>(R.id.btn_login)
        val btnRegister = findViewById<Button>(R.id.btn_register)

        btnLogin.setOnClickListener {
            val emailText = email.text.toString()
            val passwordText = password.text.toString()

            if (emailText.isNotEmpty() && passwordText.isNotEmpty()) {
                signIn(emailText, passwordText)
            } else {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
            }
        }

        btnRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
    }

    private fun switchToUserMode() {
        if (!isUserMode) {
            isUserMode = true
            animateToggle(true)
            // No need to switch activities as we're already in LoginActivity
        }
    }

    private fun switchToDriverMode() {
        if (isUserMode) {
            isUserMode = false
            animateToggle(false)
            // Switch to DriverLoginActivity with animation
            val intent = Intent(this, DriverLoginActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            finish()
        }
    }

    private fun animateToggle(toUserMode: Boolean) {
        val colorFrom = if (toUserMode) Color.WHITE else getColor(R.color.purple_500)
        val colorTo = if (toUserMode) getColor(R.color.purple_500) else Color.WHITE
        val textColorFrom = if (toUserMode) getColor(R.color.purple_500) else Color.WHITE
        val textColorTo = if (toUserMode) Color.WHITE else getColor(R.color.purple_500)

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

    private fun updateToggleState() {
        userLoginText.setBackgroundColor(if (isUserMode) getColor(R.color.purple_500) else Color.WHITE)
        userLoginText.setTextColor(if (isUserMode) Color.WHITE else getColor(R.color.purple_500))
        driverLoginText.setBackgroundColor(if (!isUserMode) getColor(R.color.purple_500) else Color.WHITE)
        driverLoginText.setTextColor(if (!isUserMode) Color.WHITE else getColor(R.color.purple_500))
    }

    private fun signIn(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Toast.makeText(baseContext, "Login successful!", Toast.LENGTH_SHORT).show()
                    val user = auth.currentUser
                    if (isUserMode) {
                        startActivityWithAnimation(HomeActivity::class.java)
                    } else {
                        startActivityWithAnimation(DriverLoginActivity::class.java)
                    }
                } else {
                    Toast.makeText(baseContext, "Authentication failed: ${task.exception?.message}",
                        Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun startActivityWithAnimation(activityClass: Class<*>) {
        val intent = Intent(this, activityClass)
        startActivity(intent)
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        finish()
    }

    override fun onStart() {
        super.onStart()
        val currentUser = auth.currentUser
        if (currentUser != null) {
            startActivityWithAnimation(HomeActivity::class.java)
        }
    }
}