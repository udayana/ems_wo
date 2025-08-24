package com.sofindo.ems.services

import android.content.Context
import android.content.SharedPreferences
import com.sofindo.ems.api.ApiService
import com.sofindo.ems.api.RetrofitClient
import com.sofindo.ems.models.Maintenance
import com.sofindo.ems.services.UserService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MaintenanceService {
    companion object {
        // Get maintenance this week - sama persis dengan Flutter
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
                try {
                    val apiService: ApiService = RetrofitClient.apiService
                    val response = apiService.updateMaintenanceTaskStatus(
                        taskId = taskId,
                        done = if (isDone) 1 else 0,
                        doneBy = doneBy
                    )
                    
                    if (response["success"] == true) {
                        response
                    } else {
                        throw Exception(response["error"] as? String ?: "Unknown error occurred")
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
                    val response = apiService.updateMaintenanceNotes(mntId, notes, propID)
                    
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
                    val response = apiService.updateMaintenanceEvent(
                        mntId = mntId,
                        status = status,
                        doneDate = currentDate,
                        notes = notes
                    )
                    
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
        
        // Get maintenance history
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
        
        // Get maintenance today for badge counter
        suspend fun getMaintenanceToday(context: Context): List<Maintenance> {
            return withContext(Dispatchers.IO) {
                try {
                    val allMaintenance = getMaintenanceThisWeek(context)
                    val todayString = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
                    
                    allMaintenance.filter { maintenance ->
                        // Parse start_date dan bandingkan dengan hari ini
                        val startDate = maintenance.startDate.split(" ")[0] // Ambil tanggal saja, hilangkan waktu
                        startDate == todayString
                    }
                } catch (e: Exception) {
                    throw Exception("Error getting maintenance today: ${e.message}")
                }
            }
        }
    }
}
