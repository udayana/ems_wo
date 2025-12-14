package com.sofindo.ems.api

import com.sofindo.ems.models.Staff
import com.sofindo.ems.models.User
import okhttp3.MultipartBody
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

interface ApiService {
    
    @FormUrlEncoded
    @POST("login.php")
    suspend fun login(
        @Field("email") emailOrHP: String,
        @Field("password") password: String
    ): Map<String, Any>
    
    @FormUrlEncoded
    @POST("register_user.php")
    suspend fun registerUser(
        @Field("aid") aid: String,
        @Field("nama") nama: String,
        @Field("email") email: String,
        @Field("telp") telp: String,
        @Field("dept") dept: String,
        @Field("password") password: String,
        @Field("language") language: String = "id"
    ): Map<String, Any>
    
    // GPS Registration APIs - exactly like iOS
    @FormUrlEncoded
    @POST("get_property_by_location.php")
    suspend fun getPropertyByLocation(
        @Field("lat") latitude: Double,
        @Field("lng") longitude: Double,
        @Field("radius") radius: Double = 500.0
    ): Map<String, Any>
    
    @FormUrlEncoded
    @POST("search_property.php")
    suspend fun searchProperty(
        @Field("query") query: String
    ): Map<String, Any>
    
    @FormUrlEncoded
    @POST("validate_property_location.php")
    suspend fun validatePropertyLocation(
        @Field("aid") aid: String,
        @Field("lat") latitude: Double,
        @Field("lng") longitude: Double,
        @Field("radius") radius: Double = 500.0
    ): Map<String, Any>
    
    @FormUrlEncoded
    @POST("check_login.php")
    suspend fun checkLogin(
        @Field("emailOrHP") emailOrHP: String,
        @Field("password") password: String
    ): Map<String, Any>
    
    // Profile APIs - exactly like Flutter
    @FormUrlEncoded
    @POST("user_profile.php")
    suspend fun getUserProfile(
        @Field("action") action: String = "get",
        @Field("id") id: String
    ): Map<String, Any>
    
    @FormUrlEncoded
    @POST("user_profile.php")
    suspend fun updateUserProfile(
        @Field("action") action: String = "update",
        @Field("id") id: String,
        @Field("fullName") fullName: String,
        @Field("email") email: String,
        @Field("phoneNumber") phoneNumber: String
    ): Map<String, Any>
    
    // Upload profile photo - exactly like Flutter
    @Multipart
    @POST("upload_profile.php")
    suspend fun uploadProfilePhoto(
        @Part("userID") userID: okhttp3.RequestBody,
        @Part photo: MultipartBody.Part
    ): Map<String, Any>
    
    // Work Order APIs - exactly like Flutter
    @GET("baca_wo.php")
    suspend fun getWorkOrders(
        @Query("propID") propID: String,
        @Query("woto") woto: String? = null,
        @Query("status") status: String? = "",
        @Query("page") page: Int = 1
    ): List<Map<String, Any>>
    
    @GET("get_all_statuses.php")
    suspend fun getAllStatuses(
        @Query("propID") propID: String,
        @Query("woto") woto: String? = null
    ): List<Map<String, Any>>
    
    // Outbox APIs - exactly like Flutter
    @FormUrlEncoded
    @POST("cari_wo_out.php")
    suspend fun getWorkOrdersOut(
        @Field("propID") propID: String,
        @Field("orderBy") orderBy: String,
        @Field("status") status: String = "",
        @Field("page") page: Int = 1,
        @Field("userDept") userDept: String = ""
    ): List<Map<String, Any>>
    
    // Submit work order - exactly like Flutter
    @FormUrlEncoded
    @POST("submit_wo.php")
    suspend fun submitWorkOrder(
        @Field("propID") propID: String,
        @Field("orderBy") orderBy: String,
        @Field("job") job: String,
        @Field("lokasi") lokasi: String,
        @Field("category") category: String,
        @Field("dept") dept: String,
        @Field("priority") priority: String,
        @Field("woto") woto: String,
        @Field("status") status: String = "new"  // ✅ Set default status to "new"
    ): Map<String, Any>
    
    // Update work order status - exactly like Flutter
    @FormUrlEncoded
    @POST("update_status_wo.php")
    suspend fun updateWorkOrderStatus(
        @Field("woId") woId: String,
        @Field("status") status: String,
        @Field("userName") userName: String,
        @Field("timeAccept") timeAccept: String? = null
    ): String
    
    // Get departments - exactly like Flutter
    @FormUrlEncoded
    @POST("get_dept.php")
    suspend fun getDepartments(
        @Field("propID") propID: String
    ): List<String>
    
