package com.sofindo.ems.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.Date

@JsonClass(generateAdapter = true)
data class WorkOrder(
    @Json(name = "woId") val woId: String? = null,
    @Json(name = "nour") val nour: String? = null,
    @Json(name = "propID") val propID: String? = null,
    @Json(name = "remark") val remark: String? = null,
    @Json(name = "detail") val detail: String? = null,
    @Json(name = "job") val job: String? = null,  // Job title field
    @Json(name = "remark_manager") val remarkManager: String? = null,
    @Json(name = "status") val status: String? = null,
    @Json(name = "photo") val photo: String? = null,
    @Json(name = "photoDone") val photoDone: String? = null,
    @Json(name = "dept") val dept: String? = null,
    @Json(name = "woto") val woto: String? = null,
    @Json(name = "dateCreate") val dateCreate: String? = null,
    @Json(name = "engineer") val engineer: String? = null,
    @Json(name = "timeStart") val timeStart: String? = null,
    @Json(name = "timeEnd") val timeEnd: String? = null,
    @Json(name = "location") val location: String? = null,
    @Json(name = "lokasi") val lokasi: String? = null,  // Location field
    @Json(name = "priority") val priority: String? = null,
    @Json(name = "category") val category: String? = null,
    @Json(name = "estimatedTime") val estimatedTime: String? = null,
    @Json(name = "actualTime") val actualTime: String? = null,
    @Json(name = "cost") val cost: String? = null,
    @Json(name = "parts") val parts: String? = null,
    @Json(name = "lastUpdate") val lastUpdate: String? = null
) {
    // Helper properties
    val displayStatus: String
        get() = when {
            status.isNullOrEmpty() -> "NEW"
            else -> status.uppercase()
        }
    
    val displayRemark: String
        get() = remark ?: "No description"
    
    val displayLocation: String 
        get() = lokasi ?: location ?: "No location"
    
    val displayEngineer: String
        get() = engineer ?: "Not assigned"
    
    val hasPhotoBefore: Boolean
        get() = !photo.isNullOrEmpty()
    
    val hasPhotoAfter: Boolean
        get() = !photoDone.isNullOrEmpty()
    
    val photoBeforeUrl: String?
        get() = if (hasPhotoBefore) "https://emshotels.net/manager/workorder/photo/$photo" else null
    
    val photoAfterUrl: String?
        get() = if (hasPhotoAfter) "https://emshotels.net/manager/workorder/photo/$photoDone" else null
    
    val priorityColor: String
        get() = when (priority?.lowercase()) {
            "high", "urgent" -> "#E74C3C"
            "medium" -> "#F39C12"
            "low" -> "#27AE60"
            else -> "#7F8C8D"
        }
    
    val statusColor: String
        get() = when (status?.lowercase()) {
            "new", null, "" -> "#E74C3C"
            "received" -> "#F39C12"
            "on progress" -> "#3498DB"
            "pending" -> "#9B59B6"
            "done" -> "#27AE60"
            else -> "#7F8C8D"
        }
}

@JsonClass(generateAdapter = true)
data class StatusCount(
    @Json(name = "status") val status: String? = null,
    @Json(name = "count") val count: Int = 0
)
