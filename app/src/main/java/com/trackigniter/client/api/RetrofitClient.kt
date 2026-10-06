package com.trackigniter.client.api

import android.content.Context
import com.trackigniter.client.SessionManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class RetrofitClient(context: Context) {

    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()
        val token = SessionManager.getToken(context)
        
        var request = original
        
        if (token != null) {
            // 1. Add Header (Standard)
            val requestBuilder = original.newBuilder()
                .header("Authorization", "Bearer $token")
            
            // 2. Add Query Param (Fallback for Server Stripping)
            val url = original.url.newBuilder()
                .addQueryParameter("token", token)
                .build()
            
            request = requestBuilder.url(url).build()
        }
        
        chain.proceed(request)
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .addInterceptor(authInterceptor)
        .addInterceptor(ApiLoggingInterceptor(context))
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val apiService: ApiService by lazy {
        val baseUrl = "https://rental.yeyocar.com/mobile/api/"
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    companion object {
        @Volatile
        private var instance: RetrofitClient? = null

        fun getInstance(context: Context): RetrofitClient {
            return instance ?: synchronized(this) {
                instance ?: RetrofitClient(context.applicationContext).also { instance = it }
            }
        }
        
        val apiService: ApiService
            get() = instance?.apiService ?: throw IllegalStateException("RetrofitClient must be initialized with context first")
    }
}
