package com.ojoclaro.android.agent

import com.ojoclaro.android.memory.SafeContactMemory
import com.ojoclaro.android.privacy.PrivacyGuard
import com.ojoclaro.android.voice.VoicePhraseNormalizer
import java.text.Normalizer
import java.util.Locale
import kotlin.math.max

class AgentConversationManager {
    var currentState: AgentState = AgentState.IDLE
        private set

    private var pendingIntent: ParsedAgentIntent? = null
    private var whatsAppGuidedRetrySpoken = false
    private var whatsAppSession = WhatsAppAgentSession()
    private var lastSpokenResponse: String? = null

    val hasPendingSlotRequest: Boolean
        get() = currentState == AgentState.WAITING_CONTACT ||
            currentState == AgentState.WAITING_MESSAGE ||
            currentState == AgentState.WAITING_PHONE_NUMBER ||
            currentState == AgentState.WAITING_DESTINATION ||
            currentState == AgentState.WAITING_LOCATION_ALIAS ||
            currentState == AgentState.WAITING_WHATSAPP_ACTION ||
            currentState == AgentState.WAITING_WHATSAPP_CHAT_OR_MESSAGE

    fun handle(parsedIntent: ParsedAgentIntent): AgentOutcome {
        if (parsedIntent.intent == AgentIntent.REPEAT_LAST) {
            // No actualiza lastSpokenResponse: el outcome reproduce el último mensaje,
            // pero la "respuesta de referencia" sigue siendo la original.
            return repeatLast()
        }
        val outcome = when (parsedIntent.intent) {
            AgentIntent.CANCEL -> cancel()
            AgentIntent.CONFIRM -> confirm()
            AgentIntent.STOP_SPEAKING -> stopSpeaking()
            else -> handleIntent(parsedIntent)
        }
        if (outcome.spokenText.isNotBlank()) {
            lastSpokenResponse = outcome.spokenText
        }
        return outcome
    }

    fun clear() {
        clearPendingState()
        whatsAppSession = WhatsAppAgentSession()
    }

    private fun clearPendingState() {
        pendingIntent = null
        currentState = AgentState.IDLE
        whatsAppGuidedRetrySpoken = false
    }

