package com.ojoclaro.android.maps

import android.Manifest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LocationProviderTest {

    @Test
    fun withoutPermissionReturnsPermissionMissing() {
        val provider = LocationProvider(
            hasPermission = { false },
            isLocationEnabled = { true },
            readLastLocation = { LocationSnapshot(-38.95, -68.06, 20f) }
        )

        assertIs<LocationResult.PermissionMissing>(provider.getCurrentLocation())
    }

    @Test
    fun locationDisabledReturnsLocationDisabled() {
        val provider = LocationProvider(
            hasPermission = { true },
            isLocationEnabled = { false },
            readLastLocation = { LocationSnapshot(-38.95, -68.06, 20f) }
        )

        assertIs<LocationResult.LocationDisabled>(provider.getCurrentLocation())
    }

    @Test
    fun fakeProviderReturnsSuccess() {
        val provider = LocationProvider(
            hasPermission = { true },
            isLocationEnabled = { true },
            readLastLocation = { LocationSnapshot(-38.95, -68.06, 20f) }
        )

        val result = assertIs<LocationResult.Success>(provider.getCurrentLocation())
        assertEquals(-38.95, result.latitude)
        assertEquals(-68.06, result.longitude)
        assertEquals(20f, result.accuracyMeters)
    }

    @Test
    fun noBackgroundLocationPermissionIsDeclaredByProvider() {
        assertTrue(LocationProvider.REQUIRED_PERMISSIONS.contains(Manifest.permission.ACCESS_FINE_LOCATION))
        assertTrue(LocationProvider.REQUIRED_PERMISSIONS.contains(Manifest.permission.ACCESS_COARSE_LOCATION))
        assertFalse(LocationProvider.REQUIRED_PERMISSIONS.contains(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
    }
}
