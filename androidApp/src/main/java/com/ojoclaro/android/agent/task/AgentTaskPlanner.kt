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
        } else if (isWhatsAppTaskRequest(normalized)) {
            buildWhatsAppPlan(
                rawUserCommand = cleanCommand,
                normalizedCommand = normalized,
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

    private fun buildWhatsAppPlan(
        rawUserCommand: String,
        normalizedCommand: String,
        now: Long
    ): AgentTaskPlan {
        val slots = extractWhatsAppSlots(
            rawCommand = rawUserCommand,
            normalizedCommand = normalizedCommand
        )
        val planType = if (slots.wantsAudio) {
            AgentTaskType.SEND_WHATSAPP_AUDIO
        } else {
            AgentTaskType.SEND_WHATSAPP_MESSAGE
        }
        val planId = idFactory(
            now,
            if (slots.wantsAudio) "send-whatsapp-audio" else "send-whatsapp-message"
        )
        val contactData = slots.contactName?.let {
            mapOf(AgentTaskRequiredData.CONTACT_NAME to it)
        }.orEmpty()
        val contentData = slots.messageText?.let {
            mapOf(
                AgentTaskRequiredData.MESSAGE_TEXT to it,
                AgentTaskRequiredData.WANTS_AUDIO to slots.wantsAudio.toString()
            )
        }.orEmpty()
        val contactStatus = if (slots.contactName == null) {
            AgentTaskTicketStatus.WAITING_FOR_USER
        } else {
            AgentTaskTicketStatus.PENDING
        }
        val contentStatus = if (slots.messageText == null) {
            AgentTaskTicketStatus.WAITING_FOR_USER
        } else {
            AgentTaskTicketStatus.PENDING
        }
        val title = when {
            slots.wantsAudio && slots.contactName != null ->
                "Mandar audio a ${slots.contactName}"
            slots.wantsAudio -> "Preparar audio de WhatsApp"
            slots.contactName != null -> "Mandar mensaje a ${slots.contactName}"
            else -> "Preparar mensaje de WhatsApp"
        }

        val tickets = listOf(
            AgentTaskTicket(
                id = "$planId-ticket-1-open-whatsapp",
                title = "Abrir WhatsApp",
                description = "Abrir WhatsApp sin tocar chats ni enviar mensajes.",
                status = AgentTaskTicketStatus.ACTIVE,
                requiredData = setOf(
                    AgentTaskRequiredData.TARGET_APP,
                    AgentTaskRequiredData.WHATSAPP_OPENED
                ),
                resolvedData = mapOf(AgentTaskRequiredData.TARGET_APP to "WhatsApp"),
                riskLevel = AgentTaskRiskLevel.LOW,
                appPackageHint = "com.whatsapp",
                safeForAutomation = true
            ),
            AgentTaskTicket(
                id = "$planId-ticket-2-search-chat",
                title = "Buscar contacto o chat",
                description = "Ubicar el chat correcto en WhatsApp.",
                status = contactStatus,
                requiredData = setOf(AgentTaskRequiredData.CONTACT_NAME),
                resolvedData = contactData,
                riskLevel = AgentTaskRiskLevel.MEDIUM,
                safeForAutomation = false
            ),
            AgentTaskTicket(
                id = "$planId-ticket-3-confirm-chat",
                title = "Confirmar chat correcto",
                description = "Confirmar con el usuario que el chat visible es el correcto.",
                status = AgentTaskTicketStatus.PENDING,
                requiredData = setOf(AgentTaskRequiredData.WHATSAPP_CHAT_CONFIRMED),
                riskLevel = AgentTaskRiskLevel.MEDIUM,
                confirmationRequired = true,
                safeForAutomation = false
            ),
            AgentTaskTicket(
                id = "$planId-ticket-4-prepare-content",
                title = if (slots.wantsAudio) "Preparar contenido del audio" else "Preparar contenido del mensaje",
                description = if (slots.wantsAudio) {
                    "Preparar el contenido del audio sin grabarlo ni enviarlo."
                } else {
                    "Preparar el contenido del mensaje sin escribirlo ni enviarlo automaticamente."
                },
                status = contentStatus,
                requiredData = setOf(
                    AgentTaskRequiredData.MESSAGE_TEXT,
                    AgentTaskRequiredData.WANTS_AUDIO
                ),
                resolvedData = contentData,
                riskLevel = AgentTaskRiskLevel.MEDIUM,
                safeForAutomation = false
            ),
            AgentTaskTicket(
                id = "$planId-ticket-5-final-confirmation",
                title = "Confirmacion final antes de enviar",
                description = "Pedir confirmacion final antes de cualquier envio.",
                status = AgentTaskTicketStatus.PENDING,
                requiredData = setOf(AgentTaskRequiredData.FINAL_MESSAGE_CONFIRMATION),
                riskLevel = AgentTaskRiskLevel.HIGH,
                confirmationRequired = true,
                safeForAutomation = false
            ),
            AgentTaskTicket(
                id = "$planId-ticket-6-send-blocked",
                title = "Envio bloqueado hasta paquete futuro",
                description = "El envio real queda bloqueado hasta que exista una capacidad segura futura.",
                status = AgentTaskTicketStatus.BLOCKED,
                requiredData = setOf(AgentTaskRequiredData.MESSAGE_SEND_BLOCKED),
                riskLevel = AgentTaskRiskLevel.HIGH,
                confirmationRequired = true,
                safeForAutomation = false
            )
        )

        return AgentTaskPlan(
            id = planId,
            type = planType,
            title = title,
            userGoal = sanitizeOperationalText(rawUserCommand).ifBlank { title },
            status = if (slots.contactName == null || slots.messageText == null) {
                AgentTaskState.WAITING_FOR_USER
            } else {
                AgentTaskState.ACTIVE
            },
            tickets = tickets,
            createdAt = now,
            updatedAt = now,
            riskLevel = AgentTaskRiskLevel.HIGH,
            requiresFinalConfirmation = true,
            safeSummaryForSpeech = if (slots.wantsAudio) {
                "Voy a preparar una tarea para un audio por WhatsApp. No voy a grabarlo ni enviarlo sin tu confirmacion final."
            } else {
                "Voy a preparar una tarea para un mensaje por WhatsApp. No voy a enviarlo sin tu confirmacion final."
            }
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

    private fun isWhatsAppTaskRequest(normalized: String): Boolean =
        normalized == "anda a whatsapp" ||
            normalized == "anda al whatsapp" ||
            normalized == "abri whatsapp" ||
            normalized == "abrir whatsapp" ||
            normalized == "busca whatsapp" ||
            normalized.contains(" whatsapp") ||
            normalized.startsWith("whatsapp ") ||
            normalized.contains("chat de ") ||
            normalized.contains("chat con ") ||
            normalized.contains("mandale un mensaje") ||
            normalized.contains("mandale mensaje") ||
            normalized.contains("mandale un audio") ||
            normalized.contains("mandale audio") ||
            normalized.contains("mandarle un audio") ||
            normalized.contains("mandarle audio") ||
            normalized.contains("escribile a ") ||
            normalized.contains("decile a ") ||
            normalized.contains("prepara un mensaje") ||
            normalized.contains("preparar un mensaje")

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

    private fun extractWhatsAppSlots(
        rawCommand: String,
        normalizedCommand: String
    ): WhatsAppTaskSlots {
        val wantsAudio = normalizedCommand.contains(" audio") ||
            normalizedCommand.contains("mensaje de voz") ||
            normalizedCommand.contains("nota de voz") ||
            normalizedCommand.contains("microfono")

        val messageMatch = whatsAppMessagePatterns
            .asSequence()
            .mapNotNull { regex -> regex.find(normalizedCommand) }
            .firstOrNull()
        val contactFromMessage = messageMatch
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        val contentFromMessage = messageMatch
            ?.groupValues
            ?.getOrNull(2)
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        val contactFromChat = whatsAppContactPatterns
            .asSequence()
            .mapNotNull { regex -> regex.find(normalizedCommand)?.groupValues?.getOrNull(1) }
            .firstOrNull()
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        return WhatsAppTaskSlots(
            contactName = (contactFromMessage ?: contactFromChat)
                ?.let { restoreNormalizedSegment(rawCommand, it) }
                ?.let(::sanitizeSlotValue),
            messageText = contentFromMessage
                ?.let { restoreNormalizedSegment(rawCommand, it) }
                ?.let(::sanitizeSlotValue),
            wantsAudio = wantsAudio
        )
    }

    private fun restoreNormalizedSegment(
        rawCommand: String,
        normalizedSegment: String
    ): String {
        val rawNormalizedMap = rawCommand
            .split(Regex("\\s+"))
            .associateBy { normalize(it) }
        return normalizedSegment
            .split(Regex("\\s+"))
            .joinToString(" ") { token -> rawNormalizedMap[token] ?: token }
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
        private val whatsAppMessagePatterns = listOf(
            Regex("\\b(?:mandale|manda|mandar|enviar|enviale|enviarle)\\s+(?:un\\s+)?(?:mensaje|audio|whatsapp|mensaje de voz|nota de voz)?\\s*(?:a|para)\\s+(.+?)\\s+(?:diciendo|que)\\s+(.+)$"),
            Regex("\\b(?:escribile|decile)\\s+(?:a|para)\\s+(.+?)\\s+que\\s+(.+)$"),
            Regex("\\b(?:quiero\\s+)?mandarle\\s+(?:un\\s+)?(?:mensaje|audio|whatsapp|mensaje de voz|nota de voz)\\s+(?:a|para)\\s+(.+?)\\s+(?:diciendo|que)\\s+(.+)$")
        )
        private val whatsAppContactPatterns = listOf(
            Regex("\\b(?:busca|buscar)\\s+(?:el\\s+)?chat\\s+(?:de|con)\\s+(.+)$"),
            Regex("\\b(?:prepara|preparar)\\s+(?:un\\s+)?mensaje\\s+(?:para|a)\\s+(.+)$"),
            Regex("\\b(?:quiero\\s+)?mandarle\\s+(?:un\\s+)?(?:audio|mensaje|whatsapp|mensaje de voz|nota de voz)\\s+(?:a|para)\\s+(.+)$"),
            Regex("\\b(?:mandale|manda|enviar|enviale|escribile|decile)\\s+(?:un\\s+)?(?:mensaje|audio|whatsapp)?\\s*(?:a|para)\\s+(.+)$")
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

private data class WhatsAppTaskSlots(
    val contactName: String?,
    val messageText: String?,
    val wantsAudio: Boolean
)
