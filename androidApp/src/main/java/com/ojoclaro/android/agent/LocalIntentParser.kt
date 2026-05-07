package com.ojoclaro.android.agent

import com.ojoclaro.android.external.CommandConfidence
import com.ojoclaro.android.external.CommandRouter
import com.ojoclaro.android.external.ExternalCommand
import com.ojoclaro.android.external.ExternalCommandType
import com.ojoclaro.android.privacy.PrivacyGuard
import com.ojoclaro.android.voice.ArgentineSpanishLexicon
import com.ojoclaro.android.voice.VoicePhraseNormalizer
import java.text.Normalizer
import java.util.Locale

data class ParsedAgentIntent(
    val intent: AgentIntent,
    val slots: List<AgentSlot>,
    val rawText: String,
    val confidence: Float,
    val missingSlots: List<String> = emptyList(),
    val requiresConfirmation: Boolean = false
) {
    fun slotValue(name: String): String? =
        slots.firstOrNull { it.name == name }?.value

    fun hasSensitiveSlot(): Boolean =
        slots.any { it.isSensitive }
}

class LocalIntentParser(
    private val commandRouter: CommandRouter = CommandRouter()
) : IntentInterpreter {
    override fun parse(rawText: String): ParsedAgentIntent {
        // Pasamos por VoicePhraseNormalizer ANTES de cualquier regex para
        // strippear muletillas argentinas y reescribir voseo. Si el resultado
        // queda vacío, el normalizer preserva el original (caller decide).
        val cleanText = VoicePhraseNormalizer.normalizeForParser(rawText.trim())
        val normalized = normalize(cleanText)

        if (normalized.isBlank()) {
            return unknown(rawText, confidence = 0f)
        }

        if (isHelpCommand(normalized)) {
            return parsed(AgentIntent.HELP, cleanText, confidence = 0.98f)
        }

        if (isStopCommand(normalized)) {
            return parsed(AgentIntent.STOP_SPEAKING, cleanText, confidence = 0.99f)
        }

        if (isReadOcrCommand(normalized)) {
            return parsed(AgentIntent.READ_OCR_TEXT, cleanText, confidence = 0.92f)
        }

        parseMapsAndLocation(cleanText, normalized)?.let { return it }
        parseSafeContactMemory(cleanText, normalized)?.let { return it }
        parseOpenPhone(cleanText, normalized)?.let { return it }
        parseCallContact(cleanText, normalized)?.let { return it }
        parseOpenWhatsAppChat(cleanText, normalized)?.let { return it }
        parseOpenWhatsAppGuidedOrPrincipal(cleanText, normalized)?.let { return it }

        val command = commandRouter.parse(cleanText)
        return when (command.type) {
            ExternalCommandType.OPEN_WHATSAPP ->
                parsed(AgentIntent.OPEN_WHATSAPP, cleanText, confidence = command.confidence.toFloat())

            // CommandRouter nunca emite OPEN_WHATSAPP_CHAT directo desde parse(): ese
            // intent lo detectamos arriba con parseOpenWhatsAppChat. La rama queda
            // exhaustiva por seguridad — si llegara, lo tratamos como UNKNOWN para no
            // disparar acciones sin slots.
            ExternalCommandType.OPEN_WHATSAPP_CHAT ->
                unknown(cleanText, confidence = command.confidence.toFloat())

            ExternalCommandType.OPEN_PHONE ->
                parsed(AgentIntent.OPEN_PHONE, cleanText, confidence = command.confidence.toFloat())

            ExternalCommandType.COMPOSE_WHATSAPP_MESSAGE ->
                parseCompose(command, cleanText)

            ExternalCommandType.CALL_CONTACT ->
                parseCallCommand(command, cleanText)

            ExternalCommandType.NAVIGATE_TO_DESTINATION,
            ExternalCommandType.NAVIGATE_TO_COORDINATES ->
                unknown(cleanText, confidence = command.confidence.toFloat())

            ExternalCommandType.READ_VISIBLE_SCREEN ->
                parsed(AgentIntent.READ_VISIBLE_SCREEN, cleanText, confidence = command.confidence.toFloat())

            ExternalCommandType.REMEMBER_MEMORY ->
                parsed(
                    intent = AgentIntent.REMEMBER_MEMORY,
                    rawText = cleanText,
                    confidence = command.confidence.toFloat(),
                    slots = listOf(rawCommandSlot(cleanText, command.confidence.toFloat())),
                    requiresConfirmation = true
                )

            ExternalCommandType.LIST_MEMORY ->
                parsed(AgentIntent.LIST_MEMORY, cleanText, confidence = command.confidence.toFloat())

            ExternalCommandType.CLEAR_MEMORY ->
                parsed(
                    intent = AgentIntent.CLEAR_MEMORY,
                    rawText = cleanText,
                    confidence = command.confidence.toFloat(),
                    requiresConfirmation = true
                )

            ExternalCommandType.CONFIRM_PENDING_ACTION ->
                parsed(AgentIntent.CONFIRM, cleanText, confidence = command.confidence.toFloat())

            ExternalCommandType.CANCEL_PENDING_ACTION ->
                parsed(AgentIntent.CANCEL, cleanText, confidence = command.confidence.toFloat())

            ExternalCommandType.FORGET_LAST_MEMORY,
            ExternalCommandType.UNSUPPORTED ->
                unknown(cleanText, confidence = command.confidence.toFloat())
        }
    }

    private fun parseOpenPhone(cleanText: String, normalized: String): ParsedAgentIntent? {
        if (normalized !in openPhonePhrases) return null
        return parsed(AgentIntent.OPEN_PHONE, cleanText, confidence = 0.96f)
    }

    /**
     * Detecta intentos de abrir un chat directo en WhatsApp con un contacto.
     *
     * Importante:
     *  - Si la frase contiene un verbo de mensajería (mandale/decile/escribile/...)
     *    devuelve null y deja que CommandRouter la trate como COMPOSE_WHATSAPP_MESSAGE.
     *  - Si "abrí WhatsApp" / "abrí wp" viene SIN contacto, devuelve null y deja
     *    que el flujo de OPEN_WHATSAPP existente la maneje.
     */
    private fun parseOpenWhatsAppChat(cleanText: String, normalized: String): ParsedAgentIntent? {
        if (messageIntentRegex.containsMatchIn(normalized)) return null

        openWhatsAppChatRegexes.firstNotNullOfOrNull { regex -> regex.matchEntire(cleanText) }
            ?.let { match ->
                val contactName = match.groupValues[1].cleanSlotValue()
                return whatsAppChatIntent(cleanText, contactName)
            }

        if (normalized in openWhatsAppChatMissingContactPhrases) {
            return whatsAppChatIntent(cleanText, contactName = "")
        }

        return null
    }

    private fun whatsAppChatIntent(cleanText: String, contactName: String): ParsedAgentIntent {
        val confidence = if (contactName.isBlank()) 0.78f else 0.92f
        return ParsedAgentIntent(
            intent = AgentIntent.OPEN_WHATSAPP_CHAT,
            slots = buildList {
                if (contactName.isNotBlank()) {
                    add(AgentSlot(AgentSlotName.CONTACT_NAME, contactName, confidence = confidence))
                }
                add(rawCommandSlot(cleanText, confidence))
            },
            rawText = cleanText,
            confidence = confidence,
            missingSlots = if (contactName.isBlank()) listOf(AgentSlotName.CONTACT_NAME) else emptyList(),
            requiresConfirmation = contactName.isNotBlank()
        )
    }

    private fun parseOpenWhatsAppGuidedOrPrincipal(
        cleanText: String,
        normalized: String
    ): ParsedAgentIntent? {
        val normalizedForRouting = normalizeWhatsAppAliases(normalized)
        if (normalizedForRouting in openWhatsAppPrincipalPhrases) {
            return parsed(AgentIntent.OPEN_WHATSAPP, cleanText, confidence = 0.96f)
        }
        if (normalizedForRouting !in openWhatsAppGuidedPhrases) return null

        return ParsedAgentIntent(
            intent = AgentIntent.OPEN_WHATSAPP,
            slots = listOf(rawCommandSlot(cleanText, 0.92f)),
            rawText = cleanText,
            confidence = 0.92f,
            missingSlots = listOf(AgentSlotName.WHATSAPP_ACTION)
        )
    }

    private fun parseMapsAndLocation(cleanText: String, normalized: String): ParsedAgentIntent? {
        if (normalized in openMapsPhrases) {
            return parsed(AgentIntent.OPEN_MAPS, cleanText, confidence = 0.96f)
        }

        if (normalized in currentLocationPhrases) {
            return parsed(AgentIntent.GET_CURRENT_LOCATION, cleanText, confidence = 0.95f)
        }

        listLocationAliasesPhrases.firstOrNull { normalized == it }?.let {
            return parsed(AgentIntent.LIST_LOCATION_ALIASES, cleanText, confidence = 0.94f)
        }

        saveLocationAliasRegexes.firstNotNullOfOrNull { regex -> regex.matchEntire(cleanText) }?.let { match ->
            val alias = match.groupValues[1].cleanSlotValue()
            return ParsedAgentIntent(
                intent = AgentIntent.SAVE_LOCATION_ALIAS,
                slots = buildList {
                    if (alias.isNotBlank()) {
                        add(AgentSlot(AgentSlotName.LOCATION_ALIAS, alias, confidence = 0.93f, isSensitive = true))
                    }
                    add(rawCommandSlot(cleanText, 0.93f))
                },
                rawText = cleanText,
                confidence = 0.93f,
                missingSlots = if (alias.isBlank()) listOf(AgentSlotName.LOCATION_ALIAS) else emptyList(),
                requiresConfirmation = alias.isNotBlank()
            )
        }

        saveLocationMissingAliasPhrases.firstOrNull { normalized == it }?.let {
            return ParsedAgentIntent(
                intent = AgentIntent.SAVE_LOCATION_ALIAS,
                slots = listOf(rawCommandSlot(cleanText, 0.8f)),
                rawText = cleanText,
                confidence = 0.8f,
                missingSlots = listOf(AgentSlotName.LOCATION_ALIAS)
            )
        }

        deleteLocationAliasRegex.matchEntire(cleanText)?.let { match ->
            val alias = match.groupValues[1].cleanSlotValue()
            return ParsedAgentIntent(
                intent = AgentIntent.DELETE_LOCATION_ALIAS,
                slots = buildList {
                    if (alias.isNotBlank()) {
                        add(AgentSlot(AgentSlotName.LOCATION_ALIAS, alias, confidence = 0.9f, isSensitive = true))
                    }
                    add(rawCommandSlot(cleanText, 0.9f))
                },
                rawText = cleanText,
                confidence = 0.9f,
                missingSlots = if (alias.isBlank()) listOf(AgentSlotName.LOCATION_ALIAS) else emptyList(),
                requiresConfirmation = alias.isNotBlank()
            )
        }

        navigationRegexes.firstNotNullOfOrNull { regex -> regex.matchEntire(cleanText) }
            ?.let { match ->
                val destination = match.groupValues[1].cleanDestinationValue()
                return if (destination.isBlank()) {
                    ParsedAgentIntent(
                        intent = AgentIntent.NAVIGATE_TO_DESTINATION,
                        slots = listOf(rawCommandSlot(cleanText, 0.78f)),
                        rawText = cleanText,
                        confidence = 0.78f,
                        missingSlots = listOf(AgentSlotName.DESTINATION)
                    )
                } else {
                    val destinationSlotName = if (isLikelyLocationAlias(destination)) {
                        AgentSlotName.LOCATION_ALIAS
                    } else {
                        AgentSlotName.DESTINATION
                    }
                    ParsedAgentIntent(
                        intent = AgentIntent.NAVIGATE_TO_DESTINATION,
                        slots = listOf(
                            AgentSlot(destinationSlotName, destination, confidence = 0.9f, isSensitive = true),
                            AgentSlot(AgentSlotName.DESTINATION, destination, confidence = 0.9f, isSensitive = true),
                            rawCommandSlot(cleanText, 0.9f)
                        ).distinctBy { it.name },
                        rawText = cleanText,
                        confidence = 0.9f,
                        requiresConfirmation = true
                    )
                }
            }

        if (normalized in missingDestinationPhrases) {
            return ParsedAgentIntent(
                intent = AgentIntent.NAVIGATE_TO_DESTINATION,
                slots = listOf(rawCommandSlot(cleanText, 0.78f)),
                rawText = cleanText,
                confidence = 0.78f,
                missingSlots = listOf(AgentSlotName.DESTINATION)
            )
        }

        return null
    }

    private fun parseSafeContactMemory(cleanText: String, normalized: String): ParsedAgentIntent? {
        if (normalized in listTrustedContactsPhrases) {
            return parsed(
                intent = AgentIntent.LIST_CONTACTS,
                rawText = cleanText,
                confidence = 0.94f,
                slots = listOf(
                    AgentSlot(AgentSlotName.CONTACT_TYPE, CONTACT_TYPE_TRUSTED, confidence = 0.94f),
                    rawCommandSlot(cleanText, 0.94f)
                )
            )
        }

        deleteContactRegex.matchEntire(cleanText)?.let { match ->
            val contactName = match.groupValues[1].cleanSlotValue()
            return ParsedAgentIntent(
                intent = AgentIntent.DELETE_CONTACT,
                slots = buildList {
                    if (contactName.isNotBlank()) {
                        add(AgentSlot(AgentSlotName.CONTACT_NAME, contactName, confidence = 0.92f))
                    }
                    add(rawCommandSlot(cleanText, 0.92f))
                },
                rawText = cleanText,
                confidence = 0.92f,
                missingSlots = if (contactName.isBlank()) listOf(AgentSlotName.CONTACT_NAME) else emptyList(),
                requiresConfirmation = contactName.isNotBlank()
            )
        }

        savePhoneRegex.matchEntire(cleanText)?.let { match ->
            val contactName = match.groupValues[1].cleanSlotValue()
            val phoneNumber = match.groupValues[2].cleanSlotValue()
            return ParsedAgentIntent(
                intent = AgentIntent.SAVE_CONTACT_PHONE,
                slots = buildList {
                    add(AgentSlot(AgentSlotName.CONTACT_NAME, contactName, confidence = 0.94f))
                    add(
                        AgentSlot(
                            AgentSlotName.PHONE_NUMBER,
                            phoneNumber,
                            confidence = 0.94f,
                            isSensitive = true
                        )
                    )
                    add(rawCommandSlot(cleanText, 0.94f))
                },
                rawText = cleanText,
                confidence = 0.94f,
                requiresConfirmation = true
            )
        }

        savePhoneMissingNumberRegex.matchEntire(cleanText)?.let { match ->
            val contactName = match.groupValues[1].cleanSlotValue()
            return ParsedAgentIntent(
                intent = AgentIntent.SAVE_CONTACT_PHONE,
                slots = buildList {
                    if (contactName.isNotBlank()) {
                        add(AgentSlot(AgentSlotName.CONTACT_NAME, contactName, confidence = 0.85f))
                    }
                    add(rawCommandSlot(cleanText, 0.85f))
                },
                rawText = cleanText,
                confidence = 0.85f,
                missingSlots = buildList {
                    if (contactName.isBlank()) add(AgentSlotName.CONTACT_NAME)
                    add(AgentSlotName.PHONE_NUMBER)
                }
            )
        }

        savePhoneMissingContactRegex.matchEntire(cleanText)?.let {
            return ParsedAgentIntent(
                intent = AgentIntent.SAVE_CONTACT_PHONE,
                slots = listOf(rawCommandSlot(cleanText, 0.8f)),
                rawText = cleanText,
                confidence = 0.8f,
                missingSlots = listOf(AgentSlotName.CONTACT_NAME, AgentSlotName.PHONE_NUMBER)
            )
        }

        trustedContactRegex.matchEntire(cleanText)?.let { match ->
            return safeContactIntent(
                cleanText = cleanText,
                contactName = match.groupValues[1].cleanSlotValue(),
                contactType = CONTACT_TYPE_TRUSTED
            )
        }

        emergencyContactRegex.matchEntire(cleanText)?.let { match ->
            return safeContactIntent(
                cleanText = cleanText,
                contactName = match.groupValues[1].cleanSlotValue(),
                contactType = CONTACT_TYPE_EMERGENCY
            )
        }

        return null
    }

    private fun safeContactIntent(
        cleanText: String,
        contactName: String,
        contactType: String
    ): ParsedAgentIntent =
        ParsedAgentIntent(
            intent = AgentIntent.SAVE_CONTACT,
            slots = buildList {
                if (contactName.isNotBlank()) {
                    add(AgentSlot(AgentSlotName.CONTACT_NAME, contactName, confidence = 0.94f))
                }
                add(AgentSlot(AgentSlotName.CONTACT_TYPE, contactType, confidence = 0.94f))
                add(rawCommandSlot(cleanText, 0.94f))
            },
            rawText = cleanText,
            confidence = 0.94f,
            missingSlots = if (contactName.isBlank()) listOf(AgentSlotName.CONTACT_NAME) else emptyList(),
            requiresConfirmation = contactName.isNotBlank()
        )

    private fun parseCallContact(cleanText: String, normalized: String): ParsedAgentIntent? {
        if (normalized in callMissingContactPhrases) {
            return missingContactCallIntent(cleanText)
        }

        callContactRegexes.firstNotNullOfOrNull { regex -> regex.matchEntire(cleanText) }
            ?.let { match ->
                val contactName = match.groupValues[1].cleanSlotValue()
                return if (contactName.isBlank()) {
                    missingContactCallIntent(cleanText)
                } else {
                    ParsedAgentIntent(
                        intent = AgentIntent.CALL_CONTACT,
                        slots = listOf(
                            AgentSlot(AgentSlotName.CONTACT_NAME, contactName, confidence = 0.9f),
                            rawCommandSlot(cleanText, 0.9f)
                        ),
                        rawText = cleanText,
                        confidence = 0.9f,
                        requiresConfirmation = true
                    )
                }
            }

        return null
    }

    private fun missingContactCallIntent(cleanText: String): ParsedAgentIntent =
        ParsedAgentIntent(
            intent = AgentIntent.CALL_CONTACT,
            slots = listOf(rawCommandSlot(cleanText, 0.72f)),
            rawText = cleanText,
            confidence = 0.72f,
            missingSlots = listOf(AgentSlotName.CONTACT_NAME)
        )

    private fun isHelpCommand(normalized: String): Boolean =
        normalized in helpPhrases

    private fun isReadOcrCommand(normalized: String): Boolean {
        if (normalized in readOcrPhrases) return true
        if (readOcrPrefixes.any { normalized.startsWith(it) }) return true
        if (readOcrAnchoredRegex.matches(normalized)) return true
        return false
    }

    private fun parseCallCommand(command: ExternalCommand, cleanText: String): ParsedAgentIntent {
        val confidence = command.confidence.toFloat()
        val contactName = command.targetName.orEmpty().trim()
        return ParsedAgentIntent(
            intent = AgentIntent.CALL_CONTACT,
            slots = buildList {
                if (contactName.isNotBlank()) {
                    add(AgentSlot(AgentSlotName.CONTACT_NAME, contactName, confidence))
                }
                add(rawCommandSlot(cleanText, confidence))
            },
            rawText = cleanText,
            confidence = confidence,
            missingSlots = if (contactName.isBlank()) listOf(AgentSlotName.CONTACT_NAME) else emptyList(),
            requiresConfirmation = contactName.isNotBlank()
        )
    }

    private fun parseCompose(command: ExternalCommand, cleanText: String): ParsedAgentIntent {
        val confidence = command.confidence.toFloat()
        val contactName = command.contactName.orEmpty().trim()
        val messageText = command.messageText.orEmpty().trim()
        val messageIsSensitive = messageText.isNotBlank() &&
            !PrivacyGuard.isSafeMessagePayload(messageText)

        val slots = buildList {
            if (contactName.isNotBlank()) {
                add(
                    AgentSlot(
                        name = AgentSlotName.CONTACT_NAME,
                        value = contactName,
                        confidence = confidence
                    )
                )
            }
            if (messageText.isNotBlank()) {
                add(
                    AgentSlot(
                        name = AgentSlotName.MESSAGE_TEXT,
                        value = messageText,
                        confidence = confidence,
                        isSensitive = messageIsSensitive
                    )
                )
            }
            add(rawCommandSlot(cleanText, confidence))
        }

        val missingSlots = when {
            contactName.isBlank() -> listOf(AgentSlotName.CONTACT_NAME)
            messageText.isBlank() -> listOf(AgentSlotName.MESSAGE_TEXT)
            else -> emptyList()
        }

        return ParsedAgentIntent(
            intent = AgentIntent.COMPOSE_WHATSAPP_MESSAGE,
            slots = slots,
            rawText = cleanText,
            confidence = confidence,
            missingSlots = missingSlots,
            requiresConfirmation = missingSlots.isEmpty() && !messageIsSensitive
        )
    }

    private fun parsed(
        intent: AgentIntent,
        rawText: String,
        confidence: Float,
        slots: List<AgentSlot> = listOf(rawCommandSlot(rawText, confidence)),
        requiresConfirmation: Boolean = false
    ): ParsedAgentIntent =
        ParsedAgentIntent(
            intent = intent,
            slots = slots,
            rawText = rawText,
            confidence = confidence,
            requiresConfirmation = requiresConfirmation
        )

    private fun unknown(rawText: String, confidence: Float): ParsedAgentIntent =
        parsed(AgentIntent.UNKNOWN, rawText, confidence)

    private fun rawCommandSlot(value: String, confidence: Float): AgentSlot =
        AgentSlot(
            name = AgentSlotName.RAW_COMMAND,
            value = value,
            confidence = confidence
        )

    private fun CommandConfidence.toFloat(): Float = when (this) {
        CommandConfidence.HIGH -> 0.95f
        CommandConfidence.MEDIUM -> 0.75f
        CommandConfidence.LOW -> 0.45f
    }

    private fun isStopCommand(normalized: String): Boolean =
        normalized in stopPhrases ||
            normalized.contains(" callar ") ||
            normalized.startsWith("callar ") ||
            normalized.endsWith(" callar")

    private fun normalizeWhatsAppAliases(normalized: String): String =
        whatsappAliasNormalizedRegex.replace(normalized, "whatsapp")

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

    private fun String.cleanSlotValue(): String =
        trim()
            .trim('.', ',', ';', ':')
            .replace(Regex("\\s+"), " ")

    private fun String.cleanDestinationValue(): String =
        cleanSlotValue()
            .replace(Regex("^(?:el|la|los|las)\\s+", RegexOption.IGNORE_CASE), "")

    private fun isLikelyLocationAlias(destination: String): Boolean =
        normalize(destination) in locationAliasWords

    companion object {
        private val diacriticRegex = Regex("\\p{Mn}+")

        private val helpPhrases = setOf(
            "que puedo decir",
            "que puedo hacer",
            "que podes hacer",
            "que sabes hacer",
            "ayuda",
            "necesito ayuda",
            "ayudame con la app"
        )

        // "callar" se chequea aparte porque permite uso embebido ("callate por favor").
        // Ya normalizado (sin acentos) — no agregar variantes con tildes.
        private val stopPhrases = setOf(
            "callar",
            "callate",
            "callate por favor",
            "silencio",
            "para",
            "parar",
            "deja de hablar",
            "dejame de hablar"
        )

        // Ya normalizado (sin acentos). "leé" se reduce a "lee" tras normalize().
        private val readOcrPhrases = setOf(
            "leer texto",
            "lee texto",
            "leeme texto",
            "leer un texto",
            "leeme este papel",
            "lee este papel",
            "lee esto",
            "leeme esto",
            "que dice ahi",
            "que dice eso",
            "escanear texto",
            "escanea texto",
            "leer cartel",
            "lee el cartel"
        )

        private val readOcrPrefixes = setOf(
            "leer texto ",
            "leeme texto ",
            "lee texto ",
            "escanear texto ",
            "leer cartel "
        )

        // Captura "lee/leeme + esto/eso/(el|este) cartel/(el|este) papel + cola opcional".
        // Cuidadoso: NO matchea "leeme este mensaje" — eso es READ_VISIBLE_SCREEN y
        // lo delega al CommandRouter.
        private val readOcrAnchoredRegex = Regex(
            "^(?:lee|leeme)\\s+(?:esto|eso|el\\s+cartel|este\\s+cartel|el\\s+papel|este\\s+papel|texto)(?:\\s+.*)?$"
        )

        private val openPhonePhrases = setOf(
            "abri telefono",
            "abrir telefono",
            "abre telefono",
            "abreme telefono",
            "abri el telefono",
            "abrir el telefono",
            "marca este numero",
            "marca un numero"
        )

        private val openMapsPhrases = setOf(
            "abri mapas",
            "abrir mapas",
            "abre mapas",
            "abri google maps",
            "abrir google maps",
            "abre google maps",
            "abri mapas principal",
            "abrir mapas principal",
            "abre mapas principal",
            "abri google maps principal",
            "abrir google maps principal",
            "abre google maps principal"
        )

        private val currentLocationPhrases: Set<String> = setOf(
            "donde estoy",
            "en que lugar estoy",
            "decime mi ubicacion",
            "ubicacion actual",
            "abri mi ubicacion en mapas",
            "abrir mi ubicacion en mapas"
        ) + ArgentineSpanishLexicon.EXTRA_CURRENT_LOCATION_PHRASES

        private val listLocationAliasesPhrases = setOf(
            "que lugares tengo guardados",
            "que ubicaciones tengo guardadas",
            "mis lugares guardados",
            "lugares guardados"
        )

        private val saveLocationMissingAliasPhrases = setOf(
            "guarda esta ubicacion",
            "guarda mi ubicacion",
            "recorda esta ubicacion"
        )

        private val missingDestinationPhrases = setOf(
            "llevame",
            "llevar",
            "como llego",
            "navegar",
            "navegar a",
            "abri mapas hacia",
            "abrir mapas hacia"
        )

        private val saveLocationAliasRegexes = listOf(
            Regex(
                "^\\s*(?:guard[áa]|guarda|guardar|record[áa]|recorda|recordar)\\s+(?:esta|mi)\\s+ubicaci[oó]n\\s+como\\s+(.+?)\\s*$",
                RegexOption.IGNORE_CASE
            ),
            Regex(
                "^\\s*(.+?)\\s+es\\s+esta\\s+ubicaci[oó]n\\s*$",
                RegexOption.IGNORE_CASE
            )
        )

        private val deleteLocationAliasRegex = Regex(
            "^\\s*(?:olvid[áa]|olvida|borr[áa]|borra)\\s+(?:la\\s+)?ubicaci[oó]n\\s+(.+?)\\s*$",
            RegexOption.IGNORE_CASE
        )

        private val navigationRegexes = listOf(
            Regex("^\\s*(?:llevame|llevar)\\s+(?:a|al)\\s+(.+?)\\s*$", RegexOption.IGNORE_CASE),
            Regex("^\\s*llevame\\s+a\\s+(.+?)\\s*$", RegexOption.IGNORE_CASE),
            Regex("^\\s*c[oó]mo\\s+llego\\s+a\\s+(.+?)\\s*$", RegexOption.IGNORE_CASE),
            Regex("^\\s*abr[íi]\\s+(?:google\\s+)?mapas\\s+hacia\\s+(.+?)\\s*$", RegexOption.IGNORE_CASE),
            Regex("^\\s*abrir\\s+(?:google\\s+)?mapas\\s+hacia\\s+(.+?)\\s*$", RegexOption.IGNORE_CASE),
            Regex("^\\s*navegar\\s+a\\s+(.+?)\\s*$", RegexOption.IGNORE_CASE)
        )

        private val locationAliasWords = setOf(
            "casa",
            "mi casa",
            "trabajo",
            "mi trabajo",
            "laburo",
            "mi laburo"
        )

        private val callMissingContactPhrases = setOf(
            "llamar",
            "llama",
            "llama a",
            "llamar a",
            "quiero llamar",
            "quiero llamar a"
        )

        private val callContactRegexes = listOf(
            // "llamá a Sofi", "llama a mamá", "llamar a Sofi"
            Regex(
                "^\\s*(?:llam[áa]|llama|llamar)\\s+a\\s+(.+?)\\s*$",
                RegexOption.IGNORE_CASE
            ),
            // "quiero llamar a Sofi"
            Regex(
                "^\\s*quiero\\s+llamar\\s+a\\s+(.+?)\\s*$",
                RegexOption.IGNORE_CASE
            ),
            // "llamá emergencias", "llamar al 911", "llama al 107"
            Regex(
                "^\\s*(?:llam[áa]|llama|llamar)\\s+(?:al\\s+)?(emergencias?|\\d{2,4})\\s*$",
                RegexOption.IGNORE_CASE
            )
        )
        const val CONTACT_TYPE_TRUSTED = "trusted"
        const val CONTACT_TYPE_EMERGENCY = "emergency"

        private val listTrustedContactsPhrases = setOf(
            "quienes son mis contactos de confianza",
            "quien son mis contactos de confianza",
            "listar contactos de confianza",
            "lista contactos de confianza",
            "mis contactos de confianza"
        )

        private val trustedContactRegex = Regex(
            "^\\s*(?:record[áa]\\s+que\\s+)?(.+?)\\s+es\\s+(?:mi\\s+)?contacto\\s+de\\s+confianza\\s*$",
            RegexOption.IGNORE_CASE
        )

        private val emergencyContactRegex = Regex(
            "^\\s*(?:record[áa]\\s+que\\s+)?(.+?)\\s+es\\s+(?:mi\\s+)?contacto\\s+de\\s+emergencia\\s*$",
            RegexOption.IGNORE_CASE
        )

        private val savePhoneRegex = Regex(
            "^\\s*(?:el\\s+)?n[úu]mero\\s+de\\s+(.+?)\\s+es\\s+(.+?)\\s*$",
            RegexOption.IGNORE_CASE
        )

        private val savePhoneMissingNumberRegex = Regex(
            "^\\s*(?:guard[áa]|guarda|guardar)\\s+(?:el\\s+)?n[úu]mero\\s+de\\s+(.+?)\\s*$",
            RegexOption.IGNORE_CASE
        )

        private val savePhoneMissingContactRegex = Regex(
            "^\\s*(?:guard[áa]|guarda|guardar)\\s+(?:el\\s+)?n[úu]mero\\s*$",
            RegexOption.IGNORE_CASE
        )

        private val deleteContactRegex = Regex(
            "^\\s*(?:olvid[áa]|olvida|borr[áa]|borra)\\s+(?:el\\s+)?contacto\\s+(.+?)\\s*$",
            RegexOption.IGNORE_CASE
        )

        // Verbos de mensajería que indican que el usuario quiere REDACTAR un mensaje,
        // no abrir el chat vacío. Si la frase los contiene, parseOpenWhatsAppChat
        // se hace a un lado y deja que COMPOSE_WHATSAPP_MESSAGE tome el control.
        // Replicamos la lista de CommandRouter para no exponerla cross-paquete.
        private val messageIntentRegex = Regex(
            "\\b(?:mandale|mandarle|manda|mandar|enviale|enviarle|enviar|escribile|escribirle|escribir|decile|decirle|decir|avisale|avisarle|avisar)\\b|" +
                "\\b(?:mandar|enviar)\\s+mensaje\\b|" +
                "\\bescribile\\s+por\\s+whatsapp\\b"
        )

        private const val WHATSAPP_ALIAS_RAW =
            "(?:whats\\s*app|whatsapp|wp|wsp|wpp|wasap|guasap|watsap|whasap)"
        private val whatsappAliasNormalizedRegex =
            Regex("\\b(?:whats app|whatsapp|wp|wsp|wpp|wasap|guasap|watsap|whasap)\\b")

        private val openWhatsAppGuidedPhrases = setOf(
            "abri whatsapp",
            "abrir whatsapp",
            "abre whatsapp",
            "abreme whatsapp"
        )

        private val openWhatsAppPrincipalPhrases = setOf(
            "abri whatsapp principal",
            "abrir whatsapp principal",
            "abre whatsapp principal",
            "abreme whatsapp principal",
            "abri whatsapp solamente",
            "abrir whatsapp solamente",
            "abre whatsapp solamente",
            "abreme whatsapp solamente",
            "solo abri whatsapp",
            "solo abrir whatsapp",
            "solo abre whatsapp",
            "solo abreme whatsapp",
            "solamente abri whatsapp",
            "solamente abrir whatsapp",
            "solamente abre whatsapp",
            "solamente abreme whatsapp"
        )

        // Patrones que abren el chat directo. Capturan el contacto en el grupo 1.
        // Nada de mensaje: si lo hay, messageIntentRegex frena el match antes.
        private val openWhatsAppChatRegexes = listOf(
            Regex(
                "^\\s*(?:abr[íi]|abri|abre|abreme|abrir)\\s+$WHATSAPP_ALIAS_RAW\\s+y\\s+(?:el\\s+)?(?:del\\s+)?chat\\s+(?:de|con)\\s+(.+?)\\s*$",
                RegexOption.IGNORE_CASE
            ),
            Regex(
                "^\\s*(?:abr[íi]|abri|abre|abreme|abrir)\\s+$WHATSAPP_ALIAS_RAW\\s+y\\s+(?:and[áa]|anda|ir|ve)\\s+al\\s+chat\\s+(?:de|con)\\s+(.+?)\\s*$",
                RegexOption.IGNORE_CASE
            ),
            // "buscá el chat de X" / "busca el chat de X" / "buscar chat de X" / "encontrá el chat de X"
            Regex(
                "^\\s*(?:busc[áa]|buscar|b[úu]scame|buscame|encontr[áa]|encontrar|encuentra)\\s+(?:el\\s+)?chat\\s+(?:(?:de|con)\\s+)?(.+?)\\s*$",
                RegexOption.IGNORE_CASE
            ),
            // "abrí el chat de X" / "abri el chat de X" / "abre el chat de X" / "abreme el chat de X"
            Regex(
                "^\\s*(?:abr[íi]|abri|abre|abreme|abrir)\\s+(?:el\\s+)?chat\\s+(?:(?:de|con)\\s+)?(.+?)\\s*$",
                RegexOption.IGNORE_CASE
            ),
            // "andá al chat de X" / "anda al chat de X" / "ir al chat de X" / "ve al chat de X"
            Regex(
                "^\\s*(?:and[áa]|anda|ir|ve)\\s+al\\s+chat\\s+(?:de|con)\\s+(.+?)\\s*$",
                RegexOption.IGNORE_CASE
            ),
            // "abrí WhatsApp con X" / "abrí wp con X" / "abrí wsp con X" / "abreme WhatsApp con X"
            Regex(
                "^\\s*(?:abr[íi]|abre|abreme|abrir)\\s+$WHATSAPP_ALIAS_RAW\\s+con\\s+(.+?)\\s*$",
                RegexOption.IGNORE_CASE
            ),
            // "quiero hablar con X por WhatsApp"
            Regex(
                "^\\s*quiero\\s+hablar\\s+con\\s+(.+?)\\s+por\\s+$WHATSAPP_ALIAS_RAW\\s*$",
                RegexOption.IGNORE_CASE
            ),
            // "buscá a X en WhatsApp" / "busca a X en wp" — variante donde el "en wp" va al final.
            Regex(
                "^\\s*(?:busc[áa]|buscar|b[úu]scame|buscame|encontr[áa]|encontrar|encuentra)\\s+(?:a\\s+)?(.+?)\\s+en\\s+$WHATSAPP_ALIAS_RAW\\s*$",
                RegexOption.IGNORE_CASE
            )
        )

        // "abrí chat" / "abrir chat" / "andá al chat" sin contacto explícito.
        // Ya normalizado: sin tildes.
        private val openWhatsAppChatMissingContactPhrases = setOf(
            "abri chat",
            "abrir chat",
            "abre chat",
            "abreme chat",
            "abri un chat",
            "abrir un chat",
            "abre un chat",
            "abri el chat",
            "abrir el chat",
            "abre el chat",
            "anda al chat",
            "ir al chat",
            "ve al chat",
            "busca el chat",
            "buscar el chat",
            "buscar chat",
            "buscame el chat",
            "encontra el chat",
            "encontrar chat"
        )
    }
}
