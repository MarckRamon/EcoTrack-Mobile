package com.example.grabtrash

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.grabtrash.utils.SessionManager

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Initialize SessionManager with application context
        SessionManager.getInstance(applicationContext)
        
        // Navigate to login or home based on auth state
        val sessionManager = SessionManager.getInstance(this)
        if (sessionManager.isLoggedIn()) {
            startActivity(android.content.Intent(this, HomeActivity::class.java))
        } else {
            startActivity(android.content.Intent(this, LoginActivity::class.java))
        }
        finish()
    }
}