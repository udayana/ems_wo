package com.sofindo.ems.services

import android.content.Context
import com.sofindo.ems.database.EMSDatabase
import com.sofindo.ems.database.PendingMaintenanceTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

object OfflineMaintenanceService {
    
    private var database: EMSDatabase? = null
    
    fun init(context: Context) {
        database = EMSDatabase.getDatabase(context)
    }
    
    private fun getDao() = database?.pendingMaintenanceTaskDao()
        ?: throw IllegalStateException("OfflineMaintenanceService not initialized. Call init() first.")
    
    suspend fun addPendingMaintenanceTask(task: PendingMaintenanceTask): Long = withContext(Dispatchers.IO) {
        getDao().insertPendingMaintenanceTask(task)
    }
    
    suspend fun getPendingMaintenanceTasks(limit: Int = 20): List<PendingMaintenanceTask> = withContext(Dispatchers.IO) {
        getDao().getPendingMaintenanceTasks(limit)
    }
    
    fun getAllPendingMaintenanceTasksFlow(): Flow<List<PendingMaintenanceTask>> {
        return getDao().getAllPendingMaintenanceTasks()
    }
    
    suspend fun getPendingCount(): Int = withContext(Dispatchers.IO) {
        getDao().getPendingCount()
    }
    
    suspend fun updatePendingMaintenanceTask(task: PendingMaintenanceTask) = withContext(Dispatchers.IO) {
        getDao().updatePendingMaintenanceTask(task)
    }
    
    suspend fun deletePendingMaintenanceTask(task: PendingMaintenanceTask) = withContext(Dispatchers.IO) {
        getDao().deletePendingMaintenanceTask(task)
    }
    
    suspend fun deletePendingMaintenanceTaskById(id: Long) = withContext(Dispatchers.IO) {
        getDao().deletePendingMaintenanceTaskById(id)
    }
}




















