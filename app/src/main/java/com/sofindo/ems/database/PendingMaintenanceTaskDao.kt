package com.sofindo.ems.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingMaintenanceTaskDao {
    
    @Query("SELECT * FROM pending_maintenance_tasks ORDER BY createdAt ASC")
    fun getAllPendingMaintenanceTasks(): Flow<List<PendingMaintenanceTask>>
    
    @Query("SELECT * FROM pending_maintenance_tasks WHERE id = :id")
    suspend fun getPendingMaintenanceTaskById(id: Long): PendingMaintenanceTask?
    
    @Query("SELECT * FROM pending_maintenance_tasks ORDER BY createdAt ASC LIMIT :limit")
    suspend fun getPendingMaintenanceTasks(limit: Int = 20): List<PendingMaintenanceTask>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingMaintenanceTask(task: PendingMaintenanceTask): Long
    
    @Update
    suspend fun updatePendingMaintenanceTask(task: PendingMaintenanceTask)
    
    @Delete
    suspend fun deletePendingMaintenanceTask(task: PendingMaintenanceTask)
    
    @Query("DELETE FROM pending_maintenance_tasks WHERE id = :id")
    suspend fun deletePendingMaintenanceTaskById(id: Long)
    
    @Query("SELECT COUNT(*) FROM pending_maintenance_tasks")
    suspend fun getPendingCount(): Int
    
    @Query("SELECT COUNT(*) FROM pending_maintenance_tasks WHERE requestType = :requestType")
    suspend fun getPendingCountByType(requestType: String): Int
}

































