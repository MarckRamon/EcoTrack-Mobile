package com.example.grabtrash.api

import com.example.grabtrash.models.xendit.CreateInvoiceRequest
import com.example.grabtrash.models.xendit.InvoiceResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Xendit API service interface
 * Documentation: https://developers.xendit.co/api-reference
 */
interface XenditApiService {
    
    /**
     * Creates an Invoice
     * An Invoice is a payment request that can be paid through various payment methods
     */
    @POST("v2/invoices")
    suspend fun createInvoice(
        @Header("Authorization") authorization: String,
        @Body requestBody: CreateInvoiceRequest
    ): Response<InvoiceResponse>
    
    companion object {
        const val BASE_URL = "https://api.xendit.co/"
        
        // Use your Xendit SECRET KEY here for server-to-server API calls
        const val API_KEY = "xnd_development_wJcSKLR5LqskWEwnm9Aei1N5CylOVltUyGwBDtaJS3cZxtlk6d40NZQXlyMdE"
        
        fun getAuthHeader(): String {
            val authHeader = "Basic ${android.util.Base64.encodeToString(
                "$API_KEY:".toByteArray(),
                android.util.Base64.NO_WRAP
            )}"
            android.util.Log.d("XenditApiService", "Auth Header: $authHeader")
            return authHeader
        }
    }
} 