package com.sofindo.ems.models

import com.google.gson.annotations.SerializedName
import java.util.Date

data class User(
    @SerializedName("id") val id: String,
    @SerializedName("username") val username: String,
    @SerializedName("email") val email: String,
    @SerializedName("nama") val fullName: String? = null,
    @SerializedName("telp") val phoneNumber: String? = null,
    @SerializedName("photoprofile") val profileImage: String? = null,
    @SerializedName("role") val role: String = "user",
    @SerializedName("propID") val propID: String? = null,
    @SerializedName("dept") val dept: String? = null,
    @SerializedName("createdAt") val createdAt: String? = null,
    @SerializedName("lastLogin") val lastLogin: String? = null
)
