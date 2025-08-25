package com.sofindo.ems.api

import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private const val BASE_URL = "https://emshotels.net/apiKu/"
    
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    
    // Moshi dengan konfigurasi lenient untuk menangani JSON malformed
    private val lenientMoshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    // Interceptor khusus untuk melog payload submit_wo.php dengan tag mudah difilter
    private val submitWoDebugInterceptor = Interceptor { chain ->
        val request = chain.request()
        val url = request.url
        if (url.encodedPath.endsWith("/submit_wo.php") || url.toString().contains("submit_wo.php")) {
            Log.i("HTTP_SUBMIT_WO", "URL: $url")
            val body = request.body
            if (body is FormBody) {
                val sb = StringBuilder()
                for (i in 0 until body.size) {
                    sb.append(body.name(i)).append("=").append(body.value(i))
                    if (i < body.size - 1) sb.append("&")
                }
                Log.i("HTTP_SUBMIT_WO", "BODY: ${sb.toString()}")
            } else {
                Log.i("HTTP_SUBMIT_WO", "BODY: (non-form or empty)")
            }
        }
        chain.proceed(request)
    }
    
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(submitWoDebugInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()
    
    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(ScalarsConverterFactory.create()) // Handle plain text responses first
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
    
    val apiService: ApiService = retrofit.create(ApiService::class.java)
}