    // Get categories - exactly like Flutter
    @FormUrlEncoded
    @POST("get_category.php")
    suspend fun getCategories(
        @Field("propID") propID: String
    ): List<String>
    
    // Get locations - exactly like Flutter
    @FormUrlEncoded
    @POST("get_lokasi.php")
    suspend fun getLocations(
        @Field("propID") propID: String
    ): List<String>
    
    // Get property name - exactly like Flutter
    @GET("get_propName.php")
    suspend fun getPropertyName(
        @Query("propID") propID: String
    ): Map<String, Any>
    
    // Search work orders - exactly like Flutter
    @GET("search_wo.php")
    suspend fun searchWorkOrders(
        @Query("propID") propID: String,
        @Query("search") searchTerm: String
    ): List<Map<String, Any>>
    
    // Delete work order - exactly like Flutter
    @FormUrlEncoded
    @POST("delete_wo.php")
    suspend fun deleteWorkOrder(
        @Field("woId") woId: String
    ): Map<String, Any>
    
    // Get work order by ID - exactly like Flutter
    @GET("get_wo_by_id.php")
    suspend fun getWorkOrderById(
        @Query("woId") woId: String
    ): Map<String, Any>
    
    // Update work order - exactly like Flutter
    @FormUrlEncoded
    @POST("update_wo.php")
    suspend fun updateWorkOrder(
        @Field("woId") woId: String,
        @Field("lokasi") lokasi: String,
        @Field("job") job: String,
        @Field("priority") priority: String,
        @Field("category") category: String,
        @Field("woto") woto: String
    ): Map<String, Any>
    
    // Search work orders by text - exactly like Flutter
    @FormUrlEncoded
    @POST("cari_wo.php")
    suspend fun searchWorkOrdersByText(
        @Field("propID") propID: String,
        @Field("searchText") searchText: String
    ): List<Map<String, Any>>
    
    // Search work orders out - exactly like iOS bacaWOOut.swift
    @FormUrlEncoded
    @POST("cari_wo_out.php")
    suspend fun searchWorkOrdersOut(
        @Field("propID") propID: String,
        @Field("orderBy") orderBy: String? = null,
        @Field("dept") dept: String? = null,
        @Field("status") status: String,
        @Field("page") page: Int
    ): List<Map<String, Any>>
    
    // Count new work orders - exactly like Flutter
    @GET("count_new_wo.php")
    suspend fun countNewWorkOrders(
        @Query("propID") propID: String
    ): Map<String, Any>
    
    // Update pending/done - exactly like Flutter
    @Multipart
    @POST("update_pending_done.php")
    suspend fun updatePendingDone(
        @Part("woId") woId: okhttp3.RequestBody,
        @Part("status") status: okhttp3.RequestBody,
        @Part("userName") userName: okhttp3.RequestBody,
        @Part("remarks") remarks: okhttp3.RequestBody,
        @Part("doneBy") doneBy: okhttp3.RequestBody? = null,
        @Part("timeDone") timeDone: okhttp3.RequestBody? = null,
        @Part("timeSpent") timeSpent: okhttp3.RequestBody? = null,
        @Part photoFile: MultipartBody.Part? = null
    ): String
    
    // Asset API - exactly like Flutter
    @GET("get_asset_detail.php")
    suspend fun getAssetDetail(
        @Query("noAsset") noAsset: String
    ): Map<String, Any>
    
    // Asset Inventory APIs for AssetFragment - using Assets_edit.php
    @FormUrlEncoded
    @POST("Assets_edit.php")
    suspend fun getInventoryLocations(
        @Field("action") action: String = "get_locations",
        @Field("propID") propID: String
    ): List<String>
    
    @FormUrlEncoded
    @POST("Assets_edit.php")
    suspend fun searchInventory(
        @Field("action") action: String = "search",
        @Field("propID") propID: String,
        @Field("search") searchText: String,
        @Field("lokasi") lokasi: String? = null
    ): List<Map<String, Any>>
    
    @FormUrlEncoded
    @POST("Assets_edit.php")
    suspend fun getInventoryDetail(
        @Field("action") action: String = "get_detail",
        @Field("no") no: Int? = null,
        @Field("Property") property: String? = null,
        @Field("Lokasi") lokasi: String? = null,
        @Field("propID") propID: String? = null
    ): Map<String, Any>
    
    @FormUrlEncoded
    @POST("Assets_edit.php")
    suspend fun updateInventory(
        @Field("action") action: String = "update",
        @Field("no") no: String,
        @Field("propID") propID: String,
        @Field("tgl") tgl: String,
        @Field("mntld") mntld: String? = null,
        @Field("Category") category: String,
        @Field("Lokasi") lokasi: String? = null,
        @Field("Property") property: String? = null,
        @Field("Merk") merk: String? = null,
        @Field("Model") model: String? = null,
        @Field("serno") serno: String,
        @Field("Capacity") capacity: String? = null,
        @Field("DatePurchased") datePurchased: String? = null,
        @Field("Suplier") suplier: String,
        @Field("Keterangan") keterangan: String
    ): Map<String, Any>

