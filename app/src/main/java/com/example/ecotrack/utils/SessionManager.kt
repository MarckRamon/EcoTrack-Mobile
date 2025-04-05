package com.example.ecotrack.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import androidx.fragment.app.FragmentActivity
import com.example.ecotrack.LoginActivity
import com.example.ecotrack.ui.SessionTimeoutDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SessionManager private constructor(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val editor: SharedPreferences.Editor = prefs.edit()
    private val appContext: Context = context.applicationContext
    private var tokenExpiryTimer: Handler? = null
    private var weakActivity: Activity? = null
    private val TAG = "SessionManager"
    private var sessionTimeoutJob: Job? = null
    private var sessionTimeoutDialogShown = false
    private var currentActivity: Activity? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    
    companion object {
        private const val PREF_NAME = "EcoTrackSessionPrefs"
        private const val TOKEN_KEY = "user_token"
        private const val USER_ID_KEY = "user_id"
        private const val USER_TYPE_KEY = "user_type"
        private const val TOKEN_EXPIRY = "token_expiry"
        private const val KEY_LAST_ACTIVITY = "last_activity"
        private const val SESSION_TIMEOUT = 30L // 5 seconds for testing
        
        // Keep a single instance to avoid multiple timers
        @Volatile
        private var instance: SessionManager? = null
        
        fun getInstance(context: Context): SessionManager {
            return instance ?: synchronized(this) {
                instance ?: SessionManager(context).also { instance = it }
            }
        }
    }
    
    // Current activity tracking
    fun getCurrentActivity(): Activity? {
        return currentActivity
    }
    
    fun setCurrentActivity(activity: Activity?) {
        this.currentActivity = activity
        Log.d(TAG, "Current activity set to: ${activity?.javaClass?.simpleName}")
        if (activity != null && !sessionTimeoutDialogShown) {
            resetSessionTimeout()
        }
    }
    
    fun saveToken(token: String) {
        editor.putString(TOKEN_KEY, token)
        
        // Calculate and store expiry time (30 minutes from now)
        val expiryTime = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(30)
        editor.putLong(TOKEN_EXPIRY, expiryTime)
        
        editor.apply()
        
        Log.d(TAG, "Token saved, expires in 30 minutes")
        startExpiryTimer()
        
        // Extract userId from token if possible
        try {
            val userId = extractUserIdFromToken(token)
            if (userId != null) {
                saveUserId(userId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract userId from token", e)
        }
    }
    
    fun getToken(): String? {
        return prefs.getString(TOKEN_KEY, null)
    }
    
    fun saveUserId(userId: String) {
        editor.putString(USER_ID_KEY, userId)
        editor.apply()
        Log.d(TAG, "UserId saved: $userId")
    }
    
    fun getUserId(): String? {
        return prefs.getString(USER_ID_KEY, null)
    }
    
    fun saveUserType(userType: String) {
        editor.putString(USER_TYPE_KEY, userType)
        editor.apply()
        Log.d(TAG, "User type saved: $userType")
    }
    
    fun getUserType(): String? {
        return prefs.getString(USER_TYPE_KEY, null)
    }
    
    fun isLoggedIn(): Boolean {
        val token = getToken()
        return token != null
    }
    
    fun logout() {
        Log.d(TAG, "Logging out user")
        cancelSessionTimeout()
        editor.clear()
        editor.apply()
        sessionTimeoutDialogShown = false
        stopExpiryTimer()
    }
    
    // Session timeout handling
    private fun cancelSessionTimeout() {
        Log.d(TAG, "Canceling session timeout timer")
        sessionTimeoutJob?.cancel()
        sessionTimeoutJob = null
    }
    
    private fun resetSessionTimeout() {
        if (sessionTimeoutDialogShown) {
            Log.d(TAG, "Session timeout dialog already shown, not resetting timer")
            return
        }
        
        cancelSessionTimeout()
        startSessionTimeout()
    }
    
    private fun startSessionTimeout() {
        if (!isLoggedIn()) {
            Log.d(TAG, "Not starting timer - user not logged in")
            return
        }
        
        Log.d(TAG, "Starting session timeout timer for $SESSION_TIMEOUT seconds")
        sessionTimeoutJob = CoroutineScope(Dispatchers.Main).launch {
            delay(TimeUnit.SECONDS.toMillis(SESSION_TIMEOUT))
            Log.d(TAG, "Session timeout reached")
            
            if (sessionTimeoutDialogShown) {
                Log.d(TAG, "Dialog already shown, not showing again")
                return@launch
            }
            
            // We'll logout after showing the dialog
            sessionTimeoutDialogShown = true
            showSessionTimeoutDialog()
        }
    }
    
    private fun showSessionTimeoutDialog() {
        Log.d(TAG, "Attempting to show session timeout dialog")
        
        // Run on the main thread
        mainHandler.post {
            try {
                // Use the current activity if available
                val activity = currentActivity
                if (activity != null && !activity.isFinishing && activity is FragmentActivity) {
                    try {
                        val fm = activity.supportFragmentManager
                        if (!fm.isDestroyed) {
                            // Check if dialog is already showing
                            val existingDialog = fm.findFragmentByTag(SessionTimeoutDialog.TAG)
                            if (existingDialog == null) {
                                val dialog = SessionTimeoutDialog.newInstance()
                                dialog.isCancelable = false
                                dialog.show(fm, SessionTimeoutDialog.TAG)
                                Log.d(TAG, "Dialog shown successfully on ${activity.javaClass.simpleName}")
                            } else {
                                Log.d(TAG, "Dialog already exists, not showing again")
                            }
                        } else {
                            Log.e(TAG, "FragmentManager is destroyed")
                            // Don't logout here, let the dialog handle it
                            navigateToLogin(activity)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error showing dialog", e)
                        logout()
                        navigateToLogin(activity)
                    }
                } else {
                    Log.e(TAG, "No valid activity to show dialog, activity=${activity?.javaClass?.simpleName}, finishing=${activity?.isFinishing}")
                    logout()
                    navigateToLoginWithContext()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception showing dialog", e)
                logout()
                navigateToLoginWithContext()
            }
        }
    }
    
    private fun navigateToLogin(activity: Activity) {
        try {
            Log.d(TAG, "Navigating to login screen")
            val intent = Intent(activity, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            activity.startActivity(intent)
            activity.finish()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to navigate to login", e)
            navigateToLoginWithContext()
        }
    }
    
    private fun navigateToLoginWithContext() {
        try {
            Log.d(TAG, "Navigating to login with application context")
            val intent = Intent(appContext, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            appContext.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to navigate to login with context", e)
        }
    }

    fun updateLastActivity() {
        if (!isLoggedIn()) {
            return
        }
        
        if (!sessionTimeoutDialogShown) {
            Log.d(TAG, "Updating last activity timestamp")
            editor.putLong(KEY_LAST_ACTIVITY, System.currentTimeMillis())
            editor.apply()
            resetSessionTimeout()
        } else {
            Log.d(TAG, "Session timeout dialog is shown, not updating activity")
        }
    }
    
    private fun extractUserIdFromToken(token: String): String? {
        try {
            val parts = token.split(".")
            if (parts.size == 3) {
                val payload = parts[1]
                val decodedBytes = Base64.decode(payload, Base64.URL_SAFE)
                val decodedString = String(decodedBytes)
                Log.d(TAG, "Decoded JWT payload: $decodedString")
                val jsonObject = JSONObject(decodedString)
                return if (jsonObject.has("userId")) {
                    val userId = jsonObject.getString("userId")
                    Log.d(TAG, "Extracted userId from token: $userId")
                    userId
                } else {
                    Log.w(TAG, "No userId field found in token payload")
                    null
                }
            } else {
                Log.w(TAG, "Invalid JWT format, expected 3 parts but got ${parts.size}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting userId from token", e)
        }
        return null
    }
    
    private fun startExpiryTimer() {
        // Stop any existing timer first
        stopExpiryTimer()
        
        val expiryTime = prefs.getLong(TOKEN_EXPIRY, 0)
        val currentTime = System.currentTimeMillis()
        
        if (expiryTime <= currentTime) {
            Log.d(TAG, "Token already expired, logging out")
            logout()
            return
        }
        
        // For testing, use 5 seconds timeout
        val delayMillis = SESSION_TIMEOUT * 1000
        
        // Create a new timer with delay
        tokenExpiryTimer = Handler(Looper.getMainLooper())
        tokenExpiryTimer?.postDelayed({
            Log.d(TAG, "Token expired, logging out")
            logout()
            showSessionExpiredDialog()
        }, delayMillis)
        
        Log.d(TAG, "Expiry timer started: ${delayMillis}ms")
    }
    
    private fun stopExpiryTimer() {
        tokenExpiryTimer?.removeCallbacksAndMessages(null)
        tokenExpiryTimer = null
        Log.d(TAG, "Expiry timer stopped")
    }
    
    private fun showSessionExpiredDialog() {
        try {
            Log.d(TAG, "Attempting to show session timeout dialog")
            val activity = currentActivity
            
            if (activity != null && !activity.isFinishing && activity is FragmentActivity) {
                Log.d(TAG, "Current activity available: ${activity.javaClass.simpleName}")
                
                // Create and show the dialog
                val dialog = SessionTimeoutDialog.newInstance()
                dialog.show(activity.supportFragmentManager, SessionTimeoutDialog.TAG)
                
                Log.d(TAG, "Session timeout dialog shown")
            } else {
                Log.d(TAG, "No valid activity to show dialog, redirecting to login")
                
                // If no activity is available or it's not a FragmentActivity, just start the login activity
                val intent = Intent(appContext, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                appContext.startActivity(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error showing session expired dialog", e)
        }
    }

    /**
     * Extracts the role field from the JWT token
     * @param token The JWT token to extract the role from
     * @return The role string or null if not found
     */
    fun extractRoleFromToken(token: String): String? {
        try {
            val parts = token.split(".")
            if (parts.size == 3) {
                val payload = parts[1]
                val decodedBytes = Base64.decode(payload, Base64.URL_SAFE)
                val decodedString = String(decodedBytes)
                Log.d(TAG, "Decoded JWT payload: $decodedString")
                
                val jsonObject = JSONObject(decodedString)
                return if (jsonObject.has("role")) {
                    val role = jsonObject.getString("role")
                    Log.d(TAG, "Extracted role from token: $role")
                    role
                } else {
                    Log.w(TAG, "No role field found in token payload")
                    null
                }
            } else {
                Log.w(TAG, "Invalid JWT format, expected 3 parts but got ${parts.size}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting role from token", e)
        }
        return null
    }
} 