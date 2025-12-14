package com.sofindo.ems.services

import android.content.Context
import android.util.Log
import androidx.work.*
import com.sofindo.ems.api.RetrofitClient
import com.sofindo.ems.database.PendingWorkOrder
import com.sofindo.ems.database.PendingProject
import com.sofindo.ems.database.PendingMaintenanceTask
import com.sofindo.ems.services.MaintenanceService
import com.sofindo.ems.services.OfflineMaintenanceService
import com.sofindo.ems.services.OfflineQueueService
import com.sofindo.ems.services.OfflineProjectService
import com.sofindo.ems.utils.NetworkUtils
import org.json.JSONArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Check internet connection
            if (!NetworkUtils.hasServerConnection()) {
                Log.d("SyncWorker", "No server connection, retrying later")
                return@withContext Result.retry()
            }
            
            // Get pending work orders
            val pendingWorkOrders = OfflineQueueService.getPendingWorkOrders(20)
            
            if (pendingWorkOrders.isEmpty()) {
                Log.d("SyncWorker", "No pending work orders to sync")
                return@withContext Result.success()
            }
            
            Log.d("SyncWorker", "Syncing ${pendingWorkOrders.size} pending work orders")
            
            var successCount = 0
            var failedCount = 0
            
            for (pendingWO in pendingWorkOrders) {
                try {
                    val success = when (pendingWO.requestType) {
                        "create" -> syncCreateWorkOrder(pendingWO)
                        "update_status" -> syncUpdateStatus(pendingWO)
                        "update_pending_done" -> syncUpdatePendingDone(pendingWO)
                        else -> false
                    }
                    
                    if (success) {
                        OfflineQueueService.deletePendingWorkOrderById(pendingWO.id)
                        // Delete photo file if exists
                        pendingWO.photoPath?.let { path ->
                            try {
                                File(path).delete()
                            } catch (e: Exception) {
                                Log.e("SyncWorker", "Error deleting photo: ${e.message}")
                            }
                        }
                        successCount++
                    } else {
                        // Increment retry count
                        val updatedWO = pendingWO.copy(
                            retryCount = pendingWO.retryCount + 1,
                            lastError = "Sync failed"
                        )
                        OfflineQueueService.updatePendingWorkOrder(updatedWO)
                        failedCount++
                    }
                } catch (e: Exception) {
                    Log.e("SyncWorker", "Error syncing work order ${pendingWO.id}: ${e.message}", e)
                    val updatedWO = pendingWO.copy(
                        retryCount = pendingWO.retryCount + 1,
                        lastError = e.message
                    )
                    OfflineQueueService.updatePendingWorkOrder(updatedWO)
                    failedCount++
                }
            }
            
            Log.d("SyncWorker", "Work orders sync completed: $successCount success, $failedCount failed")
            
            // Sync pending projects
            val pendingProjects = OfflineProjectService.getPendingProjects(20)
            
            if (pendingProjects.isNotEmpty()) {
                Log.d("SyncWorker", "Syncing ${pendingProjects.size} pending projects")
                
                var projectSuccessCount = 0
                var projectFailedCount = 0
                
                for (pendingProject in pendingProjects) {
                    try {
                        val success = when (pendingProject.requestType) {
                            "create" -> syncCreateProject(pendingProject)
                            "update" -> syncUpdateProject(pendingProject)
                            else -> false
                        }
                        
                        if (success) {
                            OfflineProjectService.deletePendingProjectById(pendingProject.id)
                            // Delete photo files if exist
                            pendingProject.photoPathsJson?.let { pathsJson ->
                                try {
                                    val pathsArray = JSONArray(pathsJson)
                                    for (i in 0 until pathsArray.length()) {
                                        val path = pathsArray.getString(i)
                                        File(path).delete()
                                    }
                                } catch (e: Exception) {
                                    Log.e("SyncWorker", "Error deleting project photos: ${e.message}")
                                }
                            }
                            projectSuccessCount++
                        } else {
                            val updatedProject = pendingProject.copy(
                                retryCount = pendingProject.retryCount + 1,
                                lastError = "Sync failed"
                            )
                            OfflineProjectService.updatePendingProject(updatedProject)
                            projectFailedCount++
                        }
                    } catch (e: Exception) {
                        Log.e("SyncWorker", "Error syncing project ${pendingProject.id}: ${e.message}", e)
                        val updatedProject = pendingProject.copy(
                            retryCount = pendingProject.retryCount + 1,
                            lastError = e.message
                        )
                        OfflineProjectService.updatePendingProject(updatedProject)
                        projectFailedCount++
                    }
                }
                
                Log.d("SyncWorker", "Projects sync completed: $projectSuccessCount success, $projectFailedCount failed")
            }
            
            // Sync pending maintenance tasks
            val pendingMaintenanceTasks = OfflineMaintenanceService.getPendingMaintenanceTasks(20)
            
            if (pendingMaintenanceTasks.isNotEmpty()) {
                Log.d("SyncWorker", "Syncing ${pendingMaintenanceTasks.size} pending maintenance tasks")
                
                var maintenanceSuccessCount = 0
                var maintenanceFailedCount = 0
                
                for (pendingTask in pendingMaintenanceTasks) {
                    try {
                        val success = when (pendingTask.requestType) {
                            "update_task_status" -> syncMaintenanceTaskStatus(pendingTask)
                            "update_notes_photos" -> syncMaintenanceNotesPhotos(pendingTask)
                            else -> false
                        }
                        
                        if (success) {
                            OfflineMaintenanceService.deletePendingMaintenanceTaskById(pendingTask.id)
                            // Delete photo files if exist
                            pendingTask.photoPathsJson?.let { pathsJson ->
                                try {
                                    val pathsArray = JSONArray(pathsJson)
                                    for (i in 0 until pathsArray.length()) {
                                        val path = pathsArray.getString(i)
                                        File(path).delete()
                                    }
                                } catch (e: Exception) {
                                    Log.e("SyncWorker", "Error deleting maintenance photos: ${e.message}")
                                }
                            }
                            maintenanceSuccessCount++
                        } else {
                            val updatedTask = pendingTask.copy(
                                retryCount = pendingTask.retryCount + 1,
                                lastError = "Sync failed"
                            )
                            OfflineMaintenanceService.updatePendingMaintenanceTask(updatedTask)
                            maintenanceFailedCount++
                        }
                    } catch (e: Exception) {
                        Log.e("SyncWorker", "Error syncing maintenance task ${pendingTask.id}: ${e.message}", e)
                        val updatedTask = pendingTask.copy(
                            retryCount = pendingTask.retryCount + 1,
                            lastError = e.message
                        )
                        OfflineMaintenanceService.updatePendingMaintenanceTask(updatedTask)
                        maintenanceFailedCount++
                    }
                }
                
                Log.d("SyncWorker", "Maintenance tasks sync completed: $maintenanceSuccessCount success, $maintenanceFailedCount failed")
            }
            
            // If there are more pending items, schedule another sync
            val remainingWOCount = OfflineQueueService.getPendingCount()
            val remainingProjectCount = OfflineProjectService.getPendingCount()
            val remainingMaintenanceCount = OfflineMaintenanceService.getPendingCount()
            
            if (remainingWOCount > 0 || remainingProjectCount > 0 || remainingMaintenanceCount > 0) {
                Log.d("SyncWorker", "Scheduling another sync for $remainingWOCount work orders, $remainingProjectCount projects, and $remainingMaintenanceCount maintenance tasks")
                scheduleSync(applicationContext)
            }
            
            Result.success()
        } catch (e: Exception) {
            Log.e("SyncWorker", "Error in sync worker: ${e.message}", e)
            Result.retry()
        }
    }
    
    private suspend fun syncCreateWorkOrder(pendingWO: PendingWorkOrder): Boolean {
        return try {
            if (pendingWO.photoPath != null && File(pendingWO.photoPath).exists()) {
                // Submit with photo
                val result = submitWorkOrderWithPhoto(pendingWO)
                result["status"] == "success" || result["id"] != null || result["success"] == true
            } else {
                // Submit without photo
                val response = RetrofitClient.apiService.submitWorkOrder(
                    propID = pendingWO.propID,
                    orderBy = pendingWO.orderBy,
                    job = pendingWO.job,
                    lokasi = pendingWO.lokasi,
                    category = pendingWO.category,
                    dept = pendingWO.dept,
                    priority = pendingWO.priority,
                    woto = pendingWO.woto,
                    status = pendingWO.status
                )
                response["status"] == "success" || response["id"] != null || response["success"] == true
            }
        } catch (e: Exception) {
            Log.e("SyncWorker", "Error syncing create work order: ${e.message}", e)
            false
        }
    }
    
    private suspend fun syncUpdateStatus(pendingWO: PendingWorkOrder): Boolean {
        return try {
            val woId = pendingWO.woId ?: return false
            val newStatus = pendingWO.newStatus ?: return false
            val userName = pendingWO.userName ?: return false
            
            val response = RetrofitClient.apiService.updateWorkOrderStatus(
                woId = woId,
                status = newStatus,
                userName = userName,
                timeAccept = pendingWO.timeAccept
            )
            response.trim().equals("Success", ignoreCase = true)
        } catch (e: Exception) {
            Log.e("SyncWorker", "Error syncing update status: ${e.message}", e)
            false
        }
    }
    
    private suspend fun syncUpdatePendingDone(pendingWO: PendingWorkOrder): Boolean {
        return try {
            val woId = pendingWO.woId ?: return false
            val status = pendingWO.newStatus ?: return false
            val userName = pendingWO.userName ?: return false
            val remarks = pendingWO.remarks ?: return false
            
            val woIdBody = woId.toRequestBody("text/plain".toMediaTypeOrNull())
            val statusBody = status.toRequestBody("text/plain".toMediaTypeOrNull())
            val userNameBody = userName.toRequestBody("text/plain".toMediaTypeOrNull())
            
            val currentTime = SimpleDateFormat("dd MMM yyyy  HH:mm", Locale.getDefault()).format(Date())
            val finalRemarks = "$currentTime -> ${status.uppercase()}  ( $userName ) : $remarks"
            val remarksBody = finalRemarks.toRequestBody("text/plain".toMediaTypeOrNull())
            
            var doneByBody: okhttp3.RequestBody? = null
            var timeDoneBody: okhttp3.RequestBody? = null
            var timeSpentBody: okhttp3.RequestBody? = null
            
            if (status.lowercase() == "done") {
                doneByBody = userName.toRequestBody("text/plain".toMediaTypeOrNull())
                timeDoneBody = pendingWO.timeDone?.toRequestBody("text/plain".toMediaTypeOrNull())
                timeSpentBody = pendingWO.timeSpent?.toRequestBody("text/plain".toMediaTypeOrNull())
            }
            
            val photoPart = pendingWO.photoPath?.let { path ->
                val file = File(path)
                if (file.exists()) {
                    val requestFile = file.asRequestBody("image/*".toMediaTypeOrNull())
                    val photoName = "${status}${woId}.jpg"
                    MultipartBody.Part.createFormData("file", photoName, requestFile)
                } else {
                    null
                }
            }
            
            val response = RetrofitClient.apiService.updatePendingDone(
                woId = woIdBody,
                status = statusBody,
                userName = userNameBody,
                remarks = remarksBody,
                doneBy = doneByBody,
                timeDone = timeDoneBody,
                timeSpent = timeSpentBody,
                photoFile = photoPart
            )
            
            response.trim().equals("Success", ignoreCase = true)
        } catch (e: Exception) {
            Log.e("SyncWorker", "Error syncing update pending done: ${e.message}", e)
            false
        }
    }
    
    private suspend fun submitWorkOrderWithPhoto(pendingWO: PendingWorkOrder): Map<String, Any> {
        // For now, submit without photo in sync (photo upload can be complex)
        // TODO: Implement multipart upload for photo if needed
        val response = RetrofitClient.apiService.submitWorkOrder(
            propID = pendingWO.propID,
            orderBy = pendingWO.orderBy,
            job = pendingWO.job,
            lokasi = pendingWO.lokasi,
            category = pendingWO.category,
            dept = pendingWO.dept,
            priority = pendingWO.priority,
            woto = pendingWO.woto,
            status = pendingWO.status
        )
        return response
    }
    
    private suspend fun syncCreateProject(pendingProject: PendingProject): Boolean {
        return try {
            val jobsJson = pendingProject.jobsJson ?: return false
            val materialsJson = pendingProject.materialsJson ?: ""
            
            // Prepare RequestBody
            val actionBody = "create".toRequestBody("text/plain".toMediaTypeOrNull())
            val propIDBody = (pendingProject.propID ?: "").toRequestBody("text/plain".toMediaTypeOrNull())
            val projectNameBody = (pendingProject.projectName ?: "").toRequestBody("text/plain".toMediaTypeOrNull())
            val priorityBody = (pendingProject.priority ?: "").toRequestBody("text/plain".toMediaTypeOrNull())
            val lokasiBody = (pendingProject.lokasi ?: "").toRequestBody("text/plain".toMediaTypeOrNull())
            val categoryBody = (pendingProject.category ?: "").toRequestBody("text/plain".toMediaTypeOrNull())
            val orderByBody = (pendingProject.orderBy ?: "").toRequestBody("text/plain".toMediaTypeOrNull())
            val deptBody = (pendingProject.dept ?: "").toRequestBody("text/plain".toMediaTypeOrNull())
            val wotoBody = (pendingProject.woto ?: "").toRequestBody("text/plain".toMediaTypeOrNull())
            val statusBody = (pendingProject.status ?: "new").toRequestBody("text/plain".toMediaTypeOrNull())
            val jobsBody = jobsJson.toRequestBody("text/plain".toMediaTypeOrNull())
            val materialsBody = materialsJson.toRequestBody("text/plain".toMediaTypeOrNull())
            
            // Prepare photos
            val photoParts = mutableListOf<MultipartBody.Part>()
            val jobIndexParts = mutableListOf<MultipartBody.Part>()
            
            pendingProject.photoPathsJson?.let { pathsJson ->
                pendingProject.jobIndexJson?.let { indicesJson ->
                    try {
                        val pathsArray = JSONArray(pathsJson)
                        val indicesArray = JSONArray(indicesJson)
                        
                        for (i in 0 until pathsArray.length()) {
                            val path = pathsArray.getString(i)
                            val file = File(path)
                            if (file.exists() && i < indicesArray.length()) {
                                val requestFile = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
                                val filename = "PROJECT_${pendingProject.propID}_${System.currentTimeMillis()}_$i.jpg"
                                photoParts.add(MultipartBody.Part.createFormData("photos[$i]", filename, requestFile))
                                
                                val jobIndex = indicesArray.getInt(i)
                                jobIndexParts.add(MultipartBody.Part.createFormData("job_index[$i]", jobIndex.toString()))
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("SyncWorker", "Error parsing photo paths: ${e.message}", e)
                    }
                }
            }
            
            val response = RetrofitClient.apiService.createProject(
                action = actionBody,
                propID = propIDBody,
                projectName = projectNameBody,
                priority = priorityBody,
                lokasi = lokasiBody,
                category = categoryBody,
                orderBy = orderByBody,
                dept = deptBody,
                woto = wotoBody,
                status = statusBody,
                jobs = jobsBody,
                materials = if (materialsJson.isNotEmpty()) materialsBody else null,
                photoFiles = if (photoParts.isNotEmpty()) photoParts else null,
                jobIndexParts = if (jobIndexParts.isNotEmpty()) jobIndexParts else null
            )
            
            response["status"] == "success" || response["success"] == true
        } catch (e: Exception) {
            Log.e("SyncWorker", "Error syncing create project: ${e.message}", e)
            false
        }
    }
    
    private suspend fun syncUpdateProject(pendingProject: PendingProject): Boolean {
        return try {
            val projectId = pendingProject.projectId ?: return false
            
            // Use OkHttp directly for update_project.php (similar to UpdateProjectActivity)
            val client = okhttp3.OkHttpClient()
            val requestBodyBuilder = okhttp3.MultipartBody.Builder()
                .setType(okhttp3.MultipartBody.FORM)
                .addFormDataPart("action", "update")
                .addFormDataPart("projectId", projectId)
                .addFormDataPart("propID", pendingProject.propID ?: "")
                .addFormDataPart("status", pendingProject.newStatus ?: "")
                .addFormDataPart("note", pendingProject.note ?: "")
            
            // Add photos
            pendingProject.photoPathsJson?.let { pathsJson ->
                pendingProject.jobIndexJson?.let { indicesJson ->
                    pendingProject.beforePhotoIdsJson?.let { beforeIdsJson ->
                        try {
                            val pathsArray = JSONArray(pathsJson)
                            val indicesArray = JSONArray(indicesJson)
                            val beforeIdsArray = JSONArray(beforeIdsJson)
                            
                            for (i in 0 until pathsArray.length()) {
                                val path = pathsArray.getString(i)
                                val file = File(path)
                                if (file.exists() && i < indicesArray.length() && i < beforeIdsArray.length()) {
                                    val requestFile = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
                                    val filename = "Done_${projectId}_${System.currentTimeMillis()}_$i.jpg"
                                    requestBodyBuilder.addFormDataPart("photos[]", filename, requestFile)
                                    
                                    val beforePhotoId = beforeIdsArray.getString(i)
                                    val jobIndex = indicesArray.getInt(i)
                                    requestBodyBuilder.addFormDataPart("photo_id[]", beforePhotoId)
                                    requestBodyBuilder.addFormDataPart("job_index[]", jobIndex.toString())
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("SyncWorker", "Error parsing update photo paths: ${e.message}", e)
                        }
                    }
                }
            }
            
            val requestBody = requestBodyBuilder.build()
            val request = okhttp3.Request.Builder()
                .url("https://emshotels.net/apiKu/update_project.php")
                .post(requestBody)
                .build()
            
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            val json = org.json.JSONObject(responseBody)
            val statusResponse = json.optString("status", "")
            
            statusResponse == "success"
        } catch (e: Exception) {
            Log.e("SyncWorker", "Error syncing update project: ${e.message}", e)
            false
        }
    }
    
    private suspend fun syncMaintenanceTaskStatus(pendingTask: PendingMaintenanceTask): Boolean {
        return try {
            val taskId = pendingTask.taskNo ?: return false
            val isDone = pendingTask.isDone
            val doneBy = pendingTask.doneBy ?: ""
            
            val requestBody = mapOf(
                "taskId" to taskId,
                "done" to (if (isDone) "1" else "0"),
                "doneby" to doneBy
            )
            
            val response = RetrofitClient.apiService.updateMaintenanceTaskStatus(requestBody)
            response["success"] == true
        } catch (e: Exception) {
            Log.e("SyncWorker", "Error syncing maintenance task status: ${e.message}", e)
            false
        }
    }
    
    private suspend fun syncMaintenanceNotesPhotos(pendingTask: PendingMaintenanceTask): Boolean {
        return try {
            val mntId = pendingTask.eventId ?: pendingTask.mntId ?: return false
            val notes = pendingTask.notes ?: ""
            
            // Load photos from paths
            val photos = mutableListOf<File>()
            pendingTask.photoPathsJson?.let { pathsJson ->
                try {
                    val pathsArray = JSONArray(pathsJson)
                    for (i in 0 until pathsArray.length()) {
                        val path = pathsArray.getString(i)
                        val file = File(path)
                        if (file.exists()) {
                            photos.add(file)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SyncWorker", "Error parsing maintenance photo paths: ${e.message}", e)
                }
            }
            
            // IMPORTANT: Use updateMaintenanceNotesAndPhotos which doesn't change status
            // Status should only be "done" when ALL tasks are completed, not when updating notes/photos
            val response = if (pendingTask.status != null && pendingTask.status == "done") {
                // Only use updateMaintenanceEvent if status was explicitly set to "done" (all tasks completed)
                MaintenanceService.updateMaintenanceEvent(
                    mntId = mntId,
                    status = "done",
                    notes = notes,
                    photos = photos
                )
            } else {
                // Use updateMaintenanceNotesAndPhotos which doesn't change status
                MaintenanceService.updateMaintenanceNotesAndPhotos(
                    mntId = mntId,
                    notes = notes,
                    photos = photos,
                    propID = pendingTask.propID
                )
            }
            
            response["success"] == true
        } catch (e: Exception) {
            Log.e("SyncWorker", "Error syncing maintenance notes/photos: ${e.message}", e)
            false
        }
    }
    
    companion object {
        private const val SYNC_WORK_NAME = "sync_work_orders"
        
        fun scheduleSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            
            val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    10_000L, // 10 seconds minimum backoff
                    java.util.concurrent.TimeUnit.MILLISECONDS
                )
                .build()
            
            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    SYNC_WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    syncRequest
                )
        }
        
        fun schedulePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            
            val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(
                15, java.util.concurrent.TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()
            
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    SYNC_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    syncRequest
                )
        }
    }
}

// Object wrapper untuk memudahkan akses dari activity/fragment
object SyncService {
    fun scheduleSync(context: Context) {
        SyncWorker.scheduleSync(context)
    }
    
    fun schedulePeriodicSync(context: Context) {
        SyncWorker.schedulePeriodicSync(context)
    }
}