    @FormUrlEncoded
    @POST("Assets_edit.php")
    suspend fun insertInventory(
        @Field("action") action: String = "insert",
        @Field("propID") propID: String,
        @Field("Category") category: String,
        @Field("Lokasi") lokasi: String? = null,
        @Field("Property") property: String? = null,
        @Field("Merk") merk: String? = null,
        @Field("Model") model: String? = null,
        @Field("serno") serno: String,
        @Field("Capacity") capacity: String? = null,
        @Field("DatePurchased") datePurchased: String? = null,
        @Field("Suplier") suplier: String,
        @Field("Keterangan") keterangan: String
    ): Map<String, Any>

    @FormUrlEncoded
    @POST("Assets_edit.php")
    suspend fun deleteInventory(
        @Field("action") action: String = "delete",
        @Field("no") no: Int
    ): Map<String, Any>
    
    @Multipart
    @POST("Assets_edit.php")
    suspend fun uploadInventoryPhoto(
        @Part("action") action: okhttp3.RequestBody,
        @Part("no") no: okhttp3.RequestBody,
        @Part photoFile: MultipartBody.Part,
        @Part thumbFile: MultipartBody.Part? = null
    ): Map<String, Any>
    
    // Maintenance APIs - exactly like Flutter
    @GET("get_maintenance_this_week.php")
    suspend fun getMaintenanceThisWeek(
        @Query("propID") propID: String
    ): Map<String, Any>
    
    @GET("get_maintask_job.php")
    suspend fun getMaintenanceTaskJob(
        @Query("noAssets") noAssets: String,
        @Query("propID") propID: String,
        @Query("eventId") eventId: String
    ): Map<String, Any>
    
    @POST("update_maintask_status.php")
    suspend fun updateMaintenanceTaskStatus(
        @retrofit2.http.Body requestBody: Map<String, String>
    ): Map<String, Any>
    
    @GET("maintenance_notes.php")
    suspend fun getMaintenanceNotes(
        @Query("mntId") mntId: String,
        @Query("propID") propID: String? = null
    ): Map<String, Any>
    
    @POST("maintenance_notes.php")
    suspend fun updateMaintenanceNotes(
        @retrofit2.http.Body requestBody: Map<String, String>
    ): Map<String, Any>
    
    @POST("update_maintenance_event.php")
    suspend fun updateMaintenanceEvent(
        @retrofit2.http.Body requestBody: Map<String, String>
    ): Map<String, Any>
    
    @Multipart
    @POST("update_maintenance_event.php")
    suspend fun updateMaintenanceEventWithPhotos(
        @Part("mntId") mntId: okhttp3.RequestBody,
        @Part("status") status: okhttp3.RequestBody,
        @Part("doneDate") doneDate: okhttp3.RequestBody,
        @Part("notes") notes: okhttp3.RequestBody,
        @Part photoFiles: List<MultipartBody.Part>
    ): Map<String, Any>
    
    // Upload maintenance photos separately
    @Multipart
    @POST("uploadPhotoMaintenance.php")
    suspend fun uploadMaintenancePhotos(
        @Part photoFiles: List<MultipartBody.Part>
    ): Map<String, Any>
    
    @GET("get_maintenance_history.php")
    suspend fun getMaintenanceHistory(
        @Query("mntId") mntId: String,
        @Query("propID") propID: String
    ): Map<String, Any>
    
    @GET("asset_schedule.php")
    suspend fun getAssetSchedule(
        @Query("noAssets") noAssets: String,
        @Query("propID") propID: String
    ): Map<String, Any>
    
    // Support Ticket APIs - exactly like Flutter
    
    @FormUrlEncoded
    @POST("submit_support_ticket.php")
    suspend fun submitSupportTicket(
        @Field("name") name: String,
        @Field("email") email: String,
        @Field("mobile_number") mobileNumber: String?,
        @Field("issue") issue: String,
        @Field("description") description: String,
        @Field("screenshot_path") screenshotPath: String? = null
    ): Map<String, Any>
    
    @Multipart
    @POST("upload_support_attachment.php")
    suspend fun uploadSupportAttachment(
        @Part attachment: MultipartBody.Part
    ): Map<String, Any>
    
    // Utility APIs - exactly like Swift
    @FormUrlEncoded
    @POST("utility_name.php")
    suspend fun getUtilities(
        @Field("propID") propID: String
    ): List<Map<String, Any>>
    
