package com.sofindo.ems.models

import org.json.JSONObject

data class ProjectPhoto(
    val photoId: String,
    val projectId: String,
    val photo: String,
    val photoType: String,
    val photoDone: String?,
    val uploadedBy: String?,
    val uploadedAt: String?
) {
    companion object {
        fun fromJson(json: JSONObject): ProjectPhoto {
            return ProjectPhoto(
                photoId = json.optString("photoId", ""),
                projectId = json.optString("projectId", ""),
                photo = json.optString("photo", ""),
                photoType = json.optString("photo_type", ""),
                photoDone = json.optString("photoDone", null)?.takeIf { it.isNotEmpty() },
                uploadedBy = json.optString("uploaded_by", null)?.takeIf { it.isNotEmpty() },
                uploadedAt = json.optString("uploaded_at", null)?.takeIf { it.isNotEmpty() }
            )
        }
    }
}

