package com.ojoclaro.android.agent.task

import com.ojoclaro.android.agent.command.ParsedCommand
import com.ojoclaro.android.agent.core.screen.StructuredScreenSnapshot
import java.text.Normalizer
import java.util.Locale

class AgentTaskPlanner(
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val idFactory: (Long, String) -> String = { now, suffix -> "task_${now}_$suffix" }
) {

    fun plan(
        rawUserCommand: String,
        parsedCommand: ParsedCommand? = null,
        currentScreenSnapshot: StructuredScreenSnapshot? = null,
        knownApps: List<AgentTaskKnownApp> = emptyList(),
        userPreferences: Map<String, String> = emptyMap()
    ): AgentTaskPlan {
        val cleanCommand = rawUserCommand.trim()
        val now = clock()
        val normalized = normalize(cleanCommand)
        return if (isRideRequest(normalized)) {
            buildRidePlan(
                rawUserCommand = cleanCommand,
                normalizedCommand = normalized,
                knownApps = knownApps,
                now = now
            )
        } else {
            buildUnknownPlan(
                rawUserCommand = cleanCommand.ifBlank { "comando vacio" },
                now = now
            )
        }
    }

    private fun buildRidePlan(
        rawUserCommand: String,
        normalizedCommand: String,
        knownApps: List<AgentTaskKnownApp>,
        now: Long
    ): AgentTaskPlan {
        val planId = idFactory(now, "request-ride")
        val appHint = rideAppHint(normalizedCommand, knownApps)
        val destination = extractDestination(rawUserCommand, normalizedCommand)
        val destinationData = destination?.let {
            mapOf(AgentTaskRequiredData.DESTINATION to it)
        }.orEmpty()
        val destinationStatus = if (destination == null) {
            AgentTaskTicketStatus.WAITING_FOR_USER
        } else {
            AgentTaskTicketStatus.PENDING
        }

        val tickets = listOf(
            AgentTaskTicket(
                id = "$planId-ticket-1-app",
                title = "Buscar app de transporte",
                description = "Identificar una app de transporte disponible para preparar el viaje.",
                status = AgentTaskTicketStatus.ACTIVE,
                riskLevel = AgentTaskRiskLevel.LOW,
                appPackageHint = appHint,
                safeForAutomation = true
            ),
            AgentTaskTicket(
                id = "$planId-ticket-2-destination",
                title = "Confirmar destino",
                description = "Confirmar a donde queres ir antes de avanzar.",
                status = destinationStatus,
                requiredData = setOf(AgentTaskRequiredData.DESTINATION),
                resolvedData = destinationData,
                riskLevel = AgentTaskRiskLevel.LOW,
                safeForAutomation = false
            ),
            AgentTaskTicket(
                id = "$planId-ticket-3-location",
                title = "Revisar ubicacion actual",
                description = "Revisar la ubicacion actual antes de estimar el viaje.",
                status = AgentTaskTicketStatus.PENDING,
                requiredData = setOf(AgentTaskRequiredData.CURRENT_LOCATION),
                riskLevel = AgentTaskRiskLevel.MEDIUM,
                safeForAutomation = false
            ),
            AgentTaskTicket(
                id = "$planId-ticket-4-payment",
                title = "Revisar metodo de pago",
                description = "Revisar la forma de pago sin guardar datos completos.",
                status = AgentTaskTicketStatus.PENDING,
                requiredData = setOf(AgentTaskRequiredData.PAYMENT_METHOD),
                riskLevel = AgentTaskRiskLevel.HIGH,
                confirmationRequired = true,
                safeForAutomation = false
            ),
            AgentTaskTicket(
                id = "$planId-ticket-5-price-driver",
                title = "Revisar precio y conductor",
                description = "Revisar precio, conductor y datos visibles antes de decidir.",
                status = AgentTaskTicketStatus.PENDING,
                requiredData = setOf(AgentTaskRequiredData.PRICE_AND_DRIVER),
                riskLevel = AgentTaskRiskLevel.HIGH,
                confirmationRequired = true,
                safeForAutomation = false
            ),
            AgentTaskTicket(
                id = "$planId-ticket-6-final-confirmation",
                title = "Confirmacion final para solicitar viaje",
                description = "Pedir confirmacion final explicita antes de solicitar el viaje.",
                status = AgentTaskTicketStatus.PENDING,
                requiredData = setOf(AgentTaskRequiredData.FINAL_RIDE_CONFIRMATION),
                riskLevel = AgentTaskRiskLevel.CRITICAL,
                confirmationRequired = true,
                safeForAutomation = false
            )
        )

        return AgentTaskPlan(
            id = planId,
            type = AgentTaskType.REQUEST_RIDE,
            title = "Pedir viaje",
            userGoal = sanitizeOperationalText(rawUserCommand).ifBlank { "pedir viaje" },
            status = if (destination == null) {
                AgentTaskState.WAITING_FOR_USER
            } else {
                AgentTaskState.ACTIVE
            },
            tickets = tickets,
            createdAt = now,
            updatedAt = now,
            riskLevel = AgentTaskRiskLevel.CRITICAL,
            requiresFinalConfirmation = true,
            safeSummaryForSpeech = RIDE_SAFE_SUMMARY
        )
    }

    private fun buildUnknownPlan(rawUserCommand: String, now: Long): AgentTaskPlan {
        val planId = idFactory(now, "unknown")
        return AgentTaskPlan(
            id = planId,
            type = AgentTaskType.UNKNOWN,
            title = "Tarea desconocida",
            userGoal = sanitizeOperationalText(rawUserCommand).ifBlank { "comando desconocido" },
            status = AgentTaskState.BLOCKED,
            tickets = listOf(
                AgentTaskTicket(
                    id = "$planId-ticket-1-unknown",
                    title = "No reconocido",
                    description = "No se creo una tarea porque el comando no coincide con un plan seguro.",
                    status = AgentTaskTicketStatus.BLOCKED,
                    riskLevel = AgentTaskRiskLevel.LOW,
                    safeForAutomation = false
                )
            ),
            createdAt = now,
            updatedAt = now,
            riskLevel = AgentTaskRiskLevel.LOW,
            requiresFinalConfirmation = false,
            safeSummaryForSpeech = "No reconoci una tarea segura para planificar."
        )
    }

    private fun isRideRequest(normalized: String): Boolean =
        normalized.contains("pedime un taxi") ||
            normalized.contains("llamame un taxi") ||
            normalized.contains("pedi un uber") ||
            normalized.contains("conseguime un viaje") ||
            normalized.contains("quiero ir a ") ||
            normalized == "quiero ir a casa" ||
            normalized.contains("pedime un auto") ||
            normalized.contains("buscame un cabify")

    private fun rideAppHint(
        normalizedCommand: String,
        knownApps: List<AgentTaskKnownApp>
    ): String? {
        val detectedName = when {
            normalizedCommand.contains("uber") -> "Uber"
            normalizedCommand.contains("cabify") -> "Cabify"
            else -> null
        }
        if (detectedName == null) return null
        val known = knownApps.firstOrNull {
            it.displayName.equals(detectedName, ignoreCase = true) ||
                it.packageName?.contains(detectedName, ignoreCase = true) == true
        }
        return known?.packageName ?: detectedName
    }

    private fun extractDestination(
        rawCommand: String,
        normalizedCommand: String
    ): String? {
        val normalizedDestination = destinationPatterns
            .asSequence()
            .mapNotNull { regex -> regex.find(normalizedCommand)?.groupValues?.getOrNull(1) }
            .firstOrNull()
            ?.trim()
            ?.removeSuffix(" por favor")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return null

        val rawNormalizedMap = rawCommand
            .split(Regex("\\s+"))
            .associateBy { normalize(it) }
        val restored = normalizedDestination
            .split(Regex("\\s+"))
            .joinToString(" ") { token -> rawNormalizedMap[token] ?: token }
        return sanitizeSlotValue(restored)
    }

    private fun sanitizeSlotValue(value: String): String? {
        val clean = value.trim().trim('.', ',', ';', ':')
        if (clean.isBlank()) return null
        if (containsSensitiveOperationalData(clean)) return null
        return sanitizeOperationalText(clean).take(MAX_SLOT_CHARS).trim().takeIf { it.isNotBlank() }
    }

    companion object {
        const val RIDE_SAFE_SUMMARY: String =
            "Voy a ayudarte a pedir un viaje. Primero necesito confirmar el destino " +
                "y luego revisar precio y forma de pago. No voy a solicitar el viaje " +
                "sin tu confirmacion final."

        fun normalize(text: String): String {
            val withoutMarks = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "")
            return withoutMarks
                .lowercase(Locale.ROOT)
                .replace(Regex("[^a-z0-9\\s]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
        }

        fun sanitizeOperationalText(text: String): String {
            val withoutPasswords = passwordAssignmentRegex.replace(text) {
                "[dato sensible omitido]"
            }
            val withoutCards = cardCandidateRegex.replace(withoutPasswords) { match ->
                val digits = match.value.filter(Char::isDigit)
                if (digits.length in 13..19) "[dato sensible omitido]" else match.value
            }
            val normalized = normalize(withoutCards)
            return if (verificationKeywordRegex.containsMatchIn(normalized)) {
                verificationDigitsRegex.replace(withoutCards, "[dato sensible omitido]")
            } else {
                withoutCards
            }.take(MAX_GOAL_CHARS).trim()
        }

        fun containsSensitiveOperationalData(text: String): Boolean {
            val normalized = normalize(text)
            return passwordAssignmentRegex.containsMatchIn(text) ||
                cardCandidateRegex.findAll(text).any {
                    it.value.filter(Char::isDigit).length in 13..19
                } ||
                (verificationKeywordRegex.containsMatchIn(normalized) &&
                    verificationDigitsRegex.containsMatchIn(text)) ||
                sensitiveKeywordRegex.containsMatchIn(normalized)
        }

        private val destinationPatterns = listOf(
            Regex("\\bquiero ir a (.+)$"),
            Regex("\\b(?:pedime|llamame|pedi|conseguime|buscame)\\s+(?:un|una)?\\s*(?:taxi|uber|cabify|viaje|auto)\\s+(?:para ir a|hasta|a)\\s+(.+)$")
        )
        private val passwordAssignmentRegex =
            Regex("\\b(?:mi\\s+)?(?:contrasena|contrase\\u00f1a|password|clave|pin)\\s*(?:es|:|=)\\s*\\S+", RegexOption.IGNORE_CASE)
        private val cardCandidateRegex = Regex("(?:\\d[ -]?){13,19}")
        private val verificationKeywordRegex = Regex("\\b(?:codigo|cod|verificacion|2fa|otp)\\b")
        private val verificationDigitsRegex = Regex("\\b\\d{4,8}\\b")
        private val sensitiveKeywordRegex =
            Regex("\\b(?:tarjeta|credito|debito|cbu|cvu|banco|password|contrasena|clave|pin|otp)\\b")
        private const val MAX_GOAL_CHARS = 180
        private const val MAX_SLOT_CHARS = 80
    }
}
