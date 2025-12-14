package com.sofindo.ems.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object PermissionUtils {
    
    /**
     * Check if camera permission is granted
     */
    fun isCameraPermissionGranted(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context, 
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Check if all required permissions are granted
     * Note: Camera permission required for QR scanner and take photo
     * Gallery access uses Android Photo Picker (no permission required)
     */
    fun areAllPermissionsGranted(context: Context): Boolean {
        return isCameraPermissionGranted(context)
    }
}

