package com.sofindo.ems.models

import org.json.JSONArray
import org.json.JSONObject

data class ProjectDetail(
    val id: String,
    val projectName: String,
    val projectId: String,
    val propID: String,
    val dept: String?,
    val lokasi: String?,
    val category: String?,
    val priority: String?,
    val status: String?,
    val createdDate: String?,
    val createdBy: String?,
    val finishDate: String?,
    val assignto: String?,
    val section: String?,
    val grup: String?,
    val remarks: String?,
    val doneBy: String?,
    val woto: String?,
    val mulainya: String?,
    val timeAccept: String?,
    val timeDone: String?,
    val timeSpent: String?,
    val isReview: Int?,
    val createdAt: String?,
    val updatedAt: String?,
    val totalJobs: Int,
    val totalMaterialCost: Double,
    val jobs: List<ProjectJob>,
    val materials: List<ProjectMaterial>
) {
    companion object {
        fun fromJson(json: JSONObject): ProjectDetail {
            val jobsList = mutableListOf<ProjectJob>()
            if (json.has("jobs") && !json.isNull("jobs")) {
                val jobsArray = json.optJSONArray("jobs")
                if (jobsArray != null) {
                    for (i in 0 until jobsArray.length()) {
                        val jobJson = jobsArray.optJSONObject(i)
                        if (jobJson != null) {
                            jobsList.add(ProjectJob.fromJson(jobJson))
                        }
                    }
                }
            }
            
            val materialsList = mutableListOf<ProjectMaterial>()
            if (json.has("materials") && !json.isNull("materials")) {
                val materialsArray = json.optJSONArray("materials")
                if (materialsArray != null) {
                    for (i in 0 until materialsArray.length()) {
                        val materialJson = materialsArray.optJSONObject(i)
                        if (materialJson != null) {
                            materialsList.add(ProjectMaterial.fromJson(materialJson))
                        }
                    }
                }
            }
            
            return ProjectDetail(
                id = json.optString("id", ""),
                projectName = json.optString("projectName", json.optString("project_name", "")),
                projectId = json.optString("projectId", json.optString("project_id", "")),
                propID = json.optString("propID", ""),
                dept = json.optString("dept", null).takeIf { it.isNotEmpty() },
                lokasi = json.optString("lokasi", null).takeIf { it.isNotEmpty() },
                category = json.optString("category", null).takeIf { it.isNotEmpty() },
                priority = json.optString("priority", null).takeIf { it.isNotEmpty() },
                status = json.optString("status", null).takeIf { it.isNotEmpty() },
                createdDate = json.optString("createdDate", json.optString("created_date", null)).takeIf { it.isNotEmpty() },
                createdBy = json.optString("createdBy", json.optString("created_by", null)).takeIf { it.isNotEmpty() },
                finishDate = json.optString("finishDate", null).takeIf { it.isNotEmpty() },
                assignto = json.optString("assignto", null).takeIf { it.isNotEmpty() },
                section = json.optString("section", null).takeIf { it.isNotEmpty() },
                grup = json.optString("grup", null).takeIf { it.isNotEmpty() },
                remarks = json.optString("remarks", null).takeIf { it.isNotEmpty() },
                doneBy = json.optString("doneBy", null).takeIf { it.isNotEmpty() },
                woto = json.optString("woto", null).takeIf { it.isNotEmpty() },
                mulainya = json.optString("mulainya", null).takeIf { it.isNotEmpty() },
                timeAccept = json.optString("timeAccept", null).takeIf { it.isNotEmpty() },
                timeDone = json.optString("timeDone", null).takeIf { it.isNotEmpty() },
                timeSpent = json.optString("timeSpent", null).takeIf { it.isNotEmpty() },
                isReview = null, // Not used for project
                createdAt = json.optString("createdAt", json.optString("created_at", null)).takeIf { it.isNotEmpty() },
                updatedAt = json.optString("updatedAt", json.optString("updated_at", null)).takeIf { it.isNotEmpty() },
                totalJobs = if (json.has("totalJobs") && !json.isNull("totalJobs")) {
                    json.optInt("totalJobs", 0)
                } else {
                    json.optInt("total_jobs", 0)
                },
                totalMaterialCost = if (json.has("totalMaterialCost") && !json.isNull("totalMaterialCost")) {
                    json.optDouble("totalMaterialCost", 0.0)
                } else {
                    json.optDouble("total_material_cost", 0.0)
                },
                jobs = jobsList,
                materials = materialsList
            )
        }
    }
}

