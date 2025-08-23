package com.sofindo.ems.api

import com.sofindo.ems.models.User
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
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
        @Field("userDept") userDept: String = "",
        @Field("searchText") searchText: String = ""
    ): List<Map<String, Any>>
    
    @GET("get_all_statuses_outbox.php")
    suspend fun getAllStatusesOutbox(
        @Query("propID") propID: String,
        @Query("dept") dept: String? = null
    ): List<Map<String, Any>>
    
    @FormUrlEncoded
    @POST("add_work_order.php")
    suspend fun addWorkOrder(
        @Field("propID") propID: String,
        @Field("username") username: String,
        @Field("job") job: String,
        @Field("location") location: String,
        @Field("remarks") remarks: String,
        @Field("category") category: String,
        @Field("department") department: String,
        @Field("priority") priority: String
    ): Map<String, Any>
    
    // Master Data APIs
    @GET("get_categories.php")
    suspend fun getCategories(): List<String>
    
    @GET("get_departments.php")
    suspend fun getDepartments(): List<String>
    
    @GET("get_locations.php")
    suspend fun getLocations(): List<String>
}
