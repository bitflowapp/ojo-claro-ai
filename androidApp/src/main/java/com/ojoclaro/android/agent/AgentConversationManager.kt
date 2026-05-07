package com.ojoclaro.android.agent

import com.ojoclaro.android.privacy.PrivacyGuard
import com.ojoclaro.android.memory.SafeContactMemory
import com.ojoclaro.android.voice.VoicePhraseNormalizer
import java.text.Normalizer
import java.util.Locale

class AgentConversationManager {
    var currentState: AgentState = AgentState.IDLE
        private set

    private var pendingIntent: ParsedAgentIntent? = null
    private var whatsAppGuidedRetrySpoken = false

    val hasPendingSlotRequest: Boolean
        get() = currentState == AgentState.WAITING_CONTACT ||
            currentState == AgentState.WAITING_MESSAGE ||
            currentState == AgentState.WAITING_PHONE_NUMBER ||
            currentState == AgentState.WAITING_DESTINATION ||
            currentState == AgentState.WAITING_LOCATION_ALIAS ||
            currentState == AgentState.WAITING_WHATSAPP_ACTION ||
            currentState == AgentState.WAITING_WHATSAPP_CHAT_OR_MESSAGE

    fun handle(parsedIntent: ParsedAgentIntent): AgentOutcome {
        return when (parsedIntent.intent) {
            AgentIntent.CANCEL -> cancel()
            AgentIntent.CONFIRM -> confirm()
            AgentIntent.STOP_SPEAKING -> stopSpeaking()
            else -> handleIntent(parsedIntent)
        }
    }

    fun clear() {
        pendingIntent = null
        currentState = AgentState.IDLE
        whatsAppGuidedRetrySpoken = false
    }

    private fun handleIntent(parsedIntent: ParsedAgentIntent): AgentOutcome {
        val pending = pendingIntent
        if (pending != null && parsedIntent.intent != AgentIntent.UNKNOWN) {
            clear()
            return handleIntent(parsedIntent)
        }

        if (pending != null && parsedIntent.intent == AgentIntent.UNKNOWN) {
            return fillPendingSlot(parsedIntent.rawText)
        }

        return when (parsedIntent.intent) {
            AgentIntent.OPEN_WHATSAPP -> handleOpenWhatsApp(parsedIntent)
            AgentIntent.COMPOSE_WHATSAPP_MESSAGE -> handleCompose(parsedIntent)
            AgentIntent.CALL_CONTACT -> handleCall(parsedIntent)
            AgentIntent.OPEN_WHATSAPP_CHAT -> handleOpenWhatsAppChat(parsedIntent)
            AgentIntent.SAVE_CONTACT,
            AgentIntent.SAVE_CONTACT_PHONE -> handleSaveContact(parsedIntent)
            AgentIntent.DELETE_CONTACT -> handleDeleteContact(parsedIntent)
            AgentIntent.NAVIGATE_TO_DESTINATION -> handleNavigation(parsedIntent)
            AgentIntent.SAVE_LOCATION_ALIAS -> handleSaveLocationAlias(parsedIntent)
            AgentIntent.DELETE_LOCATION_ALIAS -> handleDeleteLocationAlias(parsedIntent)
            AgentIntent.UNKNOWN -> recoverableError(
                text = "No entendí. Decime, por ejemplo, mandale a Sofi que estoy llegando."
            )
            else -> {
                clear()
                AgentOutcome(
                    spokenText = "",
                    targetState = AgentState.PROCESSING,
                    suggestedIntent = parsedIntent
                )
            }
        }
    }

    private fun handleOpenWhatsApp(parsedIntent: ParsedAgentIntent): AgentOutcome {
        if (parsedIntent.missingSlots.firstOrNull() == AgentSlotName.WHATSAPP_ACTION) {
            pendingIntent = parsedIntent
            currentState = AgentState.WAITING_WHATSAPP_ACTION
            whatsAppGuidedRetrySpoken = false
            return AgentOutcome(
                spokenText = WHATSAPP_GUIDED_QUESTION,
                targetState = AgentState.WAITING_WHATSAPP_ACTION,
                missingSlot = AgentSlotName.WHATSAPP_ACTION
            )
        }

        clear()
        return AgentOutcome(
            spokenText = "",
            targetState = AgentState.PROCESSING,
            suggestedIntent = parsedIntent
        )
    }

