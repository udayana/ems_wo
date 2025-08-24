package com.sofindo.ems.services

import com.sofindo.ems.api.ApiService
import com.sofindo.ems.api.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AssetService {
    companion object {
        
        // Get asset detail by asset number or URL
        suspend fun getAssetDetail(assetUrl: String): Map<String, Any> {
            return withContext(Dispatchers.IO) {
                try {
                    // Extract noAsset from URL if it's a full URL
                    val noAsset = if (assetUrl.contains("noAsset=")) {
                        // Parse URL to get noAsset parameter
                        val uri = android.net.Uri.parse(assetUrl)
                        uri.getQueryParameter("noAsset") ?: ""
                    } else {
                        // If it's just the number, use it directly
                        assetUrl
                    }
                    
                    if (noAsset.isEmpty()) {
                        throw Exception("No asset ID provided")
                    }
                    
                    val apiService: ApiService = RetrofitClient.apiService
                    val response = apiService.getAssetDetail(noAsset)
                    
                    if (response["error"] != null) {
                        // Handle specific error messages
                        val errorMsg = response["error"].toString()
                        if (errorMsg.contains("Asset not found")) {
                            throw Exception("Asset not found in database. Please check the QR code.")
                        } else {
                            throw Exception(errorMsg)
                        }
                    }
                    
                    if (response["success"] == true && response["data"] != null) {
                        response["data"] as Map<String, Any>
                    } else {
                        throw Exception("Invalid response format from server")
                    }
                } catch (e: Exception) {
                    throw Exception("Error fetching asset data: ${e.message}")
                }
            }
        }
    }
}
