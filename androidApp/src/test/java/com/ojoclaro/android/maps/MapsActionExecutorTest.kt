package com.ojoclaro.android.maps

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import com.ojoclaro.android.external.CommandResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MapsActionExecutorTest {

    @Test
    fun openMapsUsesActionView() {
        val spec = MapsActionExecutor().buildOpenMapsIntentSpec()

        assertEquals(Intent.ACTION_VIEW, spec.action)
        assertEquals("geo:0,0", spec.dataUri)
    }

    @Test
    fun openNavigationToGeneratesValidIntent() {
        val spec = MapsActionExecutor().buildNavigationIntentSpec("farmacia")

        assertEquals(Intent.ACTION_VIEW, spec.action)
        assertTrue(spec.dataUri.startsWith("google.navigation:q="))
        assertTrue(spec.dataUri.contains("farmacia"))
    }

    @Test
    fun doesNotRequireDangerousPermission() {
        val spec = MapsActionExecutor().buildNavigationIntentSpec("casa")

        assertNull(spec.requiredPermission)
        assertNotEquals(Manifest.permission.ACCESS_BACKGROUND_LOCATION, spec.requiredPermission)
    }

    @Test
    fun handlesActivityNotFoundWithGenericFallback() {
        var calls = 0
        val executor = MapsActionExecutor(
            intentFactory = { spec -> Intent(spec.action) },
            intentStarter = { intent ->
                calls++
                if (calls == 1) {
                    throw ActivityNotFoundException("missing maps")
                }
            }
        )

        val result = executor.openMaps()

        assertIs<CommandResult.Success>(result)
        assertEquals(2, calls)
    }

    @Test
    fun handlesSecurityException() {
        val executor = MapsActionExecutor(
            intentFactory = { spec -> Intent(spec.action) },
            intentStarter = { throw SecurityException("blocked") }
        )

        val result = executor.openMaps()

        assertIs<CommandResult.Failed>(result)
    }

    @Test
    fun fallbackSpecHasNoGoogleMapsPackage() {
        val spec = MapsActionExecutor().buildOpenMapsIntentSpec(preferGoogleMaps = false)

        assertNull(spec.packageName)
    }
}
