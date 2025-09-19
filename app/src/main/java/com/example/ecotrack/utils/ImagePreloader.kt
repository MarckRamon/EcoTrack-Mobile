package com.example.ecotrack.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Global image preloader for instant image display
 */
class ImagePreloader(private val context: Context) {
    
    private val profileImageLoader = ProfileImageLoader(context)
    private val sessionManager = SessionManager.getInstance(context)
    private val TAG = "ImagePreloader"
    
    /**
     * Preload profile image on app startup
     */
    fun preloadProfileImage() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val cachedUrl = sessionManager.getProfileImageUrl()
                if (!cachedUrl.isNullOrBlank()) {
                    Log.d(TAG, "Preloading profile image: $cachedUrl")
                    profileImageLoader.preloadProfileImage(cachedUrl)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error preloading profile image", e)
            }
        }
    }
    
    /**
     * Preload multiple images
     */
    fun preloadImages(urls: List<String>) {
        CoroutineScope(Dispatchers.IO).launch {
            urls.forEach { url ->
                if (!url.isNullOrBlank()) {
                    try {
                        profileImageLoader.preloadProfileImage(url)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error preloading image: $url", e)
                    }
                }
            }
        }
    }
}
