package com.ojoclaro.android.outdoor

/**
 * Modelos del Outdoor Guidance v1. Kotlin puro, testeable en JVM.
 *
 * Privacidad: las coordenadas exactas viven SOLO en estos objetos en memoria.
 * A logs van buckets ([accuracyBucket]/[ageBucket]); al planner remoto van
 * métricas abstractas; al backend de rutas va el origen solo para calcular.
 */

data class OutdoorLocationFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val ageMillis: Long,
    val provider: String
) {
    val accuracyBucket: String
        get() = when {
            accuracyMeters == null -> "unknown"
            accuracyMeters <= 10f -> "lte10m"
            accuracyMeters <= 25f -> "lte25m"
            accuracyMeters <= 50f -> "lte50m"
            else -> "gt50m"
        }

    val ageBucket: String
        get() = when {
            ageMillis <= 5_000L -> "lte5s"
            ageMillis <= 30_000L -> "lte30s"
            else -> "stale"
        }
}

/** Resultado honesto de pedir ubicación. Nunca inventa un fix. */
sealed class OutdoorFixResult {
    data class Valid(val fix: OutdoorLocationFix) : OutdoorFixResult()
    data object PermissionMissing : OutdoorFixResult()
    data object ServicesDisabled : OutdoorFixResult()
    data object Unavailable : OutdoorFixResult()
    data class TooOld(val fix: OutdoorLocationFix) : OutdoorFixResult()
    data class TooInaccurate(val fix: OutdoorLocationFix) : OutdoorFixResult()
}

data class OutdoorRouteStep(
    val instruction: String,
    val distanceMeters: Int
)

data class OutdoorRouteData(
    val destinationName: String,
    val totalDistanceMeters: Int,
    val totalDurationSeconds: Int,
    val steps: List<OutdoorRouteStep>
)

/** Shape mínimo del progreso (contrato de la spec). */
data class OutdoorRouteProgress(
    val destinationName: String,
    val remainingDistanceMeters: Int,
    val remainingDurationSeconds: Int,
    val nextInstruction: String,
    val distanceToNextManeuverMeters: Int,
    val offRoute: Boolean,
    val locationAccuracyMeters: Float?
)

enum class OutdoorState { IDLE, LOCATING, NAVIGATING, DESCRIBING }

/** Resultado de pedir una ruta al provider. */
sealed class OutdoorRouteOutcome {
    data class Route(val data: OutdoorRouteData) : OutdoorRouteOutcome()
    data object Unconfigured : OutdoorRouteOutcome()
    data object NotFound : OutdoorRouteOutcome()

    /** V1.10.2 — destino real pero demasiado lejos para una ruta a pie. */
    data object TooFar : OutdoorRouteOutcome()

    data class Error(val code: String) : OutdoorRouteOutcome()
}

/** Resultado de la descripción de escena bajo demanda. */
sealed class OutdoorSceneOutcome {
    data class Described(val spokenText: String) : OutdoorSceneOutcome()
    data object CameraPermissionMissing : OutdoorSceneOutcome()
    data object CaptureFailed : OutdoorSceneOutcome()
    data object Timeout : OutdoorSceneOutcome()
    data class Error(val code: String) : OutdoorSceneOutcome()
}

object OutdoorBudgets {
    /** Validez de un fix para hablar de "tu ubicación". */
    const val MAX_FIX_AGE_MILLIS: Long = 30_000L
    const val MAX_ACCURACY_METERS: Float = 50f

    /** Navegación. */
    const val OFF_ROUTE_THRESHOLD_METERS: Double = 40.0
    const val STEP_ADVANCE_THRESHOLD_METERS: Double = 15.0
    const val ARRIVAL_THRESHOLD_METERS: Double = 20.0
    const val MANEUVER_ALERT_METERS: Int = 50

    /**
     * V1.9 — un fix con peor precisión que esto NO alimenta el odómetro de
     * navegación: el ruido de 80-100 m inventa metros caminados y dispara
     * maniobras falsas. Más laxo que MAX_ACCURACY_METERS porque caminando
     * entre edificios la señal degrada y cortar la guía sería peor.
     */
    const val NAV_MAX_ACCURACY_METERS: Float = 60f

    /**
     * V1.9 — si durante la navegación no llega NINGÚN update de ubicación por
     * este tiempo (quieto o GPS caído), Estela avisa UNA vez que sigue ahí.
     * Nunca en loop: el aviso se rearma solo cuando vuelve a haber updates.
     */
    const val STILLNESS_SILENCE_MILLIS: Long = 90_000L

    /**
     * V1.10.1 — por encima de esto, el anuncio de ruta agrega la sugerencia
     * de transporte (que el USUARIO pide por su cuenta: Estela jamás pide
     * un viaje real).
     */
    const val LONG_WALK_SUGGEST_METERS: Int = 1_800

    /** Cámara bajo demanda. */
    const val SCENE_CAPTURE_TIMEOUT_MILLIS: Long = 10_000L
    const val SCENE_DESCRIBE_TIMEOUT_MILLIS: Long = 20_000L
}
