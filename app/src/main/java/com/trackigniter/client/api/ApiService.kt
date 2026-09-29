package com.trackigniter.client.api

import com.trackigniter.client.api.models.LoginRequest
import com.trackigniter.client.api.models.LoginResponse
import com.trackigniter.client.api.models.Trip
import com.trackigniter.client.api.models.StatusOption
import com.trackigniter.client.api.models.UpdateStatusRequest
import com.trackigniter.client.api.models.LocationRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Multipart
import retrofit2.http.Part
import retrofit2.http.Query

interface ApiService {

    @POST("login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("logout")
    suspend fun logout(): Response<com.trackigniter.client.api.models.LogoutResponse>

    @GET("trips")
    suspend fun getTrips(): Response<List<Trip>>

    @GET("trip_status_list")
    suspend fun getTripStatuses(): Response<List<StatusOption>>

    @POST("update_trip_status")
    suspend fun updateTripStatus(@Body request: UpdateStatusRequest): Response<Void>

    @POST("add_fuel_entry")
    suspend fun addFuelEntry(@Body request: com.trackigniter.client.api.models.FuelEntryRequest): Response<Void>

    @POST("tripexpadd")
    suspend fun addTripExpense(@Body request: com.trackigniter.client.api.models.TripExpenseRequest): Response<Void>

    @POST("track")
    suspend fun track(@Body request: LocationRequest): Response<Void>

    @Multipart
    @POST("upload_proof")
    suspend fun uploadProof(
        @Part("trip_id") tripId: RequestBody,
        @Part("proof_type") proofType: RequestBody,
        @Part image: MultipartBody.Part
    ): Response<Void>

    @POST("update_fcm_token_post")
    suspend fun updateFcmToken(@Body body: Map<String, String>): Response<Void>

    @GET("chats")
    suspend fun getChats(): Response<List<com.trackigniter.client.api.models.ChatMessage>>

    @POST("chats")
    suspend fun sendChat(@Body request: com.trackigniter.client.api.models.ChatSendRequest): Response<Void>

    @GET("profile")
    suspend fun getProfile(): Response<com.trackigniter.client.api.models.DriverProfile>

    @POST("expenses_delete")
    suspend fun deleteExpense(@Body request: com.trackigniter.client.api.models.DeleteRequest): Response<Void>

    @GET("fuel")
    suspend fun getFuel(@Query("trip_id") tripId: String? = null): Response<List<com.trackigniter.client.api.models.Fuel>>

    @POST("fuel_delete")
    suspend fun deleteFuel(@Body request: com.trackigniter.client.api.models.DeleteRequest): Response<Void>

    // New Add Methods
    @POST("expense_add")
    suspend fun addExpense(@Body request: Map<String, String>): Response<Void>

    @Multipart
    @POST("add_fuel_entry")
    suspend fun addFuelWithImage(
        @Part("v_id") vId: RequestBody,
        @Part("fuel_quantity") qty: RequestBody,
        @Part("odometerreading") odo: RequestBody,
        @Part("fuelprice") price: RequestBody,
        @Part("fuelfilldate") date: RequestBody,
        @Part("fueladdedby") addedBy: RequestBody,
        @Part("fuelcomments") comments: RequestBody,
        @Part("fuelsource") source: RequestBody,
        @Part("fuelvendor") vendor: RequestBody,
        @Part image: MultipartBody.Part?
    ): Response<Void>

    @POST("incident_create")
    suspend fun reportIncident(@Body request: com.trackigniter.client.api.models.IncidentRequest): Response<Void>

    @GET("expenses")
    suspend fun getExpenses(@Query("trip_id") tripId: String? = null): Response<List<com.trackigniter.client.api.models.Expense>>
    @POST("log")
    suspend fun sendLogs(@Body logs: List<com.trackigniter.client.api.models.LogEntry>): retrofit2.Response<Void>
}
