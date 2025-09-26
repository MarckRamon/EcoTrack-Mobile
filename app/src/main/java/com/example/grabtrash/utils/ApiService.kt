package com.example.grabtrash.utils

import android.util.Log
import com.example.grabtrash.models.*
import com.example.grabtrash.models.payment.Payment
import com.example.grabtrash.models.payment.PaymentRequest
import com.example.grabtrash.models.payment.PaymentResponse
import com.example.grabtrash.models.quote.QuoteRequest
import com.example.grabtrash.models.quote.QuoteResponse
import com.example.grabtrash.models.Barangay
import com.example.grabtrash.models.DeliveryStatusUpdate
import com.example.grabtrash.utils.NetworkUtils.Companion.ChunkedTransferFixInterceptor
import com.google.gson.GsonBuilder
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

interface ApiService {
    @POST("api/users/login")
    suspend fun login(@Body loginRequest: LoginRequest): Response<LoginResponse>

    @POST("api/users/register")
    suspend fun register(@Body registrationRequest: RegistrationRequest): Response<Map<String, Any>>

    @GET("api/users/profile/{userId}")
    suspend fun getProfile(
        @Path("userId") userId: String,
        @Header("Authorization") authToken: String
    ): Response<UserProfile>

    @PUT("api/users/profile/{userId}")
    suspend fun updateProfile(
        @Path("userId") userId: String,
        @Header("Authorization") authToken: String,
        @Body profileUpdateRequest: ProfileUpdateRequest
    ): Response<Map<String, String>>

    @PUT("api/users/profile/{userId}/password")
    suspend fun updatePassword(
        @Path("userId") userId: String,
        @Header("Authorization") authToken: String,
        @Body passwordUpdateRequest: PasswordUpdateRequest
    ): Response<Map<String, String>>

    @POST("api/users/forgot-password/reset")
    suspend fun resetPassword(@Body forgotPasswordRequest: ForgotPasswordRequest): Response<ResponseBody>

    @POST("api/users/forgot-password/question")
    suspend fun getSecurityQuestion(@Body requestBody: okhttp3.RequestBody): Response<ResponseBody>

    @GET("api/users/security-questions")
    suspend fun getSecurityQuestions(): Response<SecurityQuestionsResponse>

    @GET("users/{userId}/security-questions")
    suspend fun getUserSecurityQuestions(@Header("Authorization") token: String, @Path("userId") userId: String): Response<UserSecurityQuestionsResponse>

    @GET("api/users/profile/security-question")
    suspend fun getUserProfileSecurityQuestions(
        @Header("Authorization") token: String
    ): Response<UserSecurityQuestionsResponse>

    @GET("api/users/profile/security-questions")
    suspend fun getUserProfileSecurityQuestionsAlternative(
        @Header("Authorization") token: String
    ): Response<UserSecurityQuestionsResponse>

    @GET("api/pickup-locations")
    suspend fun getPickupSites(): Response<PickupLocationsResponse>

    @GET("api/pickup-locations/{id}")
    suspend fun getPickupSiteDetails(@Path("id") id: String): Response<PickupSite>

    // Trucks Endpoint
    @GET("api/trucks")
    suspend fun getTrucks(@Header("Authorization") authToken: String): Response<List<Truck>>

    // Collection Schedule Endpoints
    @GET("api/collection-schedules/barangay/{barangayId}")
    suspend fun getSchedulesByBarangay(
        @Path("barangayId") barangayId: String,
        @Header("Authorization") authToken: String
    ): Response<List<CollectionScheduleResponse>>

    @GET("api/collection-schedules/barangay/{barangayId}/upcoming")
    suspend fun getUpcomingSchedules(
        @Path("barangayId") barangayId: String,
        @Header("Authorization") authToken: String
    ): Response<List<CollectionScheduleResponse>>

    @GET("api/collection-schedules/barangay/{barangayId}/recurring")
    suspend fun getRecurringSchedules(
        @Path("barangayId") barangayId: String,
        @Header("Authorization") authToken: String
    ): Response<List<CollectionScheduleResponse>>

    // Missed Reports Endpoints
    @GET("api/missed-reports/user")
    suspend fun getMissedReports(
        @Header("Authorization") authToken: String
    ): Response<List<MissedReportResponse>>

    // Payment Endpoints
    @POST("api/payments/quote")
    suspend fun getQuote(
        @Body quoteRequest: QuoteRequest,
        @Header("Authorization") authToken: String
    ): Response<QuoteResponse>
    
    @POST("api/payments")
    suspend fun processPayment(
        @Body paymentRequest: PaymentRequest,
        @Header("Authorization") authToken: String
    ): Response<PaymentResponse>

    @GET("api/payments")
    suspend fun getAllPayments(): Response<List<PaymentResponse>>

    @GET("api/payments/{id}")
    suspend fun getPaymentById(
        @Path("id") id: String
    ): Response<PaymentResponse>

