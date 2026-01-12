package com.sofindo.ems.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object NetworkUtils {
    
    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            networkInfo?.isConnected == true
        }
    }
    
    suspend fun hasInternetConnection(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (!isNetworkAvailable(context)) {
            return@withContext false
        }
        
        try {
            val url = URL("https://www.google.com")
            val connection = url.openConnection() as HttpURLConnection
            connection.setRequestProperty("User-Agent", "Test")
            connection.setRequestProperty("Connection", "close")
            connection.connectTimeout = 3000
            connection.readTimeout = 3000
            connection.connect()
            val result = connection.responseCode == 200
            connection.disconnect()
            result
        } catch (e: Exception) {
            false
        }
    }
    
    suspend fun hasServerConnection(): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://emshotels.net/apiKu/login.php")
            val connection = url.openConnection() as HttpURLConnection
            connection.setRequestProperty("User-Agent", "EMS-App")
            connection.setRequestProperty("Connection", "close")
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.requestMethod = "GET"
            connection.connect()
            val result = connection.responseCode in 200..399
            connection.disconnect()
            result
        } catch (e: Exception) {
            false
        }
    }
}

































