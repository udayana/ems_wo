package com.sofindo.ems.services

import android.content.Context
import com.sofindo.ems.database.EMSDatabase
import com.sofindo.ems.database.PendingWorkOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

object OfflineQueueService {
    
    private var database: EMSDatabase? = null
    
    fun init(context: Context) {
        database = EMSDatabase.getDatabase(context)
    }
    
    private fun getDao() = database?.pendingWorkOrderDao()
        ?: throw IllegalStateException("OfflineQueueService not initialized. Call init() first.")
    
    suspend fun addPendingWorkOrder(workOrder: PendingWorkOrder): Long = withContext(Dispatchers.IO) {
        getDao().insertPendingWorkOrder(workOrder)
    }
    
    suspend fun getPendingWorkOrders(limit: Int = 10): List<PendingWorkOrder> = withContext(Dispatchers.IO) {
        getDao().getPendingWorkOrders(limit)
    }
    
    fun getAllPendingWorkOrdersFlow(): Flow<List<PendingWorkOrder>> {
        return getDao().getAllPendingWorkOrders()
    }
    
    suspend fun getPendingCount(): Int = withContext(Dispatchers.IO) {
        getDao().getPendingCount()
    }
    
    suspend fun updatePendingWorkOrder(workOrder: PendingWorkOrder) = withContext(Dispatchers.IO) {
        getDao().updatePendingWorkOrder(workOrder)
    }
    
    suspend fun deletePendingWorkOrder(workOrder: PendingWorkOrder) = withContext(Dispatchers.IO) {
        getDao().deletePendingWorkOrder(workOrder)
    }
    
    suspend fun deletePendingWorkOrderById(id: Long) = withContext(Dispatchers.IO) {
        getDao().deletePendingWorkOrderById(id)
    }
}

































