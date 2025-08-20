package com.sofindo.ems.api

import com.sofindo.ems.models.User
import com.sofindo.ems.models.WorkOrder
import com.sofindo.ems.models.StatusCount
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST
import retrofit2.http.GET
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
}
