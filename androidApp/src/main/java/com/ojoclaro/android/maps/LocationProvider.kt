package com.ojoclaro.android.maps

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat

sealed class LocationResult {
    data class Success(
        val latitude: Double,
        val longitude: Double,
        val accuracyMeters: Float? = null
    ) : LocationResult()

    data object PermissionMissing : LocationResult()
    data object LocationDisabled : LocationResult()
    data object Unavailable : LocationResult()
}

data class LocationSnapshot(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float? = null
)

class LocationProvider(
    private val hasPermission: () -> Boolean,
    private val isLocationEnabled: () -> Boolean,
    private val readLastLocation: () -> LocationSnapshot?
) {
    constructor(context: Context) : this(
        hasPermission = {
            REQUIRED_PERMISSIONS.any { permission ->
                ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            }
        },
        isLocationEnabled = {
            val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            manager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
                manager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
        },
        readLastLocation = {
            val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            manager?.bestLastKnownLocation()
        }
    )

    fun getCurrentLocation(): LocationResult {
        if (!hasPermission()) return LocationResult.PermissionMissing
        if (!isLocationEnabled()) return LocationResult.LocationDisabled

        val snapshot = runCatching { readLastLocation() }.getOrNull()
            ?: return LocationResult.Unavailable

        return if (SafeLocationMemory.isValidCoordinate(snapshot.latitude, snapshot.longitude)) {
            LocationResult.Success(
                latitude = snapshot.latitude,
                longitude = snapshot.longitude,
                accuracyMeters = snapshot.accuracyMeters
            )
        } else {
            LocationResult.Unavailable
        }
    }

    companion object {
        val REQUIRED_PERMISSIONS = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }
}

private fun LocationManager.bestLastKnownLocation(): LocationSnapshot? {
    val candidates = listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER,
        LocationManager.PASSIVE_PROVIDER
    ).mapNotNull { provider ->
        try {
            getLastKnownLocation(provider)
        } catch (_: SecurityException) {
            null
        }
    }

    return candidates
        .maxByOrNull(Location::getTime)
        ?.let { location ->
            LocationSnapshot(
                latitude = location.latitude,
                longitude = location.longitude,
                accuracyMeters = location.accuracy.takeIf { location.hasAccuracy() }
            )
        }
}
