package com.ojoclaro.android.domain

import com.ojoclaro.android.ai.AiContext
import com.ojoclaro.android.ai.AiProvider
import com.ojoclaro.android.ai.AiTask
import com.ojoclaro.android.ai.FutureCloudAiProvider
import com.ojoclaro.android.ai.LocalRuleBasedAiProvider
import com.ojoclaro.android.agent.AgentIntent
import com.ojoclaro.android.agent.AgentSlotName
import com.ojoclaro.android.agent.AgentState
import com.ojoclaro.android.agent.LocalIntentParser
import com.ojoclaro.android.agent.ParsedAgentIntent
import com.ojoclaro.android.agent.toAppState
import com.ojoclaro.android.agent.toAgentState
import com.ojoclaro.android.capabilities.Capability
import com.ojoclaro.android.capabilities.CapabilityRegistry
import com.ojoclaro.android.consent.ConsentDecision
import com.ojoclaro.android.consent.ConsentManager
import com.ojoclaro.android.consent.ConsentPhrases
import com.ojoclaro.android.consent.PendingSensitiveAction
import com.ojoclaro.android.consent.SensitiveActionType
import com.ojoclaro.android.external.CommandResult
import com.ojoclaro.android.external.CommandRouter
import com.ojoclaro.android.external.ExternalActionEvent
import com.ojoclaro.android.external.ExternalCommand
import com.ojoclaro.android.external.ExternalCommandRoute
import com.ojoclaro.android.external.ExternalCommandType
import com.ojoclaro.android.external.PendingConfirmation
import com.ojoclaro.android.global.GlobalAssistantCapability
import com.ojoclaro.android.global.GlobalAssistantCapabilityGate
import com.ojoclaro.android.maps.LocationCommandPhrases
import com.ojoclaro.android.maps.LocationProvider
import com.ojoclaro.android.maps.LocationResult
import com.ojoclaro.android.maps.SafeLocationMemory
import com.ojoclaro.android.maps.StoredLocation
import com.ojoclaro.android.memory.MemoryPolicy
import com.ojoclaro.android.memory.MemoryStore
import com.ojoclaro.android.memory.MemoryType
import com.ojoclaro.android.memory.SafeContactMemory
import com.ojoclaro.android.memory.UserMemory
import com.ojoclaro.android.model.AppState
import com.ojoclaro.android.patterns.FrequentPatternTracker
import com.ojoclaro.android.phone.ContactCandidate
import com.ojoclaro.android.phone.ContactResolutionResult
import com.ojoclaro.android.phone.ContactResolver
import com.ojoclaro.android.phone.ContactSource
import com.ojoclaro.android.phone.MemoryContactResolver
import com.ojoclaro.android.phone.PhoneActionExecutor
import com.ojoclaro.android.privacy.PrivacyGuard
import com.ojoclaro.android.risk.RiskDetector
import com.ojoclaro.android.risk.RiskWarning
import com.ojoclaro.android.voice.VoicePhraseNormalizer
import java.util.UUID

/**
 * Orquestador central que decide qué hacer con cada entrada del usuario.
 *
 * Responsabilidades:
 *  - Parsea la entrada con CommandRouter (comandos externos seguros).
 *  - Si no hay comando externo, deriva a un AiTask y al AiProvider local.
 *  - Verifica con CapabilityRegistry antes de prometer una acción.
 *  - Devuelve un OrchestratorOutcome plano que la UI solo aplica.
 *
 * Lo que NO hace:
 *  - Hablar (la UI emite SpeechEvent a partir del Outcome).
 *  - Lanzar Intents (la UI escucha externalEvent).
 *  - Persistir nada.
 *  - Llamar IA cloud real (FutureCloudAiProvider responde no-configurado).
 */
