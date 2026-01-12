package com.sofindo.ems.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import java.util.Date

@Entity(tableName = "pending_maintenance_tasks")
@TypeConverters(Converters::class)
data class PendingMaintenanceTask(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    // Request type: "update_task_status", "update_notes_photos"
    val requestType: String,
    
    // For update task status
    val taskId: String? = null,
    val taskNo: String? = null,
    val isDone: Boolean = false,
    val doneBy: String? = null,
    
    // For update notes and photos
    val mntId: String? = null,
    val eventId: String? = null,
    val notes: String? = null,
    val status: String? = null,
    
    // Photo paths (JSON array)
    val photoPathsJson: String? = null,
    
    // Asset info
    val assetNo: String? = null,
    val propID: String? = null,
    
    // Timestamp
    val createdAt: Date = Date(),
    val retryCount: Int = 0,
    val lastError: String? = null
)

































