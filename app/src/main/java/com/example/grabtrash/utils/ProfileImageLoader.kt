package com.example.grabtrash.utils

import android.content.Context
import android.util.Log
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Ultra-fast profile image loader with aggressive caching and preloading
 */
class ProfileImageLoader(private val context: Context) {
    
    private val fileLuService = FileLuService(context)
    private val TAG = "ProfileImageLoader"
    
    // Cache for converted URLs to avoid repeated API calls
    private val urlCache = ConcurrentHashMap<String, String>()
    
    // Cache for preloaded images
    private val preloadedImages = ConcurrentHashMap<String, Boolean>()
    
    /**
     * Ultra-fast profile image loading with aggressive optimizations
     */
    fun loadProfileImageUltraFast(
        url: String,
        imageView: ImageView,
        placeholderResId: Int = android.R.drawable.ic_menu_gallery,
        errorResId: Int = android.R.drawable.ic_menu_gallery
    ) {
        // Check if we have a cached converted URL
        val cachedConvertedUrl = urlCache[url]
        
        if (cachedConvertedUrl != null) {
            // Use cached converted URL immediately
            loadImageDirectly(cachedConvertedUrl, imageView, placeholderResId, errorResId)
        } else if (url.contains("filelu.com") && !url.contains("cdnfinal.space")) {
            // FileLu URL - try to load from cache first, then convert
            loadImageDirectly(url, imageView, placeholderResId, errorResId)
            
            // Convert URL in background and cache it
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val displayUrl = fileLuService.convertToDisplayUrl(url)
                    if (displayUrl != null && displayUrl != url) {
                        // Cache the converted URL
                        urlCache[url] = displayUrl
                        
                        // Load the converted image
                        withContext(Dispatchers.Main) {
                            loadImageDirectly(displayUrl, imageView, placeholderResId, errorResId)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error converting profile image URL", e)
                }
            }
        } else {
            // Direct URL - load immediately
            loadImageDirectly(url, imageView, placeholderResId, errorResId)
        }
    }
    
    /**
     * Load image directly with maximum performance settings
     */
    private fun loadImageDirectly(
        url: String,
        imageView: ImageView,
        placeholderResId: Int,
        errorResId: Int
    ) {
        val requestOptions = RequestOptions()
            .placeholder(placeholderResId)
            .error(errorResId)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .skipMemoryCache(false) // Enable memory cache
            .centerCrop()
            .dontAnimate() // No animations for speed
            .timeout(5000) // 5 second timeout
            .encodeFormat(android.graphics.Bitmap.CompressFormat.JPEG) // Faster encoding
            .encodeQuality(85) // Balanced quality/speed
        
        Glide.with(context)
            .load(url)
            .apply(requestOptions)
            .into(imageView)
    }
    
    /**
     * Load profile image with thumbnail for instant display
     */
    fun loadProfileImageWithThumbnail(
        url: String,
        imageView: ImageView,
        placeholderResId: Int = android.R.drawable.ic_menu_gallery,
        errorResId: Int = android.R.drawable.ic_menu_gallery
    ) {
        // First load a small thumbnail for instant display
        val thumbnailOptions = RequestOptions()
            .placeholder(placeholderResId)
            .error(errorResId)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .skipMemoryCache(false)
            .centerCrop()
            .dontAnimate()
            .override(100, 100) // Small thumbnail size
        
        Glide.with(context)
            .load(url)
            .apply(thumbnailOptions)
            .into(imageView)
        
        // Then load the full resolution image
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val displayUrl = if (url.contains("filelu.com") && !url.contains("cdnfinal.space")) {
                    val cached = urlCache[url]
                    if (cached != null) {
                        cached
                    } else {
                        val converted = fileLuService.convertToDisplayUrl(url)
                        if (converted != null) {
                            urlCache[url] = converted
                            converted
                        } else {
                            url
                        }
                    }
                } else {
                    url
                }
                
                withContext(Dispatchers.Main) {
                    val fullOptions = RequestOptions()
                        .placeholder(placeholderResId)
                        .error(errorResId)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .skipMemoryCache(false)
                        .centerCrop()
                        .dontAnimate()
                    
                    Glide.with(context)
                        .load(displayUrl)
                        .apply(fullOptions)
                        .into(imageView)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading full resolution image", e)
            }
        }
    }
    
    /**
     * Preload profile image for instant display
     */
    fun preloadProfileImage(url: String) {
        if (preloadedImages[url] == true) return // Already preloaded
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val displayUrl = if (url.contains("filelu.com") && !url.contains("cdnfinal.space")) {
                    val cached = urlCache[url]
                    if (cached != null) {
                        cached
                    } else {
                        val converted = fileLuService.convertToDisplayUrl(url)
                        if (converted != null) {
                            urlCache[url] = converted
                            converted
                        } else {
                            url
                        }
                    }
                } else {
                    url
                }
                
                withContext(Dispatchers.Main) {
                    Glide.with(context)
                        .load(displayUrl)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .skipMemoryCache(false)
                        .preload()
                    
                    preloadedImages[url] = true
                    Log.d(TAG, "Preloaded image: $url")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error preloading profile image", e)
            }
        }
    }
    
    /**
     * Load proof image with enhanced debugging and error handling
     */
    fun loadProofImage(
        url: String,
        imageView: ImageView,
        placeholderResId: Int = android.R.drawable.ic_menu_camera,
        errorResId: Int = android.R.drawable.ic_menu_camera
    ) {
        Log.d(TAG, "Loading proof image: $url")
        
        // Check if this is a FileLu URL that needs conversion
        if (url.contains("filelu.com") && !url.contains("cdnfinal.space")) {
            Log.d(TAG, "Detected FileLu URL, converting...")
            // Try to load from cache first
            val cachedConvertedUrl = urlCache[url]
            if (cachedConvertedUrl != null) {
                Log.d(TAG, "Using cached converted URL: $cachedConvertedUrl")
                loadImageDirectly(cachedConvertedUrl, imageView, placeholderResId, errorResId)
            } else {
                // Load original URL first, then convert
                loadImageDirectly(url, imageView, placeholderResId, errorResId)
                
                // Convert URL in background
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val displayUrl = fileLuService.convertToDisplayUrl(url)
                        if (displayUrl != null && displayUrl != url) {
                            Log.d(TAG, "Converted FileLu URL: $url -> $displayUrl")
                            urlCache[url] = displayUrl
                            
                            withContext(Dispatchers.Main) {
                                loadImageDirectly(displayUrl, imageView, placeholderResId, errorResId)
                            }
                        } else {
                            Log.w(TAG, "URL conversion returned null or same URL")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error converting proof image URL", e)
                    }
                }
            }
        } else {
            Log.d(TAG, "Direct URL detected, loading immediately")
            loadImageDirectly(url, imageView, placeholderResId, errorResId)
        }
    }
    
    /**
     * Legacy method for backward compatibility
     */
    fun loadProfileImageOptimized(
        url: String,
        imageView: ImageView,
        placeholderResId: Int = android.R.drawable.ic_menu_gallery,
        errorResId: Int = android.R.drawable.ic_menu_gallery
    ) {
        loadProfileImageUltraFast(url, imageView, placeholderResId, errorResId)
    }
    
    /**
     * Clear all caches (useful for memory management)
     */
    fun clearCache() {
        urlCache.clear()
        preloadedImages.clear()
        Glide.get(context).clearMemory()
    }
    
    /**
     * Get cache size for debugging
     */
    fun getCacheSize(): Int {
        return urlCache.size + preloadedImages.size
    }
}