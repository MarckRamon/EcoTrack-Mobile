package com.example.ecotrack.utils

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import java.io.IOException

/**
 * This class contains utility functions related to network operations
 */
class NetworkUtils {
    companion object {
        private const val TAG = "NetworkUtils"
        
        /**
         * This interceptor handles chunked transfer encoding issues that might cause EOFExceptions
         */
        class ChunkedTransferFixInterceptor : Interceptor {
            @Throws(IOException::class)
            override fun intercept(chain: Interceptor.Chain): Response {
                val request = chain.request()
                val response: Response
                
                try {
                    response = chain.proceed(request)
                } catch (e: IOException) {
                    Log.e(TAG, "Error in network request", e)
                    throw e
                }
                
                // Check for error responses first
                if (!response.isSuccessful) {
                    Log.w(TAG, "Response not successful: ${response.code}")
                    
                    // For 500 errors, we need special handling to avoid EOFException
                    if (response.code == 500) {
                        val originalBody = response.body
                        if (originalBody != null) {
                            try {
                                // Create a safe copy of the response body
                                val contentType = originalBody.contentType()
                                val emptyBody = ResponseBody.create(contentType, byteArrayOf())
                                
                                // Return a modified response with an empty but valid body
                                return response.newBuilder()
                                    .body(emptyBody)
                                    .build()
                            } catch (e: Exception) {
                                Log.e(TAG, "Error handling 500 response", e)
                            }
                        }
                    }
                    return response
                }
                
                // If the response body is chunked or has potential issues, handle it carefully
                if (response.header("Transfer-Encoding") == "chunked") {
                    try {
                        val originalBody = response.body
                        if (originalBody != null) {
                            val contentType = originalBody.contentType()
                            var bodyString: String? = null
                            
                            try {
                                // Try to safely read the body as a string
                                bodyString = originalBody.string()
                            } catch (e: IOException) {
                                Log.e(TAG, "Error reading chunked response body", e)
                                // Return an empty but valid body
                                val emptyBody = ResponseBody.create(contentType, "")
                                return response.newBuilder()
                                    .removeHeader("Transfer-Encoding")
                                    .body(emptyBody)
                                    .build()
                            }
                            
                            // Create a new response with a non-chunked body
                            val newBody = ResponseBody.create(contentType, bodyString ?: "")
                            return response.newBuilder()
                                .removeHeader("Transfer-Encoding")
                                .header("Content-Length", newBody.contentLength().toString())
                                .body(newBody)
                                .build()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error handling chunked response", e)
                    }
                }
                
                return response
            }
        }
    }
} 