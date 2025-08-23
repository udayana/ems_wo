package com.sofindo.ems.models

data class LocationSuggestion(
    val primaryName: String,
    val secondaryInfo: String? = null
) {
    val displayName: String
        get() = if (secondaryInfo != null) "$primaryName" else primaryName
    
    val displayInfo: String
        get() = secondaryInfo ?: ""
}
