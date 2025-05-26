package com.example.ecotrack.utils

import android.util.Log
import com.example.ecotrack.models.*
import com.example.ecotrack.models.payment.Payment
import com.example.ecotrack.models.payment.PaymentRequest
import com.example.ecotrack.models.payment.PaymentResponse
import com.example.ecotrack.models.Barangay
import com.example.ecotrack.utils.NetworkUtils.Companion.ChunkedTransferFixInterceptor
import com.google.gson.GsonBuilder
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
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
    
    // Driver Job Order Status Update
    @PUT("api/driver/job/{paymentId}/status")
    suspend fun updateJobOrderStatus(
        @Path("paymentId") paymentId: String,
        @Body statusUpdate: JobOrderStatusUpdate,
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

    companion object {
        private const val TAG = "ApiService"
        private const val BASE_URL = "http://10.0.2.2:8080/" // Android emulator localhost

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