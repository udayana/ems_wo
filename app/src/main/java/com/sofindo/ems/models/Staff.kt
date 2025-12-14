package com.sofindo.ems.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Staff(
    @Json(name = "id") val id: String,
    @Json(name = "nama") val nama: String,
    @Json(name = "dept") val dept: String? = null,
    @Json(name = "photo") val photo: String? = null
)

