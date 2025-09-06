package com.sofindo.ems.services

import android.content.Context
import android.content.SharedPreferences
import com.sofindo.ems.models.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object UserService {
    
    private const val PREF_NAME = "ems_user_prefs"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_USERNAME = "username"
    private const val KEY_EMAIL = "email"
    private const val KEY_FULL_NAME = "full_name"
    private const val KEY_PHONE = "phone"
    private const val KEY_PROFILE_IMAGE = "profile_image"
    private const val KEY_ROLE = "role"
    private const val KEY_PROP_ID = "prop_id"
    private const val KEY_DEPT = "dept"
    
    private lateinit var prefs: SharedPreferences
    
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }
    
    suspend fun saveUser(user: User) = withContext(Dispatchers.IO) {
        prefs.edit().apply {
            putString(KEY_USER_ID, user.id)
            putString(KEY_USERNAME, user.username)
            putString(KEY_EMAIL, user.email)
            putString(KEY_FULL_NAME, user.fullName)
            putString(KEY_PHONE, user.phoneNumber)
            putString(KEY_PROFILE_IMAGE, user.profileImage)
            putString(KEY_ROLE, user.role)
            putString(KEY_PROP_ID, user.propID)
            putString(KEY_DEPT, user.dept)
        }.apply()
    }
    
    suspend fun getCurrentUser(): User? = withContext(Dispatchers.IO) {
        val userId = prefs.getString(KEY_USER_ID, null) ?: return@withContext null
        
        return@withContext User(
            id = userId,
            username = prefs.getString(KEY_USERNAME, "") ?: "",
            email = prefs.getString(KEY_EMAIL, "") ?: "",
            fullName = prefs.getString(KEY_FULL_NAME, null),
            phoneNumber = prefs.getString(KEY_PHONE, null),
            profileImage = prefs.getString(KEY_PROFILE_IMAGE, null),
            role = prefs.getString(KEY_ROLE, "user") ?: "user",
            propID = prefs.getString(KEY_PROP_ID, null),
            dept = prefs.getString(KEY_DEPT, null)
        )
    }
    
    suspend fun getCurrentPropID(): String? = withContext(Dispatchers.IO) {
        return@withContext prefs.getString(KEY_PROP_ID, null)
    }
    
    suspend fun getCurrentDept(): String? = withContext(Dispatchers.IO) {
        return@withContext prefs.getString(KEY_DEPT, null)
    }
    
    // Synchronous versions for faster access
    fun getCurrentPropIDSync(): String? {
        return prefs.getString(KEY_PROP_ID, null)
    }
    
    fun getCurrentDeptSync(): String? {
        return prefs.getString(KEY_DEPT, null)
    }
    
    suspend fun getCurrentUsername(): String? = withContext(Dispatchers.IO) {
        return@withContext prefs.getString(KEY_USERNAME, null)
    }
    
    suspend fun clearUserData() = withContext(Dispatchers.IO) {
        prefs.edit().clear().apply()
    }
    
    // Logout but keep user data for display (similar to Flutter implementation)
    suspend fun logout() = withContext(Dispatchers.IO) {
        // Only remove propID, keep user data for display
        prefs.edit().remove(KEY_PROP_ID).apply()
    }
    
    // Get user data for display (even if logged out)
    suspend fun getDisplayUser(): User? = withContext(Dispatchers.IO) {
        val userId = prefs.getString(KEY_USER_ID, null) ?: return@withContext null
        
        return@withContext User(
            id = userId,
            username = prefs.getString(KEY_USERNAME, "") ?: "",
            email = prefs.getString(KEY_EMAIL, "") ?: "",
            fullName = prefs.getString(KEY_FULL_NAME, null),
            phoneNumber = prefs.getString(KEY_PHONE, null),
            profileImage = prefs.getString(KEY_PROFILE_IMAGE, null),
            role = prefs.getString(KEY_ROLE, "user") ?: "user",
            propID = prefs.getString(KEY_PROP_ID, null),
            dept = prefs.getString(KEY_DEPT, null)
        )
    }
    
    // Check if user is logged in (has propID)
    suspend fun isLoggedIn(): Boolean = withContext(Dispatchers.IO) {
        val propID = prefs.getString(KEY_PROP_ID, null)
        val hasPropID = !propID.isNullOrEmpty()
        return@withContext hasPropID
    }
}

