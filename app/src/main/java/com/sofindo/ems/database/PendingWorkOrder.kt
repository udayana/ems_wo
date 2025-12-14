package com.sofindo.ems.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import java.util.Date

@Entity(tableName = "pending_workorders")
@TypeConverters(Converters::class)
data class PendingWorkOrder(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    // WorkOrder data
    val propID: String,
    val orderBy: String,
    val job: String,
    val lokasi: String,
    val category: String,
    val dept: String,
    val priority: String,
    val woto: String,
    val status: String = "new",
    
    // Photo path (stored locally)
    val photoPath: String? = null,
    
    // Request type: "create", "update_status", "update_pending_done"
    val requestType: String,
    
    // Additional data for different request types
    val woId: String? = null, // For update requests
    val newStatus: String? = null, // For status update
    val remarks: String? = null, // For pending/done
    val userName: String? = null, // For status update
    val timeAccept: String? = null, // For received status
    val doneBy: String? = null, // For done status
    val timeDone: String? = null, // For done status
    val timeSpent: String? = null, // For done status
    
    // Timestamp
    val createdAt: Date = Date(),
    val retryCount: Int = 0,
    val lastError: String? = null
)




















