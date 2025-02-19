package com.example.ecotrack

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class RegisterActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        supportActionBar?.hide()

        val fullName = findViewById<TextInputEditText>(R.id.et_full_name)
        val email = findViewById<TextInputEditText>(R.id.et_email)
        val password = findViewById<TextInputEditText>(R.id.et_password)
        val confirmPassword = findViewById<TextInputEditText>(R.id.et_confirm_password)
        val btnRegister = findViewById<MaterialButton>(R.id.btn_register)
        val btnLogin = findViewById<MaterialButton>(R.id.btn_login)

        btnRegister.setOnClickListener {
            when {
                fullName.text.toString().isEmpty() -> {
                    showToast("Please enter your full name")
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
                    // Here you would typically implement your registration logic
                    showToast("Registration Successful")
                    finish() // Return to Login
                }
            }
        }

        btnLogin.setOnClickListener {
            finish() // Return to Login screen
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}