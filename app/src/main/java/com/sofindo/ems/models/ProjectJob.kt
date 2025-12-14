package com.sofindo.ems.models

import org.json.JSONArray
import org.json.JSONObject

data class ProjectJob(
    val jobIndex: Int,
    val jobDescription: String,
    val photos: List<ProjectPhoto>
) {
    companion object {
        fun fromJson(json: JSONObject): ProjectJob {
            val photosList = mutableListOf<ProjectPhoto>()
            if (json.has("photos") && !json.isNull("photos")) {
                val photosArray = json.optJSONArray("photos")
                if (photosArray != null) {
                    for (i in 0 until photosArray.length()) {
                        val photoJson = photosArray.optJSONObject(i)
                        if (photoJson != null) {
                            photosList.add(ProjectPhoto.fromJson(photoJson))
                        }
                    }
                }
            }
            
            return ProjectJob(
                jobIndex = json.optInt("job_index", 0),
                jobDescription = json.optString("job_description", ""),
                photos = photosList
            )
        }
    }
}

