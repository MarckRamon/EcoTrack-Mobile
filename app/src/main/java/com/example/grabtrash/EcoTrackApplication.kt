package com.example.grabtrash

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.util.Log
import com.google.firebase.FirebaseApp
import com.example.grabtrash.utils.ActivityLifecycleHandler
import com.example.grabtrash.utils.SessionManager
import com.google.firebase.messaging.FirebaseMessaging

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
        
        // Create notification channel for FCM
        createNotificationChannel()
        
        // Initialize Firebase
        try {
            FirebaseApp.initializeApp(this)
            Log.d("EcoTrackApplication", "Firebase initialized successfully")
            
            // Request FCM token
            FirebaseMessaging.getInstance().token
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val token = task.result
                        Log.d("EcoTrackApplication", "FCM token: $token")
                        sessionManager.saveFcmToken(token)
                    } else {
                        Log.e("EcoTrackApplication", "Failed to get FCM token", task.exception)
                    }
                }
        } catch (e: Exception) {
            Log.e("EcoTrackApplication", "Failed to initialize Firebase: ${e.message}")
            e.printStackTrace()
        }
        
        Log.d("EcoTrackApplication", "Application initialized")
    }
    
    private fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "ecotrack_channel"
            val name = "EcoTrack Notifications"
            val descriptionText = "Notifications from EcoTrack"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
                enableLights(true)
                lightColor = Color.GREEN
                enableVibration(true)
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            
            Log.d("EcoTrackApplication", "Notification channel created")
        }
    }
} 