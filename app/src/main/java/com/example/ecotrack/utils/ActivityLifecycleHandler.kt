package com.example.ecotrack.utils

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log

/**
 * Activity lifecycle callback handler that tracks the current foreground activity
 */
class ActivityLifecycleHandler(private val sessionManager: SessionManager) : Application.ActivityLifecycleCallbacks {
    private val TAG = "ActivityLifecycleHandler"
    
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        Log.d(TAG, "Activity created: ${activity.javaClass.simpleName}")
    }

    override fun onActivityStarted(activity: Activity) {
        Log.d(TAG, "Activity started: ${activity.javaClass.simpleName}")
    }

    override fun onActivityResumed(activity: Activity) {
        Log.d(TAG, "Activity resumed: ${activity.javaClass.simpleName}")
        sessionManager.setCurrentActivity(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        Log.d(TAG, "Activity paused: ${activity.javaClass.simpleName}")
        // Don't clear current activity yet, as another activity might be coming to the foreground
    }

    override fun onActivityStopped(activity: Activity) {
        Log.d(TAG, "Activity stopped: ${activity.javaClass.simpleName}")
        // Only clear if this is the current activity
        if (activity == sessionManager.getCurrentActivity()) {
            sessionManager.setCurrentActivity(null)
        }
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        // Not needed for tracking
    }

    override fun onActivityDestroyed(activity: Activity) {
        Log.d(TAG, "Activity destroyed: ${activity.javaClass.simpleName}")
        // Only clear if this is the current activity
        if (activity == sessionManager.getCurrentActivity()) {
            sessionManager.setCurrentActivity(null)
        }
    }
} 