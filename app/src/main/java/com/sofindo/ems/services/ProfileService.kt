package com.sofindo.ems.services

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.sofindo.ems.api.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

object ProfileService {
    
    private val apiService = RetrofitClient.apiService
    
    suspend fun uploadProfilePhoto(userID: String, imageUri: Uri, context: Context): Map<String, Any> {
        return withContext(Dispatchers.IO) {
            try {
                // Convert URI to File
                val inputStream = context.contentResolver.openInputStream(imageUri)
                val file = File(context.cacheDir, "temp_profile_image.jpg")
                val outputStream = FileOutputStream(file)
                inputStream?.use { input ->
                    outputStream.use { output ->
                        input.copyTo(output)
                    }
                }
                
                // Check if file exists and is readable (like TambahWOFragment)
                if (!file.exists()) {
                    return@withContext mapOf<String, Any>("success" to false, "message" to "Image file not found")
                }

                // Get file size and check if it's reasonable (max 10MB) - like TambahWOFragment
                val fileSize = file.length()
                if (fileSize > 10 * 1024 * 1024) {
                    return@withContext mapOf<String, Any>("success" to false, "message" to "Image file too large (max 10MB)")
                }

                // Create multipart request - same as successful Flutter implementation
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("userID", userID)
                    .addFormDataPart("photo", "USER_$userID.jpg", file.asRequestBody("image/jpeg".toMediaTypeOrNull()))
                    .build()
                
                // Make request using OkHttp with timeout - like Flutter
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                    .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                
                val request = okhttp3.Request.Builder()
                    .url("https://emshotels.net/apiKu/upload_profile.php")
                    .post(requestBody)
                    .build()
                
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""
                
                // Parse response - same as Flutter (text parsing, not JSON)
                val responseText = responseBody.lowercase()
                
                // Clean up temp file
                file.delete()
                
                // If response contains SUCCESS, assume successful
                if (responseText.contains("success")) {
                    mapOf<String, Any>("success" to true, "message" to "Upload successful")
                }
                // If response contains clear error, assume failed
                else if (responseText.contains("error") || responseText.contains("failed") || responseText.contains("exception")) {
                    mapOf<String, Any>("success" to false, "message" to responseBody)
                }
                // If no clear error and file data exists, assume successful
                else if (responseText.contains("tmp_name") || responseText.contains("size")) {
                    mapOf<String, Any>("success" to true, "message" to "Upload successful")
                }
                // Default: assume successful if no error
                else {
                    mapOf<String, Any>("success" to true, "message" to "Upload successful")
                }
            } catch (e: Exception) {
                mapOf<String, Any>("success" to false, "message" to "Upload failed: ${e.message}")
            }
        }
    }
    
    suspend fun updateUserProfile(
        id: String,
        fullName: String,
        email: String,
        phoneNumber: String
    ): Map<String, Any> {
        return withContext(Dispatchers.IO) {
            try {
                apiService.updateUserProfile(
                    action = "update",
                    id = id,
                    fullName = fullName,
                    email = email,
                    phoneNumber = phoneNumber
                )
            } catch (e: Exception) {
                mapOf(
                    "success" to false,
                    "message" to "Update failed: ${e.message}"
                )
            }
        }
    }
    
    suspend fun getUserProfile(id: String): Map<String, Any> {
        return withContext(Dispatchers.IO) {
            try {
                apiService.getUserProfile(
                    action = "get",
                    id = id
                )
            } catch (e: Exception) {
                mapOf(
                    "success" to false,
                    "message" to "Failed to get profile: ${e.message}"
                )
            }
        }
    }
}
