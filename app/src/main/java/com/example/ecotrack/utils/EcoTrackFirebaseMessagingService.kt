package com.example.ecotrack.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.ecotrack.LoginActivity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicInteger

class EcoTrackFirebaseMessagingService : FirebaseMessagingService() {
    private val TAG = "FirebaseMsgService"
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val retryCount = AtomicInteger(0)
    private val MAX_RETRIES = 3
    
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token received: $token")
        
        // Store the token for later use
        val sessionManager = SessionManager.getInstance(applicationContext)
        sessionManager.saveFcmToken(token)
        
        // If the user is logged in, send the token to the server with retry mechanism
        if (sessionManager.isLoggedIn()) {
            registerTokenWithRetry(token)
        }
    }
    
    private fun registerTokenWithRetry(token: String) {
        retryCount.set(0)
        attemptRegistration(token)
    }
    
    private fun attemptRegistration(token: String) {
        val sessionManager = SessionManager.getInstance(applicationContext)
        
        if (!sessionManager.isLoggedIn()) {
            Log.d(TAG, "User not logged in, skipping FCM token registration")
            return
        }
        
        serviceScope.launch {
            try {
                Log.d(TAG, "Attempting FCM token registration (attempt ${retryCount.get() + 1}/$MAX_RETRIES)")
                
                val authToken = sessionManager.getToken()
                if (authToken == null) {
                    Log.e(TAG, "No auth token available, cannot register FCM token")
                    return@launch
                }
                
                val bearerToken = if (authToken.startsWith("Bearer ")) authToken else "Bearer $authToken"
                val fcmTokenRequest = com.example.ecotrack.models.FcmTokenRequest(token)
                val apiService = ApiService.create()
                
                try {
                    Log.d(TAG, "Sending FCM token to server: $token")
                    val response = apiService.registerFcmToken(fcmTokenRequest, bearerToken)
                    
                    if (response.isSuccessful) {
                        Log.d(TAG, "FCM token registered successfully with server")
                        // Registration successful, no need to retry
                        return@launch
                    } else {
                        Log.e(TAG, "Failed to register FCM token: ${response.code()} - ${response.message()}")
                        
                        try {
                            val errorBody = response.errorBody()?.string()
                            Log.e(TAG, "Error response body: $errorBody")
                            
                            // Try alternative method if 500 error
                            if (response.code() == 500) {
                                Log.d(TAG, "Trying alternative approach for FCM token registration")
                                val requestBody = mapOf("fcmToken" to token)
                                val altResponse = apiService.registerFcmTokenRaw(requestBody, bearerToken)
                                
                                if (altResponse.isSuccessful) {
                                    Log.d(TAG, "Alternative FCM token registration successful")
                                    return@launch
                                } else {
                                    Log.e(TAG, "Alternative FCM token registration failed: ${altResponse.code()}")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Could not read error body", e)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error during FCM token registration", e)
                }
                
                // If we're still logged in and have retries left, try again with exponential backoff
                if (sessionManager.isLoggedIn() && retryCount.incrementAndGet() < MAX_RETRIES) {
                    val delayTime = (1000L * Math.pow(2.0, retryCount.get().toDouble())).toLong()
                    Log.d(TAG, "Scheduling retry in $delayTime ms")
                    delay(delayTime)
                    attemptRegistration(token)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during FCM token registration retry", e)
            }
        }
    }
    
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "From: ${remoteMessage.from}")
        
        // Check if notifications are enabled
        val sessionManager = SessionManager.getInstance(applicationContext)
        if (!sessionManager.getNotificationPreference()) {
            Log.d(TAG, "Notifications are disabled, not showing notification")
            return
        }
        
        // Get message details
        val title = remoteMessage.notification?.title ?: "EcoTrack"
        val body = remoteMessage.notification?.body ?: "You have a new notification"
        Log.d(TAG, "Message Notification Title: $title")
        Log.d(TAG, "Message Notification Body: $body")
        
        // Generate a unique notification ID
        val notificationId = System.currentTimeMillis().toInt()
        
        // Create a pending intent for when notification is tapped
        val intent = Intent(this, LoginActivity::class.java)  // Or your main activity
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        
        // Add any data from the message to the intent
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Message Data: ${remoteMessage.data}")
            remoteMessage.data.forEach { (key, value) ->
                intent.putExtra(key, value)
            }
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) 
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT 
            else 
                PendingIntent.FLAG_ONE_SHOT
        )
        
        // Build the notification
        val channelId = "ecotrack_channel"
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Default icon if custom is not available
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        
        // Show the notification
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Create the notification channel (required for Android O and above)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "EcoTrack Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications from EcoTrack"
                enableLights(true)
                lightColor = Color.GREEN
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created")
        }
        
        notificationManager.notify(notificationId, notificationBuilder.build())
        Log.d(TAG, "Notification displayed with ID: $notificationId")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
} 