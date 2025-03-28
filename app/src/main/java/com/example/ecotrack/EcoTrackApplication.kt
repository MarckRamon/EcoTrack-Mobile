package com.example.ecotrack

import android.app.Application
import com.google.firebase.FirebaseApp
import android.util.Log

class EcoTrackApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            FirebaseApp.initializeApp(this)
            Log.d("EcoTrackApplication", "Firebase initialized successfully")
        } catch (e: Exception) {
            Log.e("EcoTrackApplication", "Failed to initialize Firebase: ${e.message}")
            e.printStackTrace()
        }
    }
} 