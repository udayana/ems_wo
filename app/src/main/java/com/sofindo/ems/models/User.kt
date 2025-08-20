package com.sofindo.ems.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.Date

@JsonClass(generateAdapter = true)
data class User(
    @Json(name = "id") val id: String,
    @Json(name = "username") val username: String,
    @Json(name = "email") val email: String,
    @Json(name = "nama") val fullName: String? = null,
    @Json(name = "telp") val phoneNumber: String? = null,
    @Json(name = "photoprofile") val profileImage: String? = null,
    @Json(name = "role") val role: String = "user",
    @Json(name = "propID") val propID: String? = null,
    @Json(name = "dept") val dept: String? = null,
    @Json(name = "createdAt") val createdAt: String? = null,
    @Json(name = "lastLogin") val lastLogin: String? = null
)
