package com.sofindo.ems.services

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class LocationService private constructor(private val context: Context) {
    
    private val locationManager: LocationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    
    companion object {
        @Volatile
        private var INSTANCE: LocationService? = null
        
        fun getInstance(context: Context): LocationService {
            return INSTANCE ?: synchronized(this) {
                val instance = LocationService(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
    
    fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }
    
    fun isLocationEnabled(): Boolean {
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }
    
    suspend fun getCurrentLocation(): Location = suspendCancellableCoroutine { continuation ->
        android.util.Log.d("LocationService", "getCurrentLocation called")
        
        if (!hasLocationPermission()) {
            android.util.Log.e("LocationService", "Location permission not granted")
            continuation.resumeWithException(SecurityException("Location permission not granted"))
            return@suspendCancellableCoroutine
        }
        
        if (!isLocationEnabled()) {
            android.util.Log.e("LocationService", "Location services disabled")
            continuation.resumeWithException(Exception("Location services are disabled. Please enable GPS in Settings."))
            return@suspendCancellableCoroutine
        }
        
        var locationResolved = false
        
        val locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                android.util.Log.d("LocationService", "Location updated: lat=${location.latitude}, lng=${location.longitude}, accuracy=${location.accuracy}")
                locationManager.removeUpdates(this)
                if (!locationResolved && !continuation.isCompleted) {
                    locationResolved = true
                    continuation.resume(location)
                }
            }
            
            override fun onProviderDisabled(provider: String) {
                android.util.Log.w("LocationService", "Provider disabled: $provider")
                locationManager.removeUpdates(this)
            }
            
            override fun onProviderEnabled(provider: String) {
                android.util.Log.d("LocationService", "Provider enabled: $provider")
            }
            
            override fun onStatusChanged(provider: String, status: Int, extras: Bundle?) {}
        }
        
        try {
            // Get last known location first as quick fallback
            var lastKnownLocation: Location? = null
            val gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            val networkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            
            android.util.Log.d("LocationService", "GPS enabled: $gpsEnabled, Network enabled: $networkEnabled")
            
            // Try to get last known location from both providers
            if (gpsEnabled) {
                lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                android.util.Log.d("LocationService", "Last known GPS: ${lastKnownLocation?.latitude}, ${lastKnownLocation?.longitude}")
            }
            
            if (lastKnownLocation == null && networkEnabled) {
                lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                android.util.Log.d("LocationService", "Last known Network: ${lastKnownLocation?.latitude}, ${lastKnownLocation?.longitude}")
            }
            
            // If last known location is available and not too old (within 10 minutes), use it immediately
            if (lastKnownLocation != null) {
                val ageMinutes = (System.currentTimeMillis() - lastKnownLocation.time) / (1000 * 60)
                android.util.Log.d("LocationService", "Last known location age: ${ageMinutes} minutes")
                
                if (ageMinutes < 10) {
                    android.util.Log.d("LocationService", "Using last known location")
                    if (!locationResolved && !continuation.isCompleted) {
                        locationResolved = true
                        continuation.resume(lastKnownLocation)
                        return@suspendCancellableCoroutine
                    }
                }
            }
            
            // Request fresh location updates
            if (gpsEnabled) {
                android.util.Log.d("LocationService", "Requesting GPS location updates")
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000L,
                    1f, // Smaller distance filter for better accuracy
                    locationListener
                )
            }
            
            if (networkEnabled) {
                android.util.Log.d("LocationService", "Requesting Network location updates")
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    1000L,
                    10f,
                    locationListener
                )
            }
            
            if (!gpsEnabled && !networkEnabled) {
                android.util.Log.e("LocationService", "No location providers available")
                // Use last known location even if old
                if (lastKnownLocation != null && !locationResolved && !continuation.isCompleted) {
                    locationResolved = true
                    android.util.Log.d("LocationService", "Using old last known location as fallback")
                    continuation.resume(lastKnownLocation)
                } else {
                    continuation.resumeWithException(Exception("No location providers available"))
                }
                return@suspendCancellableCoroutine
            }
            
            // Timeout after 10 seconds - use last known location if available
            Handler(Looper.getMainLooper()).postDelayed({
                if (!locationResolved && !continuation.isCompleted) {
                    locationManager.removeUpdates(locationListener)
                    if (lastKnownLocation != null) {
                        locationResolved = true
                        android.util.Log.d("LocationService", "Timeout - using last known location")
                        continuation.resume(lastKnownLocation)
                    } else {
                        locationResolved = true
                        android.util.Log.e("LocationService", "Timeout - no location available")
                        continuation.resumeWithException(Exception("Location timeout. Please try again."))
                    }
                }
            }, 10000) // 10 second timeout
            
        } catch (e: SecurityException) {
            android.util.Log.e("LocationService", "SecurityException: ${e.message}", e)
            continuation.resumeWithException(e)
        } catch (e: Exception) {
            android.util.Log.e("LocationService", "Exception: ${e.message}", e)
            continuation.resumeWithException(e)
        }
        
        continuation.invokeOnCancellation {
            try {
                locationManager.removeUpdates(locationListener)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
}