    private fun handleCompose(parsedIntent: ParsedAgentIntent): AgentOutcome {
        val missingSlot = parsedIntent.missingSlots.firstOrNull()
        if (missingSlot != null) {
            pendingIntent = parsedIntent
            currentState = when (missingSlot) {
                AgentSlotName.CONTACT_NAME -> AgentState.WAITING_CONTACT
                AgentSlotName.MESSAGE_TEXT -> AgentState.WAITING_MESSAGE
                else -> AgentState.ERROR_RECOVERABLE
            }
            return AgentOutcome(
                spokenText = questionForMissingSlot(missingSlot),
                targetState = currentState,
                missingSlot = missingSlot
            )
        }

        val message = parsedIntent.slotValue(AgentSlotName.MESSAGE_TEXT).orEmpty()
        if (message.isNotBlank() && !PrivacyGuard.isSafeMessagePayload(message)) {
            clear()
            return recoverableError(
                text = "No puedo preparar ese mensaje porque parece contener datos sensibles.",
                safetyNotice = "Mensaje sensible bloqueado antes de confirmar."
            )
        }

        pendingIntent = parsedIntent
        currentState = AgentState.WAITING_CONFIRMATION
        return AgentOutcome(
            spokenText = buildComposeConfirmation(parsedIntent),
            targetState = AgentState.WAITING_CONFIRMATION,
            needsConfirmation = true,
            suggestedIntent = parsedIntent
        )
    }

    private fun handleOpenWhatsAppChat(parsedIntent: ParsedAgentIntent): AgentOutcome {
        if (parsedIntent.missingSlots.firstOrNull() == AgentSlotName.CONTACT_NAME) {
            pendingIntent = parsedIntent
            currentState = AgentState.WAITING_CONTACT
            return AgentOutcome(
                spokenText = "¿Qué chat querés abrir?",
                targetState = AgentState.WAITING_CONTACT,
                missingSlot = AgentSlotName.CONTACT_NAME
            )
        }

        clear()
        return AgentOutcome(
            spokenText = "",
            targetState = AgentState.PROCESSING,
            suggestedIntent = parsedIntent
        )
    }

    private fun handleCall(parsedIntent: ParsedAgentIntent): AgentOutcome {
        if (parsedIntent.missingSlots.firstOrNull() == AgentSlotName.CONTACT_NAME) {
            pendingIntent = parsedIntent
            currentState = AgentState.WAITING_CONTACT
            return AgentOutcome(
                spokenText = "¿A quién querés llamar?",
                targetState = AgentState.WAITING_CONTACT,
                missingSlot = AgentSlotName.CONTACT_NAME
            )
        }

        clear()
        return AgentOutcome(
            spokenText = "",
            targetState = AgentState.PROCESSING,
            suggestedIntent = parsedIntent
        )
    }

    private fun handleSaveContact(parsedIntent: ParsedAgentIntent): AgentOutcome {
        val missingSlot = parsedIntent.missingSlots.firstOrNull()
        if (missingSlot != null) {
            pendingIntent = parsedIntent
            currentState = when (missingSlot) {
                AgentSlotName.CONTACT_NAME -> AgentState.WAITING_CONTACT
                AgentSlotName.PHONE_NUMBER -> AgentState.WAITING_PHONE_NUMBER
                else -> AgentState.ERROR_RECOVERABLE
            }
            return AgentOutcome(
                spokenText = questionForContactMissingSlot(parsedIntent, missingSlot),
                targetState = currentState,
                missingSlot = missingSlot
            )
        }

        pendingIntent = parsedIntent
        currentState = AgentState.WAITING_CONFIRMATION
        return AgentOutcome(
            spokenText = buildContactConfirmation(parsedIntent),
            targetState = AgentState.WAITING_CONFIRMATION,
            needsConfirmation = true,
            suggestedIntent = parsedIntent
        )
    }

    private fun handleDeleteContact(parsedIntent: ParsedAgentIntent): AgentOutcome {
        if (parsedIntent.missingSlots.firstOrNull() == AgentSlotName.CONTACT_NAME) {
            pendingIntent = parsedIntent
            currentState = AgentState.WAITING_CONTACT
            return AgentOutcome(
                spokenText = "¿Qué contacto querés olvidar?",
                targetState = AgentState.WAITING_CONTACT,
                missingSlot = AgentSlotName.CONTACT_NAME
            )
        }

        pendingIntent = parsedIntent
        currentState = AgentState.WAITING_CONFIRMATION
        return AgentOutcome(
            spokenText = buildContactConfirmation(parsedIntent),
            targetState = AgentState.WAITING_CONFIRMATION,
            needsConfirmation = true,
            suggestedIntent = parsedIntent
        )
    }

    private fun handleNavigation(parsedIntent: ParsedAgentIntent): AgentOutcome {
        if (parsedIntent.missingSlots.firstOrNull() == AgentSlotName.DESTINATION) {
            pendingIntent = parsedIntent
            currentState = AgentState.WAITING_DESTINATION
            return AgentOutcome(
                spokenText = "¿A dónde querés ir?",
                targetState = AgentState.WAITING_DESTINATION,
                missingSlot = AgentSlotName.DESTINATION
            )
        }

        clear()
        return AgentOutcome(
            spokenText = "",
            targetState = AgentState.PROCESSING,
            suggestedIntent = parsedIntent
        )
    }

