package com.sofindo.ems.models

data class Maintenance(
    val id: Int,
    val title: String,
    val description: String,
    val startDate: String,
    val endDate: String,
    val status: String,
    val propID: String,
    val formattedDate: String
) {
    companion object {
        fun fromJson(json: Map<String, Any>): Maintenance {
            return Maintenance(
                id = (json["id"] as? Number)?.toInt() ?: 0,
                title = json["title"] as? String ?: "",
                description = json["description"] as? String ?: "",
                startDate = json["start_date"] as? String ?: "",
                endDate = json["end_date"] as? String ?: "",
                status = json["status"] as? String ?: "",
                propID = json["propID"] as? String ?: "",
                formattedDate = json["formatted_date"] as? String ?: ""
            )
        }
    }

    fun toJson(): Map<String, Any> {
        return mapOf(
            "id" to id,
            "title" to title,
            "description" to description,
            "start_date" to startDate,
            "end_date" to endDate,
            "status" to status,
            "propID" to propID,
            "formatted_date" to formattedDate
        )
    }
}
