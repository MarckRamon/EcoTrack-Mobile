package com.example.ecotrack

import android.app.Application
import com.google.firebase.FirebaseApp

class EcoTrackApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initializeFirebase()
    }

    private fun initializeFirebase() {
        try {
            // Initialize Firebase with default configuration
            FirebaseApp.initializeApp(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
} 