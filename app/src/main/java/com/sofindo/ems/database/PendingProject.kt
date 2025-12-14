package com.sofindo.ems.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import java.util.Date

@Entity(tableName = "pending_projects")
@TypeConverters(Converters::class)
data class PendingProject(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    // Request type: "create", "update"
    val requestType: String,
    
    // For create project
    val propID: String? = null,
    val projectName: String? = null,
    val lokasi: String? = null,
    val category: String? = null,
    val priority: String? = null,
    val orderBy: String? = null,
    val dept: String? = null,
    val woto: String? = null,
    val status: String? = null,
    val jobsJson: String? = null, // JSON array of job descriptions
    val materialsJson: String? = null, // JSON array of materials
    
    // For update project
    val projectId: String? = null,
    val newStatus: String? = null,
    val note: String? = null,
    
    // Photo paths (comma-separated or JSON array)
    val photoPathsJson: String? = null, // JSON array of photo paths
    val jobIndexJson: String? = null, // JSON array of job indices for photos
    val beforePhotoIdsJson: String? = null, // JSON array of before photo IDs (for update)
    
    // Timestamp
    val createdAt: Date = Date(),
    val retryCount: Int = 0,
    val lastError: String? = null
)




















