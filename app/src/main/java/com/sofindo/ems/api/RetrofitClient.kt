package com.sofindo.ems.api

import com.squareup.moshi.Moshi
import com.squareup.moshi.JsonReader
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okio.Buffer
import retrofit2.Converter
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.io.IOException
import java.lang.reflect.Type
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private const val BASE_URL = "https://emshotels.net/apiKu/"
    
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    
    // Custom converter factory with lenient JSON parsing
    private class LenientMoshiConverterFactory private constructor(
        private val moshi: Moshi
    ) : Converter.Factory() {
        
        override fun responseBodyConverter(
            type: Type,
            annotations: Array<Annotation>,
            retrofit: Retrofit
        ): Converter<ResponseBody, *>? {
            @Suppress("UNCHECKED_CAST")
            val adapter = moshi.adapter<Any>(type) as com.squareup.moshi.JsonAdapter<Any>
            return LenientResponseBodyConverter(adapter)
        }
        
        private class LenientResponseBodyConverter(
            private val adapter: com.squareup.moshi.JsonAdapter<Any>
        ) : Converter<ResponseBody, Any?> {
            
            @Throws(IOException::class)
            override fun convert(value: ResponseBody): Any? {
                val rawString = try {
                    value.string()
                } catch (e: Exception) {
                    throw IOException("Failed to read response body", e)
                }
                
                // Check if response is HTML instead of JSON
                val trimmedResponse = rawString.trim()
                if (trimmedResponse.startsWith("<!DOCTYPE", ignoreCase = true) ||
                    trimmedResponse.startsWith("<html", ignoreCase = true) ||
                    trimmedResponse.startsWith("<HTML", ignoreCase = true)) {
                    android.util.Log.e("RetrofitClient", "Server returned HTML instead of JSON")
                    android.util.Log.e("RetrofitClient", "HTML response (first 500 chars): ${trimmedResponse.take(500)}")
                    // Throw simple error, let the activity handle user-friendly message
                    throw IOException("Server error", null)
                }
                
                // Check if response is empty
                if (trimmedResponse.isEmpty()) {
                    android.util.Log.e("RetrofitClient", "Empty response from server")
                    throw IOException("Server error", null)
                }
                
                return try {
                    // Use lenient JSON reader to handle malformed JSON
                    val buffer = Buffer().writeUtf8(rawString)
                    val reader = JsonReader.of(buffer)
                    reader.isLenient = true
                    adapter.fromJson(reader)
                } catch (e: Exception) {
                    // Log the raw response for debugging
                    android.util.Log.e("RetrofitClient", "JSON parsing error: ${e.message}")
                    android.util.Log.e("RetrofitClient", "Raw response (first 500 chars): ${rawString.take(500)}")
                    android.util.Log.e("RetrofitClient", "Full response length: ${rawString.length}")
                    
                    // Log detailed error for debugging
                    val errorMsg = e.message ?: "Unknown error"
                    if (errorMsg.contains("path")) {
                        android.util.Log.e("RetrofitClient", "Error at path mentioned in exception")
                    }
                    
                    // Throw simple error, let the activity handle user-friendly message
                    throw IOException("Server error", e)
                } finally {
                    value.close()
                }
            }
            
        }
        
        companion object {
            fun create(moshi: Moshi): LenientMoshiConverterFactory {
                return LenientMoshiConverterFactory(moshi)
            }
        }
    }
    
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)
            
            // Check Content-Type header
            val contentType = response.header("Content-Type", "")
            if (contentType?.contains("text/html", ignoreCase = true) == true) {
                android.util.Log.w("RetrofitClient", "Server returned HTML (Content-Type: $contentType) for request: ${request.url}")
                // Don't fail here, let the converter handle it with better error message
            }
            
            response
        }
        .build()
    
    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(ScalarsConverterFactory.create()) // Handle plain text responses first
        .addConverterFactory(LenientMoshiConverterFactory.create(moshi)) // Use lenient converter
        .build()
    
    val apiService: ApiService = retrofit.create(ApiService::class.java)
}