    private fun handleSaveLocationAlias(parsedIntent: ParsedAgentIntent): AgentOutcome {
        if (parsedIntent.missingSlots.firstOrNull() == AgentSlotName.LOCATION_ALIAS) {
            pendingIntent = parsedIntent
            currentState = AgentState.WAITING_LOCATION_ALIAS
            return AgentOutcome(
                spokenText = "¿Con qué nombre querés guardar esta ubicación?",
                targetState = AgentState.WAITING_LOCATION_ALIAS,
                missingSlot = AgentSlotName.LOCATION_ALIAS
            )
        }

        clear()
        return AgentOutcome(
            spokenText = "",
            targetState = AgentState.PROCESSING,
            suggestedIntent = parsedIntent
        )
    }

    private fun handleDeleteLocationAlias(parsedIntent: ParsedAgentIntent): AgentOutcome {
        if (parsedIntent.missingSlots.firstOrNull() == AgentSlotName.LOCATION_ALIAS) {
            pendingIntent = parsedIntent
            currentState = AgentState.WAITING_LOCATION_ALIAS
            return AgentOutcome(
                spokenText = "¿Qué ubicación querés olvidar?",
                targetState = AgentState.WAITING_LOCATION_ALIAS,
                missingSlot = AgentSlotName.LOCATION_ALIAS
            )
        }

        clear()
        return AgentOutcome(
            spokenText = "",
            targetState = AgentState.PROCESSING,
            suggestedIntent = parsedIntent
        )
    }

