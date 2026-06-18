package com.ojoclaro.android.outdoor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Motor de ubicación: una lectura fresca bajo demanda.
 *
 * Nota de diseño: el proyecto ya estandariza LocationManager (ver
 * maps/LocationProvider). Mantenemos esa base con la MISMA semántica que un
 * single-fix de Fused (lectura actual con timeout + fallback a última
 * conocida con edad real), sin sumar dependencias de Play Services. La
 * interfaz permite cambiar a FusedLocationProviderClient sin tocar callers.
 */
interface OutdoorLocationEngine {
    fun hasPermission(): Boolean
    fun servicesEnabled(): Boolean

    /** Fix más fresco disponible dentro del timeout, o null honesto. */
    suspend fun freshFix(timeoutMillis: Long): OutdoorLocationFix?
}

/**
 * Lector puro: aplica el gating de honestidad sobre el engine.
 * No declara ubicación válida con permiso ausente, servicios apagados,
 * resultado null, fix viejo o precisión excesiva.
 */
class OutdoorLocationReader(
    private val engine: OutdoorLocationEngine,
    private val maxAgeMillis: Long = OutdoorBudgets.MAX_FIX_AGE_MILLIS,
    private val maxAccuracyMeters: Float = OutdoorBudgets.MAX_ACCURACY_METERS
) {
    suspend fun read(timeoutMillis: Long = 8_000L): OutdoorFixResult {
        if (!runCatching { engine.hasPermission() }.getOrDefault(false)) {
            return OutdoorFixResult.PermissionMissing
        }
        if (!runCatching { engine.servicesEnabled() }.getOrDefault(false)) {
            return OutdoorFixResult.ServicesDisabled
        }
        val fix = runCatching { engine.freshFix(timeoutMillis) }.getOrNull()
            ?: return OutdoorFixResult.Unavailable

        return when {
            fix.ageMillis > maxAgeMillis -> OutdoorFixResult.TooOld(fix)
            (fix.accuracyMeters ?: Float.MAX_VALUE) > maxAccuracyMeters ->
                OutdoorFixResult.TooInaccurate(fix)
            else -> OutdoorFixResult.Valid(fix)
        }
    }

    /**
     * Frase honesta de "¿dónde estoy?". Sin dirección exacta: v1 no hace
     * reverse-geocoding; informa precisión real o el problema real.
     */
    fun spokenLocationText(result: OutdoorFixResult): String = when (result) {
        is OutdoorFixResult.Valid -> {
            val accuracy = result.fix.accuracyMeters?.toInt()
            if (accuracy != null) {
                "Según la ubicación disponible, tengo tu posición con una " +
                    "precisión aproximada de $accuracy metros."
            } else {
                "Tengo tu posición, pero no puedo confirmar la precisión."
            }
        }
        OutdoorFixResult.PermissionMissing ->
            "No tengo permiso de ubicación. Activalo en Ajustes para que pueda orientarte."
        OutdoorFixResult.ServicesDisabled ->
            "La ubicación del teléfono está desactivada. Encendela para que pueda orientarte."
        OutdoorFixResult.Unavailable ->
            "No pude obtener tu ubicación todavía. Probá de nuevo en un momento."
        is OutdoorFixResult.TooOld ->
            "La última ubicación que tengo es vieja y no es confiable. Probá de nuevo en un momento."
        is OutdoorFixResult.TooInaccurate -> {
            val accuracy = result.fix.accuracyMeters?.toInt() ?: 0
            "Tu ubicación está imprecisa: la tengo con unos $accuracy metros de " +
                "margen, así que no puedo darte una posición exacta. Para " +
                "orientarte mejor, acercate a una ventana o a una zona más abierta."
        }
    }
}

/** Implementación Android real (framework LocationManager). */
class AndroidOutdoorLocationEngine(
    private val context: Context
) : OutdoorLocationEngine {

    private val locationManager: LocationManager?
        get() = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    override fun hasPermission(): Boolean = listOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ).any { permission ->
        ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
    }

    override fun servicesEnabled(): Boolean {
        val manager = locationManager ?: return false
        return runCatching {
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }.getOrDefault(false)
    }

    override suspend fun freshFix(timeoutMillis: Long): OutdoorLocationFix? {
        val manager = locationManager ?: return null
        val provider = when {
            runCatching { manager.isProviderEnabled(LocationManager.GPS_PROVIDER) }
                .getOrDefault(false) -> LocationManager.GPS_PROVIDER
            runCatching { manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }
                .getOrDefault(false) -> LocationManager.NETWORK_PROVIDER
            else -> return null
        }

        val current: Location? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            withTimeoutOrNull(timeoutMillis) {
                suspendCancellableCoroutine { continuation ->
                    val signal = CancellationSignal()
                    continuation.invokeOnCancellation { signal.cancel() }
                    runCatching {
                        manager.getCurrentLocation(
                            provider,
                            signal,
                            ContextCompat.getMainExecutor(context)
                        ) { location -> continuation.resume(location) }
                    }.onFailure { continuation.resume(null) }
                }
            }
        } else {
            null
        }

        val best = current ?: bestLastKnown(manager) ?: return null
        return best.toFix()
    }

    private fun bestLastKnown(manager: LocationManager): Location? =
        listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        ).mapNotNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }.maxByOrNull { it.time }

    private fun Location.toFix(): OutdoorLocationFix {
        val ageMillis = if (elapsedRealtimeNanos > 0L) {
            ((SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos) / 1_000_000L)
                .coerceAtLeast(0L)
        } else {
            (System.currentTimeMillis() - time).coerceAtLeast(0L)
        }
        return OutdoorLocationFix(
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = if (hasAccuracy()) accuracy else null,
            ageMillis = ageMillis,
            provider = provider ?: "unknown"
        )
    }
}
