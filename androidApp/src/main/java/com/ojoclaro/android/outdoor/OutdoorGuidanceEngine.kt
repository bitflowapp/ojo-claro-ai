package com.ojoclaro.android.outdoor

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Motor puro de progreso de ruta con throttling de voz.
 *
 * NO decide seguridad: solo distancia restante y próxima maniobra, en
 * lenguaje informativo. Habla únicamente ante eventos relevantes:
 * inicio, cambio de maniobra, umbral de giro, desvío CONFIRMADO (dos fixes),
 * reingreso a ruta y llegada aproximada. Nunca en cada update GPS.
 *
 * Limitación honesta v1: sin polyline del proveedor, el progreso se estima
 * por distancia recorrida entre fixes (odómetro GPS) contra las distancias
 * de los pasos, y el desvío por heurística de avance. No usa rumbo: a baja
 * velocidad el bearing GPS es inestable y NO decimos "a tu derecha/izquierda"
 * basados solo en GPS.
 */
class OutdoorGuidanceEngine(
    private val route: OutdoorRouteData
) {
    data class SpeechDecision(
        val speak: Boolean,
        val text: String = "",
        val event: String = "none"
    )

    private var stepIndex: Int = 0
    private var traveledInStepMeters: Double = 0.0
    private var lastFix: OutdoorLocationFix? = null
    private var announcedManeuverAlert: Boolean = false
    private var offRouteCandidate: Boolean = false
    private var offRoute: Boolean = false
    private var arrived: Boolean = false
    private var noProgressFixes: Int = 0

    val isArrived: Boolean get() = arrived

    fun startAnnouncement(): String {
        val total = route.totalDistanceMeters
        val first = route.steps.firstOrNull()?.instruction ?: "seguí por la vereda"
        return "Empiezo a orientarte hacia ${route.destinationName}. " +
            "Son aproximadamente $total metros a pie. Primera indicación: $first."
    }

    fun currentProgress(): OutdoorRouteProgress {
        val remainingSteps = route.steps.drop(stepIndex)
        val currentStepRemaining =
            ((route.steps.getOrNull(stepIndex)?.distanceMeters ?: 0) - traveledInStepMeters)
                .coerceAtLeast(0.0)
        val remaining = currentStepRemaining.toInt() +
            remainingSteps.drop(1).sumOf { it.distanceMeters }
        val nextInstruction = route.steps.getOrNull(stepIndex + 1)?.instruction
            ?: route.steps.getOrNull(stepIndex)?.instruction
            ?: "llegando a destino"
        val remainingRatio = if (route.totalDistanceMeters > 0) {
            remaining.toDouble() / route.totalDistanceMeters
        } else {
            0.0
        }
        return OutdoorRouteProgress(
            destinationName = route.destinationName,
            remainingDistanceMeters = remaining,
            remainingDurationSeconds = (route.totalDurationSeconds * remainingRatio).toInt(),
            nextInstruction = nextInstruction,
            distanceToNextManeuverMeters = currentStepRemaining.toInt(),
            offRoute = offRoute,
            locationAccuracyMeters = lastFix?.accuracyMeters
        )
    }

    fun progressSpokenText(): String {
        val progress = currentProgress()
        return "Faltan aproximadamente ${progress.remainingDistanceMeters} metros. " +
            "En ${progress.distanceToNextManeuverMeters} metros, ${progress.nextInstruction}."
    }

    /** Procesa un fix nuevo y decide si corresponde hablar. */
    fun onLocation(fix: OutdoorLocationFix): SpeechDecision {
        if (arrived) return SpeechDecision(speak = false, event = "already_arrived")

        val previous = lastFix
        lastFix = fix
        if (previous == null) {
            return SpeechDecision(speak = false, event = "first_fix")
        }

        val movedMeters = haversineMeters(
            previous.latitude, previous.longitude, fix.latitude, fix.longitude
        )
        // Ruido GPS parado: ignorar micro-movimientos.
        if (movedMeters < 2.0) {
            noProgressFixes += 1
            return SpeechDecision(speak = false, event = "no_movement")
        }
        noProgressFixes = 0
        traveledInStepMeters += movedMeters

        val progress = currentProgress()

        // Llegada aproximada.
        if (stepIndex >= route.steps.lastIndex &&
            progress.remainingDistanceMeters <= OutdoorBudgets.ARRIVAL_THRESHOLD_METERS
        ) {
            arrived = true
            return SpeechDecision(
                speak = true,
                text = "Estás llegando a ${route.destinationName}. " +
                    "Verificá la entrada con tu método habitual.",
                event = "arrival"
            )
        }

        // Avance de maniobra.
        val currentStep = route.steps.getOrNull(stepIndex)
        if (currentStep != null &&
            traveledInStepMeters >=
            (currentStep.distanceMeters - OutdoorBudgets.STEP_ADVANCE_THRESHOLD_METERS)
        ) {
            stepIndex += 1
            traveledInStepMeters = 0.0
            announcedManeuverAlert = false
            offRouteCandidate = false
            val wasOffRoute = offRoute
            offRoute = false
            val instruction = route.steps.getOrNull(stepIndex)?.instruction
            return if (instruction != null) {
                SpeechDecision(
                    speak = true,
                    text = if (wasOffRoute) {
                        "Volviste a la ruta. Ahora: $instruction."
                    } else {
                        "Ahora: $instruction."
                    },
                    event = if (wasOffRoute) "rejoined_route" else "maneuver_change"
                )
            } else {
                SpeechDecision(speak = false, event = "last_step")
            }
        }

        // Umbral de aviso del próximo giro (una sola vez por paso).
        if (!announcedManeuverAlert &&
            progress.distanceToNextManeuverMeters in 1..OutdoorBudgets.MANEUVER_ALERT_METERS &&
            route.steps.getOrNull(stepIndex + 1) != null
        ) {
            announcedManeuverAlert = true
            return SpeechDecision(
                speak = true,
                text = "En ${progress.distanceToNextManeuverMeters} metros, " +
                    "${route.steps[stepIndex + 1].instruction}.",
                event = "maneuver_alert"
            )
        }

        // Desvío: recorrido del paso superó lo esperado por margen amplio.
        val expected = currentStep?.distanceMeters ?: 0
        val overshoot = traveledInStepMeters - expected
        if (expected > 0 && overshoot > OutdoorBudgets.OFF_ROUTE_THRESHOLD_METERS) {
            return if (!offRouteCandidate) {
                // Primer indicio: confirmar con el próximo fix (anti falso positivo).
                offRouteCandidate = true
                SpeechDecision(speak = false, event = "off_route_candidate")
            } else if (!offRoute) {
                offRoute = true
                SpeechDecision(
                    speak = true,
                    text = "Parece que te alejaste de la ruta. " +
                        "Puedo recalcular si me lo pedís, o volvé unos pasos.",
                    event = "off_route_confirmed"
                )
            } else {
                SpeechDecision(speak = false, event = "off_route_already_announced")
            }
        }

        return SpeechDecision(speak = false, event = "progress_silent")
    }

    companion object {
        fun haversineMeters(
            lat1: Double,
            lon1: Double,
            lat2: Double,
            lon2: Double
        ): Double {
            val earthRadius = 6_371_000.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
            return earthRadius * 2 * atan2(sqrt(a), sqrt(1 - a))
        }
    }
}