    private fun fillPendingSlot(rawSlotText: String): AgentOutcome {
        val pending = pendingIntent ?: return recoverableError("No hay ninguna acción pendiente.")
        val cleanValue = when (currentState) {
            AgentState.WAITING_WHATSAPP_ACTION,
            AgentState.WAITING_WHATSAPP_CHAT_OR_MESSAGE,
            AgentState.WAITING_CONTACT -> VoicePhraseNormalizer.normalizeForContactExtraction(rawSlotText)
            else -> rawSlotText.trim()
        }
        if (cleanValue.isBlank()) {
            return recoverableError("No escuché un comando claro.")
        }

        return when (currentState) {
            AgentState.WAITING_CONTACT -> {
                val updated = pending.withSlot(
                    AgentSlot(
                        name = AgentSlotName.CONTACT_NAME,
                        value = cleanValue,
                        confidence = 0.7f
                    )
                )
                if (pending.intent == AgentIntent.CALL_CONTACT) {
                    clear()
                    return AgentOutcome(
                        spokenText = "",
                        targetState = AgentState.PROCESSING,
                        suggestedIntent = updated.copy(
                            missingSlots = emptyList(),
                            requiresConfirmation = true
                        )
                    )
                }

                if (pending.intent == AgentIntent.OPEN_WHATSAPP_CHAT) {
                    clear()
                    return AgentOutcome(
                        spokenText = "",
                        targetState = AgentState.PROCESSING,
                        suggestedIntent = updated.copy(
                            missingSlots = emptyList(),
                            requiresConfirmation = true
                        )
                    )
                }

                if (pending.intent == AgentIntent.SAVE_CONTACT ||
                    pending.intent == AgentIntent.DELETE_CONTACT
                ) {
                    val ready = updated.copy(
                        missingSlots = emptyList(),
                        requiresConfirmation = true
                    )
                    pendingIntent = ready
                    currentState = AgentState.WAITING_CONFIRMATION
                    return AgentOutcome(
                        spokenText = buildContactConfirmation(ready),
                        targetState = AgentState.WAITING_CONFIRMATION,
                        needsConfirmation = true,
                        suggestedIntent = ready
                    )
                }

                if (pending.intent == AgentIntent.SAVE_CONTACT_PHONE) {
                    pendingIntent = updated.copy(missingSlots = listOf(AgentSlotName.PHONE_NUMBER))
                    currentState = AgentState.WAITING_PHONE_NUMBER
                    return AgentOutcome(
                        spokenText = questionForContactMissingSlot(updated, AgentSlotName.PHONE_NUMBER),
                        targetState = AgentState.WAITING_PHONE_NUMBER,
                        missingSlot = AgentSlotName.PHONE_NUMBER
                    )
                }

                pendingIntent = updated.copy(missingSlots = listOf(AgentSlotName.MESSAGE_TEXT))
                currentState = AgentState.WAITING_MESSAGE
                AgentOutcome(
                    spokenText = "¿Qué mensaje querés mandarle?",
                    targetState = AgentState.WAITING_MESSAGE,
                    missingSlot = AgentSlotName.MESSAGE_TEXT
                )
            }

            AgentState.WAITING_MESSAGE -> {
                val messageIsSensitive = !PrivacyGuard.isSafeMessagePayload(cleanValue)
                val updated = pending.withSlot(
                    AgentSlot(
                        name = AgentSlotName.MESSAGE_TEXT,
                        value = cleanValue,
                        confidence = 0.7f,
                        isSensitive = messageIsSensitive
                    )
                ).copy(
                    missingSlots = emptyList(),
                    requiresConfirmation = !messageIsSensitive
                )

                if (messageIsSensitive) {
                    clear()
                    return recoverableError(
                        text = "No puedo preparar ese mensaje porque parece contener datos sensibles.",
                        safetyNotice = "Mensaje sensible bloqueado antes de confirmar."
                    )
                }

                pendingIntent = updated
                currentState = AgentState.WAITING_CONFIRMATION
                AgentOutcome(
                    spokenText = buildComposeConfirmation(updated),
                    targetState = AgentState.WAITING_CONFIRMATION,
                    needsConfirmation = true,
                    suggestedIntent = updated
                )
            }

            AgentState.WAITING_PHONE_NUMBER -> {
                if (SafeContactMemory.normalizePhoneNumber(cleanValue) == null) {
                    return AgentOutcome(
                        spokenText = "Ese número no parece válido. Decime el número de nuevo.",
                        targetState = AgentState.WAITING_PHONE_NUMBER,
                        missingSlot = AgentSlotName.PHONE_NUMBER,
                        isError = true,
                        shouldListenAgain = true
                    )
                }

                val updated = pending.withSlot(
                    AgentSlot(
                        name = AgentSlotName.PHONE_NUMBER,
                        value = cleanValue,
                        confidence = 0.7f,
                        isSensitive = true
                    )
                ).copy(
                    missingSlots = emptyList(),
                    requiresConfirmation = true
                )

                pendingIntent = updated
                currentState = AgentState.WAITING_CONFIRMATION
                AgentOutcome(
                    spokenText = buildContactConfirmation(updated),
                    targetState = AgentState.WAITING_CONFIRMATION,
                    needsConfirmation = true,
                    suggestedIntent = updated
                )
            }

            AgentState.WAITING_DESTINATION -> {
                val updated = pending.withSlot(
                    AgentSlot(
                        name = AgentSlotName.DESTINATION,
                        value = cleanValue,
                        confidence = 0.7f,
                        isSensitive = true
                    )
                ).copy(
                    missingSlots = emptyList(),
                    requiresConfirmation = true
                )
                clear()
                AgentOutcome(
                    spokenText = "",
                    targetState = AgentState.PROCESSING,
                    suggestedIntent = updated
                )
            }

            AgentState.WAITING_LOCATION_ALIAS -> {
                val updated = pending.withSlot(
                    AgentSlot(
                        name = AgentSlotName.LOCATION_ALIAS,
                        value = cleanValue,
                        confidence = 0.7f,
                        isSensitive = true
                    )
                ).copy(
                    missingSlots = emptyList(),
                    requiresConfirmation = true
                )
                clear()
                AgentOutcome(
                    spokenText = "",
                    targetState = AgentState.PROCESSING,
                    suggestedIntent = updated
                )
            }

            AgentState.WAITING_WHATSAPP_ACTION -> {
                val nextIntent = parseWhatsAppGuidedAction(cleanValue)
                if (nextIntent != null) {
                    // Si el intent vino completo, lo entregamos al orquestador.
                    // Si vino con slots faltantes (típicamente "mensaje para X" sin texto),
                    // pedimos el slot dentro del agente para no obligar al orquestador
                    // a llenar slots conversacionales.
                    val missing = nextIntent.missingSlots.firstOrNull()
                    if (missing == null) {
                        clear()
                        return AgentOutcome(
                            spokenText = "",
                            targetState = AgentState.PROCESSING,
                            suggestedIntent = nextIntent
                        )
                    }
                    if (nextIntent.intent == AgentIntent.COMPOSE_WHATSAPP_MESSAGE &&
                        missing == AgentSlotName.MESSAGE_TEXT
                    ) {
                        val contact = nextIntent.slotValue(AgentSlotName.CONTACT_NAME).orEmpty()
                        pendingIntent = nextIntent
                        currentState = AgentState.WAITING_MESSAGE
                        return AgentOutcome(
                            spokenText = "¿Qué mensaje querés mandarle a $contact?",
                            targetState = AgentState.WAITING_MESSAGE,
                            missingSlot = AgentSlotName.MESSAGE_TEXT,
                            shouldListenAgain = true
                        )
                    }
                    if (nextIntent.intent == AgentIntent.OPEN_WHATSAPP_CHAT &&
                        missing == AgentSlotName.CONTACT_NAME
                    ) {
                        pendingIntent = nextIntent
                        currentState = AgentState.WAITING_CONTACT
                        return AgentOutcome(
                            spokenText = "¿Qué chat querés abrir?",
                            targetState = AgentState.WAITING_CONTACT,
                            missingSlot = AgentSlotName.CONTACT_NAME,
                            shouldListenAgain = true
                        )
                    }
                    // Cualquier otro caso con slots faltantes: lo pasamos como sugerencia
                    // y dejamos que el flujo legacy lo resuelva.
                    clear()
                    return AgentOutcome(
                        spokenText = "",
                        targetState = AgentState.PROCESSING,
                        suggestedIntent = nextIntent
                    )
                }

                val ambiguousContact = extractAmbiguousContactFromGuided(cleanValue)
                if (ambiguousContact != null) {
                    pendingIntent = ParsedAgentIntent(
                        intent = AgentIntent.OPEN_WHATSAPP,
                        slots = listOf(
                            AgentSlot(AgentSlotName.CONTACT_NAME, ambiguousContact, confidence = 0.78f),
                            AgentSlot(AgentSlotName.RAW_COMMAND, cleanValue, confidence = 0.78f)
                        ),
                        rawText = cleanValue,
                        confidence = 0.78f
                    )
                    currentState = AgentState.WAITING_WHATSAPP_CHAT_OR_MESSAGE
                    return AgentOutcome(
                        spokenText = "¿Abrir chat con $ambiguousContact o mandarle un mensaje?",
                        targetState = AgentState.WAITING_WHATSAPP_CHAT_OR_MESSAGE,
                        missingSlot = AgentSlotName.WHATSAPP_ACTION,
                        shouldListenAgain = true
                    )
                }

                currentState = AgentState.WAITING_WHATSAPP_ACTION
                val retryText = if (whatsAppGuidedRetrySpoken) {
                    ""
                } else {
                    whatsAppGuidedRetrySpoken = true
                    WHATSAPP_GUIDED_RETRY
                }
                AgentOutcome(
                    spokenText = retryText,
                    targetState = AgentState.WAITING_WHATSAPP_ACTION,
                    missingSlot = AgentSlotName.WHATSAPP_ACTION,
                    isError = retryText.isNotBlank(),
                    shouldListenAgain = true
                )
            }

            AgentState.WAITING_WHATSAPP_CHAT_OR_MESSAGE -> {
                val storedContact = pending.slotValue(AgentSlotName.CONTACT_NAME).orEmpty()
                val normalizedAction = normalize(cleanValue)
                when {
                    storedContact.isBlank() -> {
                        clear()
                        return recoverableError(WHATSAPP_GUIDED_RETRY)
                    }
                    normalizedAction in chatAffirmativePhrases -> {
                        clear()
                        return AgentOutcome(
                            spokenText = "",
                            targetState = AgentState.PROCESSING,
                            suggestedIntent = ParsedAgentIntent(
                                intent = AgentIntent.OPEN_WHATSAPP_CHAT,
                                slots = listOf(
                                    AgentSlot(AgentSlotName.CONTACT_NAME, storedContact, confidence = 0.86f),
                                    AgentSlot(AgentSlotName.RAW_COMMAND, "chat de $storedContact", confidence = 0.86f)
                                ),
                                rawText = "chat de $storedContact",
                                confidence = 0.86f,
                                requiresConfirmation = true
                            )
                        )
                    }
                    normalizedAction in messageAffirmativePhrases -> {
                        val composeIntent = ParsedAgentIntent(
                            intent = AgentIntent.COMPOSE_WHATSAPP_MESSAGE,
                            slots = listOf(
                                AgentSlot(AgentSlotName.CONTACT_NAME, storedContact, confidence = 0.86f),
                                AgentSlot(AgentSlotName.RAW_COMMAND, "mandale a $storedContact", confidence = 0.86f)
                            ),
                            rawText = "mandale a $storedContact",
                            confidence = 0.86f,
                            missingSlots = listOf(AgentSlotName.MESSAGE_TEXT)
                        )
                        pendingIntent = composeIntent
                        currentState = AgentState.WAITING_MESSAGE
                        return AgentOutcome(
                            spokenText = "¿Qué mensaje querés mandarle a $storedContact?",
                            targetState = AgentState.WAITING_MESSAGE,
                            missingSlot = AgentSlotName.MESSAGE_TEXT,
                            shouldListenAgain = true
                        )
                    }
                    else -> {
                        // El usuario dijo otra cosa. Si es una intención completa de WhatsApp
                        // (ej. "buscá el chat de Marco"), la dejamos pasar.
                        val nextIntent = parseWhatsAppGuidedAction(cleanValue)
                        if (nextIntent != null) {
                            clear()
                            return AgentOutcome(
                                spokenText = "",
                                targetState = AgentState.PROCESSING,
                                suggestedIntent = nextIntent
                            )
                        }
                        currentState = AgentState.WAITING_WHATSAPP_CHAT_OR_MESSAGE
                        AgentOutcome(
                            spokenText = "Decime: chat o mensaje.",
                            targetState = AgentState.WAITING_WHATSAPP_CHAT_OR_MESSAGE,
                            missingSlot = AgentSlotName.WHATSAPP_ACTION,
                            isError = true,
                            shouldListenAgain = true
                        )
                    }
                }
            }

            else -> recoverableError("No hay ninguna acción pendiente.")
        }
    }

