package com.sofindo.ems.services

import android.content.Context
import com.sofindo.ems.database.EMSDatabase
import com.sofindo.ems.database.PendingProject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

object OfflineProjectService {
    
    private var database: EMSDatabase? = null
    
    fun init(context: Context) {
        database = EMSDatabase.getDatabase(context)
    }
    
    private fun getDao() = database?.pendingProjectDao()
        ?: throw IllegalStateException("OfflineProjectService not initialized. Call init() first.")
    
    suspend fun addPendingProject(project: PendingProject): Long = withContext(Dispatchers.IO) {
        getDao().insertPendingProject(project)
    }
    
    suspend fun getPendingProjects(limit: Int = 10): List<PendingProject> = withContext(Dispatchers.IO) {
        getDao().getPendingProjects(limit)
    }
    
    fun getAllPendingProjectsFlow(): Flow<List<PendingProject>> {
        return getDao().getAllPendingProjects()
    }
    
    suspend fun getPendingCount(): Int = withContext(Dispatchers.IO) {
        getDao().getPendingCount()
    }
    
    suspend fun updatePendingProject(project: PendingProject) = withContext(Dispatchers.IO) {
        getDao().updatePendingProject(project)
    }
    
    suspend fun deletePendingProject(project: PendingProject) = withContext(Dispatchers.IO) {
        getDao().deletePendingProject(project)
    }
    
    suspend fun deletePendingProjectById(id: Long) = withContext(Dispatchers.IO) {
        getDao().deletePendingProjectById(id)
    }
}




















