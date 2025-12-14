package com.sofindo.ems.services

import android.util.Log
import com.sofindo.ems.api.RetrofitClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object NotificationService {
    
    /**
     * Send notification after work order submission
     */
    fun sendWorkOrderNotification(
        workOrderTitle: String,
        workOrderDescription: String,
        targetDepartment: String,
        onSuccess: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        // Validate input parameters
        if (workOrderTitle.isBlank() || targetDepartment.isBlank()) {
            Log.e("NotificationService", "Invalid parameters: title='$workOrderTitle', dept='$targetDepartment'")
            onError?.invoke("Invalid notification parameters")
            return
        }
        
        Log.d("NotificationService", "Starting notification send for: $workOrderTitle")
        
        // Safe notification sending with proper error handling
        try {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val title = "New Work Order: $workOrderTitle"
                    val body = "From: $targetDepartment\nDescription: $workOrderDescription"
                    
                    Log.d("NotificationService", "Sending notification - Title: $title, Body: $body")
                    
                    val response = RetrofitClient.apiService.sendWorkOrderNotification(
                        woto = targetDepartment,
                        title = title,
                        body = body,
                        propID = null // Server will handle both iOS and Android automatically
                    )
                    
                    Log.d("NotificationService", "Notification response: $response")
                    
                    // Switch to Main thread for UI callbacks
                    withContext(Dispatchers.Main) {
                        try {
                            // Check if response indicates success
                            if (response.contains("success", ignoreCase = true) || 
                                response.contains("sent", ignoreCase = true)) {
                                Log.d("NotificationService", "Notification sent successfully")
                                onSuccess?.invoke()
                            } else {
                                Log.e("NotificationService", "Notification failed: $response")
                                onError?.invoke("Failed to send notification: $response")
                            }
                        } catch (e: Exception) {
                            Log.e("NotificationService", "Error in callback", e)
                            onError?.invoke("Callback error: ${e.message}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("NotificationService", "Error sending notification", e)
                    // Switch to Main thread for UI callbacks
                    withContext(Dispatchers.Main) {
                        try {
                            onError?.invoke("Error sending notification: ${e.message}")
                        } catch (callbackError: Exception) {
                            Log.e("NotificationService", "Error in error callback", callbackError)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("NotificationService", "Error creating coroutine", e)
            onError?.invoke("Error creating notification task: ${e.message}")
        }
    }
    
    /**
     * Send notification for work order status update
     */
    fun sendStatusUpdateNotification(
        workOrderTitle: String,
        newStatus: String,
        targetDepartment: String,
        onSuccess: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val title = "Work Order Status Updated"
                val body = "WO: $workOrderTitle\nNew Status: $newStatus"
                
                val response = RetrofitClient.apiService.sendWorkOrderNotification(
                    woto = targetDepartment,
                    title = title,
                    body = body,
                    propID = null
                )
                
                // Switch to Main thread for UI callbacks
                withContext(Dispatchers.Main) {
                    if (response.contains("success", ignoreCase = true) || 
                        response.contains("sent", ignoreCase = true)) {
                        onSuccess?.invoke()
                    } else {
                        onError?.invoke("Failed to send notification: $response")
                    }
                }
            } catch (e: Exception) {
                // Switch to Main thread for UI callbacks
                withContext(Dispatchers.Main) {
                    onError?.invoke("Error sending notification: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Send notification for work order completion
     */
    fun sendCompletionNotification(
        workOrderTitle: String,
        completedBy: String,
        targetDepartment: String,
        onSuccess: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val title = "Work Order Completed"
                val body = "WO: $workOrderTitle\nCompleted by: $completedBy"
                
                val response = RetrofitClient.apiService.sendWorkOrderNotification(
                    woto = targetDepartment,
                    title = title,
                    body = body,
                    propID = null
                )
                
                // Switch to Main thread for UI callbacks
                withContext(Dispatchers.Main) {
                    if (response.contains("success", ignoreCase = true) || 
                        response.contains("sent", ignoreCase = true)) {
                        onSuccess?.invoke()
                    } else {
                        onError?.invoke("Failed to send notification: $response")
                    }
                }
            } catch (e: Exception) {
                // Switch to Main thread for UI callbacks
                withContext(Dispatchers.Main) {
                    onError?.invoke("Error sending notification: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Send notification after project creation
     */
    fun sendProjectNotification(
        projectName: String,
        location: String,
        targetDepartment: String,
        onSuccess: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        // Validate input parameters
        if (projectName.isBlank() || targetDepartment.isBlank()) {
            Log.e("NotificationService", "Invalid parameters: projectName='$projectName', dept='$targetDepartment'")
            onError?.invoke("Invalid notification parameters")
            return
        }
        
        Log.d("NotificationService", "Starting project notification send for: $projectName")
        
        try {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val title = "New Project: $projectName"
                    val body = "Location: $location\nTarget Department: $targetDepartment"
                    
                    Log.d("NotificationService", "Sending project notification - Title: $title, Body: $body")
                    
                    val response = RetrofitClient.apiService.sendWorkOrderNotification(
                        woto = targetDepartment,
                        title = title,
                        body = body,
                        propID = null // Server will handle both iOS and Android automatically
                    )
                    
                    Log.d("NotificationService", "Project notification response: $response")
                    
                    // Switch to Main thread for UI callbacks
                    withContext(Dispatchers.Main) {
                        try {
                            // Check if response indicates success
                            if (response.contains("success", ignoreCase = true) || 
                                response.contains("sent", ignoreCase = true)) {
                                Log.d("NotificationService", "Project notification sent successfully")
                                onSuccess?.invoke()
                            } else {
                                Log.e("NotificationService", "Project notification failed: $response")
                                onError?.invoke("Failed to send notification: $response")
                            }
                        } catch (e: Exception) {
                            Log.e("NotificationService", "Error in callback", e)
                            onError?.invoke("Callback error: ${e.message}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("NotificationService", "Error sending project notification", e)
                    // Switch to Main thread for UI callbacks
                    withContext(Dispatchers.Main) {
                        try {
                            onError?.invoke("Error sending notification: ${e.message}")
                        } catch (callbackError: Exception) {
                            Log.e("NotificationService", "Error in error callback", callbackError)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("NotificationService", "Error creating coroutine", e)
            onError?.invoke("Error creating notification task: ${e.message}")
        }
    }
}