    private fun cancel(): AgentOutcome {
        val hadPending = pendingIntent != null
        clear()
        return AgentOutcome(
            spokenText = if (hadPending) "Acción cancelada." else "No hay ninguna acción pendiente.",
            targetState = AgentState.IDLE
        )
    }

    private fun confirm(): AgentOutcome {
        val pending = pendingIntent
        if (pending == null || currentState != AgentState.WAITING_CONFIRMATION) {
            clear()
            return recoverableError("No hay ninguna acción pendiente para confirmar.")
        }

        clear()
        return AgentOutcome(
            spokenText = "Listo.",
            targetState = AgentState.PROCESSING,
            suggestedIntent = pending
        )
    }

    private fun stopSpeaking(): AgentOutcome {
        clear()
        return AgentOutcome(
            spokenText = "",
            targetState = AgentState.STOPPED_BY_USER,
            shouldListenAgain = true
        )
    }

    private fun recoverableError(
        text: String,
        safetyNotice: String? = null
    ): AgentOutcome {
        currentState = AgentState.ERROR_RECOVERABLE
        return AgentOutcome(
            spokenText = text,
            targetState = AgentState.ERROR_RECOVERABLE,
            safetyNotice = safetyNotice,
            isError = true,
            shouldListenAgain = true
        )
    }