    private fun handleIntent(parsedIntent: ParsedAgentIntent): AgentOutcome {
        val pending = pendingIntent

        if (pending != null && parsedIntent.intent != AgentIntent.UNKNOWN) {
            clearPendingState()
            return handleIntent(parsedIntent)
        }

        if (pending != null && parsedIntent.intent == AgentIntent.UNKNOWN) {
            return fillPendingSlot(parsedIntent.rawText)
        }

        if (parsedIntent.intent == AgentIntent.UNKNOWN) {
            parseWhatsAppSessionContinuation(parsedIntent.rawText)?.let { continuation ->
                return handleIntent(continuation)
            }
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
                text = "No entendí. Decime, por ejemplo, mandale a un contacto que estoy llegando."
            )
            else -> {
                clearPendingState()
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

        clearPendingState()
        return AgentOutcome(
            spokenText = "",
            targetState = AgentState.PROCESSING,
            suggestedIntent = parsedIntent
        )
    }

    private fun handleCompose(parsedIntent: ParsedAgentIntent): AgentOutcome {
        val rawMessage = parsedIntent.slotValue(AgentSlotName.MESSAGE_TEXT).orEmpty().trim()

        // Privacy gate: si el usuario ya entregó un mensaje y trae datos sensibles, cortamos.
        if (rawMessage.isNotBlank() && !PrivacyGuard.isSafeMessagePayload(rawMessage)) {
            clearPendingState()
            return recoverableError(
                text = "No puedo preparar ese mensaje porque parece contener datos sensibles.",
                safetyNotice = "Mensaje sensible bloqueado antes de confirmar."
            )
        }

        // Sólo intentamos reusar contacto de sesión cuando el usuario YA dio el mensaje.
        // Inventar borradores desde el raw text rompe el contrato de seguridad: no
        // proponemos mensajes que el usuario no pronunció.
        val sessionEnhancedIntent = if (rawMessage.isNotBlank()) {
            maybeApplyWhatsAppSessionToCompose(
                parsedIntent = parsedIntent,
                providedMessage = rawMessage
            )
        } else {
            null
        }

        if (sessionEnhancedIntent != null) {
            return handleCompose(sessionEnhancedIntent)
        }

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

        // Todos los slots presentes → enriquecer con ubicación si corresponde y confirmar.
        val readyIntent = if (hasLocationIntent(parsedIntent.rawText)) {
            val currentMessage = parsedIntent.slotValue(AgentSlotName.MESSAGE_TEXT).orEmpty()
            parsedIntent.withSlot(
                AgentSlot(
                    name = AgentSlotName.MESSAGE_TEXT,
                    value = enrichMessageWithLocationIntent(currentMessage, parsedIntent.rawText),
                    confidence = 0.78f
                )
            )
        } else {
            parsedIntent
        }

        rememberWhatsAppSessionFromIntent(readyIntent)

        pendingIntent = readyIntent
        currentState = AgentState.WAITING_CONFIRMATION
        return AgentOutcome(
            spokenText = buildComposeConfirmation(readyIntent),
            targetState = AgentState.WAITING_CONFIRMATION,
            needsConfirmation = true,
            suggestedIntent = readyIntent
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

        rememberWhatsAppSessionFromIntent(parsedIntent)
        clearPendingState()
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

        clearPendingState()
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

        clearPendingState()
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

        clearPendingState()
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

        clearPendingState()
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
                    clearPendingState()
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
                    val ready = updated.copy(
                        missingSlots = emptyList(),
                        requiresConfirmation = true
                    )
                    rememberWhatsAppSessionFromIntent(ready)
                    clearPendingState()
                    return AgentOutcome(
                        spokenText = "",
                        targetState = AgentState.PROCESSING,
                        suggestedIntent = ready
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

                val existingMessage = updated.slotValue(AgentSlotName.MESSAGE_TEXT).orEmpty().trim()

                if (pending.intent == AgentIntent.COMPOSE_WHATSAPP_MESSAGE && existingMessage.isNotBlank()) {
                    val ready = updated.copy(
                        missingSlots = emptyList(),
                        requiresConfirmation = true
                    )
                    rememberWhatsAppSessionFromIntent(ready)

                    pendingIntent = ready
                    currentState = AgentState.WAITING_CONFIRMATION
                    return AgentOutcome(
                        spokenText = buildComposeConfirmation(ready),
                        targetState = AgentState.WAITING_CONFIRMATION,
                        needsConfirmation = true,
                        suggestedIntent = ready
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
                val finalMessage = enrichMessageWithLocationIntent(cleanValue, cleanValue)
                val messageIsSensitive = !PrivacyGuard.isSafeMessagePayload(finalMessage)
                val updated = pending.withSlot(
                    AgentSlot(
                        name = AgentSlotName.MESSAGE_TEXT,
                        value = finalMessage,
                        confidence = 0.7f,
                        isSensitive = messageIsSensitive
                    )
                ).copy(
                    missingSlots = emptyList(),
                    requiresConfirmation = !messageIsSensitive
                )

                if (messageIsSensitive) {
                    clearPendingState()
                    return recoverableError(
                        text = "No puedo preparar ese mensaje porque parece contener datos sensibles.",
                        safetyNotice = "Mensaje sensible bloqueado antes de confirmar."
                    )
                }

                rememberWhatsAppSessionFromIntent(updated)
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
                clearPendingState()
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
                clearPendingState()
                AgentOutcome(
                    spokenText = "",
                    targetState = AgentState.PROCESSING,
                    suggestedIntent = updated
                )
            }

            AgentState.WAITING_WHATSAPP_ACTION -> {
                val nextIntent = parseWhatsAppGuidedAction(cleanValue)
                if (nextIntent != null) {
                    val missing = nextIntent.missingSlots.firstOrNull()
                    if (missing == null) {
                        rememberWhatsAppSessionFromIntent(nextIntent)
                        clearPendingState()
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
                        rememberWhatsAppContact(contact)
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
                    clearPendingState()
                    return AgentOutcome(
                        spokenText = "",
                        targetState = AgentState.PROCESSING,
                        suggestedIntent = nextIntent
                    )
                }

                val ambiguousContact = extractAmbiguousContactFromGuided(cleanValue)
                if (ambiguousContact != null) {
                    rememberWhatsAppContact(ambiguousContact)
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
                        clearPendingState()
                        return recoverableError(WHATSAPP_GUIDED_RETRY)
                    }
                    normalizedAction in chatAffirmativePhrases -> {
                        rememberWhatsAppContact(storedContact)
                        clearPendingState()
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
                        rememberWhatsAppContact(storedContact)
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
                        val nextIntent = parseWhatsAppGuidedAction(cleanValue)
                        if (nextIntent != null) {
                            rememberWhatsAppSessionFromIntent(nextIntent)
                            clearPendingState()
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
            clearPendingState()
            return recoverableError("No hay ninguna acción pendiente para confirmar.")
        }

        rememberWhatsAppSessionFromIntent(pending)
        clearPendingState()
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

    private fun repeatLast(): AgentOutcome {
        val previous = lastSpokenResponse?.takeIf { it.isNotBlank() }
            ?: return AgentOutcome(
                spokenText = REPEAT_LAST_FALLBACK_TEXT,
                targetState = currentState,
                shouldListenAgain = true
            )

        // Prefijo "Repito. " evita que SpeechController bloquee el TTS por dedup
        // cuando el usuario pide repetir justo después de oír la respuesta.
        return AgentOutcome(
            spokenText = "Repito. $previous",
            targetState = currentState,
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

    private fun maybeApplyWhatsAppSessionToCompose(
        parsedIntent: ParsedAgentIntent,
        providedMessage: String
    ): ParsedAgentIntent? {
        if (parsedIntent.intent != AgentIntent.COMPOSE_WHATSAPP_MESSAGE) return null
        if (!parsedIntent.missingSlots.contains(AgentSlotName.CONTACT_NAME)) return null
        if (providedMessage.isBlank()) return null

        val activeContact = whatsAppSession.contactName?.takeIf { it.isNotBlank() } ?: return null

        val score = AgentContextScore(
            contactContinuity = whatsAppSession.contactConfidence,
            messageSpecificity = estimateMessageSpecificity(providedMessage),
            locationIntent = if (hasLocationIntent(parsedIntent.rawText)) 1f else 0f,
            ambiguityRisk = estimateAmbiguityRisk(parsedIntent.rawText),
            privacyRisk = if (PrivacyGuard.isSafeMessagePayload(providedMessage)) 0f else 1f
        )

        if (!score.shouldReuseContext) return null

        val finalMessage = enrichMessageWithLocationIntent(providedMessage, parsedIntent.rawText)

        return parsedIntent
            .withSlot(
                AgentSlot(
                    name = AgentSlotName.CONTACT_NAME,
                    value = activeContact,
                    confidence = score.value
                )
            )
            .withSlot(
                AgentSlot(
                    name = AgentSlotName.MESSAGE_TEXT,
                    value = finalMessage,
                    confidence = max(0.72f, score.value)
                )
            )
            .copy(
                missingSlots = emptyList(),
                requiresConfirmation = true,
                confidence = max(parsedIntent.confidence, score.value)
            )
    }

    private fun parseWhatsAppSessionContinuation(rawText: String): ParsedAgentIntent? {
        val contact = whatsAppSession.contactName?.takeIf { it.isNotBlank() } ?: return null
        val normalized = normalize(rawText)

        if (normalized in WHATSAPP_GUIDED_NOISE_PHRASES) return null
        if (normalized in chatAffirmativePhrases) {
            return ParsedAgentIntent(
                intent = AgentIntent.OPEN_WHATSAPP_CHAT,
                slots = listOf(
                    AgentSlot(AgentSlotName.CONTACT_NAME, contact, confidence = 0.84f),
                    AgentSlot(AgentSlotName.RAW_COMMAND, rawText, confidence = 0.84f)
                ),
                rawText = rawText,
                confidence = 0.84f,
                requiresConfirmation = true
            )
        }

        val hasMessageSignal = containsMessageContinuationSignal(normalized)
        val hasLocationSignal = hasLocationIntent(rawText)
        val plainUsefulMessage = looksLikePlainUsefulMessage(normalized)

        if (!hasMessageSignal && !hasLocationSignal && !plainUsefulMessage) return null

        val message = extractMessageForActiveWhatsAppContact(rawText)
            .ifBlank { draftMessageFromRawRequest(rawText) }
            .ifBlank {
                if (hasLocationSignal) {
                    "Te paso mi ubicación actual."
                } else {
                    rawText.cleanGuidedValue()
                }
            }

        if (message.isBlank()) return null

        val finalMessage = enrichMessageWithLocationIntent(message, rawText)
        if (!PrivacyGuard.isSafeMessagePayload(finalMessage)) return null

        val score = AgentContextScore(
            contactContinuity = whatsAppSession.contactConfidence,
            messageSpecificity = estimateMessageSpecificity(finalMessage),
            locationIntent = if (hasLocationSignal) 1f else 0f,
            ambiguityRisk = estimateAmbiguityRisk(rawText),
            privacyRisk = 0f
        )

        if (!score.shouldReuseContext) return null

        return ParsedAgentIntent(
            intent = AgentIntent.COMPOSE_WHATSAPP_MESSAGE,
            slots = listOf(
                AgentSlot(AgentSlotName.CONTACT_NAME, contact, confidence = score.value),
                AgentSlot(AgentSlotName.MESSAGE_TEXT, finalMessage, confidence = max(0.74f, score.value)),
                AgentSlot(AgentSlotName.RAW_COMMAND, rawText, confidence = score.value)
            ),
            rawText = rawText,
            confidence = score.value,
            requiresConfirmation = true
        )
    }

    private fun rememberWhatsAppSessionFromIntent(parsedIntent: ParsedAgentIntent) {
        val contactName = parsedIntent.slotValue(AgentSlotName.CONTACT_NAME)
            ?.cleanGuidedValue()
            ?.takeIf { it.isNotBlank() }

        val messageText = parsedIntent.slotValue(AgentSlotName.MESSAGE_TEXT)
            ?.cleanGuidedValue()
            ?.takeIf { it.isNotBlank() }

        if (contactName != null) {
            rememberWhatsAppContact(contactName)
        }

        if (messageText != null) {
            whatsAppSession = whatsAppSession.copy(
                draftMessage = messageText,
                wantsLocation = whatsAppSession.wantsLocation || hasLocationIntent(messageText),
                lastUpdatedWeight = 1f
            )
        }
    }

    private fun rememberWhatsAppContact(contactName: String) {
        val cleanContact = contactName.cleanGuidedValue()
        if (cleanContact.isBlank()) return

        whatsAppSession = whatsAppSession.copy(
            contactName = cleanContact,
            contactConfidence = 0.86f,
            lastUpdatedWeight = 1f
        )
    }

    private fun extractMessageForActiveWhatsAppContact(rawText: String): String {
        val cleaned = rawText.cleanGuidedValue()

        messageContinuationRegexes.firstNotNullOfOrNull { regex ->
            regex.matchEntire(cleaned)?.groupValues?.getOrNull(1)
        }?.cleanGuidedValue()?.takeIf { it.isNotBlank() }?.let { return humanizeDraftMessage(it) }

        return when {
            hasLocationIntent(cleaned) -> "Te paso mi ubicación actual."
            else -> ""
        }
    }

    private fun enrichMessageWithLocationIntent(message: String, rawText: String): String {
        val cleanMessage = message.cleanGuidedValue()
        if (!hasLocationIntent(rawText) && !hasLocationIntent(cleanMessage)) return cleanMessage

        val normalized = normalize(cleanMessage)
        val base = cleanMessage
            .replace(Regex("\\b(?:mandale|mandarle|manda|mandar|enviale|enviarle|enviar|pasale|pasarle)\\s+(?:mi\\s+)?ubicaci[oó]n\\b", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\b(?:y\\s+)?(?:mandale|mandarle|manda|mandar|enviale|enviarle|enviar|pasale|pasarle)\\s+(?:mi\\s+)?ubicaci[oó]n\\b", RegexOption.IGNORE_CASE), "")
            .cleanGuidedValue()

        if ("ubicacion" in normalized || "ubicación" in cleanMessage.lowercase(Locale("es", "AR"))) {
            return cleanMessage
        }

        return if (base.isBlank()) {
            "Te paso mi ubicación actual."
        } else {
            "$base Te paso mi ubicación actual."
        }
    }

    private fun hasLocationIntent(text: String): Boolean {
        val normalized = normalize(text)
        return normalized.contains("ubicacion") ||
            normalized.contains("donde estoy") ||
            normalized.contains("mi lugar actual") ||
            normalized.contains("localizacion") ||
            normalized.contains("mandale ubicacion") ||
            normalized.contains("pasale ubicacion") ||
            normalized.contains("pasa a buscar") ||
            normalized.contains("pase a buscar") ||
            normalized.contains("me pasa a buscar") ||
            normalized.contains("me pase a buscar")
    }

    private fun containsMessageContinuationSignal(normalized: String): Boolean {
        return MESSAGE_CONTINUATION_TOKENS.any { token ->
            normalized.split(" ").contains(token) || normalized.startsWith("$token ")
        }
    }

    private fun looksLikePlainUsefulMessage(normalized: String): Boolean {
        if (normalized.isBlank()) return false
        if (normalized in WHATSAPP_GUIDED_NOISE_PHRASES) return false
        if (normalized.split(" ").size < 3) return false
        if (containsForbiddenContactWord(normalized)) return false

        return PLAIN_MESSAGE_HINTS.any { hint -> hint in normalized }
    }

    private fun estimateMessageSpecificity(message: String): Float {
        val normalized = normalize(message)
        if (normalized.isBlank()) return 0f

        val words = normalized.split(" ").filter { it.isNotBlank() }
        val lengthScore = (words.size / 10f).coerceIn(0.15f, 1f)
        val timeScore = if (Regex("\\b\\d{1,2}\\b").containsMatchIn(normalized) ||
            TIME_HINTS.any { it in normalized }
        ) {
            0.18f
        } else {
            0f
        }
        val intentScore = if (PLAIN_MESSAGE_HINTS.any { it in normalized }) 0.18f else 0f

        return (lengthScore + timeScore + intentScore).coerceIn(0f, 1f)
    }

    private fun estimateAmbiguityRisk(text: String): Float {
        val normalized = normalize(text)
        if (normalized.isBlank()) return 1f

        val words = normalized.split(" ").filter { it.isNotBlank() }
        var risk = 0.15f

        if (words.size <= 2) risk += 0.25f
        if (normalized in chatAffirmativePhrases) risk += 0.25f
        if (normalized in messageAffirmativePhrases) risk += 0.2f
        if (words.any { it in NAME_LIKE_NOISE_TOKENS }) risk += 0.2f
        if (normalized == "si" || normalized == "sí" || normalized == "dale" || normalized == "ok") risk += 0.4f

        return risk.coerceIn(0f, 1f)
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

    private fun buildDraftBeforeContactQuestion(message: String): String {
        val draft = humanizeDraftMessage(message)
        return "Te preparo este mensaje: \"$draft\". ¿A qué contacto querés prepararlo?"
    }

    private fun draftMessageFromRawRequest(rawText: String): String {
        val normalized = normalize(rawText)

        return when {
            "llego en" in normalized ||
                "llego dentro de" in normalized ||
                "llego aproximadamente" in normalized ||
                "llego tipo" in normalized -> {
                humanizeDraftMessage(
                    rawText
                        .replace(Regex("^\\s*(?:decile|decirle|mandale|mandarle|avisale|avisarle|escribile|escribirle)\\s+(?:que\\s+)?", RegexOption.IGNORE_CASE), "")
                        .cleanGuidedValue()
                )
            }

            "nos encontramos" in normalized ||
                "encontramos en" in normalized ||
                "nos vemos en" in normalized -> {
                humanizeDraftMessage(
                    rawText
                        .replace(Regex("^\\s*(?:decile|decirle|mandale|mandarle|avisale|avisarle|escribile|escribirle)\\s+(?:que\\s+)?", RegexOption.IGNORE_CASE), "")
                        .cleanGuidedValue()
                )
            }

            "llego tarde" in normalized ||
                "llegando tarde" in normalized ||
                "voy tarde" in normalized ||
                "llegar tarde" in normalized -> {
                "Estoy llegando un poco tarde, pero ya voy en camino. Te aviso cuando esté cerca."
            }

            hasLocationIntent(rawText) -> {
                val base = rawText
                    .replace("mandale ubicación", "", ignoreCase = true)
                    .replace("mandarle ubicación", "", ignoreCase = true)
                    .replace("pasale mi ubicación", "", ignoreCase = true)
                    .replace("pasarle mi ubicación", "", ignoreCase = true)
                    .replace("manda mi ubicación", "", ignoreCase = true)
                    .replace("mandá mi ubicación", "", ignoreCase = true)
                    .cleanGuidedValue()

                enrichMessageWithLocationIntent(
                    message = base.ifBlank { "Te paso mi ubicación actual." },
                    rawText = rawText
                )
            }

            "avisa" in normalized ||
                "avisar" in normalized ||
                "mensaje" in normalized ||
                "decile" in normalized ||
                "mandale" in normalized ||
                "escribile" in normalized -> {
                rawText
                    .replace("prepará un mensaje para", "", ignoreCase = true)
                    .replace("prepara un mensaje para", "", ignoreCase = true)
                    .replace("preparar un mensaje para", "", ignoreCase = true)
                    .replace("avisar que", "", ignoreCase = true)
                    .replace("decilo bien", "", ignoreCase = true)
                    .replace("pero decilo bien", "", ignoreCase = true)
                    .trim()
                    .ifBlank {
                        "Estoy llegando un poco tarde, pero ya voy en camino."
                    }
            }

            else -> ""
        }
    }

    private fun humanizeDraftMessage(message: String): String {
        val normalized = normalize(message)

        return when {
            "llego tarde" in normalized ||
                "llegando tarde" in normalized ||
                "voy tarde" in normalized ||
                "llegar tarde" in normalized -> {
                "Estoy llegando un poco tarde, pero ya voy en camino. Te aviso cuando esté cerca."
            }

            else -> message
                .replace("pero decilo bien", "", ignoreCase = true)
                .replace("decilo bien", "", ignoreCase = true)
                .replace("avisar que", "", ignoreCase = true)
                .trim()
                .ifBlank { "Estoy llegando un poco tarde, pero ya voy en camino." }
        }
    }

    private fun parseWhatsAppGuidedAction(rawAction: String): ParsedAgentIntent? {
        val parsed = LocalIntentParser().parse(rawAction)
        if (parsed.intent == AgentIntent.OPEN_WHATSAPP &&
            parsed.missingSlots.contains(AgentSlotName.WHATSAPP_ACTION)
        ) {
            return null
        }
        if (parsed.intent in WHATSAPP_GUIDED_ACTION_INTENTS) {
            rememberWhatsAppSessionFromIntent(parsed)
            return parsed
        }

        val chatContact = extractGuidedChatContact(rawAction)
        if (chatContact != null) {
            rememberWhatsAppContact(chatContact)
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

        val messageContact = extractGuidedMessageContact(rawAction)
        if (messageContact != null) {
            rememberWhatsAppContact(messageContact)
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

    private fun extractAmbiguousContactFromGuided(rawAction: String): String? {
        ambiguousContactRegexes.firstNotNullOfOrNull { regex ->
            regex.matchEntire(rawAction)?.groupValues?.getOrNull(1)
        }?.cleanGuidedValue()?.takeIf { isPlausibleContactPhrase(it) }
            ?.let { return it }

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

    private data class WhatsAppAgentSession(
        val contactName: String? = null,
        val draftMessage: String? = null,
        val wantsLocation: Boolean = false,
        val contactConfidence: Float = 0f,
        val lastUpdatedWeight: Float = 0f
    )

    private data class AgentContextScore(
        val contactContinuity: Float,
        val messageSpecificity: Float,
        val locationIntent: Float,
        val ambiguityRisk: Float,
        val privacyRisk: Float
    ) {
        val value: Float
            get() {
                val positive =
                    (0.35f * contactContinuity.coerceIn(0f, 1f)) +
                        (0.30f * messageSpecificity.coerceIn(0f, 1f)) +
                        (0.15f * locationIntent.coerceIn(0f, 1f))

                val negative =
                    (0.15f * ambiguityRisk.coerceIn(0f, 1f)) +
                        (0.05f * privacyRisk.coerceIn(0f, 1f))

                return (positive - negative).coerceIn(0f, 1f)
            }

        val shouldReuseContext: Boolean
            get() = value >= REUSE_CONTEXT_THRESHOLD && privacyRisk < 0.5f

        companion object {
            private const val REUSE_CONTEXT_THRESHOLD = 0.42f
        }
    }

    companion object {
        const val WHATSAPP_GUIDED_QUESTION =
            "Decime: chat de un contacto, mensaje para un contacto, o WhatsApp principal."
        const val REPEAT_LAST_FALLBACK_TEXT = "Todavía no dije nada para repetir."
        // Usa un ejemplo concreto ("Marco") porque para usuarios ciegos la frase plantilla
        // resulta más imitable que "un contacto". El nombre es ilustrativo, no implica que
        // exista un contacto guardado con ese alias.
        private const val WHATSAPP_GUIDED_RETRY =
            "No entendi. Estas en un flujo de WhatsApp. Podes decir: WhatsApp principal, chat de Marco, mensaje para Marco, o cancelar."

        private val WHATSAPP_GUIDED_ACTION_INTENTS = setOf(
            AgentIntent.OPEN_WHATSAPP,
            AgentIntent.OPEN_WHATSAPP_CHAT,
            AgentIntent.COMPOSE_WHATSAPP_MESSAGE
        )

        private val guidedChatContactRegexes = listOf(
            Regex("^\\s*(?:el\\s+)?chat\\s+(?:de\\s+|con\\s+)?(.+?)\\s*$", RegexOption.IGNORE_CASE),
            Regex("^\\s*(?:el\\s+)?del\\s+chat\\s+(?:de|con)\\s+(.+?)\\s*$", RegexOption.IGNORE_CASE),
            Regex("^\\s*abrilo\\s+(?:con|a)\\s+(.+?)\\s*$", RegexOption.IGNORE_CASE),
            Regex("^\\s*(?:abr[íi]|abre|abreme|abrir)\\s+(?:el|la)\\s+de\\s+(.+?)\\s*$", RegexOption.IGNORE_CASE),
            Regex(
                "^\\s*(?:busc[áa]|buscar|b[úu]scame|buscame|encontr[áa]|encontrar|encuentra)\\s+(?:a\\s+|al\\s+)?(.+?)\\s*$",
                RegexOption.IGNORE_CASE
            ),
            Regex("^\\s*quiero\\s+hablar\\s+con\\s+(.+?)\\s*$", RegexOption.IGNORE_CASE)
        )

        private val guidedMessageContactRegexes = listOf(
            Regex(
                "^\\s*(?:un\\s+|el\\s+)?mensaje\\s+(?:para|a)\\s+(.+?)\\s*$",
                RegexOption.IGNORE_CASE
            ),
            Regex(
                "^\\s*mandar(?:le)?\\s+(?:un\\s+)?mensaje\\s+a\\s+(.+?)\\s*$",
                RegexOption.IGNORE_CASE
            )
        )

        private val messageContinuationRegexes = listOf(
            Regex(
                "^\\s*(?:mandale|mandarle|manda|mandar|mandá|enviale|enviarle|enviar|escribile|escribirle|decile|decirle|avisale|avisarle)\\s+(?:que\\s+)?(.+?)\\s*$",
                RegexOption.IGNORE_CASE
            ),
            Regex(
                "^\\s*(?:mensaje|un\\s+mensaje|el\\s+mensaje)\\s+(?:que\\s+)?(.+?)\\s*$",
                RegexOption.IGNORE_CASE
            ),
            Regex(
                "^\\s*(?:ponele|poné|pone|agregale|agregarle)\\s+(?:que\\s+)?(.+?)\\s*$",
                RegexOption.IGNORE_CASE
            )
        )

        private val ambiguousContactRegexes = listOf(
            Regex("^\\s*con\\s+(.+?)\\s*$", RegexOption.IGNORE_CASE),
            Regex("^\\s*(?:el|la)\\s+de\\s+(.+?)\\s*$", RegexOption.IGNORE_CASE)
        )

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

        private val MESSAGE_CONTINUATION_TOKENS = setOf(
            "mandale",
            "mandarle",
            "manda",
            "mandar",
            "enviale",
            "enviarle",
            "enviar",
            "escribile",
            "escribirle",
            "decile",
            "decirle",
            "avisale",
            "avisarle",
            "ponele",
            "agregale"
        )

        private val PLAIN_MESSAGE_HINTS = setOf(
            "llego",
            "llegue",
            "llegando",
            "tarde",
            "minutos",
            "hora",
            "voy",
            "camino",
            "esperame",
            "espera",
            "nos vemos",
            "nos encontramos",
            "ubicacion",
            "pasame a buscar",
            "pasa a buscar",
            "pase a buscar"
        )

        private val TIME_HINTS = setOf(
            "minuto",
            "minutos",
            "hora",
            "horas",
            "rato",
            "un toque",
            "enseguida",
            "ya voy",
            "camino"
        )

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

        private val WHATSAPP_GUIDED_NOISE_PHRASES = setOf(
            "abri whatsapp",
            "abrir whatsapp",
            "abre whatsapp",
            "que puedo decir",
            "ayuda"
        )

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

        private const val MIN_NAME_TOKEN_LENGTH = 3
    }
}
