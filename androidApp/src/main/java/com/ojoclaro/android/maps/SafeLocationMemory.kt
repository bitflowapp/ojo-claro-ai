package com.ojoclaro.android.maps

import com.ojoclaro.android.memory.MemoryType
import com.ojoclaro.android.memory.UserMemory
import java.util.Locale

object SafeLocationMemory {
    const val VALUE_PREFIX = "geo:"

    fun value(
        latitude: Double,
        longitude: Double,
        accuracyMeters: Float?
    ): String {
        val roundedLatitude = roundCoordinate(latitude)
        val roundedLongitude = roundCoordinate(longitude)
        val accuracy = accuracyMeters?.let { ";accuracy=${it.coerceAtLeast(0f).toInt()}" }.orEmpty()
        return "$VALUE_PREFIX$roundedLatitude,$roundedLongitude$accuracy"
    }

    fun parse(value: String): StoredLocation? {
        val cleanValue = value.trim()
        if (!cleanValue.startsWith(VALUE_PREFIX, ignoreCase = true)) return null

        val coordinates = cleanValue
            .substringAfter(VALUE_PREFIX)
            .substringBefore(';')
            .split(',')
            .map(String::trim)

        if (coordinates.size != 2) return null
        val latitude = coordinates[0].toDoubleOrNull() ?: return null
        val longitude = coordinates[1].toDoubleOrNull() ?: return null
        if (!isValidCoordinate(latitude, longitude)) return null

        val accuracy = cleanValue.substringAfter("accuracy=", "")
            .toFloatOrNull()
            ?.takeIf { it >= 0f }

        return StoredLocation(latitude, longitude, accuracy)
    }

    fun isLocationAlias(memory: UserMemory): Boolean =
        memory.type == MemoryType.LOCATION_ALIAS &&
            memory.userApproved &&
            !memory.isSensitive &&
            parse(memory.value) != null

    fun isValidAlias(alias: String): Boolean {
        val cleanAlias = alias.trim()
        if (cleanAlias.isBlank()) return false
        if (cleanAlias.length > MAX_ALIAS_CHARS) return false
        return !sensitiveAliasRegex.containsMatchIn(cleanAlias.lowercase(Locale("es", "AR")))
    }

    fun isValidCoordinate(latitude: Double, longitude: Double): Boolean =
        latitude in -90.0..90.0 && longitude in -180.0..180.0

    private fun roundCoordinate(value: Double): String =
        String.format(Locale.US, "%.5f", value)

    private val sensitiveAliasRegex =
        Regex("\\b(?:banco|billetera|tarjeta|clave|password|contrasena|contraseña|dni|cbu|cvu)\\b")

    private const val MAX_ALIAS_CHARS = 60
}

data class StoredLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?
)
