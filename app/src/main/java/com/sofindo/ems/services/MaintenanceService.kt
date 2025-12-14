package com.sofindo.ems.services

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.sofindo.ems.api.ApiService
import com.sofindo.ems.api.RetrofitClient
import com.sofindo.ems.models.Maintenance
import com.sofindo.ems.services.UserService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody

class MaintenanceService {
    companion object {
        // Get maintenance this week - exactly like Flutter
        suspend fun getMaintenanceThisWeek(context: Context): List<Maintenance> {
            return withContext(Dispatchers.IO) {
                try {
                    // Get propID from UserService instead of direct SharedPreferences
                    val propID = UserService.getCurrentPropID() ?: ""
                    
                    if (propID.isEmpty()) {
                        throw Exception("Property ID not found. Please login again.")
                    }
                    
                    val apiService: ApiService = RetrofitClient.apiService
                    val response = apiService.getMaintenanceThisWeek(propID)
                    
                    if (response["status"] == "success") {
                        @Suppress("UNCHECKED_CAST")
                        val maintenanceData = response["data"] as? List<Map<String, Any>> ?: emptyList()
                        maintenanceData.map { Maintenance.fromJson(it) }
                    } else {
                        throw Exception(response["message"] as? String ?: "Failed to load maintenance data")
                    }
                } catch (e: Exception) {
                    throw Exception("Error connecting to server: ${e.message}")
                }
            }
        }
        
        // Get maintenance task job data
        suspend fun getMaintenanceTaskJob(
            context: Context,
            noAssets: String,
            propID: String,
            eventId: String
        ): Map<String, Any> {
            return withContext(Dispatchers.IO) {
                try {
                    val apiService: ApiService = RetrofitClient.apiService
                    val response = apiService.getMaintenanceTaskJob(noAssets, propID, eventId)
                    
                    if (response["success"] == true) {
                        response
                    } else {
                        throw Exception(response["error"] as? String ?: "Unknown error occurred")
                    }
                } catch (e: Exception) {
                    throw Exception("Failed to load maintenance task job: ${e.message}")
                }
            }
        }
        
        // Update maintenance task status
        suspend fun updateMaintenanceTaskStatus(
            taskId: String,
            isDone: Boolean,
            doneBy: String
        ): Map<String, Any> {
            return withContext(Dispatchers.IO) {
                val requestBody = mapOf(
                    "taskId" to taskId,
                    "done" to (if (isDone) "1" else "0"),
                    "doneby" to doneBy
                )
                
                try {
                    val apiService: ApiService = RetrofitClient.apiService
                    
                    // Make API call
                    val response = apiService.updateMaintenanceTaskStatus(requestBody)
                    
                    if (response["success"] == true) {
                        response
                    } else {
                        val errorMsg = response["error"] as? String ?: "Unknown error occurred"
                        throw Exception(errorMsg)
                    }
                } catch (e: Exception) {
                    throw Exception("Failed to update maintenance task status: ${e.message}")
                }
            }
        }
        
        // Get maintenance notes
        suspend fun getMaintenanceNotes(
            mntId: String,
            propID: String? = null
        ): String {
            return withContext(Dispatchers.IO) {
                try {
                    val apiService: ApiService = RetrofitClient.apiService
                    val response = apiService.getMaintenanceNotes(mntId, propID)
                    
                    if (response["success"] == true) {
                        response["notes"] as? String ?: ""
                    } else {
                        throw Exception(response["error"] as? String ?: "Unknown error occurred")
                    }
                } catch (e: Exception) {
                    throw Exception("Failed to get maintenance notes: ${e.message}")
                }
            }
        }
        
        // Update maintenance notes
        suspend fun updateMaintenanceNotes(
            mntId: String,
            notes: String,
            propID: String? = null
        ): Map<String, Any> {
            return withContext(Dispatchers.IO) {
                try {
                    val apiService: ApiService = RetrofitClient.apiService
                    
                    // Create JSON body as required by PHP
                    val requestBody = mutableMapOf<String, String>()
                    requestBody["mntId"] = mntId
                    requestBody["notes"] = notes
                    if (!propID.isNullOrEmpty()) {
                        requestBody["propID"] = propID
                    }
                    
                    // Update maintenance notes
                    
                    val response = apiService.updateMaintenanceNotes(requestBody)
                    
                    if (response["success"] == true) {
                        response
                    } else {
                        throw Exception(response["error"] as? String ?: "Unknown error occurred")
                    }
                } catch (e: Exception) {
                    throw Exception("Failed to update maintenance notes: ${e.message}")
                }
            }
        }
        
        // Update maintenance notes and photos WITHOUT changing status
        // This is used when user only updates notes/photos, not when completing tasks
        suspend fun updateMaintenanceNotesAndPhotos(
            mntId: String,
            notes: String,
            photos: List<java.io.File> = emptyList(),
            propID: String? = null
        ): Map<String, Any> {
            return withContext(Dispatchers.IO) {
                try {
                    // If photos are provided, upload them first
                    if (photos.isNotEmpty()) {
                        val uploadResponse = uploadMaintenancePhotos(photos)
                        // Get uploaded photo filenames from response
                        @Suppress("UNCHECKED_CAST")
                        val uploadedPhotos = uploadResponse["photos"] as? List<Map<String, Any>> ?: emptyList()
                        
                        // Extract filenames
                        val photoFilenames = uploadedPhotos.mapNotNull { it["filename"] as? String }
                        
                        // Update notes with photo filenames using maintenance_notes.php endpoint
                        // This endpoint doesn't change status
                        val requestBody = mutableMapOf<String, String>()
                        requestBody["mntId"] = mntId
                        requestBody["notes"] = notes
                        if (!propID.isNullOrEmpty()) {
                            requestBody["propID"] = propID
                        }
                        
                        // Add photo filenames if available (API supports this)
                        if (photoFilenames.isNotEmpty()) {
                            requestBody["photo1"] = photoFilenames[0]
                        }
                        if (photoFilenames.size > 1) {
                            requestBody["photo2"] = photoFilenames[1]
                        }
                        if (photoFilenames.size > 2) {
                            requestBody["photo3"] = photoFilenames[2]
                        }
                        
                        val apiService: ApiService = RetrofitClient.apiService
                        val response = apiService.updateMaintenanceNotes(requestBody)
                        
                        if (response["success"] == true) {
                            response
                        } else {
                            throw Exception(response["error"] as? String ?: "Unknown error occurred")
                        }
                    } else {
                        // No photos, just update notes (doesn't change status)
                        updateMaintenanceNotes(mntId, notes, propID)
                    }
                } catch (e: Exception) {
                    throw Exception("Failed to update maintenance notes and photos: ${e.message}")
                }
            }
        }
        
        // Upload maintenance photos separately
        suspend fun uploadMaintenancePhotos(
            photos: List<java.io.File>
        ): Map<String, Any> {
            return withContext(Dispatchers.IO) {
                try {
                    val apiService: ApiService = RetrofitClient.apiService
                    
                    // Create multipart parts for photos
                    val photoParts = photos.mapIndexed { index, file ->
                        val requestFile = file.asRequestBody("image/*".toMediaTypeOrNull())
                        val photoName = "photo_${index + 1}.jpg"
                        okhttp3.MultipartBody.Part.createFormData("photos[]", photoName, requestFile)
                    }
                    
                    val response = apiService.uploadMaintenancePhotos(photoParts)
                    
                    if (response["success"] == true) {
                        response
                    } else {
                        throw Exception(response["error"] as? String ?: "Unknown error occurred")
                    }
                } catch (e: Exception) {
                    throw Exception("Failed to upload maintenance photos: ${e.message}")
                }
            }
        }
        
        // Update maintenance event status
        suspend fun updateMaintenanceEvent(
            mntId: String,
            status: String,
            notes: String,
            photos: List<java.io.File> = emptyList()
        ): Map<String, Any> {
            return withContext(Dispatchers.IO) {
                try {
                    val apiService: ApiService = RetrofitClient.apiService
                    val currentDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
                    
                    // Prepare photo filenames
                    val photo1 = ""
                    val photo2 = ""
                    val photo3 = ""
                    
                    // If photos are provided, upload them first and get filenames
                    if (photos.isNotEmpty()) {
                        try {
                            val uploadResponse = uploadMaintenancePhotos(photos)
                            // Get uploaded photo filenames from response
                            val uploadedPhotos = uploadResponse["photos"] as? List<Map<String, Any>> ?: emptyList()
                            
                            // Extract filenames
                            val photoFilenames = uploadedPhotos.mapNotNull { it["filename"] as? String }
                            
                            // Update event with notes and photo filenames
                            val requestBody = mutableMapOf<String, String>()
                            requestBody["mntId"] = mntId
                            requestBody["status"] = status
                            requestBody["doneDate"] = currentDate
                            requestBody["notes"] = notes
                            
                            // Update event with notes and photo filenames in one call
                            val eventRequestBody = mutableMapOf<String, String>()
                            eventRequestBody["mntId"] = mntId
                            eventRequestBody["status"] = status
                            eventRequestBody["doneDate"] = currentDate
                            eventRequestBody["notes"] = notes
                            
                            // Add photo filenames (max 3)
                            if (photoFilenames.isNotEmpty()) {
                                eventRequestBody["photo1"] = photoFilenames[0]
                            }
                            if (photoFilenames.size > 1) {
                                eventRequestBody["photo2"] = photoFilenames[1]
                            }
                            if (photoFilenames.size > 2) {
                                eventRequestBody["photo3"] = photoFilenames[2]
                            }
                            
                            val eventResponse = apiService.updateMaintenanceEvent(eventRequestBody)
                            
                            if (eventResponse["success"] == true) {
                                eventResponse
                            } else {
                                throw Exception(eventResponse["error"] as? String ?: "Unknown error occurred")
                            }
                        } catch (photoError: Exception) {
                            // Log photo upload error but continue with event update
                            android.util.Log.e("MaintenanceService", "Photo upload failed: ${photoError.message}")
                            
                            // Continue with event update without photos
                            val requestBody = mutableMapOf<String, String>()
                            requestBody["mntId"] = mntId
                            requestBody["status"] = status
                            requestBody["doneDate"] = currentDate
                            requestBody["notes"] = notes
                            
                            val response = apiService.updateMaintenanceEvent(requestBody)
                            
                            if (response["success"] == true) {
                                response
                            } else {
                                throw Exception(response["error"] as? String ?: "Unknown error occurred")
                            }
                        }
                    } else {
                        // No photos, just update event with notes
                        val requestBody = mutableMapOf<String, String>()
                        requestBody["mntId"] = mntId
                        requestBody["status"] = status
                        requestBody["doneDate"] = currentDate
                        requestBody["notes"] = notes
                        
                        val response = apiService.updateMaintenanceEvent(requestBody)
                        
                        if (response["success"] == true) {
                            response
                        } else {
                            throw Exception(response["error"] as? String ?: "Unknown error occurred")
                        }
                    }
                } catch (e: Exception) {
                    throw Exception("Failed to update maintenance event: ${e.message}")
                }
            }
        }
        
        // Get maintenance today for badge counter
        suspend fun getMaintenanceToday(context: Context): List<Maintenance> {
            return withContext(Dispatchers.IO) {
                try {
                    val allMaintenance = getMaintenanceThisWeek(context)
                    val todayString = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
                    
                    allMaintenance.filter { maintenance ->
                        // Parse start_date and compare with today
                        val startDate = maintenance.startDate.split(" ")[0] // Get date only, remove time
                        startDate == todayString
                    }
                } catch (e: Exception) {
                    throw Exception("Error getting maintenance today: ${e.message}")
                }
            }
        }
        
        // Get asset schedules from tblevent joined with tblinventory
        suspend fun getAssetSchedule(
            noAssets: String,
            propID: String
        ): List<Map<String, Any>> {
            return withContext(Dispatchers.IO) {
                try {
                    val apiService: ApiService = RetrofitClient.apiService
                    val response = apiService.getAssetSchedule(noAssets, propID)
                    
                    if (response["status"] == "success") {
                        @Suppress("UNCHECKED_CAST")
                        val scheduleData = response["data"] as? List<Map<String, Any>> ?: emptyList()
                        scheduleData
                    } else {
                        throw Exception(response["message"] as? String ?: "Failed to load asset schedules")
                    }
                } catch (e: Exception) {
                    throw Exception("Error connecting to server: ${e.message}")
                }
            }
        }
        
        // Get maintenance history from tblmnthistory (same as Flutter)
        suspend fun getMaintenanceHistory(
            mntId: String,
            propID: String
        ): List<Map<String, Any>> {
            return withContext(Dispatchers.IO) {
                try {
                    val apiService: ApiService = RetrofitClient.apiService
                    val response = apiService.getMaintenanceHistory(mntId, propID)
                    
                    if (response["success"] == true) {
                        @Suppress("UNCHECKED_CAST")
                        val historyData = response["data"] as? List<Map<String, Any>> ?: emptyList()
                        historyData
                    } else {
                        throw Exception(response["error"] as? String ?: "Failed to load maintenance history")
                    }
                } catch (e: Exception) {
                    throw Exception("Error connecting to server: ${e.message}")
                }
            }
        }
    }
}