    @GET("api/payments/order/{orderId}")
    suspend fun getPaymentByOrderId(
        @Path("orderId") orderId: String,
        @Header("Authorization") authToken: String? = null
    ): Response<PaymentResponse>

    @GET("api/payments/customer")
    suspend fun getPaymentsByCustomerEmail(
        @Query("email") email: String,
        @Header("Authorization") authToken: String? = null
    ): Response<List<PaymentResponse>>

    @GET("api/payments/driver/{driverId}")
    suspend fun getPaymentsByDriverId(
        @Path("driverId") driverId: String,
        @Header("Authorization") authToken: String
    ): Response<List<Payment>>

    @POST("api/payments/{paymentId}/confirmation-image")
    suspend fun uploadPaymentConfirmationImage(
        @Path("paymentId") paymentId: String,
        @Body body: Map<String, String>,
        @Header("Authorization") authToken: String
    ): Response<Map<String, Any>>
    
    // Driver Job Order Status Update
    @PUT("api/driver/job/{paymentId}/status")
    suspend fun updateJobOrderStatus(
        @Path("paymentId") paymentId: String,
        @Body statusUpdate: JobOrderStatusUpdate,
        @Header("Authorization") authToken: String
    ): Response<Payment>

    // Customer Payment Job Order Status Update
    @PUT("api/payments/{paymentId}/job-order-status")
    suspend fun updatePaymentJobOrderStatus(
        @Path("paymentId") paymentId: String,
        @Body statusUpdate: JobOrderStatusUpdate,
        @Header("Authorization") authToken: String
    ): Response<Payment>

    // Update Payment Delivery Status
    @PUT("api/payments/{paymentId}/delivery-status")
    suspend fun updateDeliveryStatus(
        @Path("paymentId") paymentId: String,
        @Body deliveryStatus: DeliveryStatusUpdate,
        @Header("Authorization") authToken: String
    ): Response<Payment>

    // Barangay Endpoints
    @GET("api/barangays")
    suspend fun getAllBarangays(@Header("Authorization") authToken: String): Response<List<Barangay>>

    @GET("api/barangays/{barangayId}")
    suspend fun getBarangayById(
        @Path("barangayId") barangayId: String,
        @Header("Authorization") authToken: String
    ): Response<Barangay>

    @GET("api/payments/customer/{userId}/active")
    suspend fun getUserActiveOrders(
        @Path("userId") userId: String,
        @Header("Authorization") authToken: String
    ): Response<List<PaymentResponse>>
    
    // Private Entity Endpoints
    @GET("api/private-entities")
    suspend fun getAllPrivateEntities(
        @Header("Authorization") authToken: String
    ): Response<PrivateEntitiesResponse>
    
    @GET("api/private-entities/{userId}")
    suspend fun getPrivateEntity(
        @Path("userId") userId: String,
        @Header("Authorization") authToken: String
    ): Response<PrivateEntity>

    @POST("api/notifications/register-token")
    suspend fun registerFcmToken(
        @Body fcmTokenRequest: FcmTokenRequest,
        @Header("Authorization") authToken: String
    ): Response<Map<String, Any>>

    @POST("api/notifications/register-token")
    suspend fun registerFcmTokenRaw(
        @Body requestBody: Map<String, String>,
        @Header("Authorization") authToken: String
    ): Response<Map<String, Any>>

    // User Profile Image
    @PUT("api/users/profile/image")
    suspend fun updateProfileImage(
        @Header("Authorization") authToken: String,
        @Body body: Map<String, String>
    ): Response<Map<String, Any>>

    // Service Rating
    @PUT("api/payments/order/{orderId}/rating")
    suspend fun submitServiceRating(
        @Path("orderId") orderId: String,
        @Body body: Map<String, Int>,
        @Header("Authorization") authToken: String
    ): Response<Map<String, Any>>

    companion object {
        private const val TAG = "ApiService"
        private const val BASE_URL = "https://grabtrash-backend.onrender.com/" // Deployed backend API

        fun create(): ApiService {
            val loggingInterceptor = HttpLoggingInterceptor { message ->
                Log.d(TAG, message)
            }.apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                // Add our custom interceptor to handle chunked transfer issues
                .addInterceptor(ChunkedTransferFixInterceptor())
                // Add debugging interceptor
                .addInterceptor { chain ->
                    val request = chain.request()
                    Log.d(TAG, "Request URL: ${request.url}")
                    Log.d(TAG, "Request Method: ${request.method}")
                    Log.d(TAG, "Request Headers: ${request.headers}")
                    if (request.body != null) {
                        Log.d(TAG, "Request has body")
                    }
                    chain.proceed(request)
                }
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                // Add retry mechanism
                .retryOnConnectionFailure(true)
                // Ensure proper TLS is used
                .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS, ConnectionSpec.CLEARTEXT))
                .build()

            val gson = GsonBuilder()
                .setLenient() // Be lenient with malformed JSON
                .create()

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build()
                .create(ApiService::class.java)
        }
    }
}