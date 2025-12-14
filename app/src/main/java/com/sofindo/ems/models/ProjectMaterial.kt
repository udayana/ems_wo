package com.sofindo.ems.models

import org.json.JSONObject

data class ProjectMaterial(
    val costId: String,
    val projectId: String,
    val materialName: String,
    val quantity: Double,
    val unitPrice: Double,
    val amount: Double,
    val unit: String,
    val notes: String?,
    val createdAt: String?,
    val updatedAt: String?
) {
    companion object {
        fun fromJson(json: JSONObject): ProjectMaterial {
            return ProjectMaterial(
                costId = json.optString("costId", ""),
                projectId = json.optString("projectId", ""),
                materialName = json.optString("material_name", ""),
                quantity = json.optDouble("quantity", 0.0),
                unitPrice = json.optDouble("unit_price", 0.0),
                amount = json.optDouble("amount", 0.0),
                unit = json.optString("unit", ""),
                notes = json.optString("notes", null).takeIf { it.isNotEmpty() },
                createdAt = json.optString("created_at", null).takeIf { it.isNotEmpty() },
                updatedAt = json.optString("updated_at", null).takeIf { it.isNotEmpty() }
            )
        }
    }
}

