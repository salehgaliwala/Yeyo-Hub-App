package com.trackigniter.client.api

import android.content.Context
import com.trackigniter.client.ApiLogDatabaseHelper
import okhttp3.Interceptor
import okhttp3.Response
import okio.Buffer
import java.io.IOException

class ApiLoggingInterceptor(private val context: Context) : Interceptor {

    private val dbHelper = ApiLogDatabaseHelper(context)

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val method = request.method
        val url = request.url.toString()
        var requestBody: String? = null

        // Capture Request Body
        try {
            val copy = request.newBuilder().build()
            val buffer = Buffer()
            copy.body?.writeTo(buffer)
            requestBody = buffer.readUtf8()
        } catch (e: Exception) {
            requestBody = "Error reading request body: ${e.message}"
        }

        val response: Response
        try {
            response = chain.proceed(request)
        } catch (e: IOException) {
            dbHelper.logApiCall(method, url, requestBody, 0, null, e.message)
            throw e
        }

        // Capture Response Body
        var responseBody: String? = null
        val responseCode = response.code

        try {
            val source = response.body?.source()
            source?.request(Long.MAX_VALUE) // Buffer the entire body.
            val buffer = source?.buffer
            responseBody = buffer?.clone()?.readUtf8()
        } catch (e: Exception) {
            responseBody = "Error reading response body: ${e.message}"
        }

        // Log to DB
        dbHelper.logApiCall(method, url, requestBody, responseCode, responseBody)

        return response
    }
}
