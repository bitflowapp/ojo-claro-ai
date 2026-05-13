package com.ojoclaro.android.external

import com.ojoclaro.android.privacy.PrivacyGuard
import com.ojoclaro.android.voice.VoicePhraseNormalizer
import java.text.Normalizer
import java.util.Locale

class CommandRouter(
    private val confirmationTtlMillis: Long = 2 * 60 * 1_000L
) {
    fun parse(rawInput: String): ExternalCommand {
        val trimmed = VoicePhraseNormalizer.normalizeForParser(rawInput.trim())
        val normalized = normalize(trimmed)
        val normalizedForRouting = normalizeWhatsAppAliases(normalized)

        if (normalized.isBlank()) {
            return unsupported(rawInput)
        }

        parseComposeCommand(trimmed, normalizedForRouting)?.let { return it }

        return when {
            normalized in confirmPhrases ->
                ExternalCommand(ExternalCommandType.CONFIRM_PENDING_ACTION, rawInput)

            normalized in cancelPhrases ->
                ExternalCommand(ExternalCommandType.CANCEL_PENDING_ACTION, rawInput)

            isRememberCommand(normalized) ->
                ExternalCommand(ExternalCommandType.REMEMBER_MEMORY, rawInput)

            normalized in listMemoryPhrases ->
                ExternalCommand(ExternalCommandType.LIST_MEMORY, rawInput)

            normalized in forgetLastMemoryPhrases ->
                ExternalCommand(ExternalCommandType.FORGET_LAST_MEMORY, rawInput)

            normalized in clearMemoryPhrases ->
                ExternalCommand(ExternalCommandType.CLEAR_MEMORY, rawInput)

            isOpenWhatsAppCommand(normalizedForRouting) ->
                ExternalCommand(ExternalCommandType.OPEN_WHATSAPP, rawInput)

            normalized == "leeme este mensaje" ||
                normalized == "leer este mensaje" ||
                normalized == "lee este mensaje" ||
                normalized == "que dice la pantalla" ||
                normalized == "leer pantalla" ||
                normalized == "leeme la pantalla" ->
                ExternalCommand(ExternalCommandType.READ_VISIBLE_SCREEN, rawInput)

            else -> unsupported(rawInput)
        }
    }

    fun route(
        rawInput: String,
        pendingConfirmation: PendingConfirmation? = null,
        nowMillis: Long = System.currentTimeMillis()
    ): ExternalCommandRoute {
        val command = parse(rawInput)

        return when (command.type) {
            ExternalCommandType.COMPOSE_WHATSAPP_MESSAGE -> {
                val contactName = command.contactName.orEmpty()
                val messageText = command.messageText.orEmpty()
                if (contactName.isBlank()) {
                    return ExternalCommandRoute(
                        command = command,
                        result = CommandResult.Failed(
                            spokenText = "¿A quién querés mandarle el mensaje?",
                            recoverable = true
                        )
                    )
                }
                if (messageText.isBlank()) {
                    return ExternalCommandRoute(
                        command = command,
                        result = CommandResult.Failed(
                            spokenText = "¿Qué mensaje querés mandarle?",
                            recoverable = true
                        )
                    )
                }
                if (!PrivacyGuard.isSafeMessagePayload(messageText)) {
                    return ExternalCommandRoute(
                        command = command,
                        result = CommandResult.Failed(
                            spokenText = "No puedo preparar ese mensaje porque parece contener datos sensibles.",
                            recoverable = false
                        )
                    )
                }

                val confirmationId = "external-confirmation-$nowMillis"
                val spokenText = buildComposeConfirmation(command)
                val pending = PendingConfirmation(
                    id = confirmationId,
                    command = command,
                    spokenText = spokenText,
                    createdAtMillis = nowMillis
                )
                ExternalCommandRoute(
                    command = command,
                    result = CommandResult.NeedsConfirmation(spokenText, confirmationId),
                    pendingConfirmation = pending
                )
            }

            ExternalCommandType.CONFIRM_PENDING_ACTION -> {
                if (pendingConfirmation == null) {
                    ExternalCommandRoute(
                        command = command,
                        result = CommandResult.Failed(
                            spokenText = "No hay ninguna acción pendiente para confirmar.",
                            recoverable = true
                        )
                    )
                } else if (nowMillis - pendingConfirmation.createdAtMillis > confirmationTtlMillis) {
                    ExternalCommandRoute(
                        command = command,
                        result = CommandResult.Failed(
                            spokenText = "La acción pendiente venció. Volvé a pedirla.",
                            recoverable = true
                        ),
                        clearsPending = true
                    )
                } else {
                    val spokenText = when (pendingConfirmation.command.type) {
                        ExternalCommandType.CALL_CONTACT ->
                            "Confirmado. Voy a abrir Teléfono con el número preparado."
                        ExternalCommandType.NAVIGATE_TO_DESTINATION,
                        ExternalCommandType.NAVIGATE_TO_COORDINATES ->
                            "Confirmado. Voy a abrir mapas."
                        ExternalCommandType.OPEN_WHATSAPP_CHAT ->
                            "Confirmado. Voy a abrir el chat de WhatsApp. No envío ningún mensaje."
                        else ->
                            "Confirmado. Voy a abrir WhatsApp con el mensaje preparado."
                    }
                    ExternalCommandRoute(
                        command = pendingConfirmation.command,
                        result = CommandResult.Success(
                            spokenText = spokenText
                        ),
                        clearsPending = true
                    )
                }
            }

            ExternalCommandType.CANCEL_PENDING_ACTION -> {
                val spokenText = if (pendingConfirmation == null) {
                    "No hay ninguna acción pendiente."
                } else {
                    "Acción cancelada."
                }
                ExternalCommandRoute(
                    command = command,
                    result = CommandResult.Success(spokenText),
                    clearsPending = pendingConfirmation != null
                )
            }

            ExternalCommandType.OPEN_WHATSAPP ->
                ExternalCommandRoute(
                    command = command,
                    result = CommandResult.Success("Voy a abrir WhatsApp.")
                )

            // OPEN_WHATSAPP_CHAT no se emite desde parse() — es flujo del agente.
            // Lo dejamos exhaustivo: si alguna vez llega por aquí, fallback explícito.
            ExternalCommandType.OPEN_WHATSAPP_CHAT ->
                ExternalCommandRoute(
                    command = command,
                    result = CommandResult.NotSupported(unsupportedText)
                )

            ExternalCommandType.OPEN_PHONE ->
                ExternalCommandRoute(
                    command = command,
                    result = CommandResult.Success("Voy a abrir Teléfono.")
                )

            ExternalCommandType.CALL_CONTACT -> {
                val contactName = command.targetName.orEmpty()
                val phoneNumber = command.payloadText.orEmpty()
                if (contactName.isBlank()) {
                    ExternalCommandRoute(
                        command = command,
                        result = CommandResult.Failed(
                            spokenText = "¿A quién querés llamar?",
                            recoverable = true
                        )
                    )
                } else if (phoneNumber.isBlank()) {
                    ExternalCommandRoute(
                        command = command,
                        result = CommandResult.Failed(
                            spokenText = "No tengo un número guardado para $contactName. Abrí Teléfono para elegirlo.",
                            recoverable = true
                        )
                    )
                } else {
                    val confirmationId = "phone-confirmation-$nowMillis"
                    val spokenText = "Voy a preparar una llamada a $contactName. " +
                        "No voy a llamar automáticamente. Confirmá para continuar."
                    val pending = PendingConfirmation(
                        id = confirmationId,
                        command = command,
                        spokenText = spokenText,
                        createdAtMillis = nowMillis
                    )
                    ExternalCommandRoute(
                        command = command,
                        result = CommandResult.NeedsConfirmation(spokenText, confirmationId),
                        pendingConfirmation = pending
                    )
                }
            }

            ExternalCommandType.NAVIGATE_TO_DESTINATION,
            ExternalCommandType.NAVIGATE_TO_COORDINATES ->
                ExternalCommandRoute(
                    command = command,
                    result = CommandResult.NotSupported(unsupportedText)
                )

            ExternalCommandType.READ_VISIBLE_SCREEN ->
                ExternalCommandRoute(
                    command = command,
                    result = CommandResult.Success("Voy a leer el texto visible de la pantalla.")
                )

            ExternalCommandType.REMEMBER_MEMORY,
            ExternalCommandType.LIST_MEMORY,
            ExternalCommandType.FORGET_LAST_MEMORY,
            ExternalCommandType.CLEAR_MEMORY ->
                ExternalCommandRoute(
                    command = command,
                    result = CommandResult.Success("Procesando memoria local.")
                )

            ExternalCommandType.UNSUPPORTED ->
                ExternalCommandRoute(
                    command = command,
                    result = CommandResult.NotSupported(unsupportedText)
                )
        }
    }

    private fun parseComposeCommand(
        rawInput: String,
        normalizedForRouting: String
    ): ExternalCommand? {
        if (!hasMessageIntent(normalizedForRouting)) return null

        val candidate = rawInput.stripWhatsAppComposeNoise()

        colonComposeRegex.matchEntire(candidate)?.let { match ->
            return composeCommand(
                rawInput = rawInput,
                contactName = match.groupValues[1],
                messageText = match.groupValues[2],
                confidence = CommandConfidence.HIGH
            )
        }

        naturalComposeRegexes.firstNotNullOfOrNull { regex ->
            regex.matchEntire(candidate)
        }?.let { match ->
            return composeCommand(
                rawInput = rawInput,
                contactName = match.groupValues[1],
                messageText = match.groupValues[2],
                confidence = CommandConfidence.HIGH
            )
        }

        missingMessageRegex.matchEntire(candidate)?.let { match ->
            return composeCommand(
                rawInput = rawInput,
                contactName = match.groupValues[1],
                messageText = "",
                confidence = CommandConfidence.MEDIUM
            )
        }

        return ExternalCommand(
            type = ExternalCommandType.COMPOSE_WHATSAPP_MESSAGE,
            rawText = rawInput,
            normalizedText = normalizedForRouting,
            confidence = CommandConfidence.LOW
        )
    }

    private fun composeCommand(
        rawInput: String,
        contactName: String,
        messageText: String,
        confidence: CommandConfidence
    ): ExternalCommand {
        return ExternalCommand(
            type = ExternalCommandType.COMPOSE_WHATSAPP_MESSAGE,
            rawText = rawInput,
            normalizedText = normalizeWhatsAppAliases(normalize(rawInput)),
            contactName = contactName.cleanContactName(),
            messageText = messageText.cleanMessage(),
            confidence = confidence
        )
    }

    private fun buildComposeConfirmation(command: ExternalCommand): String {
        val contactName = command.contactName.orEmpty().limitForSpeech(MAX_CONTACT_CONFIRMATION_CHARS)
        val messageText = command.messageText.orEmpty().limitForSpeech(MAX_MESSAGE_CONFIRMATION_CHARS)

        return "Puedo preparar este mensaje para $contactName: '$messageText'. " +
            "No lo envío automáticamente. Para prepararlo en WhatsApp, decí: confirmar."
    }

    private fun isOpenWhatsAppCommand(normalizedForRouting: String): Boolean {
        if (hasMessageIntent(normalizedForRouting)) return false
        return openWhatsAppPhrases.any { phrase -> normalizedForRouting == phrase }
    }

    private fun hasMessageIntent(normalizedForRouting: String): Boolean =
        messageIntentRegex.containsMatchIn(normalizedForRouting)

    private fun normalizeWhatsAppAliases(normalized: String): String =
        whatsappAliasNormalizedRegex.replace(normalized, "whatsapp")

    private fun String.stripWhatsAppComposeNoise(): String =
        trim()
            .replace(leadingInWhatsAppRegex, "")
            .replace(leadingOpenWhatsAppAndRegex, "")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun isRememberCommand(normalized: String): Boolean {
        return rememberPrefixes.any { prefix -> normalized.startsWith(prefix) }
    }

    private fun unsupported(rawInput: String): ExternalCommand {
        return ExternalCommand(ExternalCommandType.UNSUPPORTED, rawInput)
    }

    private fun normalize(text: String): String {
        val withoutAccents = Normalizer.normalize(
            text.lowercase(Locale("es", "AR")),
            Normalizer.Form.NFD
        ).replace(diacriticRegex, "")

        return withoutAccents
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim('.', '!', '?', '¿', '¡')
    }

    private fun String.cleanContactName(): String {
        return trim()
            .trim('.', ',', ';', ':')
            .replace(Regex("\\s+"), " ")
    }

    private fun String.cleanMessage(): String {
        return trim()
            .trim('"', '“', '”')
            .replace(Regex("\\s+"), " ")
    }

    private fun String.limitForSpeech(maxChars: Int): String {
        val cleaned = replace(Regex("\\s+"), " ").trim()
        if (cleaned.length <= maxChars) return cleaned

        return cleaned
            .take(maxChars)
            .trimEnd()
            .trimEnd('.', ',', ';', ':')
            .plus("…")
    }

    companion object {
        const val unsupportedText =
            "No entendí esa acción. Podés decir: leer pantalla, abrir WhatsApp, escribir mensaje, recordar algo, confirmar o cancelar."

        private const val MAX_CONTACT_CONFIRMATION_CHARS = 80
        private const val MAX_MESSAGE_CONFIRMATION_CHARS = 220

        private val diacriticRegex = Regex("\\p{Mn}+")

        // Confirmaciones deliberadamente estrictas.
        // No usar "sí" ni "dale" para evitar confirmar acciones sensibles por accidente.
        // Aceptamos variantes contextuales ("confirmar llamada", "confirmar mensaje")
        // porque el agente puede sugerirlas para reducir ambigüedad.
        private val confirmPhrases = setOf(
            "confirmar",
            "confirmo",
            "aceptar",
            "confirmar llamada",
            "confirmar la llamada",
            "confirmar mensaje",
            "confirmar el mensaje"
        )

        private val cancelPhrases = setOf(
            "cancelar",
            "cancela",
            "no",
            "anular"
        )

        private val rememberPrefixes = listOf(
            "recorda que ",
            "recordar que ",
            "recordame que ",
            "recuerda que ",
            "acordate que ",
            "guarda que ",
            "quiero que recuerdes que ",
            "quiero que recordes que "
        )

        private val listMemoryPhrases = setOf(
            "que recordas de mi",
            "que recuerdas de mi",
            "que sabes de mi",
            "que tenes guardado",
            "que tienes guardado"
        )

        private val forgetLastMemoryPhrases = setOf(
            "olvida eso",
            "olvidate de eso",
            "olvida el ultimo recuerdo",
            "borra eso"
        )

        private val clearMemoryPhrases = setOf(
            "borra tu memoria",
            "borrar tu memoria",
            "borra toda tu memoria",
            "borrar toda tu memoria",
            "limpia tu memoria",
            "vaciar memoria"
        )

        private const val WHATSAPP_ALIAS_RAW =
            "(?:whats\\s*app|whatsapp|wp|wsp|wpp|wasap|guasap|watsap|whasap)"
        private const val MESSAGE_VERB_RAW =
            "(?:mandale|mandarle|mandá|manda|mandar|enviale|enviarle|enviar|escribile|escribirle|escribir|decile|decirle|decir|avisale|avisarle|avisar)"
        private const val MESSAGE_NOUN_RAW =
            "(?:(?:un\\s+)?(?:mensaje|$WHATSAPP_ALIAS_RAW))"
        private const val MESSAGE_SEPARATOR_RAW =
            "(?:diciendo\\s+que|diciendo|que|con\\s+el\\s+texto)"

        private val whatsappAliasNormalizedRegex =
            Regex("\\b(?:whats app|whatsapp|wp|wsp|wpp|wasap|guasap|watsap|whasap)\\b")

        private val messageIntentRegex = Regex(
            "\\b(?:mandale|mandarle|manda|mandar|enviale|enviarle|enviar|escribile|escribirle|escribir|decile|decirle|decir|avisale|avisarle|avisar)\\b|" +
                "\\b(?:mandar|enviar)\\s+mensaje\\b|" +
                "\\bescribile\\s+por\\s+whatsapp\\b"
        )

        private val openWhatsAppPhrases = setOf(
            "abrir whatsapp",
            "abri whatsapp",
            "abre whatsapp",
            "abreme whatsapp",
            "abrir el whatsapp",
            "abri el whatsapp",
            "abre el whatsapp",
            "abreme el whatsapp",
            "abrir whatsapp principal",
            "abri whatsapp principal",
            "abre whatsapp principal",
            "abreme whatsapp principal",
            "abrir el whatsapp principal",
            "abri el whatsapp principal",
            "abre el whatsapp principal",
            "abreme el whatsapp principal",
            "abrir whatsapp solamente",
            "abri whatsapp solamente",
            "abre whatsapp solamente",
            "abreme whatsapp solamente",
            "solo abrir whatsapp",
            "solo abri whatsapp",
            "solo abre whatsapp",
            "solo abreme whatsapp",
            "solamente abrir whatsapp",
            "solamente abri whatsapp",
            "solamente abre whatsapp",
            "solamente abreme whatsapp"
        )

        private val leadingInWhatsAppRegex = Regex(
            "^\\s*en\\s+$WHATSAPP_ALIAS_RAW\\s+",
            option = RegexOption.IGNORE_CASE
        )

        private val leadingOpenWhatsAppAndRegex = Regex(
            "^\\s*(?:abrí|abri|abrir|abre|abreme)\\s+$WHATSAPP_ALIAS_RAW\\s+y\\s+",
            option = RegexOption.IGNORE_CASE
        )

        private val colonComposeRegex = Regex(
            pattern = "^\\s*$MESSAGE_VERB_RAW(?:\\s+$MESSAGE_NOUN_RAW)?\\s+a\\s+([^:]+?)\\s*:\\s*(.+)\\s*$",
            option = RegexOption.IGNORE_CASE
        )

        private val naturalComposeRegexes = listOf(
            Regex(
                pattern = "^\\s*$MESSAGE_VERB_RAW(?:\\s+$MESSAGE_NOUN_RAW)?\\s+a\\s+(.+?)(?:\\s+por\\s+$WHATSAPP_ALIAS_RAW)?\\s+$MESSAGE_SEPARATOR_RAW\\s+(.+)\\s*$",
                option = RegexOption.IGNORE_CASE
            ),
            Regex(
                pattern = "^\\s*(?:mandar|enviar)\\s+mensaje\\s+a\\s+(.+?)(?:\\s+por\\s+$WHATSAPP_ALIAS_RAW)?\\s+$MESSAGE_SEPARATOR_RAW\\s+(.+)\\s*$",
                option = RegexOption.IGNORE_CASE
            )
        )

        private val missingMessageRegex = Regex(
            pattern = "^\\s*$MESSAGE_VERB_RAW(?:\\s+$MESSAGE_NOUN_RAW)?\\s+a\\s+(.+?)\\s*$",
            option = RegexOption.IGNORE_CASE
        )
    }
}
