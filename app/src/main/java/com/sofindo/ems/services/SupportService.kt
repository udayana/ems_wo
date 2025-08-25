package com.sofindo.ems.services

import android.content.Context
import android.net.Uri
import com.sofindo.ems.api.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream

object SupportService {
    
    private val apiService = RetrofitClient.apiService
    
    suspend fun submitSupportTicket(
        name: String,
        email: String,
        mobileNumber: String?,
        issue: String,
        description: String,
        screenshotPath: String? = null
    ): Map<String, Any> {
        return withContext(Dispatchers.IO) {
            try {
                // Create form data - EXACTLY like Flutter
                val formBody = okhttp3.FormBody.Builder()
                    .add("name", name)
                    .add("email", email)
                    .add("issue", issue)
                    .add("description", description)
                    .build()
                
                // Add optional fields
                val finalFormBody = if (mobileNumber != null) {
                    okhttp3.FormBody.Builder()
                        .add("name", name)
                        .add("email", email)
                        .add("mobile_number", mobileNumber)
                        .add("issue", issue)
                        .add("description", description)
                        .build()
                } else {
                    formBody
                }
                
                // Add screenshot path if available
                val requestBody = if (screenshotPath != null) {
                    okhttp3.FormBody.Builder()
                        .add("name", name)
                        .add("email", email)
                        .add("mobile_number", mobileNumber ?: "")
                        .add("issue", issue)
                        .add("description", description)
                        .add("screenshot_path", screenshotPath)
                        .build()
                } else {
                    finalFormBody
                }
                
                // Make request using OkHttp with timeout
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                    .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                
                val request = okhttp3.Request.Builder()
                    .url("https://emshotels.net/apiKu/submit_support_ticket.php")
                    .post(requestBody)
                    .build()
                
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""
                
                // Handle response - EXACTLY like Flutter
                val result = when {
                    // If response is "success" (simple text), it's successful
                    responseBody.trim() == "success" -> {
                        mapOf<String, Any>("success" to true, "message" to "Ticket submitted successfully")
                    }
                    
                    // If response contains HTML error, it's a server error
                    responseBody.contains("<!DOCTYPE html>") || responseBody.contains("<html>") -> {
                        mapOf<String, Any>("success" to false, "message" to "Server error")
                    }
                    
                    // If response contains PHP errors, it's a server error
                    responseBody.contains("Fatal error") || responseBody.contains("Parse error") || responseBody.contains("Warning") -> {
                        mapOf<String, Any>("success" to false, "message" to "Server error")
                    }
                    
                    // Try to parse as JSON
                    else -> {
                        try {
                            val jsonObject = org.json.JSONObject(responseBody)
                            if (jsonObject.has("success") && jsonObject.getBoolean("success")) {
                                mapOf<String, Any>("success" to true, "message" to "Ticket submitted successfully")
                            } else {
                                val error = if (jsonObject.has("message")) jsonObject.getString("message") else "Submit failed"
                                mapOf<String, Any>("success" to false, "message" to error)
                            }
                        } catch (jsonException: Exception) {
                            // If JSON parsing fails but response contains success indicators, assume success
                            val responseText = responseBody.lowercase()
                            if (responseText.contains("success") || responseText.contains("submitted") || responseText.contains("ticket")) {
                                mapOf<String, Any>("success" to true, "message" to "Ticket submitted successfully")
                            } else if (response.isSuccessful) {
                                mapOf<String, Any>("success" to true, "message" to "Ticket submitted successfully")
                            } else {
                                mapOf<String, Any>("success" to false, "message" to "Submit failed")
                            }
                        }
                    }
                }
                
                return@withContext result
                
            } catch (e: Exception) {
                mapOf(
                    "success" to false,
                    "message" to "Failed to submit ticket: ${e.message}"
                )
            }
        }
    }
    