    private fun questionForMissingSlot(slotName: String): String = when (slotName) {
        AgentSlotName.CONTACT_NAME -> "¿A quién querés mandarle el mensaje?"
        AgentSlotName.MESSAGE_TEXT -> "¿Qué mensaje querés mandarle?"
        else -> "No entendí. Decime de nuevo."
    }

    private fun questionForContactMissingSlot(
        parsedIntent: ParsedAgentIntent,
        slotName: String
    ): String = when (slotName) {
        AgentSlotName.CONTACT_NAME ->
            if (parsedIntent.intent == AgentIntent.SAVE_CONTACT_PHONE) {
                "¿De quién querés guardar el número?"
            } else {
                "¿A quién querés guardar como contacto?"
            }
        AgentSlotName.PHONE_NUMBER -> {
            val contactName = parsedIntent.slotValue(AgentSlotName.CONTACT_NAME).orEmpty()
            if (contactName.isBlank()) {
                "¿Qué número querés guardar?"
            } else {
                "¿Qué número querés guardar para $contactName?"
            }
        }
        else -> "No entendí. Decime de nuevo."
    }

    private fun buildContactConfirmation(parsedIntent: ParsedAgentIntent): String {
        val contactName = parsedIntent.slotValue(AgentSlotName.CONTACT_NAME).orEmpty()
        return when (parsedIntent.intent) {
            AgentIntent.SAVE_CONTACT_PHONE ->
                "Voy a guardar el número de $contactName. Confirmá para continuar."
            AgentIntent.SAVE_CONTACT -> {
                val type = parsedIntent.slotValue(AgentSlotName.CONTACT_TYPE)
                val label = if (type == LocalIntentParser.CONTACT_TYPE_EMERGENCY) {
                    "contacto de emergencia"
                } else {
                    "contacto de confianza"
                }
                "Voy a guardar a $contactName como $label. Confirmá para continuar."
            }
            AgentIntent.DELETE_CONTACT ->
                "Voy a olvidar el contacto $contactName. Confirmá para continuar."
            else -> "Confirmá para continuar."
        }
    }

    private fun buildComposeConfirmation(parsedIntent: ParsedAgentIntent): String {
        val contactName = parsedIntent.slotValue(AgentSlotName.CONTACT_NAME).orEmpty()
        val messageText = parsedIntent.slotValue(AgentSlotName.MESSAGE_TEXT).orEmpty()
        return "Voy a preparar un mensaje para $contactName que dice: $messageText. " +
            "No lo envío automáticamente. Confirmá para continuar."
    }

    private fun parseWhatsAppGuidedAction(rawAction: String): ParsedAgentIntent? {
        val parsed = LocalIntentParser().parse(rawAction)
        if (parsed.intent == AgentIntent.OPEN_WHATSAPP &&
            parsed.missingSlots.contains(AgentSlotName.WHATSAPP_ACTION)
        ) {
            return null
        }
        if (parsed.intent in WHATSAPP_GUIDED_ACTION_INTENTS) {
            return parsed
        }

        // Patrones de chat directo en modo guiado: "chat Marco", "chat de Marco",
        // "el chat de Marco", "del chat de Marco", "abrilo con Marco", "con Marco".
        val chatContact = extractGuidedChatContact(rawAction)
        if (chatContact != null) {
            return ParsedAgentIntent(
                intent = AgentIntent.OPEN_WHATSAPP_CHAT,
                slots = listOf(
                    AgentSlot(AgentSlotName.CONTACT_NAME, chatContact, confidence = 0.86f),
                    AgentSlot(AgentSlotName.RAW_COMMAND, rawAction, confidence = 0.86f)
                ),
                rawText = rawAction,
                confidence = 0.86f,
                requiresConfirmation = true
            )
        }

        // "mensaje para Marco" / "mensaje a Marco" → COMPOSE con missing messageText.
        val messageContact = extractGuidedMessageContact(rawAction)
        if (messageContact != null) {
            return ParsedAgentIntent(
                intent = AgentIntent.COMPOSE_WHATSAPP_MESSAGE,
                slots = listOf(
                    AgentSlot(AgentSlotName.CONTACT_NAME, messageContact, confidence = 0.86f),
                    AgentSlot(AgentSlotName.RAW_COMMAND, rawAction, confidence = 0.86f)
                ),
                rawText = rawAction,
                confidence = 0.86f,
                missingSlots = listOf(AgentSlotName.MESSAGE_TEXT)
            )
        }

        return null
    }

