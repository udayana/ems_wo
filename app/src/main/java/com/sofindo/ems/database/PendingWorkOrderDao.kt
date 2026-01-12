package com.sofindo.ems.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingWorkOrderDao {
    
    @Query("SELECT * FROM pending_workorders ORDER BY createdAt ASC")
    fun getAllPendingWorkOrders(): Flow<List<PendingWorkOrder>>
    
    @Query("SELECT * FROM pending_workorders WHERE id = :id")
    suspend fun getPendingWorkOrderById(id: Long): PendingWorkOrder?
    
    @Query("SELECT * FROM pending_workorders ORDER BY createdAt ASC LIMIT :limit")
    suspend fun getPendingWorkOrders(limit: Int = 10): List<PendingWorkOrder>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingWorkOrder(workOrder: PendingWorkOrder): Long
    
    @Update
    suspend fun updatePendingWorkOrder(workOrder: PendingWorkOrder)
    
    @Delete
    suspend fun deletePendingWorkOrder(workOrder: PendingWorkOrder)
    
    @Query("DELETE FROM pending_workorders WHERE id = :id")
    suspend fun deletePendingWorkOrderById(id: Long)
    
    @Query("SELECT COUNT(*) FROM pending_workorders")
    suspend fun getPendingCount(): Int
    
    @Query("SELECT COUNT(*) FROM pending_workorders WHERE requestType = :requestType")
    suspend fun getPendingCountByType(requestType: String): Int
}

































