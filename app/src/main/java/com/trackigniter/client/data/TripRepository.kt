package com.trackigniter.client.data

import com.trackigniter.client.api.RetrofitClient
import com.trackigniter.client.api.models.Trip
import com.trackigniter.client.api.models.UpdateStatusRequest

import okhttp3.MediaType.Companion.toMediaTypeOrNull

object TripRepository {

    suspend fun getTrips(): List<Trip> {
        val response = RetrofitClient.apiService.getTrips()
        if (response.isSuccessful) {
            return response.body() ?: emptyList()
        } else {
            throw Exception("Failed to fetch trips: ${response.code()} ${response.message()}")
        }
    }

    suspend fun getStatuses(): List<com.trackigniter.client.api.models.StatusOption> {
        val response = RetrofitClient.apiService.getTripStatuses()
        if (response.isSuccessful) {
            return response.body() ?: emptyList()
        } else {
            throw Exception("Failed to fetch statuses: ${response.message()}")
        }
    }

    suspend fun updateStatus(id: String, status: String, reason: String? = null, odometerReading: String? = null) {
        val response = RetrofitClient.apiService.updateTripStatus(UpdateStatusRequest(id, status, reason, odometerReading))
        if (!response.isSuccessful) {
             var errorMsg = response.message()
             try {
                 val errorBody = response.errorBody()?.string()
                 if (!errorBody.isNullOrEmpty()) {
                     val json = org.json.JSONObject(errorBody)
                     if (json.has("message")) errorMsg = json.getString("message")
                     else if (json.has("error")) errorMsg = json.getString("error")
                 }
             } catch (e: Exception) { e.printStackTrace() }
             throw Exception(errorMsg)
        }
    }
    suspend fun uploadProof(tripId: String, proofType: String, imageFile: java.io.File) {
        val reqTripId = okhttp3.RequestBody.create("text/plain".toMediaTypeOrNull(), tripId)
        val reqProofType = okhttp3.RequestBody.create("text/plain".toMediaTypeOrNull(), proofType)

        val requestFile = okhttp3.RequestBody.create("image/*".toMediaTypeOrNull(), imageFile)
        val body = okhttp3.MultipartBody.Part.createFormData("image", imageFile.name, requestFile)

        val response = RetrofitClient.apiService.uploadProof(reqTripId, reqProofType, body)
        if (!response.isSuccessful) {
             throw Exception("Upload failed: ${response.message()}")
        }
    }

    suspend fun updateFcmToken(token: String) {
        val response = RetrofitClient.apiService.updateFcmToken(mapOf("token" to token))
        if (!response.isSuccessful) {
             throw Exception("Failed to update FCM token: ${response.message()}")
        }
    }
}