    private fun extractGuidedChatContact(rawAction: String): String? {
        guidedChatContactRegexes.firstNotNullOfOrNull { regex ->
            regex.matchEntire(rawAction)?.groupValues?.getOrNull(1)
        }?.cleanGuidedValue()?.takeIf { it.isNotBlank() }?.let { return it }

        return null
    }

    private fun extractGuidedMessageContact(rawAction: String): String? {
        guidedMessageContactRegexes.firstNotNullOfOrNull { regex ->
            regex.matchEntire(rawAction)?.groupValues?.getOrNull(1)
        }?.cleanGuidedValue()?.takeIf { it.isNotBlank() }
            ?.let { return it }

        return null
    }

    /**
     * Extrae un contacto de frases ambiguas dichas en `WAITING_WHATSAPP_ACTION`
     * que NO especifican chat ni mensaje: "Marco Antonio", "con Marco", "el de Marco",
     * "abrilo con Marco", "quiero hablar con Marco". Si lo encuentra, dispara la
     * desambiguación "¿chat o mensaje?".
     *
     * Devuelve null si la frase es claramente un comando completo o no aporta
     * un contacto plausible (ej. confirmaciones, frases largas con verbos extra).
     */
    private fun extractAmbiguousContactFromGuided(rawAction: String): String? {
        ambiguousContactRegexes.firstNotNullOfOrNull { regex ->
            regex.matchEntire(rawAction)?.groupValues?.getOrNull(1)
        }?.cleanGuidedValue()?.takeIf { isPlausibleContactPhrase(it) }
            ?.let { return it }

        // Frase "pelada" tipo "Marco Antonio": sin verbos, hasta 4 palabras.
        val cleanRaw = rawAction.cleanGuidedValue()
        val normalized = normalize(cleanRaw)
        if (normalized.isBlank()) return null
        if (normalized.split(" ").size > 4) return null
        if (normalized in WHATSAPP_GUIDED_NOISE_PHRASES) return null
        if (containsForbiddenContactWord(normalized)) return null
        return cleanRaw.takeIf { isPlausibleContactPhrase(it) }
    }

    private fun isPlausibleContactPhrase(value: String): Boolean {
        val normalized = normalize(value)
        if (normalized.isBlank()) return false
        if (normalized in WHATSAPP_GUIDED_NOISE_PHRASES) return false
        if (containsForbiddenContactWord(normalized)) return false
        val words = normalized.split(" ")
        // Filtros conservadores para no tomar interjecciones tipo "uh eh" como nombres:
        //  - todas las palabras deben ser tokens "name-like" (no exclamaciones cortas).
        //  - al menos una palabra debe tener 3+ letras.
        if (words.any { it in NAME_LIKE_NOISE_TOKENS }) return false
        return words.any { it.length >= MIN_NAME_TOKEN_LENGTH }
    }

    private fun containsForbiddenContactWord(normalized: String): Boolean =
        FORBIDDEN_CONTACT_TOKENS.any { token ->
            normalized.split(" ").contains(token)
        }

    private fun normalize(text: String): String {
        val withoutAccents = Normalizer.normalize(
            text.lowercase(Locale("es", "AR")),
            Normalizer.Form.NFD
        ).replace(Regex("\\p{Mn}+"), "")

        return withoutAccents
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim('.', '!', '?', '¿', '¡')
    }

    private fun String.cleanGuidedValue(): String =
        trim()
            .trim('.', ',', ';', ':')
            .replace(Regex("\\s+"), " ")

    private fun ParsedAgentIntent.withSlot(slot: AgentSlot): ParsedAgentIntent {
        val nextSlots = slots
            .filterNot { it.name == slot.name }
            .plus(slot)

        return copy(slots = nextSlots)
    }

