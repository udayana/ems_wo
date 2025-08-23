package com.sofindo.ems.models

import com.google.gson.annotations.SerializedName
import java.util.Date

data class WorkOrder(
    @SerializedName("woId") val woId: String? = null,
    @SerializedName("nour") val nour: String? = null,
    @SerializedName("propID") val propID: String? = null,
    @SerializedName("remark") val remark: String? = null,
    @SerializedName("detail") val detail: String? = null,
    @SerializedName("job") val job: String? = null,  // Job title field
    @SerializedName("remark_manager") val remarkManager: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("photo") val photo: String? = null,
    @SerializedName("photoDone") val photoDone: String? = null,
    @SerializedName("dept") val dept: String? = null,
    @SerializedName("woto") val woto: String? = null,
    @SerializedName("dateCreate") val dateCreate: String? = null,
    @SerializedName("mulainya") val mulainya: String? = null,  // Start date field
    @SerializedName("engineer") val engineer: String? = null,
    @SerializedName("timeStart") val timeStart: String? = null,
    @SerializedName("timeEnd") val timeEnd: String? = null,
    @SerializedName("location") val location: String? = null,
    @SerializedName("lokasi") val lokasi: String? = null,  // Location field
    @SerializedName("orderBy") val orderBy: String? = null,  // Order by field
    @SerializedName("priority") val priority: String? = null,
    @SerializedName("category") val category: String? = null,
    @SerializedName("estimatedTime") val estimatedTime: String? = null,
    @SerializedName("actualTime") val actualTime: String? = null,
    @SerializedName("cost") val cost: String? = null,
    @SerializedName("parts") val parts: String? = null,
    @SerializedName("lastUpdate") val lastUpdate: String? = null
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
    
    val displayOrderBy: String
        get() = orderBy ?: "Unknown"
    
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
            "done" -> "#27AE60"
            "pending" -> "#F1C40F"
            "on progress" -> "#F39C12"
            "received" -> "#3498DB"
            "new", null, "" -> "#95A5A6"
            else -> "#95A5A6"
        }
}

data class StatusCount(
    @SerializedName("status") val status: String? = null,
    @SerializedName("count") val count: Int = 0
)