    @FormUrlEncoded
    @POST("utility_link.php")
    suspend fun getUtilityLinks(
        @Field("propID") propID: String
    ): List<Map<String, Any>>

    // Save FCM token (follow existing PHP in ems_flutter/api/save_token.php)
    @FormUrlEncoded
    @POST("save_token.php")
    suspend fun saveFcmToken(
        @Field("token") token: String,
        @Field("email") email: String,
        @Field("dept") dept: String
    ): String
    
    // Send FCM notification for work order - using existing v1 endpoint
    @FormUrlEncoded
    @POST("send_wo_fcm_v1.php")
    suspend fun sendWorkOrderNotification(
        @Field("woto") woto: String,
        @Field("title") title: String,
        @Field("body") body: String,
        @Field("propID") propID: String? = null // Optional property ID
    ): String
    
    // Forgot Password APIs - exactly like iOS
    @POST("check_email.php")
    suspend fun checkEmailAndSendOTP(
        @retrofit2.http.Body requestBody: Map<String, String>
    ): Map<String, Any>
    
    @POST("verify_otp.php")
    suspend fun verifyOTP(
        @retrofit2.http.Body requestBody: Map<String, String>
    ): Map<String, Any>
    
    @POST("verifikasi_OTP.php")
    suspend fun changePassword(
        @retrofit2.http.Body requestBody: Map<String, String>
    ): Map<String, Any>
    
    // Assign Work Order APIs - exactly like Swift
    @FormUrlEncoded
    @POST("assign_to.php")
    suspend fun getStaffList(
        @Field("action") action: String = "get_staff",
        @Field("propID") propID: String,
        @Field("dept") dept: String
    ): List<Staff>
    
    @FormUrlEncoded
    @POST("assign_to.php")
    suspend fun assignWorkOrder(
        @Field("action") action: String = "assign_wo",
        @Field("namaAssign") namaAssign: String,
        @Field("woId") woId: String? = null,
        @Field("nour") nour: String? = null,
        @Field("propID") propID: String? = null
    ): Map<String, Any>
    
    // User Review API - exactly like Swift
    @FormUrlEncoded
    @POST("user_review.php")
    suspend fun submitUserReview(
        @Field("action") action: String = "save",
        @Field("propID") propID: String,
        @Field("woId") woId: String,
        @Field("reviewer_id") reviewerId: String,
        @Field("rating") rating: Int,
        @Field("comment") comment: String
    ): Map<String, Any>
    
    // Update Review API
    @FormUrlEncoded
    @POST("user_review.php")
    suspend fun updateUserReview(
        @Field("action") action: String = "update",
        @Field("review_id") reviewId: Int,
        @Field("propID") propID: String,
        @Field("woId") woId: String,
        @Field("reviewer_id") reviewerId: String,
        @Field("rating") rating: Int,
        @Field("comment") comment: String,
        @Field("edit_reason") editReason: String = "Review updated by user"
    ): Map<String, Any>
    
    // Get Review API
    @FormUrlEncoded
    @POST("user_review.php")
    suspend fun getUserReview(
        @Field("action") action: String = "get",
        @Field("propID") propID: String,
        @Field("woId") woId: String? = null,
        @Field("reviewer_id") reviewerId: String? = null,
        @Field("technician_id") technicianId: String? = null
    ): Map<String, Any>
    
    // Create Project API - exactly like iOS
    @Multipart
    @POST("create_project.php")
    suspend fun createProject(
        @Part("action") action: okhttp3.RequestBody,
        @Part("propID") propID: okhttp3.RequestBody,
        @Part("projectName") projectName: okhttp3.RequestBody,
        @Part("priority") priority: okhttp3.RequestBody,
        @Part("lokasi") lokasi: okhttp3.RequestBody,
        @Part("category") category: okhttp3.RequestBody,
        @Part("orderBy") orderBy: okhttp3.RequestBody,
        @Part("dept") dept: okhttp3.RequestBody,
        @Part("woto") woto: okhttp3.RequestBody,
        @Part("status") status: okhttp3.RequestBody,
        @Part("jobs") jobs: okhttp3.RequestBody,
        @Part("materials") materials: okhttp3.RequestBody? = null,
        @Part photoFiles: List<MultipartBody.Part>? = null,
        @Part jobIndexParts: List<MultipartBody.Part>? = null
    ): Map<String, Any>
    
    // Get admin setting - check photoDone requirement
    @FormUrlEncoded
    @POST("get_admin_setting.php")
    suspend fun getAdminSetting(
        @Field("propID") propID: String
    ): Map<String, Any>
    
}