class AssistantOrchestrator(
    private val capabilityRegistry: CapabilityRegistry,
    private val commandRouter: CommandRouter = CommandRouter(),
    private val localAiProvider: AiProvider = LocalRuleBasedAiProvider(),
    private val consentManager: ConsentManager = ConsentManager(),
    private val memoryStore: MemoryStore? = null,
    private val patternTracker: FrequentPatternTracker = FrequentPatternTracker(),
    private val riskDetector: RiskDetector = RiskDetector(),
    private val locationProvider: LocationProvider? = null,
    contactResolver: ContactResolver? = null,
    private val globalAssistantCapabilityProvider: () -> GlobalAssistantCapability = {
        GlobalAssistantCapabilityGate.unavailable("global_assistant_not_validated")
    },
    private val executionPolicy: AgentExecutionPolicy = AgentExecutionPolicy(),
    @Suppress("unused") private val cloudAiProvider: AiProvider = FutureCloudAiProvider()
) {
    private val localIntentParser = LocalIntentParser(commandRouter)
    private val contactResolver: ContactResolver =
        contactResolver ?: MemoryContactResolver(memoryStore)

    suspend fun process(
        rawInput: String,
        pendingConfirmation: PendingConfirmation? = null,
        pendingConsent: PendingSensitiveAction? = null,
        ocrText: String? = null,
        visibleScreenText: String? = null,
        appState: AppState = AppState.IDLE,
        nowMillis: Long = System.currentTimeMillis()
    ): OrchestratorOutcome {
        // Limpieza argentina ANTES de cualquier parser. Strippea muletillas
        // y reescribe voseo (abrime → abrí, llamame a → llamá a). Idempotente.
        // Si el normalizador devuelve blank, preserva el original — ese caso
        // (ej. utterance entera "dale") cae al fallback de noise.
        val cleanInput = VoicePhraseNormalizer.normalizeForParser(rawInput.trim())
        if (cleanInput.isBlank()) {
            return speakError("No escuché un comando claro.")
        }

        val agentIntent = localIntentParser.parse(cleanInput)
        val parsed = commandRouter.parse(cleanInput)
        val isPhoneIntent = agentIntent.intent == AgentIntent.OPEN_PHONE ||
            agentIntent.intent == AgentIntent.CALL_CONTACT
        val isContactMemoryIntent = agentIntent.intent in CONTACT_MEMORY_INTENTS
        val isMapsIntent = agentIntent.intent in MAPS_INTENTS
        val isWhatsAppChatIntent = agentIntent.intent == AgentIntent.OPEN_WHATSAPP_CHAT
        val isWhatsAppGuidedStart = agentIntent.intent == AgentIntent.OPEN_WHATSAPP &&
            agentIntent.missingSlots.contains(AgentSlotName.WHATSAPP_ACTION)
        if (parsed.type != ExternalCommandType.UNSUPPORTED ||
            isPhoneIntent ||
            isContactMemoryIntent ||
            isMapsIntent ||
            isWhatsAppChatIntent ||
            isWhatsAppGuidedStart
        ) {
            patternTracker.recordCommand(
                rawCommand = cleanInput,
                commandType = if (
                    isPhoneIntent ||
                    isContactMemoryIntent ||
                    isMapsIntent ||
                    isWhatsAppChatIntent ||
                    isWhatsAppGuidedStart
                ) {
                    agentIntent.intent.name
                } else {
                    parsed.type.name
                },
                appPackage = null
            )
        }

        // Si hay un consent pending y el usuario confirma o cancela, esto pisa cualquier
        // otro pending. El consent es una acción sensible: no se puede ignorar a la mitad.
        var abandonedConsent = false
        if (pendingConsent != null) {
            when (parsed.type) {
                ExternalCommandType.CONFIRM_PENDING_ACTION ->
                    return handleConsentConfirm(pendingConsent, nowMillis, visibleScreenText)
                ExternalCommandType.CANCEL_PENDING_ACTION ->
                    return handleConsentCancel(pendingConsent)
                else -> {
                    // Cualquier otro comando del usuario implica que abandonó el consent.
                    // No ejecutamos la acción sensible y limpiamos el pending para no
                    // dejarlo colgado. Después seguimos procesando el nuevo comando.
                    abandonedConsent = true
                }
            }
        }

        val baseOutcome = if (isPhoneIntent) {
            handlePhoneIntent(agentIntent, nowMillis)
        } else if (isMapsIntent) {
            handleMapsIntent(agentIntent, nowMillis)
        } else if (isContactMemoryIntent) {
            handleSafeContactIntent(agentIntent, nowMillis)
        } else if (isWhatsAppGuidedStart) {
            handleWhatsAppGuidedStart(agentIntent, appState)
        } else if (isWhatsAppChatIntent) {
            handleOpenWhatsAppChatIntent(agentIntent, nowMillis)
        } else if (parsed.type != ExternalCommandType.UNSUPPORTED) {
            val route = commandRouter.route(
                rawInput = cleanInput,
                pendingConfirmation = pendingConfirmation,
                nowMillis = nowMillis
            )
            handleExternalRoute(route, visibleScreenText, nowMillis)
        } else {
            handleAiTask(
                cleanInput = cleanInput,
                ocrText = ocrText,
                visibleScreenText = visibleScreenText,
                appState = appState
            )
        }

        return if (abandonedConsent && !baseOutcome.clearsPendingConsent) {
            baseOutcome.copy(clearsPendingConsent = true)
        } else {
            baseOutcome
        }
    }

    private fun handleConsentConfirm(
        pending: PendingSensitiveAction,
        nowMillis: Long,
        visibleScreenText: String?
    ): OrchestratorOutcome {
        return when (val decision = consentManager.confirmSimple(pending, nowMillis)) {
            is ConsentDecision.Confirmed ->
                executeConsentAction(decision.pending, nowMillis, visibleScreenText)

            is ConsentDecision.Expired -> OrchestratorOutcome(
                spokenText = decision.spokenText,
                targetState = AppState.ERROR,
                clearsPendingConsent = true,
                isError = true,
                forceSpeak = true
            )

            is ConsentDecision.NoPending -> OrchestratorOutcome(
                spokenText = decision.spokenText,
                targetState = AppState.SPEAKING,
                forceSpeak = true
            )

            is ConsentDecision.Rejected -> OrchestratorOutcome(
                spokenText = decision.spokenText,
                targetState = AppState.ERROR,
                clearsPendingConsent = true,
                isError = true,
                forceSpeak = true
            )

            // Estos no deberían ocurrir desde confirmSimple, pero los manejamos defensivamente.
            is ConsentDecision.AllowedImmediately,
            is ConsentDecision.NeedsConfirmation,
            is ConsentDecision.Cancelled -> speakError("No pude procesar la confirmación.")
        }
    }

    private fun handleConsentCancel(
        pending: PendingSensitiveAction
    ): OrchestratorOutcome {
        consentManager.cancel(pending)
        val spokenText = when (pending.type) {
            SensitiveActionType.SAVE_MEMORY -> ConsentPhrases.MEMORY_SAVE_CANCELLED
            SensitiveActionType.CLEAR_MEMORY -> ConsentPhrases.MEMORY_CLEAR_CANCELLED
            SensitiveActionType.DELETE_MEMORY -> ConsentPhrases.MEMORY_DELETE_CANCELLED
            else -> ConsentPhrases.ACTION_CANCELLED
        }
        return OrchestratorOutcome(
            spokenText = spokenText,
            targetState = AppState.IDLE,
            clearsPendingConsent = true,
            forceSpeak = true
        )
    }

    private fun executeConsentAction(
        pending: PendingSensitiveAction,
        nowMillis: Long,
        visibleScreenText: String?
    ): OrchestratorOutcome {
        return when (pending.type) {
            SensitiveActionType.READ_VISIBLE_MESSAGE -> {
                if (!visibleScreenText.isNullOrBlank()) {
                    return riskAwareVisibleScreenOutcome(
                        visibleText = visibleScreenText,
                        clearsPendingConsent = true
                    )
                }

                // Re-verificamos accesibilidad: el usuario pudo haber desactivado el servicio
                // en el medio. Mejor guiar de nuevo que leer una pantalla mal.
                val accessibility = capabilityRegistry.status(Capability.ACCESSIBILITY_SERVICE)
                if (!accessibility.isAvailable) {
                    OrchestratorOutcome(
                        spokenText = accessibility.userMessageWhenMissing,
                        targetState = AppState.PERMISSION_REQUIRED,
                        clearsPendingConsent = true,
                        isError = true,
                        forceSpeak = true
                    )
                } else {
                    OrchestratorOutcome(
                        spokenText = "Leyendo pantalla.",
                        targetState = AppState.PROCESSING,
                        externalEvent = ExternalActionEvent.ReadVisibleScreen,
                        clearsPendingConsent = true,
                        forceSpeak = true
                    )
                }
            }
            SensitiveActionType.SAVE_MEMORY -> executeSaveMemory(pending, nowMillis)
            SensitiveActionType.DELETE_MEMORY -> executeDeleteMemory(pending)
            SensitiveActionType.CLEAR_MEMORY -> executeClearMemory()
            else -> speakError("Esa acción todavía no está soportada.")
        }
    }

    private fun handleExternalRoute(
        route: ExternalCommandRoute,
        visibleScreenText: String?,
        nowMillis: Long
    ): OrchestratorOutcome {
        return when (val result = route.result) {
            is CommandResult.NeedsConfirmation -> {
                // Para COMPOSE, verificar WhatsApp antes de pedir confirmación al usuario.
                // Evita que el usuario confirme y luego reciba "WhatsApp no instalado".
                if (route.command.type == ExternalCommandType.COMPOSE_WHATSAPP_MESSAGE) {
                    val whatsapp = capabilityRegistry.status(Capability.WHATSAPP)
                    if (!whatsapp.isAvailable) {
                        return OrchestratorOutcome(
                            spokenText = whatsapp.userMessageWhenMissing,
                            targetState = AppState.ERROR,
                            isError = true,
                            forceSpeak = true
                        )
                    }
                }
                OrchestratorOutcome(
                    spokenText = result.spokenText,
                    targetState = AppState.WAITING_CONFIRMATION,
                    newPending = route.pendingConfirmation,
                    clearsPending = route.clearsPending,
                    forceSpeak = true
                )
            }

            is CommandResult.NotSupported -> OrchestratorOutcome(
                spokenText = result.spokenText,
                targetState = AppState.ERROR,
                isError = true,
                forceSpeak = true
            )

            is CommandResult.Failed -> OrchestratorOutcome(
                spokenText = result.spokenText,
                targetState = AppState.ERROR,
                clearsPending = route.clearsPending,
                isError = true,
                forceSpeak = true
            )

            is CommandResult.Success -> handleExternalSuccess(route, result, visibleScreenText, nowMillis)
        }
    }

    private fun handleWhatsAppGuidedStart(
        parsedIntent: ParsedAgentIntent,
        appState: AppState
    ): OrchestratorOutcome {
        val whatsapp = capabilityRegistry.status(Capability.WHATSAPP)
        if (!whatsapp.isAvailable) {
            return OrchestratorOutcome(
                spokenText = whatsapp.userMessageWhenMissing,
                targetState = AppState.ERROR,
                isError = true,
                forceSpeak = true
            )
        }

        return executionPolicy.decideWhatsAppGuidedStart(
            AgentExecutionInput(
                originalText = parsedIntent.rawText,
                normalizedText = parsedIntent.rawText,
                detectedIntent = parsedIntent.intent,
                currentState = appState.toAgentState(),
                hasPendingAction = false,
                externalContinuation = globalAssistantCapabilityProvider()
            )
        ).toOutcome()
    }

    private fun handleOpenWhatsAppChatIntent(
        parsedIntent: ParsedAgentIntent,
        nowMillis: Long
    ): OrchestratorOutcome {
        val whatsapp = capabilityRegistry.status(Capability.WHATSAPP)
        if (!whatsapp.isAvailable) {
            return OrchestratorOutcome(
                spokenText = whatsapp.userMessageWhenMissing,
                targetState = AppState.ERROR,
                isError = true,
                forceSpeak = true
            )
        }

        val contactName = parsedIntent.slotValue(AgentSlotName.CONTACT_NAME).orEmpty()
        if (contactName.isBlank()) {
            return OrchestratorOutcome(
                spokenText = "¿Qué chat querés abrir?",
                targetState = AppState.WAITING_CONFIRMATION,
                forceSpeak = true
            )
        }

        return when (val resolution = contactResolver.resolve(contactName)) {
            is ContactResolutionResult.Resolved ->
                buildOpenChatConfirmation(parsedIntent, resolution.candidate, nowMillis)

            is ContactResolutionResult.MultipleMatches -> OrchestratorOutcome(
                spokenText = "Tengo varios contactos con ese nombre. " +
                    "Decime el nombre completo o el número.",
                targetState = AppState.WAITING_CONFIRMATION,
                forceSpeak = true
            )

            ContactResolutionResult.NotFound -> OrchestratorOutcome(
                spokenText = "No tengo un número guardado para $contactName. " +
                    "Podés decir: el número de $contactName es...",
                targetState = AppState.SPEAKING,
                forceSpeak = true
            )

            ContactResolutionResult.NeedsContactsPermission -> OrchestratorOutcome(
                spokenText = "Para abrir chats por nombre necesito acceso a tus contactos. " +
                    "Por ahora, decime el número de $contactName.",
                targetState = AppState.SPEAKING,
                forceSpeak = true
            )
        }
    }

    private fun buildOpenChatConfirmation(
        parsedIntent: ParsedAgentIntent,
        candidate: ContactCandidate,
        nowMillis: Long
    ): OrchestratorOutcome {
        val spokenText = "Voy a abrir el chat de WhatsApp con ${candidate.displayName}. " +
            "No voy a enviar ningún mensaje. Confirmá para continuar."
        val pending = PendingConfirmation(
            id = "whatsapp-chat-confirmation-$nowMillis",
            command = ExternalCommand(
                type = ExternalCommandType.OPEN_WHATSAPP_CHAT,
                rawText = parsedIntent.rawText,
                targetName = candidate.displayName,
                payloadText = candidate.phoneE164
            ),
            spokenText = spokenText,
            createdAtMillis = nowMillis
        )
        return OrchestratorOutcome(
            spokenText = spokenText,
            targetState = AppState.WAITING_CONFIRMATION,
            newPending = pending,
            forceSpeak = true
        )
    }

    private fun handlePhoneIntent(
        parsedIntent: ParsedAgentIntent,
        nowMillis: Long
    ): OrchestratorOutcome {
        if (parsedIntent.intent == AgentIntent.OPEN_PHONE) {
            return executionPolicy.decidePrincipalAppOpen(
                externalAppName = "Teléfono",
                spokenText = PHONE_HANDOFF_TEXT,
                delegate = ExternalActionEvent.OpenPhone,
                capability = globalAssistantCapabilityProvider()
            ).toOutcome()
        }

        return when (parsedIntent.intent) {
            AgentIntent.OPEN_PHONE -> OrchestratorOutcome(
                spokenText = PHONE_HANDOFF_TEXT,
                targetState = AppState.EXTERNAL_APP_HANDOFF,
                externalEvent = handoffEvent(
                    externalAppName = "Teléfono",
                    spokenText = PHONE_HANDOFF_TEXT,
                    delegate = ExternalActionEvent.OpenPhone
                ),
                forceSpeak = true
            )

            AgentIntent.CALL_CONTACT -> {
                val contactName = parsedIntent.slotValue(AgentSlotName.CONTACT_NAME).orEmpty()
                if (contactName.isBlank()) {
                    return OrchestratorOutcome(
                        spokenText = "¿A quién querés llamar?",
                        targetState = AppState.WAITING_CONFIRMATION,
                        isError = false,
                        forceSpeak = true
                    )
                }

                when (val resolution = contactResolver.resolve(contactName)) {
                    is ContactResolutionResult.Resolved ->
                        buildCallConfirmation(parsedIntent, resolution.candidate, nowMillis)

                    is ContactResolutionResult.MultipleMatches -> OrchestratorOutcome(
                        spokenText = "Tengo varios contactos con ese nombre. " +
                            "Decime el nombre completo o el número.",
                        targetState = AppState.WAITING_CONFIRMATION,
                        forceSpeak = true
                    )

                    ContactResolutionResult.NotFound -> OrchestratorOutcome(
                        spokenText = "No tengo un número guardado para $contactName. " +
                            "Podés decir: el número de $contactName es...",
                        targetState = AppState.SPEAKING,
                        forceSpeak = true
                    )

                    ContactResolutionResult.NeedsContactsPermission -> OrchestratorOutcome(
                        spokenText = "Para llamar por nombre necesito acceso a tus contactos. " +
                            "Por ahora, decime el número de $contactName.",
                        targetState = AppState.SPEAKING,
                        forceSpeak = true
                    )
                }
            }

            else -> speakError("Esa acción todavía no está soportada.")
        }
    }

    private fun handleMapsIntent(
        parsedIntent: ParsedAgentIntent,
        nowMillis: Long
    ): OrchestratorOutcome {
        if (parsedIntent.intent == AgentIntent.OPEN_MAPS) {
            val decision = if (isPrincipalAppOpen(parsedIntent.rawText)) {
                executionPolicy.decidePrincipalAppOpen(
                    externalAppName = "Maps",
                    spokenText = MAPS_HANDOFF_TEXT,
                    delegate = ExternalActionEvent.OpenMaps,
                    capability = globalAssistantCapabilityProvider()
                )
            } else {
                executionPolicy.decideNonPrincipalAppOpen(
                    promptText = "Decime un destino o mapas principal.",
                    waitingState = AgentState.WAITING_DESTINATION,
                    capability = globalAssistantCapabilityProvider(),
                    externalAppName = "Maps",
                    spokenText = MAPS_HANDOFF_TEXT,
                    delegate = ExternalActionEvent.OpenMaps
                )
            }
            return decision.toOutcome(safetyNotice = LocationCommandPhrases.STREET_SAFETY_NOTICE)
        }

        return when (parsedIntent.intent) {
            AgentIntent.OPEN_MAPS -> OrchestratorOutcome(
                spokenText = MAPS_HANDOFF_TEXT,
                targetState = AppState.EXTERNAL_APP_HANDOFF,
                externalEvent = handoffEvent(
                    externalAppName = "Maps",
                    spokenText = MAPS_HANDOFF_TEXT,
                    delegate = ExternalActionEvent.OpenMaps
                ),
                forceSpeak = true,
                safetyNotice = LocationCommandPhrases.STREET_SAFETY_NOTICE
            )

            AgentIntent.GET_CURRENT_LOCATION -> handleCurrentLocation(parsedIntent)
            AgentIntent.NAVIGATE_TO_DESTINATION -> handleNavigateToDestination(parsedIntent, nowMillis)
            AgentIntent.SAVE_LOCATION_ALIAS -> handleSaveLocationAlias(parsedIntent, nowMillis)
            AgentIntent.LIST_LOCATION_ALIASES -> handleListLocationAliases()
            AgentIntent.DELETE_LOCATION_ALIAS -> handleDeleteLocationAlias(parsedIntent, nowMillis)
            else -> speakError("Esa acción todavía no está soportada.")
        }
    }

    private fun handleCurrentLocation(parsedIntent: ParsedAgentIntent): OrchestratorOutcome {
        return when (val location = locationProvider?.getCurrentLocation() ?: LocationResult.PermissionMissing) {
            LocationResult.PermissionMissing -> OrchestratorOutcome(
                spokenText = LocationCommandPhrases.LOCATION_PERMISSION_REQUIRED,
                targetState = AppState.PERMISSION_REQUIRED,
                externalEvent = ExternalActionEvent.RequestLocationPermission,
                forceSpeak = true
            )
            LocationResult.LocationDisabled -> OrchestratorOutcome(
                spokenText = "La ubicación del teléfono parece desactivada. Puedo abrir mapas para que busques un destino.",
                targetState = AppState.ERROR,
                isError = true,
                forceSpeak = true
            )
            LocationResult.Unavailable -> OrchestratorOutcome(
                spokenText = "No pude obtener una ubicación actual confiable. Puedo abrir mapas para que busques un destino.",
                targetState = AppState.ERROR,
                isError = true,
                forceSpeak = true
            )
            is LocationResult.Success -> {
                val shouldOpenMaps = MemoryPolicy.normalize(parsedIntent.rawText).contains("mapas")
                OrchestratorOutcome(
                    spokenText = "${LocationCommandPhrases.CURRENT_LOCATION_READY} ${LocationCommandPhrases.STREET_SAFETY_NOTICE}",
                    targetState = if (shouldOpenMaps) AppState.EXTERNAL_APP_HANDOFF else AppState.SPEAKING,
                    externalEvent = if (shouldOpenMaps) {
                        handoffEvent(
                            externalAppName = "Maps",
                            spokenText = MAPS_HANDOFF_TEXT,
                            delegate = ExternalActionEvent.OpenCurrentLocation(location.latitude, location.longitude)
                        )
                    } else {
                        null
                    },
                    forceSpeak = true,
                    safetyNotice = LocationCommandPhrases.STREET_SAFETY_NOTICE
                )
            }
        }
    }

    private fun handleNavigateToDestination(
        parsedIntent: ParsedAgentIntent,
        nowMillis: Long
    ): OrchestratorOutcome {
        val destination = parsedIntent.slotValue(AgentSlotName.DESTINATION).orEmpty().cleanMemoryValue()
        if (destination.isBlank()) {
            return OrchestratorOutcome(
                spokenText = "¿A dónde querés ir?",
                targetState = AppState.WAITING_CONFIRMATION,
                forceSpeak = true
            )
        }

        val stored = findLocationAlias(destination)
        if (isLocationAliasRequest(parsedIntent, destination) && stored == null) {
            return OrchestratorOutcome(
                spokenText = LocationCommandPhrases.missingAlias(destination),
                targetState = AppState.SPEAKING,
                forceSpeak = true
            )
        }

        val command = if (stored != null) {
            ExternalCommand(
                type = ExternalCommandType.NAVIGATE_TO_COORDINATES,
                rawText = parsedIntent.rawText,
                targetName = destination,
                payloadText = "${stored.latitude},${stored.longitude}"
            )
        } else {
            ExternalCommand(
                type = ExternalCommandType.NAVIGATE_TO_DESTINATION,
                rawText = parsedIntent.rawText,
                targetName = destination
            )
        }
        val spokenText = "${LocationCommandPhrases.navigateConfirmation(destination)} ${LocationCommandPhrases.STREET_SAFETY_NOTICE}"
        return OrchestratorOutcome(
            spokenText = spokenText,
            targetState = AppState.WAITING_CONFIRMATION,
            newPending = PendingConfirmation(
                id = "maps-confirmation-$nowMillis",
                command = command,
                spokenText = spokenText,
                createdAtMillis = nowMillis
            ),
            forceSpeak = true,
            safetyNotice = LocationCommandPhrases.STREET_SAFETY_NOTICE
        )
    }

    private fun handleSaveLocationAlias(
        parsedIntent: ParsedAgentIntent,
        nowMillis: Long
    ): OrchestratorOutcome {
        memoryStore ?: return memoryUnavailable()
        val alias = parsedIntent.slotValue(AgentSlotName.LOCATION_ALIAS).orEmpty().cleanMemoryValue()
        if (alias.isBlank()) {
            return OrchestratorOutcome(
                spokenText = "¿Con qué nombre querés guardar esta ubicación?",
                targetState = AppState.WAITING_CONFIRMATION,
                forceSpeak = true
            )
        }
        if (!SafeLocationMemory.isValidAlias(alias)) {
            return OrchestratorOutcome(
                spokenText = "No puedo guardar esa ubicación con ese nombre porque parece sensible.",
                targetState = AppState.ERROR,
                isError = true,
                forceSpeak = true
            )
        }

        val location = when (val result = locationProvider?.getCurrentLocation() ?: LocationResult.PermissionMissing) {
            LocationResult.PermissionMissing -> return OrchestratorOutcome(
                spokenText = LocationCommandPhrases.LOCATION_PERMISSION_REQUIRED,
                targetState = AppState.PERMISSION_REQUIRED,
                externalEvent = ExternalActionEvent.RequestLocationPermission,
                forceSpeak = true
            )
            LocationResult.LocationDisabled -> return OrchestratorOutcome(
                spokenText = "La ubicación del teléfono parece desactivada. No guardé nada.",
                targetState = AppState.ERROR,
                isError = true,
                forceSpeak = true
            )
            LocationResult.Unavailable -> return OrchestratorOutcome(
                spokenText = "No pude obtener una ubicación actual confiable. No guardé nada.",
                targetState = AppState.ERROR,
                isError = true,
                forceSpeak = true
            )
            is LocationResult.Success -> result
        }

        val memory = UserMemory(
            id = stableLocationMemoryId(alias),
            type = MemoryType.LOCATION_ALIAS,
            label = alias,
            value = SafeLocationMemory.value(location.latitude, location.longitude, location.accuracyMeters),
            createdAtMillis = nowMillis,
            updatedAtMillis = nowMillis,
            expiresAtMillis = null,
            isSensitive = false,
            userApproved = true,
            requiresConfirmationBeforeUse = true
        )
        if (!PrivacyGuard.canStoreMemory(memory)) {
            return OrchestratorOutcome(
                spokenText = "No puedo guardar esa ubicación porque parece sensible.",
                targetState = AppState.ERROR,
                isError = true,
                forceSpeak = true
            )
        }

        return requestMemoryConsent(
            type = SensitiveActionType.SAVE_MEMORY,
            spokenExplanation = LocationCommandPhrases.saveAliasConfirmation(alias),
            payload = memory.toPayload(),
            nowMillis = nowMillis
        )
    }

    private fun handleListLocationAliases(): OrchestratorOutcome {
        val store = memoryStore ?: return memoryUnavailable()
        val aliases = store.getByType(MemoryType.LOCATION_ALIAS)
            .filter(SafeLocationMemory::isLocationAlias)
            .map { it.label }
            .distinct()
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
        val spokenText = if (aliases.isEmpty()) {
            "No tenés lugares guardados."
        } else {
            "Tus lugares guardados son: ${aliases.joinToString(", ")}."
        }
        return OrchestratorOutcome(
            spokenText = spokenText,
            targetState = AppState.SPEAKING,
            forceSpeak = true
        )
    }

    private fun handleDeleteLocationAlias(
        parsedIntent: ParsedAgentIntent,
        nowMillis: Long
    ): OrchestratorOutcome {
        val store = memoryStore ?: return memoryUnavailable()
        val alias = parsedIntent.slotValue(AgentSlotName.LOCATION_ALIAS).orEmpty().cleanMemoryValue()
        if (alias.isBlank()) {
            return OrchestratorOutcome(
                spokenText = "¿Qué ubicación querés olvidar?",
                targetState = AppState.WAITING_CONFIRMATION,
                forceSpeak = true
            )
        }

        val memory = findLocationAliasMemory(alias)
            ?: return OrchestratorOutcome(
                spokenText = "No encontré una ubicación guardada llamada $alias.",
                targetState = AppState.SPEAKING,
                forceSpeak = true
            )

        return requestMemoryConsent(
            type = SensitiveActionType.DELETE_MEMORY,
            spokenExplanation = "Voy a olvidar la ubicación $alias. Confirmá para continuar.",
            payload = mapOf(
                PAYLOAD_MEMORY_ID to memory.id,
                PAYLOAD_MEMORY_LABEL to memory.label
            ),
            nowMillis = nowMillis
        )
    }

    private fun handleSafeContactIntent(
        parsedIntent: ParsedAgentIntent,
        nowMillis: Long
    ): OrchestratorOutcome {
        return when (parsedIntent.intent) {
            AgentIntent.SAVE_CONTACT,
            AgentIntent.SAVE_CONTACT_PHONE -> handleSaveSafeContact(parsedIntent, nowMillis)
            AgentIntent.LIST_CONTACTS -> handleListSafeContacts(parsedIntent)
            AgentIntent.DELETE_CONTACT -> handleDeleteSafeContact(parsedIntent, nowMillis)
            else -> speakError("Esa acción todavía no está soportada.")
        }
    }

    private fun handleSaveSafeContact(
        parsedIntent: ParsedAgentIntent,
        nowMillis: Long
    ): OrchestratorOutcome {
        memoryStore ?: return memoryUnavailable()
        val contactName = parsedIntent.slotValue(AgentSlotName.CONTACT_NAME).orEmpty().cleanMemoryValue()
        if (contactName.isBlank()) {
            return OrchestratorOutcome(
                spokenText = "¿A quién querés guardar como contacto?",
                targetState = AppState.WAITING_CONFIRMATION,
                forceSpeak = true
            )
        }

        val isEmergency =
            parsedIntent.slotValue(AgentSlotName.CONTACT_TYPE) == LocalIntentParser.CONTACT_TYPE_EMERGENCY
        val type = if (isEmergency) MemoryType.EMERGENCY_CONTACT else MemoryType.TRUSTED_CONTACT

        val value = if (parsedIntent.intent == AgentIntent.SAVE_CONTACT_PHONE) {
            val phoneNumber = parsedIntent.slotValue(AgentSlotName.PHONE_NUMBER).orEmpty()
            if (phoneNumber.isBlank()) {
                return OrchestratorOutcome(
                    spokenText = "¿Qué número querés guardar para $contactName?",
                    targetState = AppState.WAITING_CONFIRMATION,
                    forceSpeak = true
                )
            }
            SafeContactMemory.phoneValue(phoneNumber)
                ?: return OrchestratorOutcome(
                    spokenText = "No puedo guardar ese número porque no parece válido.",
                    targetState = AppState.ERROR,
                    isError = true,
                    forceSpeak = true
                )
        } else {
            SafeContactMemory.contactValue(contactName)
        }

        val memory = UserMemory(
            id = stableContactMemoryId(type, contactName),
            type = type,
            label = contactName,
            value = value,
            createdAtMillis = nowMillis,
            updatedAtMillis = nowMillis,
            expiresAtMillis = null,
            isSensitive = false,
            userApproved = true,
            requiresConfirmationBeforeUse = parsedIntent.intent == AgentIntent.SAVE_CONTACT_PHONE
        )

        if (!PrivacyGuard.canStoreMemory(memory)) {
            return OrchestratorOutcome(
                spokenText = "No puedo guardar ese contacto porque parece tener datos sensibles.",
                targetState = AppState.ERROR,
                isError = true,
                forceSpeak = true
            )
        }

        return requestMemoryConsent(
            type = SensitiveActionType.SAVE_MEMORY,
            spokenExplanation = ConsentPhrases.saveMemory(memorySpokenSummary(memory)),
            payload = memory.toPayload(),
            nowMillis = nowMillis
        )
    }

    private fun handleListSafeContacts(parsedIntent: ParsedAgentIntent): OrchestratorOutcome {
        val store = memoryStore ?: return memoryUnavailable()
        val contactType = parsedIntent.slotValue(AgentSlotName.CONTACT_TYPE)
        val types = if (contactType == LocalIntentParser.CONTACT_TYPE_EMERGENCY) {
            setOf(MemoryType.EMERGENCY_CONTACT)
        } else {
            setOf(MemoryType.TRUSTED_CONTACT)
        }
        val contacts = types
            .flatMap(store::getByType)
            .filter { it.userApproved && !it.isSensitive && PrivacyGuard.canStoreMemory(it) }
            .map { it.label }
            .distinct()
            .sortedWith(String.CASE_INSENSITIVE_ORDER)

        val spokenText = if (contacts.isEmpty()) {
            "No tenés contactos de confianza guardados."
        } else {
            "Tus contactos de confianza son: ${contacts.joinToString(", ")}."
        }

        return OrchestratorOutcome(
            spokenText = spokenText,
            targetState = AppState.SPEAKING,
            forceSpeak = true
        )
    }

    private fun handleDeleteSafeContact(
        parsedIntent: ParsedAgentIntent,
        nowMillis: Long
    ): OrchestratorOutcome {
        val store = memoryStore ?: return memoryUnavailable()
        val contactName = parsedIntent.slotValue(AgentSlotName.CONTACT_NAME).orEmpty().cleanMemoryValue()
        if (contactName.isBlank()) {
            return OrchestratorOutcome(
                spokenText = "¿Qué contacto querés olvidar?",
                targetState = AppState.WAITING_CONFIRMATION,
                forceSpeak = true
            )
        }

        val normalizedName = MemoryPolicy.normalize(contactName)
        val memory = listOf(MemoryType.TRUSTED_CONTACT, MemoryType.EMERGENCY_CONTACT)
            .flatMap(store::getByType)
            .firstOrNull { MemoryPolicy.normalize(it.label) == normalizedName }
            ?: return OrchestratorOutcome(
                spokenText = "No encontré un contacto guardado llamado $contactName.",
                targetState = AppState.SPEAKING,
                forceSpeak = true
            )

        return requestMemoryConsent(
            type = SensitiveActionType.DELETE_MEMORY,
            spokenExplanation = ConsentPhrases.deleteMemory("el contacto ${memory.label}"),
            payload = mapOf(
                PAYLOAD_MEMORY_ID to memory.id,
                PAYLOAD_MEMORY_LABEL to memory.label
            ),
            nowMillis = nowMillis
        )
    }

    private fun buildCallConfirmation(
        parsedIntent: ParsedAgentIntent,
        candidate: ContactCandidate,
        nowMillis: Long
    ): OrchestratorOutcome {
        val isEmergency = candidate.source == ContactSource.EMERGENCY_DEFAULT
        val spokenText = if (isEmergency) {
            "${PhoneActionExecutor.RESPONSIBLE_EMERGENCY_NOTICE} " +
                "Voy a preparar el marcador con ${candidate.phoneE164}. " +
                "No voy a llamar automáticamente. " +
                "Para confirmar, decí: confirmar llamada."
        } else {
            "Voy a preparar una llamada a ${candidate.displayName}. " +
                "No voy a llamar automáticamente. " +
                "Para confirmar, decí: confirmar llamada."
        }
        val pending = PendingConfirmation(
            id = "phone-confirmation-$nowMillis",
            command = ExternalCommand(
                type = ExternalCommandType.CALL_CONTACT,
                rawText = parsedIntent.rawText,
                targetName = candidate.displayName,
                payloadText = candidate.phoneE164
            ),
            spokenText = spokenText,
            createdAtMillis = nowMillis
        )
        return OrchestratorOutcome(
            spokenText = spokenText,
            targetState = AppState.WAITING_CONFIRMATION,
            newPending = pending,
            forceSpeak = true
        )
    }

    private fun handleExternalSuccess(
        route: ExternalCommandRoute,
        result: CommandResult.Success,
        visibleScreenText: String?,
        nowMillis: Long
    ): OrchestratorOutcome {
        return when (route.command.type) {
            ExternalCommandType.OPEN_WHATSAPP -> {
                val whatsapp = capabilityRegistry.status(Capability.WHATSAPP)
                if (!whatsapp.isAvailable) {
                    OrchestratorOutcome(
                        spokenText = whatsapp.userMessageWhenMissing,
                        targetState = AppState.ERROR,
                        isError = true,
                        forceSpeak = true
                    )
                } else {
                    executionPolicy.decidePrincipalAppOpen(
                        externalAppName = "WhatsApp",
                        spokenText = WHATSAPP_PRINCIPAL_HANDOFF_TEXT,
                        delegate = ExternalActionEvent.OpenWhatsApp,
                        capability = globalAssistantCapabilityProvider()
                    ).toOutcome(clearsPending = route.clearsPending)
                }
            }

            ExternalCommandType.OPEN_WHATSAPP_CHAT -> {
                val whatsapp = capabilityRegistry.status(Capability.WHATSAPP)
                if (!whatsapp.isAvailable) {
                    return OrchestratorOutcome(
                        spokenText = whatsapp.userMessageWhenMissing,
                        targetState = AppState.ERROR,
                        clearsPending = true,
                        isError = true,
                        forceSpeak = true
                    )
                }
                val contact = route.command.targetName.orEmpty()
                val phoneE164 = route.command.payloadText.orEmpty()
                if (contact.isBlank() || phoneE164.isBlank()) {
                    return OrchestratorOutcome(
                        spokenText = "No pude preparar el chat. Volvé a pedirlo.",
                        targetState = AppState.ERROR,
                        clearsPending = true,
                        isError = true,
                        forceSpeak = true
                    )
                }
                OrchestratorOutcome(
                    spokenText = WHATSAPP_CHAT_HANDOFF_TEXT,
                    targetState = AppState.EXTERNAL_APP_HANDOFF,
                    externalEvent = handoffEvent(
                        externalAppName = "WhatsApp",
                        spokenText = WHATSAPP_CHAT_HANDOFF_TEXT,
                        delegate = ExternalActionEvent.OpenWhatsAppChat(
                            confirmationId = "confirmed",
                            contactName = contact,
                            phoneE164 = phoneE164
                        )
                    ),
                    clearsPending = route.clearsPending,
                    forceSpeak = true
                )
            }

            ExternalCommandType.OPEN_PHONE -> OrchestratorOutcome(
                spokenText = PHONE_HANDOFF_TEXT,
                targetState = AppState.EXTERNAL_APP_HANDOFF,
                externalEvent = handoffEvent(
                    externalAppName = "Teléfono",
                    spokenText = PHONE_HANDOFF_TEXT,
                    delegate = ExternalActionEvent.OpenPhone
                ),
                clearsPending = route.clearsPending,
                forceSpeak = true
            )

            ExternalCommandType.READ_VISIBLE_SCREEN -> {
                // Verificación temprana: si no hay accesibilidad, no pedimos consent —
                // primero guiamos al usuario a activar el servicio.
                val accessibility = capabilityRegistry.status(Capability.ACCESSIBILITY_SERVICE)
                if (!accessibility.isAvailable) {
                    return OrchestratorOutcome(
                        spokenText = accessibility.userMessageWhenMissing,
                        targetState = AppState.PERMISSION_REQUIRED,
                        isError = true,
                        forceSpeak = true
                    )
                }
                // Pedimos consentimiento explícito: la pantalla puede tener mensajes,
                // y leerla sin avisar es un mal default.
                val decision = consentManager.requestAction(
                    type = SensitiveActionType.READ_VISIBLE_MESSAGE,
                    spokenExplanation = ConsentPhrases.READ_VISIBLE_MESSAGE,
                    nowMillis = nowMillis
                )
                when (decision) {
                    is ConsentDecision.NeedsConfirmation -> OrchestratorOutcome(
                        spokenText = decision.spokenText,
                        targetState = AppState.WAITING_CONFIRMATION,
                        newPendingConsent = decision.pending,
                        clearsPending = route.clearsPending,
                        forceSpeak = true
                    )
                    // Para READ_VISIBLE_MESSAGE no debería ocurrir el resto, pero lo cubrimos.
                    is ConsentDecision.AllowedImmediately -> OrchestratorOutcome(
                        spokenText = "Leyendo pantalla.",
                        targetState = AppState.PROCESSING,
                        externalEvent = ExternalActionEvent.ReadVisibleScreen,
                        clearsPending = route.clearsPending,
                        forceSpeak = true
                    )
                    is ConsentDecision.Rejected -> OrchestratorOutcome(
                        spokenText = decision.spokenText,
                        targetState = AppState.ERROR,
                        isError = true,
                        forceSpeak = true
                    )
                    else -> speakError("No pude procesar la lectura de pantalla.")
                }
            }

            ExternalCommandType.COMPOSE_WHATSAPP_MESSAGE -> {
                val whatsapp = capabilityRegistry.status(Capability.WHATSAPP)
                if (!whatsapp.isAvailable) {
                    return OrchestratorOutcome(
                        spokenText = whatsapp.userMessageWhenMissing,
                        targetState = AppState.ERROR,
                        clearsPending = true,
                        isError = true,
                        forceSpeak = true
                    )
                }
                val contact = route.command.contactName.orEmpty()
                val message = route.command.messageText.orEmpty()
                if (contact.isBlank() || message.isBlank()) {
                    return OrchestratorOutcome(
                        spokenText = "No pude preparar ese mensaje. Volvé a dictarlo.",
                        targetState = AppState.ERROR,
                        clearsPending = true,
                        isError = true,
                        forceSpeak = true
                    )
                }
                OrchestratorOutcome(
                    spokenText = WHATSAPP_COMPOSE_HANDOFF_TEXT,
                    targetState = AppState.EXTERNAL_APP_HANDOFF,
                    externalEvent = handoffEvent(
                        externalAppName = "WhatsApp",
                        spokenText = WHATSAPP_COMPOSE_HANDOFF_TEXT,
                        delegate = ExternalActionEvent.ComposeWhatsAppMessage(
                            confirmationId = "confirmed",
                            contactName = contact,
                            messageText = message
                        )
                    ),
                    clearsPending = route.clearsPending,
                    forceSpeak = true
                )
            }

            ExternalCommandType.CALL_CONTACT -> {
                val contact = route.command.targetName.orEmpty()
                val phoneNumber = route.command.payloadText.orEmpty()
                if (contact.isBlank() || phoneNumber.isBlank()) {
                    return OrchestratorOutcome(
                        spokenText = "No pude preparar esa llamada. Abrí Teléfono para elegir el contacto.",
                        targetState = AppState.ERROR,
                        clearsPending = true,
                        isError = true,
                        forceSpeak = true
                    )
                }
                OrchestratorOutcome(
                    spokenText = PHONE_HANDOFF_TEXT,
                    targetState = AppState.EXTERNAL_APP_HANDOFF,
                    externalEvent = handoffEvent(
                        externalAppName = "Teléfono",
                        spokenText = PHONE_HANDOFF_TEXT,
                        delegate = ExternalActionEvent.DialPhoneNumber(
                            contactName = contact,
                            phoneNumber = phoneNumber
                        )
                    ),
                    clearsPending = route.clearsPending,
                    forceSpeak = true
                )
            }

            ExternalCommandType.NAVIGATE_TO_DESTINATION -> {
                val destination = route.command.targetName.orEmpty()
                if (destination.isBlank()) {
                    return OrchestratorOutcome(
                        spokenText = "No pude preparar esa navegación. Volvé a dictar el destino.",
                        targetState = AppState.ERROR,
                        clearsPending = true,
                        isError = true,
                        forceSpeak = true
                    )
                }
                OrchestratorOutcome(
                    spokenText = MAPS_HANDOFF_TEXT,
                    targetState = AppState.EXTERNAL_APP_HANDOFF,
                    externalEvent = handoffEvent(
                        externalAppName = "Maps",
                        spokenText = MAPS_HANDOFF_TEXT,
                        delegate = ExternalActionEvent.NavigateToDestination(destination)
                    ),
                    clearsPending = route.clearsPending,
                    forceSpeak = true,
                    safetyNotice = LocationCommandPhrases.STREET_SAFETY_NOTICE
                )
            }

            ExternalCommandType.NAVIGATE_TO_COORDINATES -> {
                val label = route.command.targetName.orEmpty()
                val coords = route.command.payloadText.orEmpty().split(',')
                val latitude = coords.getOrNull(0)?.toDoubleOrNull()
                val longitude = coords.getOrNull(1)?.toDoubleOrNull()
                if (label.isBlank() || latitude == null || longitude == null) {
                    return OrchestratorOutcome(
                        spokenText = "No pude preparar esa navegación. Volvé a dictar el destino.",
                        targetState = AppState.ERROR,
                        clearsPending = true,
                        isError = true,
                        forceSpeak = true
                    )
                }
                OrchestratorOutcome(
                    spokenText = MAPS_HANDOFF_TEXT,
                    targetState = AppState.EXTERNAL_APP_HANDOFF,
                    externalEvent = handoffEvent(
                        externalAppName = "Maps",
                        spokenText = MAPS_HANDOFF_TEXT,
                        delegate = ExternalActionEvent.NavigateToCoordinates(label, latitude, longitude)
                    ),
                    clearsPending = route.clearsPending,
                    forceSpeak = true,
                    safetyNotice = LocationCommandPhrases.STREET_SAFETY_NOTICE
                )
            }

            ExternalCommandType.REMEMBER_MEMORY ->
                handleRememberMemory(route.command.rawText, nowMillis)

            ExternalCommandType.LIST_MEMORY ->
                handleListMemory()

            ExternalCommandType.FORGET_LAST_MEMORY ->
                handleForgetLastMemory(nowMillis)

            ExternalCommandType.CLEAR_MEMORY ->
                handleClearMemory(nowMillis)

            ExternalCommandType.CONFIRM_PENDING_ACTION,
            ExternalCommandType.CANCEL_PENDING_ACTION,
            ExternalCommandType.UNSUPPORTED -> OrchestratorOutcome(
                spokenText = result.spokenText,
                targetState = AppState.SPEAKING,
                clearsPending = route.clearsPending,
                forceSpeak = true
            )
        }
    }

    private suspend fun handleAiTask(
        cleanInput: String,
        ocrText: String?,
        visibleScreenText: String?,
        appState: AppState
    ): OrchestratorOutcome {
        val task = mapToAiTask(cleanInput)
        val context = AiContext(
            rawCommand = cleanInput,
            ocrText = ocrText,
            visibleScreenText = visibleScreenText,
            appState = appState,
            allowCloud = false,
            safetyMode = true
        )

        if (task == AiTask.READ_VISIBLE_SCREEN) {
            val accessibility = capabilityRegistry.status(Capability.ACCESSIBILITY_SERVICE)
            if (!accessibility.isAvailable) {
                return OrchestratorOutcome(
                    spokenText = accessibility.userMessageWhenMissing,
                    targetState = AppState.PERMISSION_REQUIRED,
                    isError = true,
                    forceSpeak = true
                )
            }
        }

        if (task == AiTask.READ_TEXT) {
            val camera = capabilityRegistry.status(Capability.CAMERA)
            if (!camera.isAvailable && ocrText.isNullOrBlank()) {
                return OrchestratorOutcome(
                    spokenText = camera.userMessageWhenMissing,
                    targetState = AppState.PERMISSION_REQUIRED,
                    isError = true,
                    forceSpeak = true
                )
            }
        }

        if (task == AiTask.READ_TEXT && !ocrText.isNullOrBlank()) {
            val warnings = riskDetector.detectFromOcrText(ocrText)
            if (warnings.isNotEmpty()) {
                return OrchestratorOutcome(
                    spokenText = riskAwareSpokenText("El texto dice:", ocrText, warnings),
                    targetState = AppState.SPEAKING,
                    safetyNotice = RISK_GENERIC_NOTICE,
                    forceSpeak = false
                )
            }
        }

        val result = localAiProvider.process(task, context)
        val state = if (result.requiresConfirmation) AppState.WAITING_CONFIRMATION else AppState.SPEAKING

        return OrchestratorOutcome(
            spokenText = result.spokenText,
            displayText = result.displayText,
            targetState = state,
            safetyNotice = result.safetyNotice,
            forceSpeak = false
        )
    }

    private fun handleRememberMemory(rawInput: String, nowMillis: Long): OrchestratorOutcome {
        val store = memoryStore ?: return memoryUnavailable()
        val memory = buildMemoryFromCommand(rawInput, nowMillis)
            ?: return OrchestratorOutcome(
                spokenText = "No pude convertir eso en un recuerdo seguro.",
                targetState = AppState.ERROR,
                isError = true,
                forceSpeak = true
            )

        if (!PrivacyGuard.canStoreMemory(memory)) {
            return OrchestratorOutcome(
                spokenText = "No puedo guardar eso porque parece sensible.",
                targetState = AppState.ERROR,
                isError = true,
                forceSpeak = true
            )
        }

        val explanation = ConsentPhrases.saveMemory(memorySpokenSummary(memory))
        return requestMemoryConsent(
            type = SensitiveActionType.SAVE_MEMORY,
            spokenExplanation = explanation,
            payload = memory.toPayload(),
            nowMillis = nowMillis
        )
    }

    private fun handleListMemory(): OrchestratorOutcome {
        val store = memoryStore ?: return memoryUnavailable()
        val summaries = store.listAllSafeSummaries()
        val spokenText = if (summaries.isEmpty()) {
            ConsentPhrases.MEMORY_EMPTY
        } else {
            "${ConsentPhrases.MEMORY_LIST_HEADER} ${summaries.joinToString(" ")}"
        }

        return OrchestratorOutcome(
            spokenText = spokenText,
            targetState = AppState.SPEAKING,
            forceSpeak = true
        )
    }

    private fun handleForgetLastMemory(nowMillis: Long): OrchestratorOutcome {
        val store = memoryStore ?: return memoryUnavailable()
        val memory = store.findRelevant("").firstOrNull()
            ?: return OrchestratorOutcome(
                spokenText = ConsentPhrases.MEMORY_EMPTY,
                targetState = AppState.SPEAKING,
                forceSpeak = true
            )

        return requestMemoryConsent(
            type = SensitiveActionType.DELETE_MEMORY,
            spokenExplanation = ConsentPhrases.deleteMemory(memory.label),
            payload = mapOf(
                PAYLOAD_MEMORY_ID to memory.id,
                PAYLOAD_MEMORY_LABEL to memory.label
            ),
            nowMillis = nowMillis
        )
    }

    private fun handleClearMemory(nowMillis: Long): OrchestratorOutcome {
        memoryStore ?: return memoryUnavailable()
        return requestMemoryConsent(
            type = SensitiveActionType.CLEAR_MEMORY,
            spokenExplanation = ConsentPhrases.CLEAR_MEMORY_CONFIRM,
            payload = emptyMap(),
            nowMillis = nowMillis
        )
    }

    private fun requestMemoryConsent(
        type: SensitiveActionType,
        spokenExplanation: String,
        payload: Map<String, String>,
        nowMillis: Long
    ): OrchestratorOutcome {
        return when (val decision = consentManager.requestAction(type, spokenExplanation, payload, nowMillis)) {
            is ConsentDecision.NeedsConfirmation -> OrchestratorOutcome(
                spokenText = decision.spokenText,
                targetState = AppState.WAITING_CONFIRMATION,
                newPendingConsent = decision.pending,
                forceSpeak = true
            )
            is ConsentDecision.Rejected -> OrchestratorOutcome(
                spokenText = decision.spokenText,
                targetState = AppState.ERROR,
                isError = true,
                forceSpeak = true
            )
            is ConsentDecision.AllowedImmediately -> OrchestratorOutcome(
                spokenText = decision.spokenText,
                targetState = AppState.SPEAKING,
                forceSpeak = true
            )
            else -> speakError("No pude preparar la confirmación.")
        }
    }

    private fun executeSaveMemory(
        pending: PendingSensitiveAction,
        nowMillis: Long
    ): OrchestratorOutcome {
        val store = memoryStore ?: return memoryUnavailable()
        val memory = pending.payload.toMemory(nowMillis)
            ?: return speakError("No pude guardar ese recuerdo.")

        if (!PrivacyGuard.canStoreMemory(memory)) {
            return OrchestratorOutcome(
                spokenText = "No puedo guardar eso porque parece sensible.",
                targetState = AppState.ERROR,
                clearsPendingConsent = true,
                isError = true,
                forceSpeak = true
            )
        }

        store.save(memory)
        return OrchestratorOutcome(
            spokenText = ConsentPhrases.MEMORY_SAVED,
            targetState = AppState.SPEAKING,
            clearsPendingConsent = true,
            forceSpeak = true
        )
    }

    private fun executeDeleteMemory(pending: PendingSensitiveAction): OrchestratorOutcome {
        val store = memoryStore ?: return memoryUnavailable()
        val id = pending.payload[PAYLOAD_MEMORY_ID].orEmpty()
        if (id.isBlank()) return speakError("No pude olvidar ese recuerdo.")

        store.delete(id)
        return OrchestratorOutcome(
            spokenText = "Listo. Lo olvidé.",
            targetState = AppState.SPEAKING,
            clearsPendingConsent = true,
            forceSpeak = true
        )
    }

    private fun executeClearMemory(): OrchestratorOutcome {
        val store = memoryStore ?: return memoryUnavailable()
        store.clearAll()
        return OrchestratorOutcome(
            spokenText = ConsentPhrases.MEMORY_CLEARED,
            targetState = AppState.SPEAKING,
            clearsPendingConsent = true,
            forceSpeak = true
        )
    }

    private fun buildMemoryFromCommand(rawInput: String, nowMillis: Long): UserMemory? {
        val content = rawInput
            .replace(Regex("^\\s*record[áa]\\s+que\\s+", RegexOption.IGNORE_CASE), "")
            .trim()
            .trim('.', '!', '?')
        if (content.isBlank()) return null

        val normalized = MemoryPolicy.normalize(content)
        val memoryType: MemoryType
        val label: String
        val value: String

        val trustedContact = Regex(
            "^(.+?)\\s+es\\s+(?:mi\\s+)?contacto\\s+de\\s+confianza$",
            RegexOption.IGNORE_CASE
        ).matchEntire(content)
        val emergencyContact = Regex(
            "^(.+?)\\s+es\\s+(?:mi\\s+)?contacto\\s+de\\s+emergencia$",
            RegexOption.IGNORE_CASE
        ).matchEntire(content)
        val warningKeyword = Regex(
            "^si\\s+aparece\\s+(.+?)\\s+me\\s+avis(?:es|a)$",
            RegexOption.IGNORE_CASE
        ).matchEntire(content)

        when {
            trustedContact != null -> {
                memoryType = MemoryType.TRUSTED_CONTACT
                label = trustedContact.groupValues[1].cleanMemoryValue()
                value = SafeContactMemory.contactValue(label)
            }
            emergencyContact != null -> {
                memoryType = MemoryType.EMERGENCY_CONTACT
                label = emergencyContact.groupValues[1].cleanMemoryValue()
                value = SafeContactMemory.contactValue(label)
            }
            normalized.contains("prefiero respuestas cortas") -> {
                memoryType = MemoryType.USER_PREFERENCE
                label = "respuestas cortas"
                value = "respuestas cortas"
            }
            normalized.startsWith("prefiero ") -> {
                memoryType = MemoryType.USER_PREFERENCE
                value = content.substringAfter("prefiero", "").cleanMemoryValue()
                label = value
            }
            warningKeyword != null -> {
                memoryType = MemoryType.WARNING_KEYWORD
                label = warningKeyword.groupValues[1].cleanMemoryValue()
                value = label
            }
            normalized.contains("app sensible") -> {
                memoryType = MemoryType.APP_SENSITIVITY
                label = content.substringBefore("es", content).cleanMemoryValue()
                value = label
            }
            normalized.startsWith("quiero que ") || normalized.startsWith("si ") -> {
                memoryType = MemoryType.SAFETY_RULE
                label = "regla de seguridad"
                value = content.cleanMemoryValue()
            }
            else -> return null
        }

        if (label.isBlank() || value.isBlank()) return null

        return UserMemory(
            id = UUID.randomUUID().toString(),
            type = memoryType,
            label = label,
            value = value,
            createdAtMillis = nowMillis,
            updatedAtMillis = nowMillis,
            expiresAtMillis = null,
            isSensitive = false,
            userApproved = true
        )
    }

    private fun riskAwareVisibleScreenOutcome(
        visibleText: String,
        clearsPendingConsent: Boolean
    ): OrchestratorOutcome {
        val warnings = riskDetector.detectFromVisibleText(visibleText)
        return OrchestratorOutcome(
            spokenText = riskAwareSpokenText("La pantalla dice:", visibleText, warnings),
            targetState = AppState.SPEAKING,
            clearsPendingConsent = clearsPendingConsent,
            safetyNotice = warnings.takeIf { it.isNotEmpty() }?.let { RISK_GENERIC_NOTICE },
            forceSpeak = true
        )
    }

    private fun riskAwareSpokenText(
        prefix: String,
        text: String,
        warnings: List<RiskWarning>
    ): String {
        if (warnings.isEmpty()) {
            return "$prefix ${PrivacyGuard.sanitizeScreenText(redactForSpeech(text))}"
        }

        val warningText = buildRiskWarningText(warnings)
        if (!PrivacyGuard.canReadAloud(text, warnings)) {
            return warningText
        }

        return "$warningText $prefix ${PrivacyGuard.sanitizeScreenText(redactForSpeech(text))}"
    }

    private fun buildRiskWarningText(warnings: List<RiskWarning>): String {
        val details = warnings.joinToString(" ") { it.spokenText }
        return "$RISK_GENERIC_NOTICE $details".trim()
    }

    private fun redactForSpeech(text: String): String =
        PrivacyGuard.redactCardLikeNumbers(
            PrivacyGuard.redactVerificationCodes(
                PrivacyGuard.redactPasswords(text)
            )
        )

    private fun UserMemory.toPayload(): Map<String, String> = mapOf(
        PAYLOAD_MEMORY_ID to id,
        PAYLOAD_MEMORY_TYPE to type.name,
        PAYLOAD_MEMORY_LABEL to label,
        PAYLOAD_MEMORY_VALUE to value,
        PAYLOAD_MEMORY_CREATED_AT to createdAtMillis.toString(),
        PAYLOAD_MEMORY_IS_SENSITIVE to isSensitive.toString()
    )

    private fun Map<String, String>.toMemory(nowMillis: Long): UserMemory? {
        val type = runCatching {
            MemoryType.valueOf(getValue(PAYLOAD_MEMORY_TYPE))
        }.getOrNull() ?: return null

        return UserMemory(
            id = this[PAYLOAD_MEMORY_ID].orEmpty().ifBlank { UUID.randomUUID().toString() },
            type = type,
            label = this[PAYLOAD_MEMORY_LABEL].orEmpty(),
            value = this[PAYLOAD_MEMORY_VALUE].orEmpty(),
            createdAtMillis = this[PAYLOAD_MEMORY_CREATED_AT]?.toLongOrNull() ?: nowMillis,
            updatedAtMillis = nowMillis,
            expiresAtMillis = null,
            isSensitive = this[PAYLOAD_MEMORY_IS_SENSITIVE].toBoolean(),
            userApproved = true
        )
    }

    private fun memorySpokenSummary(memory: UserMemory): String = when (memory.type) {
        MemoryType.TRUSTED_CONTACT ->
            if (memory.value.startsWith(SafeContactMemory.PHONE_PREFIX, ignoreCase = true)) {
                "el número de ${memory.label} como contacto de confianza"
            } else {
                "${memory.label} es contacto de confianza"
            }
        MemoryType.EMERGENCY_CONTACT ->
            if (memory.value.startsWith(SafeContactMemory.PHONE_PREFIX, ignoreCase = true)) {
                "el número de ${memory.label} como contacto de emergencia"
            } else {
                "${memory.label} es contacto de emergencia"
            }
        MemoryType.LOCATION_ALIAS -> "la ubicación ${memory.label}"
        MemoryType.USER_PREFERENCE -> "preferís ${memory.value}"
        MemoryType.WARNING_KEYWORD -> "te avise si aparece ${memory.value}"
        MemoryType.APP_SENSITIVITY -> "${memory.label} es app sensible"
        MemoryType.SAFETY_RULE -> memory.value
        MemoryType.FREQUENT_COMMAND -> "usás seguido ${memory.label}"
        MemoryType.ROUTINE_PATTERN -> memory.value
    }

    private fun String.cleanMemoryValue(): String =
        trim()
            .trim('.', ',', ';', ':')
            .replace(Regex("\\s+"), " ")

    private fun stableContactMemoryId(type: MemoryType, contactName: String): String =
        "${type.name.lowercase()}-${MemoryPolicy.normalize(contactName).replace(Regex("[^a-z0-9]+"), "-")}"

    private fun stableLocationMemoryId(alias: String): String =
        "location-${MemoryPolicy.normalize(alias).replace(Regex("[^a-z0-9]+"), "-")}"

    private fun findLocationAlias(alias: String): StoredLocation? =
        findLocationAliasMemory(alias)?.let { SafeLocationMemory.parse(it.value) }

    private fun findLocationAliasMemory(alias: String): UserMemory? {
        val store = memoryStore ?: return null
        val normalizedAlias = MemoryPolicy.normalize(alias)
        return store.getByType(MemoryType.LOCATION_ALIAS)
            .firstOrNull { memory ->
                SafeLocationMemory.isLocationAlias(memory) &&
                    MemoryPolicy.normalize(memory.label) == normalizedAlias
            }
    }

    private fun isLocationAliasRequest(
        parsedIntent: ParsedAgentIntent,
        destination: String
    ): Boolean {
        val normalized = MemoryPolicy.normalize(destination)
        return parsedIntent.slotValue(AgentSlotName.LOCATION_ALIAS) != null ||
            normalized in DEFAULT_LOCATION_ALIASES
    }

    private fun memoryUnavailable(): OrchestratorOutcome =
        OrchestratorOutcome(
            spokenText = "La memoria local no está disponible.",
            targetState = AppState.ERROR,
            isError = true,
            forceSpeak = true
        )

    private fun mapToAiTask(cleanInput: String): AiTask {
        val normalized = cleanInput.lowercase().trim()
        return when {
            normalized.containsAny("ayuda", "que puedo decir", "qué puedo decir", "ayudame con la app") -> AiTask.HELP
            normalized.containsAny("emergencia", "auxilio", "perdido", "perdida") -> AiTask.EMERGENCY_HELP
            normalized.containsAny("describir", "qué tengo enfrente", "que tengo enfrente", "que ves", "qué ves") -> AiTask.DESCRIBE_SCENE
            normalized.containsAny("explicar pantalla", "explicame la pantalla") -> AiTask.EXPLAIN_SCREEN
            normalized.containsAny("leer", "leeme", "texto", "cartel", "ticket", "factura") -> AiTask.READ_TEXT
            else -> AiTask.UNKNOWN
        }
    }

    private fun speakError(text: String) = OrchestratorOutcome(
        spokenText = text,
        targetState = AppState.ERROR,
        isError = true,
        forceSpeak = true
    )

    private fun AgentDecision.toOutcome(
        clearsPending: Boolean = false,
        safetyNotice: String? = null
    ): OrchestratorOutcome {
        return when (this) {
            is AgentDecision.AskQuestion -> OrchestratorOutcome(
                spokenText = spokenText,
                targetState = targetState.toAppState(),
                clearsPending = clearsPending,
                forceSpeak = true,
                safetyNotice = safetyNotice,
                agentState = targetState,
                decisionDebugLabel = debugLabel
            )

            is AgentDecision.ExecuteExternalAction -> OrchestratorOutcome(
                spokenText = spokenText,
                targetState = AppState.EXTERNAL_APP_HANDOFF,
                externalEvent = externalEvent,
                clearsPending = clearsPending,
                forceSpeak = true,
                safetyNotice = safetyNotice,
                decisionDebugLabel = debugLabel
            )

            is AgentDecision.StayInApp -> OrchestratorOutcome(
                spokenText = spokenText,
                targetState = targetState?.toAppState() ?: AppState.SPEAKING,
                clearsPending = clearsPending,
                forceSpeak = true,
                safetyNotice = safetyNotice,
                agentState = targetState,
                decisionDebugLabel = debugLabel
            )

            is AgentDecision.RequestConfirmation -> OrchestratorOutcome(
                spokenText = spokenText,
                targetState = AppState.WAITING_CONFIRMATION,
                clearsPending = clearsPending,
                forceSpeak = true,
                safetyNotice = safetyNotice,
                agentState = AgentState.WAITING_CONFIRMATION,
                decisionDebugLabel = debugLabel
            )

            is AgentDecision.RetryListening -> OrchestratorOutcome(
                spokenText = spokenText,
                targetState = AppState.ERROR,
                clearsPending = clearsPending,
                isError = true,
                forceSpeak = true,
                safetyNotice = safetyNotice,
                decisionDebugLabel = debugLabel
            )

            is AgentDecision.RejectUnsafe -> OrchestratorOutcome(
                spokenText = spokenText,
                targetState = AppState.ERROR,
                clearsPending = clearsPending,
                isError = true,
                forceSpeak = true,
                safetyNotice = safetyNotice,
                decisionDebugLabel = debugLabel
            )

            is AgentDecision.Cancel -> OrchestratorOutcome(
                spokenText = spokenText,
                targetState = AppState.IDLE,
                clearsPending = true,
                forceSpeak = true,
                safetyNotice = safetyNotice,
                decisionDebugLabel = debugLabel
            )
        }
    }

    private fun isPrincipalAppOpen(rawText: String): Boolean {
        val normalized = MemoryPolicy.normalize(rawText)
        return normalized.contains("principal") ||
            normalized.contains("solamente") ||
            normalized.startsWith("solo ")
    }

    private fun handoffEvent(
        externalAppName: String,
        spokenText: String,
        delegate: ExternalActionEvent
    ): ExternalActionEvent.ExternalAppHandoff =
        ExternalActionEvent.ExternalAppHandoff(
            externalAppName = externalAppName,
            reason = spokenText,
            returnHint = EXTERNAL_RETURN_HINT,
            spokenText = spokenText,
            delegate = delegate
        )

    private fun String.containsAny(vararg tokens: String) = tokens.any { contains(it) }

    companion object {
        private const val RISK_GENERIC_NOTICE =
            "Antes de responder, te aviso: este texto puede ser sensible."
        private const val EXTERNAL_RETURN_HINT =
            "Para seguir, volvé con el botón Estela."
        private const val WHATSAPP_GENERAL_HANDOFF_TEXT =
            "Abrí WhatsApp principal. Mientras estés ahí no escucho comandos. Para seguir, volvé a Estela."
        private const val WHATSAPP_CHAT_HANDOFF_TEXT =
            "Voy a abrir el chat de WhatsApp. No envío nada. Para seguir, volvé con el botón Estela."
        private const val WHATSAPP_COMPOSE_HANDOFF_TEXT =
            "Voy a abrir WhatsApp con el mensaje preparado. No lo envío. Para seguir, volvé con el botón Estela."
        private const val WHATSAPP_PRINCIPAL_HANDOFF_TEXT =
            "Abro WhatsApp principal. Para seguir, toca Escuchar o volve a Estela."
        private const val MAPS_HANDOFF_TEXT =
            "Voy a abrir mapas. Te puedo orientar, pero no detecto peligros de la calle."
        private const val PHONE_HANDOFF_TEXT =
            "Voy a abrir el marcador. No llamo automáticamente."
        private const val PAYLOAD_MEMORY_ID = "memory_id"
        private const val PAYLOAD_MEMORY_TYPE = "memory_type"
        private const val PAYLOAD_MEMORY_LABEL = "memory_label"
        private const val PAYLOAD_MEMORY_VALUE = "memory_value"
        private const val PAYLOAD_MEMORY_CREATED_AT = "memory_created_at"
        private const val PAYLOAD_MEMORY_IS_SENSITIVE = "memory_is_sensitive"

        private val CONTACT_MEMORY_INTENTS: Set<AgentIntent> = setOf(
            AgentIntent.SAVE_CONTACT,
            AgentIntent.SAVE_CONTACT_PHONE,
            AgentIntent.LIST_CONTACTS,
            AgentIntent.DELETE_CONTACT
        )

        private val MAPS_INTENTS: Set<AgentIntent> = setOf(
            AgentIntent.OPEN_MAPS,
            AgentIntent.GET_CURRENT_LOCATION,
            AgentIntent.NAVIGATE_TO_DESTINATION,
            AgentIntent.SAVE_LOCATION_ALIAS,
            AgentIntent.LIST_LOCATION_ALIASES,
            AgentIntent.DELETE_LOCATION_ALIAS
        )

        private val DEFAULT_LOCATION_ALIASES: Set<String> = setOf(
            "casa",
            "mi casa",
            "trabajo",
            "mi trabajo",
            "laburo",
            "mi laburo"
        )
    }
}
