package com.sofindo.ems.api

import com.sofindo.ems.models.WorkOrder
import com.sofindo.ems.models.StatusCount
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
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
	
	@GET("baca_wo.php")
	suspend fun getWorkOrders(
		@Query("propID") propID: String,
		@Query("woto") woto: String,
		@Query("status") status: String = "",
		@Query("page") page: Int = 1
	): List<WorkOrder>
	
	@GET("get_all_statuses.php")
	suspend fun getAllStatuses(
		@Query("propID") propID: String,
		@Query("woto") woto: String
	): List<StatusCount>
	
	@GET("search_wo.php")
	suspend fun searchWorkOrders(
		@Query("propID") propID: String,
		@Query("search") searchQuery: String
	): List<WorkOrder>
	
	@FormUrlEncoded
	@POST("user_profile.php")
	suspend fun updateUserProfile(
		@Field("action") action: String = "update",
		@Field("id") id: String,
		@Field("fullName") fullName: String,
		@Field("email") email: String,
		@Field("phoneNumber") phoneNumber: String
	): Map<String, Any>
	
	// Support: submit ticket (same endpoint as Flutter)
	@FormUrlEncoded
	@POST("submit_support_ticket.php")
	suspend fun submitSupportTicket(
		@Field("name") name: String,
		@Field("email") email: String,
		@Field("mobile_number") mobileNumber: String?,
		@Field("issue") issueCategory: String,
		@Field("description") description: String,
		@Field("screenshot_path") screenshotPath: String? = null
	): ResponseBody
	
	// Support: upload attachment (same endpoint as Flutter)
	@Multipart
	@POST("upload_support_attachment.php")
	suspend fun uploadSupportAttachment(
		@Part attachment: MultipartBody.Part
	): ResponseBody
	
	// Outbox: get work orders out (same endpoint as Flutter)
	@FormUrlEncoded
	@POST("cari_wo_out.php")
	suspend fun getWorkOrdersOut(
		@Field("propID") propID: String,
		@Field("orderBy") orderBy: String,
		@Field("status") status: String = "",
		@Field("page") page: Int = 1,
		@Field("userDept") userDept: String = ""
	): List<WorkOrder>
	
	// Outbox: get all statuses for outbox (same endpoint as Flutter)
	@GET("get_all_statuses_outbox.php")
	suspend fun getAllStatusesOutbox(
		@Query("propID") propID: String,
		@Query("dept") dept: String = ""
	): List<StatusCount>
	
	// Outbox: delete work order (same endpoint as Flutter)
	@FormUrlEncoded
	@POST("delete_wo.php")
	suspend fun deleteWorkOrder(
		@Field("woId") woId: String
	): Map<String, Any>
	
	// Get categories (same endpoint as Flutter)
	@FormUrlEncoded
	@POST("get_category.php")
	suspend fun getCategories(
		@Field("propID") propID: String
	): List<String>
	
	// Get departments (same endpoint as Flutter)
	@FormUrlEncoded
	@POST("get_dept.php")
	suspend fun getDepartments(
		@Field("propID") propID: String
	): List<String>
	
	// Get locations (same endpoint as Flutter)
	@FormUrlEncoded
	@POST("get_lokasi.php")
	suspend fun getLocations(
		@Field("propID") propID: String
	): List<String>
	
	// Submit work order without photo (same endpoint as Flutter)
	@FormUrlEncoded
	@POST("submit_wo.php")
	suspend fun submitWorkOrder(
		@Field("propID") propID: String,
		@Field("job") job: String,
		@Field("lokasi") lokasi: String,
		@Field("category") category: String,
		@Field("dept") dept: String,
		@Field("priority") priority: String,
		@Field("orderBy") orderBy: String,
		@Field("woto") woto: String
	): Map<String, Any>
	
	// Submit work order with photo (same as Flutter MultipartRequest)
	@Multipart
	@POST("submit_wo.php")
	suspend fun submitWorkOrderWithPhoto(
		@Part("propID") propID: RequestBody,
		@Part("job") job: RequestBody,
		@Part("lokasi") lokasi: RequestBody,
		@Part("category") category: RequestBody,
		@Part("dept") dept: RequestBody,
		@Part("priority") priority: RequestBody,
		@Part("orderBy") orderBy: RequestBody,
		@Part("woto") woto: RequestBody,
		@Part photo: MultipartBody.Part
	): Map<String, Any>
	
	// Upload work order photo separately (same endpoint as Flutter)
	@Multipart
	@POST("submit_wo.php")
	suspend fun uploadWorkOrderPhoto(
		@Part("woId") woId: RequestBody,
		@Part photo: MultipartBody.Part
	): Map<String, Any>

}
