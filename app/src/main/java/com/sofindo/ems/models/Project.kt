package com.sofindo.ems.models

import org.json.JSONObject

data class Project(
    val id: String,
    val projectName: String,
    val projectId: String?,
    val propID: String,
    val dept: String?,
    val lokasi: String?,
    val category: String?,
    val priority: String?,
    val status: String?,
    val createdDate: String?,
    val createdBy: String?,
    val totalJobs: Int?,
    val totalMaterialCost: Double?
) {
    companion object {
        fun fromJson(json: JSONObject): Project {
            return Project(
                id = json.optString("id", ""),
                projectName = json.optString("projectName", json.optString("project_name", "")),
                projectId = json.optString("projectId", json.optString("project_id", null)).takeIf { it.isNotEmpty() },
                propID = json.optString("propID", ""),
                dept = json.optString("dept", null).takeIf { it.isNotEmpty() },
                lokasi = json.optString("lokasi", null).takeIf { it.isNotEmpty() },
                category = json.optString("category", null).takeIf { it.isNotEmpty() },
                priority = json.optString("priority", null).takeIf { it.isNotEmpty() },
                status = json.optString("status", null).takeIf { it.isNotEmpty() },
                createdDate = json.optString("createdDate", json.optString("created_date", null)).takeIf { it.isNotEmpty() },
                createdBy = json.optString("createdBy", json.optString("created_by", null)).takeIf { it.isNotEmpty() },
                totalJobs = if (json.has("totalJobs") && !json.isNull("totalJobs")) {
                    json.optInt("totalJobs", 0).takeIf { it > 0 }
                } else if (json.has("total_jobs") && !json.isNull("total_jobs")) {
                    json.optInt("total_jobs", 0).takeIf { it > 0 }
                } else null,
                totalMaterialCost = if (json.has("totalMaterialCost") && !json.isNull("totalMaterialCost")) {
                    json.optDouble("totalMaterialCost", 0.0).takeIf { it > 0 }
                } else if (json.has("total_material_cost") && !json.isNull("total_material_cost")) {
                    json.optDouble("total_material_cost", 0.0).takeIf { it > 0 }
                } else null
            )
        }
    }
}

