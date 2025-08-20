package com.sofindo.ems.services

import android.content.Context
import android.content.SharedPreferences
import com.sofindo.ems.models.User
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

object UserService {
    private const val PREF_NAME = "ems_user_prefs"
    private const val KEY_CURRENT_USER = "current_user"
    private const val KEY_PROP_ID = "prop_id"

    private fun getSharedPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    // Save user data after successful login
    suspend fun saveUser(context: Context, user: User) {
        val prefs = getSharedPreferences(context)
        val userJson = moshi.adapter(User::class.java).toJson(user)
        prefs.edit()
            .putString(KEY_CURRENT_USER, userJson)
            .putString(KEY_PROP_ID, user.propID)
            .apply()
    }

    // Get current logged in user
    suspend fun getCurrentUser(context: Context): User? {
        val prefs = getSharedPreferences(context)
        val userJson = prefs.getString(KEY_CURRENT_USER, null)
        
        return if (userJson != null) {
            try {
                moshi.adapter(User::class.java).fromJson(userJson)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    // Get propID from currently logged in user
    suspend fun getCurrentPropID(context: Context): String? {
        val prefs = getSharedPreferences(context)
        return prefs.getString(KEY_PROP_ID, null)
    }

    // Get dept from currently logged in user
    suspend fun getCurrentDept(context: Context): String? {
        val user = getCurrentUser(context)
        return user?.dept
    }

    // Clear user data (for logout)
    suspend fun clearUser(context: Context) {
        val prefs = getSharedPreferences(context)
        prefs.edit()
            .remove(KEY_CURRENT_USER)
            .remove(KEY_PROP_ID)
            .apply()
    }

    // Check if user is logged in
    suspend fun isLoggedIn(context: Context): Boolean {
        val propID = getCurrentPropID(context)
        return propID != null && propID.isNotEmpty()
    }
}
