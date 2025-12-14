package com.sofindo.ems.models

import org.json.JSONArray
import org.json.JSONObject

data class Property(
    val id: String, // aid (Property ID)
    val nama: String, // Property name
    val latitude: Double?,
    val longitude: Double?,
    val distance: Double? // Distance in meters (calculated on server)
) {
    companion object {
        fun fromJson(json: JSONObject): Property {
            return try {
                // Safe parsing - gunakan optString untuk menghindari crash
                val aid = json.optString("aid", "").takeIf { it.isNotEmpty() } ?: ""
                val propName = json.optString("propName", null)?.takeIf { it.isNotEmpty() }
                val namaProp = json.optString("nama", null)?.takeIf { it.isNotEmpty() }
                val nama = propName ?: namaProp ?: aid
                
                // Safe parsing untuk double
                val latitude = when {
                    json.isNull("lat") -> null
                    else -> {
                        val lat = json.optDouble("lat", Double.NaN)
                        if (lat.isNaN() || lat == 0.0) null else lat
                    }
                }
                
                val longitude = when {
                    json.isNull("lng") -> null
                    else -> {
                        val lng = json.optDouble("lng", Double.NaN)
                        if (lng.isNaN() || lng == 0.0) null else lng
                    }
                }
                
                val distance = when {
                    json.isNull("distance") -> null
                    else -> {
                        val dist = json.optDouble("distance", Double.NaN)
                        if (dist.isNaN() || dist == 0.0) null else dist
                    }
                }
                
                Property(
                    id = aid,
                    nama = nama,
                    latitude = latitude,
                    longitude = longitude,
                    distance = distance
                )
            } catch (e: Exception) {
                android.util.Log.e("Property", "Error parsing from JSON: ${e.message}", e)
                e.printStackTrace()
                Property(
                    id = "",
                    nama = "",
                    latitude = null,
                    longitude = null,
                    distance = null
                )
            }
        }
    }
}

