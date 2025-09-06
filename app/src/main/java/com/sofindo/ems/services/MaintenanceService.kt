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
            propID: String
        ): Map<String, Any> {
            return withContext(Dispatchers.IO) {
                try {
                    val apiService: ApiService = RetrofitClient.apiService
                    val response = apiService.getMaintenanceTaskJob(noAssets, propID)
                    
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
        

        

        
        // Update maintenance event status
        suspend fun updateMaintenanceEvent(
            mntId: String,
            status: String,
            notes: String
        ): Map<String, Any> {
            return withContext(Dispatchers.IO) {
                try {
                    val apiService: ApiService = RetrofitClient.apiService
                    val currentDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
                    
                    // Create JSON body as required by PHP (same as Flutter)
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
