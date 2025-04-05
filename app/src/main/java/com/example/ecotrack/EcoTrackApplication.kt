package com.example.ecotrack

import android.app.Application
import com.google.firebase.FirebaseApp
import android.util.Log
import com.example.ecotrack.utils.ActivityLifecycleHandler
import com.example.ecotrack.utils.SessionManager

class EcoTrackApplication : Application() {
    private lateinit var sessionManager: SessionManager
    private lateinit var activityLifecycleHandler: ActivityLifecycleHandler
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize the session manager with application context
        sessionManager = SessionManager.getInstance(applicationContext)
        
        // Register activity lifecycle callbacks to track current activity
        activityLifecycleHandler = ActivityLifecycleHandler(sessionManager)
        registerActivityLifecycleCallbacks(activityLifecycleHandler)
        
        // Initialize Firebase
        try {
            FirebaseApp.initializeApp(this)
            Log.d("EcoTrackApplication", "Firebase initialized successfully")
        } catch (e: Exception) {
            Log.e("EcoTrackApplication", "Failed to initialize Firebase: ${e.message}")
            e.printStackTrace()
        }
        
        Log.d("EcoTrackApplication", "Application initialized")
    }
} 