data class PropertySearchResponse(
    val success: Boolean,
    val properties: List<Property>?,
    val message: String?
) {
    companion object {
        fun fromJson(json: JSONObject): PropertySearchResponse {
            val dataArray = json.optJSONArray("data")
            val properties = if (dataArray != null) {
                (0 until dataArray.length()).map { i ->
                    Property.fromJson(dataArray.getJSONObject(i))
                }
            } else {
                null
            }
            
            return PropertySearchResponse(
                success = json.optBoolean("success", false),
                properties = properties,
                message = json.optString("message", null).takeIf { !json.isNull("message") && it.isNotEmpty() }
            )
        }
        
        fun fromMap(map: Map<String, Any>): PropertySearchResponse {
            try {
                android.util.Log.d("PropertySearchResponse", "Parsing response: $map")
                
                val dataList = map["data"]
                android.util.Log.d("PropertySearchResponse", "Data type: ${dataList?.javaClass?.simpleName}")
                
                val properties = when (dataList) {
                    is List<*> -> {
                        android.util.Log.d("PropertySearchResponse", "Data is List with ${dataList.size} items")
                        try {
                            dataList.mapNotNull { item ->
                                try {
                                    when (item) {
                                        is Map<*, *> -> {
                                            @Suppress("UNCHECKED_CAST")
                                            try {
                                                val property = Property.fromMap(item as Map<String, Any>)
                                                android.util.Log.d("PropertySearchResponse", "Parsed property: ${property.id} - ${property.nama}")
                                                property
                                            } catch (e: Exception) {
                                                android.util.Log.e("PropertySearchResponse", "Error parsing property: ${e.message}", e)
                                                e.printStackTrace()
                                                null
                                            }
                                        }
                                        else -> {
                                            android.util.Log.w("PropertySearchResponse", "Item is not a Map: ${item?.javaClass?.simpleName}")
                                            null
                                        }
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("PropertySearchResponse", "Error processing item: ${e.message}", e)
                                    null
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("PropertySearchResponse", "Error mapping list: ${e.message}", e)
                            e.printStackTrace()
                            emptyList()
                        }
                    }
                    null -> {
                        android.util.Log.d("PropertySearchResponse", "Data is null")
                        emptyList()
                    }
                    else -> {
                        android.util.Log.w("PropertySearchResponse", "Data is not a List: ${dataList.javaClass.simpleName}")
                        emptyList()
                    }
                }
                
                val success = when (val successValue = map["success"]) {
                    is Boolean -> successValue
                    is String -> successValue.equals("true", ignoreCase = true)
                    is Number -> successValue.toInt() != 0
                    else -> false
                }
                
                android.util.Log.d("PropertySearchResponse", "Success: $success, Properties count: ${properties.size}")
                
                return PropertySearchResponse(
                    success = success,
                    properties = properties,
                    message = map["message"] as? String
                )
            } catch (e: Exception) {
                android.util.Log.e("PropertySearchResponse", "Error in fromMap: ${e.message}", e)
                e.printStackTrace()
                return PropertySearchResponse(
                    success = false,
                    properties = emptyList(),
                    message = "Error parsing response: ${e.message}"
                )
            }
        }
    }
}

// Helper to convert Map to Property
fun Property.Companion.fromMap(map: Map<String, Any>): Property {
    return try {
        // Safe parsing - jangan crash jika data tidak valid
        val aid = when (val aidValue = map["aid"]) {
            is String -> aidValue
            is Number -> aidValue.toString()
            else -> ""
        }
        
        val nama = when {
            map["propName"] is String -> map["propName"] as String
            map["nama"] is String -> map["nama"] as String
            aid.isNotEmpty() -> aid
            else -> ""
        }
        
        // Safe parsing untuk latitude/longitude/distance
        val latitude = when (val latValue = map["lat"]) {
            is Number -> latValue.toDouble().takeIf { it != 0.0 }
            is String -> latValue.toDoubleOrNull()?.takeIf { it != 0.0 }
            else -> null
        }
        
        val longitude = when (val lngValue = map["lng"]) {
            is Number -> lngValue.toDouble().takeIf { it != 0.0 }
            is String -> lngValue.toDoubleOrNull()?.takeIf { it != 0.0 }
            else -> null
        }
        
        val distance = when (val distValue = map["distance"]) {
            is Number -> distValue.toDouble().takeIf { it != 0.0 }
            is String -> distValue.toDoubleOrNull()?.takeIf { it != 0.0 }
            else -> null
        }
        
        Property(
            id = aid,
            nama = nama,
            latitude = latitude,
            longitude = longitude,
            distance = distance
        )
    } catch (e: Exception) {
        android.util.Log.e("Property", "Error parsing property: ${e.message}", e)
        e.printStackTrace()
        // Return empty property jika parsing gagal
        Property(
            id = "",
            nama = "",
            latitude = null,
            longitude = null,
            distance = null
        )
    }
}

data class PropertyValidationResponse(
    val success: Boolean,
    val isValid: Boolean,
    val distance: Double?,
    val message: String?
) {
    companion object {
        fun fromJson(json: JSONObject): PropertyValidationResponse {
            return PropertyValidationResponse(
                success = json.optBoolean("success", false),
                isValid = json.optBoolean("isValid", false),
                distance = json.optDouble("distance").takeIf { !json.isNull("distance") && it != 0.0 },
                message = json.optString("message", null).takeIf { !json.isNull("message") && it.isNotEmpty() }
            )
        }
        
        fun fromMap(map: Map<String, Any>): PropertyValidationResponse {
            return PropertyValidationResponse(
                success = map["success"] as? Boolean ?: false,
                isValid = map["isValid"] as? Boolean ?: false,
                distance = (map["distance"] as? Number)?.toDouble()?.takeIf { it != 0.0 },
                message = map["message"] as? String
            )
        }
    }
}