    companion object {
        // Frases cortas para no agotar al usuario no vidente. Probadas en QA física.
        const val WHATSAPP_GUIDED_QUESTION =
            "Decime: chat de Sofi, mensaje para Sofi, o WhatsApp principal."
        private const val WHATSAPP_GUIDED_RETRY =
            "No escuché bien. Decime: chat de Marco, mensaje para Marco, o cancelar."

        private val WHATSAPP_GUIDED_ACTION_INTENTS = setOf(
            AgentIntent.OPEN_WHATSAPP,
            AgentIntent.OPEN_WHATSAPP_CHAT,
            AgentIntent.COMPOSE_WHATSAPP_MESSAGE
        )

        private val guidedChatContactRegexes = listOf(
            // "chat Marco" / "chat de Marco" / "el chat de Marco" / "el chat con Marco"
            Regex("^\\s*(?:el\\s+)?chat\\s+(?:de\\s+|con\\s+)?(.+?)\\s*$", RegexOption.IGNORE_CASE),
            // "del chat de Marco" / "el del chat de Marco"
            Regex("^\\s*(?:el\\s+)?del\\s+chat\\s+(?:de|con)\\s+(.+?)\\s*$", RegexOption.IGNORE_CASE),
            // "abrilo con Marco" / "abrilo a Marco"
            Regex("^\\s*abrilo\\s+(?:con|a)\\s+(.+?)\\s*$", RegexOption.IGNORE_CASE),
            // "abrí el de Marco" / "abri el de Marco" / "abre el de Marco"
            Regex("^\\s*(?:abr[íi]|abre|abreme|abrir)\\s+(?:el|la)\\s+de\\s+(.+?)\\s*$", RegexOption.IGNORE_CASE),
            // "buscá Marco" / "busca Marco" / "buscar Marco" / "buscame Marco" / "buscame a Marco"
            Regex(
                "^\\s*(?:busc[áa]|buscar|b[úu]scame|buscame|encontr[áa]|encontrar|encuentra)\\s+(?:a\\s+|al\\s+)?(.+?)\\s*$",
                RegexOption.IGNORE_CASE
            ),
            // "quiero hablar con Marco" (sin "por WhatsApp") — dentro del modo guiado
            // ya sabemos que el contexto es WhatsApp, así que es chat directo.
            Regex("^\\s*quiero\\s+hablar\\s+con\\s+(.+?)\\s*$", RegexOption.IGNORE_CASE)
        )

        private val guidedMessageContactRegexes = listOf(
            // "mensaje para Marco" / "un mensaje para Marco" / "el mensaje para Marco"
            Regex(
                "^\\s*(?:un\\s+|el\\s+)?mensaje\\s+(?:para|a)\\s+(.+?)\\s*$",
                RegexOption.IGNORE_CASE
            ),
            // "mandarle un mensaje a Marco" / "mandar un mensaje a Marco"
            Regex(
                "^\\s*mandar(?:le)?\\s+(?:un\\s+)?mensaje\\s+a\\s+(.+?)\\s*$",
                RegexOption.IGNORE_CASE
            )
        )

        // Frases que con un contacto al lado indican que el usuario quiere algo de
        // WhatsApp pero no eligió chat ni mensaje. Capturan el contacto en el grupo 1.
        private val ambiguousContactRegexes = listOf(
            // "con Marco" / "con Marco Antonio"
            Regex("^\\s*con\\s+(.+?)\\s*$", RegexOption.IGNORE_CASE),
            // "el de Marco" / "la de Marco"
            Regex("^\\s*(?:el|la)\\s+de\\s+(.+?)\\s*$", RegexOption.IGNORE_CASE)
        )

        // Confirmaciones implícitas dentro de WAITING_WHATSAPP_CHAT_OR_MESSAGE.
        private val chatAffirmativePhrases = setOf(
            "chat",
            "el chat",
            "abrir chat",
            "abrir el chat",
            "abri chat",
            "abri el chat",
            "abre chat",
            "abre el chat",
            "abrilo"
        )

        private val messageAffirmativePhrases = setOf(
            "mensaje",
            "un mensaje",
            "el mensaje",
            "mandarle un mensaje",
            "mandarle mensaje",
            "mandar mensaje",
            "mandar un mensaje",
            "mandar",
            "decir",
            "mandale",
            "mandale un mensaje",
            "escribir",
            "escribirle"
        )

        // Tokens que NO pueden ser interpretados como nombre de contacto en modo guiado:
        // confirmaciones, cancelaciones, palabras genéricas. Si alguna aparece en la
        // frase, abortamos la desambiguación.
        private val FORBIDDEN_CONTACT_TOKENS = setOf(
            "si",
            "no",
            "dale",
            "confirmar",
            "confirmo",
            "aceptar",
            "cancelar",
            "cancela",
            "anular",
            "callar",
            "callate",
            "silencio",
            "ayuda",
            "chat",
            "mensaje",
            "principal",
            "solamente",
            "solo"
        )

        // Frases que vienen del propio TTS o que pueden colarse y no deben tratarse
        // como contacto. Ya normalizadas (sin tildes).
        private val WHATSAPP_GUIDED_NOISE_PHRASES = setOf(
            "abri whatsapp",
            "abrir whatsapp",
            "abre whatsapp",
            "que puedo decir",
            "ayuda"
        )

        // Sonidos de relleno y palabras de 1-2 letras que NUNCA pueden ser un contacto:
        // "uh", "eh", "ah", "mm", "ja". Si la frase contiene alguna, abortamos.
        private val NAME_LIKE_NOISE_TOKENS = setOf(
            "uh",
            "eh",
            "ah",
            "oh",
            "mm",
            "ja",
            "ay",
            "uy",
            "ey",
            "em"
        )

        // Una palabra plausible como nombre debe tener al menos 3 letras
        // ("Ana", "Sofi", "Marco"). Filtra "uh eh" / "mm".
        private const val MIN_NAME_TOKEN_LENGTH = 3
    }
}
