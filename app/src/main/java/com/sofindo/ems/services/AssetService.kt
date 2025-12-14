package com.sofindo.ems.services

import com.sofindo.ems.api.ApiService
import com.sofindo.ems.api.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull

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
                        @Suppress("UNCHECKED_CAST")
                        response["data"] as Map<String, Any>
                    } else {
                        throw Exception("Invalid response format from server")
                    }
                } catch (e: Exception) {
                    throw Exception("Error fetching asset data: ${e.message}")
                }
            }
        }
        
        // Get distinct locations from tblinventory
        suspend fun getInventoryLocations(propID: String): List<String> {
            return withContext(Dispatchers.IO) {
                try {
                    val apiService: ApiService = RetrofitClient.apiService
                    val locations = apiService.getInventoryLocations(propID = propID)
                    locations
                } catch (e: Exception) {
                    throw Exception("Error fetching inventory locations: ${e.message}")
                }
            }
        }
        
        // Search inventory by Property name (min 2 characters)
        suspend fun searchInventory(propID: String, searchText: String, lokasi: String? = null): List<Map<String, Any>> {
            return withContext(Dispatchers.IO) {
                try {
                    val apiService: ApiService = RetrofitClient.apiService
                    val results = apiService.searchInventory(
                        propID = propID,
                        searchText = searchText,
                        lokasi = lokasi
                    )
                    results
                } catch (e: Exception) {
                    throw Exception("Error searching inventory: ${e.message}")
                }
            }
        }
        
        // Get inventory detail by No (primary key) OR by Property + Lokasi
        suspend fun getInventoryDetail(
            no: Int? = null,
            property: String? = null,
            lokasi: String? = null,
            propID: String? = null
        ): Map<String, Any> {
            return withContext(Dispatchers.IO) {
                try {
                    val apiService: ApiService = RetrofitClient.apiService
                    val response = apiService.getInventoryDetail(
                        no = no,
                        property = property,
                        lokasi = lokasi,
                        propID = propID
                    )
                    
                    if (response["error"] != null) {
                        throw Exception(response["error"].toString())
                    }
                    
                    response
                } catch (e: Exception) {
                    throw Exception("Error fetching inventory detail: ${e.message}")
                }
            }
        }
        
        // Update inventory data only (without photo)
        suspend fun updateInventory(
            no: Int,
            propID: String,
            tgl: String,
            mntld: String?,
            category: String,
            lokasi: String?,
            property: String?,
            merk: String?,
            model: String?,
            serno: String,
            capacity: String?,
            datePurchased: String?,
            suplier: String,
            keterangan: String
        ): Map<String, Any> {
            return withContext(Dispatchers.IO) {
                try {
                    val apiService: ApiService = RetrofitClient.apiService
                    
                    val response = apiService.updateInventory(
                        action = "update",
                        no = no.toString(),
                        propID = propID,
                        tgl = tgl,
                        mntld = mntld,
                        category = category,
                        lokasi = lokasi,
                        property = property,
                        merk = merk,
                        model = model,
                        serno = serno,
                        capacity = capacity,
                        datePurchased = datePurchased,
                        suplier = suplier,
                        keterangan = keterangan
                    )
                    
                    if (response["error"] != null) {
                        throw Exception(response["error"].toString())
                    }
                    
                    response
                } catch (e: Exception) {
                    throw Exception("Error updating inventory: ${e.message}")
                }
            }
        }

        // Insert new inventory (create asset)
        suspend fun insertInventory(
            propID: String,
            category: String,
            lokasi: String?,
            property: String?,
            merk: String?,
            model: String?,
            serno: String,
            capacity: String?,
            datePurchased: String?,
            suplier: String,
            keterangan: String
        ): Map<String, Any> {
            return withContext(Dispatchers.IO) {
                try {
                    val apiService: ApiService = RetrofitClient.apiService
                    apiService.insertInventory(
                        propID = propID,
                        category = category,
                        lokasi = lokasi,
                        property = property,
                        merk = merk,
                        model = model,
                        serno = serno,
                        capacity = capacity,
                        datePurchased = datePurchased,
                        suplier = suplier,
                        keterangan = keterangan
                    )
                } catch (e: Exception) {
                    throw Exception("Error inserting inventory: ${e.message}")
                }
            }
        }

        // Delete inventory by No
        suspend fun deleteInventory(no: Int): Map<String, Any> {
            return withContext(Dispatchers.IO) {
                try {
                    val apiService: ApiService = RetrofitClient.apiService
                    apiService.deleteInventory(no = no)
                } catch (e: Exception) {
                    throw Exception("Error deleting inventory: ${e.message}")
                }
            }
        }
        
        // Upload inventory photo separately (with thumbnail)
        suspend fun uploadInventoryPhoto(
            no: Int,
            photoFile: java.io.File,
            thumbFile: java.io.File? = null
        ): Map<String, Any> {
            return withContext(Dispatchers.IO) {
                try {
                    val apiService: ApiService = RetrofitClient.apiService
                    
                    val actionBody = "upload_photo".toRequestBody("text/plain".toMediaTypeOrNull())
                    val noBody = no.toString().toRequestBody("text/plain".toMediaTypeOrNull())
                    
                    // Main image with correct name: assets_[no].jpg
                    val mainImageName = "assets_${no}.jpg"
                    val requestFile = photoFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
                    val photoPart = okhttp3.MultipartBody.Part.createFormData("photo", mainImageName, requestFile)
                    
                    // Thumbnail with correct name: thumb_assets_[no].jpg
                    var thumbPart: okhttp3.MultipartBody.Part? = null
                    if (thumbFile != null && thumbFile.exists()) {
                        val thumbImageName = "thumb_assets_${no}.jpg"
                        val thumbRequestBody = thumbFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
                        thumbPart = okhttp3.MultipartBody.Part.createFormData("thumb", thumbImageName, thumbRequestBody)
                    }
                    
                    val response = apiService.uploadInventoryPhoto(
                        action = actionBody,
                        no = noBody,
                        photoFile = photoPart,
                        thumbFile = thumbPart
                    )
                    
                    if (response["error"] != null) {
                        throw Exception(response["error"].toString())
                    }
                    
                    response
                } catch (e: Exception) {
                    throw Exception("Error uploading photo: ${e.message}")
                }
            }
        }
    }
}