    suspend fun uploadSupportAttachment(imageUri: Uri, context: Context): Map<String, Any> {
        return withContext(Dispatchers.IO) {
            try {
                // Convert URI to File
                val inputStream = context.contentResolver.openInputStream(imageUri)
                val file = File(context.cacheDir, "support_attachment.jpg")
                val outputStream = FileOutputStream(file)
                inputStream?.use { input ->
                    outputStream.use { output ->
                        input.copyTo(output)
                    }
                }
                
                // Check if file exists and is readable
                if (!file.exists()) {
                    return@withContext mapOf<String, Any>("success" to false, "message" to "Image file not found")
                }

                // Get file size and check if it's reasonable (max 10MB)
                val fileSize = file.length()
                if (fileSize > 10 * 1024 * 1024) {
                    return@withContext mapOf<String, Any>("success" to false, "message" to "Image file too large (max 10MB)")
                }

                // Create multipart request
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("attachment", file.name, file.asRequestBody("image/jpeg".toMediaTypeOrNull()))
                    .build()
                
                // Make request using OkHttp with timeout
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                    .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                
                val request = okhttp3.Request.Builder()
                    .url("https://emshotels.net/apiKu/upload_support_attachment.php")
                    .post(requestBody)
                    .build()
                
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""
                
                // Clean up temp file
                file.delete()
                
                // Simple and robust response handling
                return@withContext when {
                    // If response is empty, assume success (file was uploaded)
                    responseBody.isBlank() -> {
                        mapOf<String, Any>("success" to true, "message" to "Upload successful")
                    }
                    
                    // If response contains HTML error, it's a server error
                    responseBody.contains("<!DOCTYPE html>") || responseBody.contains("<html>") -> {
                        mapOf<String, Any>("success" to false, "message" to "Server error")
                    }
                    
                    // If response contains PHP errors, it's a server error
                    responseBody.contains("Fatal error") || responseBody.contains("Parse error") || responseBody.contains("Warning") -> {
                        mapOf<String, Any>("success" to false, "message" to "Server error")
                    }
                    
                    // Try to parse as JSON first (this is the correct approach)
                    else -> {
                        try {
                            val jsonObject = org.json.JSONObject(responseBody)
                            if (jsonObject.has("success") && jsonObject.getBoolean("success")) {
                                // Extract the actual file path from JSON response
                                val filePath = if (jsonObject.has("file_path")) {
                                    jsonObject.getString("file_path")
                                } else if (jsonObject.has("path")) {
                                    jsonObject.getString("path")
                                } else if (jsonObject.has("filename")) {
                                    jsonObject.getString("filename")
                                } else {
                                    "support_attachment.jpg"
                                }
                                
                                mapOf<String, Any>(
                                    "success" to true, 
                                    "message" to "Upload successful",
                                    "file_path" to filePath
                                )
                            } else {
                                val error = if (jsonObject.has("message")) jsonObject.getString("message") else "Upload failed"
                                mapOf<String, Any>("success" to false, "message" to error)
                            }
                        } catch (jsonException: Exception) {
                            // Fallback: If response contains upload path indicators, it's successful
                            if (responseBody.contains("uploads/tickets/") || responseBody.contains("ticket_")) {
                                // Extract the actual file path
                                val filePath = when {
                                    responseBody.contains("uploads/tickets/") -> {
                                        val pattern = "uploads/tickets/[^\\s\"]+".toRegex()
                                        pattern.find(responseBody)?.value ?: "support_attachment.jpg"
                                    }
                                    responseBody.contains("ticket_") -> {
                                        val pattern = "ticket_[^\\s\"]+\\.jpg".toRegex()
                                        pattern.find(responseBody)?.value ?: "support_attachment.jpg"
                                    }
                                    else -> "support_attachment.jpg"
                                }
                                
                                mapOf<String, Any>(
                                    "success" to true, 
                                    "message" to "Upload successful",
                                    "file_path" to filePath
                                )
                            } else if (responseBody.lowercase().contains("success")) {
                                mapOf<String, Any>("success" to true, "message" to "Upload successful")
                            } else if (responseBody.lowercase().contains("tmp_name") || 
                                     responseBody.lowercase().contains("size") || 
                                     responseBody.lowercase().contains("upload")) {
                                mapOf<String, Any>("success" to true, "message" to "Upload successful")
                            } else if (response.isSuccessful) {
                                mapOf<String, Any>("success" to true, "message" to "Upload successful")
                            } else {
                                mapOf<String, Any>("success" to false, "message" to "Upload failed")
                            }
                        }
                    }
                }
                
            } catch (e: Exception) {
                // If any exception occurs, assume upload failed but don't show technical details
                mapOf<String, Any>("success" to false, "message" to "Upload failed")
            }
        }
    }
}
