package com.ojoclaro.android.maps

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.ojoclaro.android.external.CommandResult
import java.net.URLEncoder

data class MapsIntentSpec(
    val action: String,
    val dataUri: String,
    val packageName: String? = MapsActionExecutor.GOOGLE_MAPS_PACKAGE,
    val requiredPermission: String? = null
) {
    fun toIntent(): Intent =
        Intent(action, Uri.parse(dataUri)).apply {
            packageName?.let(::setPackage)
        }
}

class MapsActionExecutor(
    private val context: Context? = null,
    private val intentStarter: ((Intent) -> Unit)? = null,
    private val intentFactory: (MapsIntentSpec) -> Intent = { spec -> spec.toIntent() }
) {
    fun buildOpenMapsIntentSpec(preferGoogleMaps: Boolean = true): MapsIntentSpec =
        MapsIntentSpec(
            action = Intent.ACTION_VIEW,
            dataUri = "geo:0,0",
            packageName = GOOGLE_MAPS_PACKAGE.takeIf { preferGoogleMaps }
        )

    fun buildCurrentLocationIntentSpec(
        latitude: Double,
        longitude: Double,
        preferGoogleMaps: Boolean = true
    ): MapsIntentSpec =
        MapsIntentSpec(
            action = Intent.ACTION_VIEW,
            dataUri = "geo:$latitude,$longitude?q=$latitude,$longitude",
            packageName = GOOGLE_MAPS_PACKAGE.takeIf { preferGoogleMaps }
        )

    fun buildNavigationIntentSpec(
        destination: String,
        preferGoogleMaps: Boolean = true
    ): MapsIntentSpec =
        MapsIntentSpec(
            action = Intent.ACTION_VIEW,
            dataUri = "google.navigation:q=${destination.urlEncode()}",
            packageName = GOOGLE_MAPS_PACKAGE.takeIf { preferGoogleMaps }
        )

    fun buildNavigationToCoordinatesIntentSpec(
        latitude: Double,
        longitude: Double,
        label: String?,
        preferGoogleMaps: Boolean = true
    ): MapsIntentSpec {
        val query = label?.takeIf(String::isNotBlank)?.urlEncode()
            ?: "$latitude,$longitude"
        return MapsIntentSpec(
            action = Intent.ACTION_VIEW,
            dataUri = "google.navigation:q=$latitude,$longitude($query)",
            packageName = GOOGLE_MAPS_PACKAGE.takeIf { preferGoogleMaps }
        )
    }

    fun openMaps(): CommandResult =
        startWithFallback(
            preferred = buildOpenMapsIntentSpec(preferGoogleMaps = true),
            fallback = buildOpenMapsIntentSpec(preferGoogleMaps = false),
            successText = "Abrí mapas."
        )

    fun openCurrentLocation(latitude: Double, longitude: Double): CommandResult =
        startWithFallback(
            preferred = buildCurrentLocationIntentSpec(latitude, longitude, preferGoogleMaps = true),
            fallback = buildCurrentLocationIntentSpec(latitude, longitude, preferGoogleMaps = false),
            successText = "Abrí tu ubicación en mapas."
        )

    fun openNavigationTo(destination: String): CommandResult =
        startWithFallback(
            preferred = buildNavigationIntentSpec(destination, preferGoogleMaps = true),
            fallback = buildNavigationIntentSpec(destination, preferGoogleMaps = false),
            successText = "Abrí mapas hacia $destination."
        )

    fun openNavigationToCoordinates(
        latitude: Double,
        longitude: Double,
        label: String?
    ): CommandResult =
        startWithFallback(
            preferred = buildNavigationToCoordinatesIntentSpec(latitude, longitude, label, preferGoogleMaps = true),
            fallback = buildNavigationToCoordinatesIntentSpec(latitude, longitude, label, preferGoogleMaps = false),
            successText = "Abrí mapas hacia ${label.orEmpty().ifBlank { "el destino" }}."
        )

    private fun startWithFallback(
        preferred: MapsIntentSpec,
        fallback: MapsIntentSpec,
        successText: String
    ): CommandResult {
        val first = startSafely(preferred)
        if (first == null) return CommandResult.Success(successText)
        if (first is ActivityNotFoundException && preferred.packageName != null) {
            val second = startSafely(fallback)
            if (second == null) return CommandResult.Success(successText)
            return CommandResult.Failed("No pude abrir mapas en este dispositivo.", recoverable = true)
        }
        return CommandResult.Failed("No pude abrir mapas en este dispositivo.", recoverable = true)
    }

    private fun startSafely(spec: MapsIntentSpec): Throwable? {
        return try {
            val intent = intentFactory(spec)
            if (context != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            intentStarter?.invoke(intent) ?: context?.startActivity(intent)
            null
        } catch (error: ActivityNotFoundException) {
            error
        } catch (error: SecurityException) {
            error
        }
    }

    companion object {
        const val GOOGLE_MAPS_PACKAGE = "com.google.android.apps.maps"
    }
}

private fun String.urlEncode(): String =
    URLEncoder.encode(this, "UTF-8")
