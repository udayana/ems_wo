package com.sofindo.ems.api

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
    @POST("check_login.php")
    suspend fun checkLogin(
        @Field("emailOrHP") emailOrHP: String,
        @Field("password") password: String
    ): Map<String, Any>
    
    // Profile APIs - sama persis dengan Flutter
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
    
    // Upload profile photo - sama persis dengan Flutter
    @Multipart
    @POST("upload_profile.php")
    suspend fun uploadProfilePhoto(
        @Part("userID") userID: okhttp3.RequestBody,
        @Part photo: MultipartBody.Part
    ): Map<String, Any>
    
    // Work Order APIs - sama persis dengan Flutter
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
    
    // Outbox APIs - sama persis dengan Flutter
    @FormUrlEncoded
    @POST("cari_wo_out.php")
    suspend fun getWorkOrdersOut(
        @Field("propID") propID: String,
        @Field("orderBy") orderBy: String,
        @Field("status") status: String = "",
        @Field("page") page: Int = 1,
        @Field("userDept") userDept: String = ""
    ): List<Map<String, Any>>
    
    // Submit work order - sama persis dengan Flutter
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
        @Field("woto") woto: String
    ): Map<String, Any>
    
    // Update work order status - sama persis dengan Flutter
    @FormUrlEncoded
    @POST("update_status_wo.php")
    suspend fun updateWorkOrderStatus(
        @Field("woId") woId: String,
        @Field("status") status: String,
        @Field("userName") userName: String,
        @Field("timeAccept") timeAccept: String? = null
    ): String
    
    // Get departments - sama persis dengan Flutter
    @FormUrlEncoded
    @POST("get_dept.php")
    suspend fun getDepartments(
        @Field("propID") propID: String
    ): List<String>
    
    // Get categories - sama persis dengan Flutter
    @FormUrlEncoded
    @POST("get_category.php")
    suspend fun getCategories(
        @Field("propID") propID: String
    ): List<String>
    
    // Get locations - sama persis dengan Flutter
    @FormUrlEncoded
    @POST("get_lokasi.php")
    suspend fun getLocations(
        @Field("propID") propID: String
    ): List<String>
    
    // Get property name - sama persis dengan Flutter
    @GET("get_propName.php")
    suspend fun getPropertyName(
        @Query("propID") propID: String
    ): Map<String, Any>
    
    // Search work orders - sama persis dengan Flutter
    @GET("search_wo.php")
    suspend fun searchWorkOrders(
        @Query("propID") propID: String,
        @Query("search") searchTerm: String
    ): List<Map<String, Any>>
    
    // Delete work order - sama persis dengan Flutter
    @FormUrlEncoded
    @POST("delete_wo.php")
    suspend fun deleteWorkOrder(
        @Field("woId") woId: String
    ): Map<String, Any>
    
    // Get work order by ID - sama persis dengan Flutter
    @GET("get_wo_by_id.php")
    suspend fun getWorkOrderById(
        @Query("woId") woId: String
    ): Map<String, Any>
    
    // Update work order - sama persis dengan Flutter
    @FormUrlEncoded
    @POST("update_wo.php")
    suspend fun updateWorkOrder(
        @Field("woId") woId: String,
        @Field("lokasi") lokasi: String,
        @Field("job") job: String,
        @Field("priority") priority: String
    ): Map<String, Any>
    
    // Search work orders by text - sama persis dengan Flutter
    @FormUrlEncoded
    @POST("cari_wo.php")
    suspend fun searchWorkOrdersByText(
        @Field("propID") propID: String,
        @Field("searchText") searchText: String
    ): List<Map<String, Any>>
    
    // Count new work orders - sama persis dengan Flutter
    @GET("count_new_wo.php")
    suspend fun countNewWorkOrders(
        @Query("propID") propID: String
    ): Map<String, Any>
    
    // Update pending/done - sama persis dengan Flutter
    @Multipart
    @POST("update_pending_done.php")
    suspend fun updatePendingDone(
        @Part("woId") woId: okhttp3.RequestBody,
        @Part("status") status: okhttp3.RequestBody,
        @Part("userName") userName: okhttp3.RequestBody,
        @Part("remarks") remarks: okhttp3.RequestBody,
        @Part photoFile: MultipartBody.Part? = null
    ): String
    
    // Asset API - sama persis dengan Flutter
    @GET("get_asset_detail.php")
    suspend fun getAssetDetail(
        @Query("noAsset") noAsset: String
    ): Map<String, Any>
    
    // Maintenance APIs - sama persis dengan Flutter
    @GET("get_maintenance_this_week.php")
    suspend fun getMaintenanceThisWeek(
        @Query("propID") propID: String
    ): Map<String, Any>
    
    @GET("get_maintask_job.php")
    suspend fun getMaintenanceTaskJob(
        @Query("noAssets") noAssets: String,
        @Query("propID") propID: String
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
    
    @GET("get_maintenance_history.php")
    suspend fun getMaintenanceHistory(
        @Query("mntId") mntId: String,
        @Query("propID") propID: String
    ): Map<String, Any>
    
    // Support Ticket APIs - sama persis dengan Flutter
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
}
