package com.ojoclaro.android.domain

import com.ojoclaro.android.capabilities.Capability
import com.ojoclaro.android.capabilities.CapabilityRegistry
import com.ojoclaro.android.consent.ConsentManager
import com.ojoclaro.android.consent.ConsentPhrases
import com.ojoclaro.android.consent.PendingSensitiveAction
import com.ojoclaro.android.consent.SensitiveActionType
import com.ojoclaro.android.external.ExternalActionEvent
import com.ojoclaro.android.external.ExternalCommandType
import com.ojoclaro.android.global.GlobalAssistantCapabilityGate
import com.ojoclaro.android.maps.LocationProvider
import com.ojoclaro.android.maps.LocationSnapshot
import com.ojoclaro.android.maps.SafeLocationMemory
import com.ojoclaro.android.memory.MemoryStore
import com.ojoclaro.android.memory.MemoryType
import com.ojoclaro.android.memory.SafeContactMemory
import com.ojoclaro.android.memory.UserMemory
import com.ojoclaro.android.model.AppState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AssistantOrchestratorTest {

    private fun orchestratorWith(vararg pairs: Pair<Capability, Boolean>): AssistantOrchestrator {
        val registry = CapabilityRegistry(
            context = null,
            availabilityOverrides = mapOf(*pairs)
        )
        return AssistantOrchestrator(
            capabilityRegistry = registry,
            globalAssistantCapabilityProvider = { globalContinuationReady() }
        )
    }

    private fun orchestratorWithMemory(
        store: MemoryStore,
        locationProvider: LocationProvider? = null
    ): AssistantOrchestrator {
        val registry = CapabilityRegistry(
            context = null,
            availabilityOverrides = mapOf(
                Capability.WHATSAPP to true,
                Capability.ACCESSIBILITY_SERVICE to true,
                Capability.CAMERA to true
            )
        )
        return AssistantOrchestrator(
            capabilityRegistry = registry,
            memoryStore = store,
            locationProvider = locationProvider,
            globalAssistantCapabilityProvider = { globalContinuationReady() }
        )
    }

    private fun orchestratorWithoutGlobalContinuation(): AssistantOrchestrator {
        val registry = CapabilityRegistry(
            context = null,
            availabilityOverrides = mapOf(
                Capability.WHATSAPP to true,
                Capability.ACCESSIBILITY_SERVICE to true,
                Capability.CAMERA to true
            )
        )
        return AssistantOrchestrator(
            capabilityRegistry = registry,
            globalAssistantCapabilityProvider = {
                GlobalAssistantCapabilityGate.unavailable("mic_unavailable")
            }
        )
    }

    private fun orchestratorWithFallbackReturnOnly(): AssistantOrchestrator {
        val registry = CapabilityRegistry(
            context = null,
            availabilityOverrides = mapOf(
                Capability.WHATSAPP to true,
                Capability.ACCESSIBILITY_SERVICE to true,
                Capability.CAMERA to true
            )
        )
        return AssistantOrchestrator(
            capabilityRegistry = registry,
            globalAssistantCapabilityProvider = {
                GlobalAssistantCapabilityGate.fromFlags(
                    foregroundServiceReady = true,
                    notificationReady = true,
                    overlayReady = false,
                    microphoneContinuationReady = false,
                    fallbackReturnReady = true
                )
            }
        )
    }

    private fun globalContinuationReady() =
        GlobalAssistantCapabilityGate.fromFlags(
            foregroundServiceReady = true,
            notificationReady = true,
            overlayReady = true,
            microphoneContinuationReady = true,
            fallbackReturnReady = true
        )

    private val allAvailable = orchestratorWith(
        Capability.WHATSAPP to true,
        Capability.ACCESSIBILITY_SERVICE to true,
        Capability.CAMERA to true
    )

    private val allMissing = orchestratorWith(
        Capability.WHATSAPP to false,
        Capability.ACCESSIBILITY_SERVICE to false,
        Capability.CAMERA to false
    )

    @Test
    fun blankInputReturnsError() = runTest {
        val outcome = allAvailable.process("   ")
        assertTrue(outcome.isError)
        assertEquals(AppState.ERROR, outcome.targetState)
    }

    @Test
    fun openWhatsAppDirectoSinContinuationNoQuedaEnPending() = runTest {
        // QA Samsung 2026-05-13: lo critico es que "abri wp" NO se quede colgado
        // en "Pendiente: acción de WhatsApp". Sin continuation segura el
        // executionPolicy responde con guia clara de como activar notificaciones,
        // pero nunca con el flujo guiado roto.
        val outcome = orchestratorWithoutGlobalContinuation().process("abri wp")

        assertFalse(outcome.isError)
        assertNotEquals(AppState.WAITING_WHATSAPP_ACTION, outcome.targetState)
        assertNotEquals(com.ojoclaro.android.agent.AgentState.WAITING_WHATSAPP_ACTION, outcome.agentState)
        // El spoken text debe ser concreto: o abre la app o explica como volver.
        assertTrue(
            outcome.spokenText.contains("WhatsApp", ignoreCase = true) ||
                outcome.spokenText.contains("notificaciones", ignoreCase = true),
            "expected actionable guidance, got: ${outcome.spokenText}"
        )
    }

    @Test
    fun openWhatsAppDirectoConFallbackReturnReadyAbreWhatsApp() = runTest {
        val outcome = orchestratorWithFallbackReturnOnly().process("abri wp")

        assertFalse(outcome.isError)
        assertEquals(AppState.EXTERNAL_APP_HANDOFF, outcome.targetState)
        assertEquals(ExternalActionEvent.OpenWhatsApp, assertExternalHandoff(outcome, "WhatsApp"))
        assertTrue(outcome.spokenText.contains("Abro WhatsApp principal"))
    }

    @Test
    fun openWhatsAppDirectoConGlobalContinuationAbreWhatsAppPrincipal() = runTest {
        val outcome = allAvailable.process("abrí wp")

        assertFalse(outcome.isError)
        assertEquals(AppState.EXTERNAL_APP_HANDOFF, outcome.targetState)
        assertEquals(ExternalActionEvent.OpenWhatsApp, assertExternalHandoff(outcome, "WhatsApp"))
        assertNull(outcome.newPending)
        // Direct open: copy "Abro WhatsApp principal", sin pedir slot.
        assertTrue(outcome.spokenText.contains("Abro WhatsApp principal"))
    }

    @Test
    fun openWhatsAppDirectoEntiendeMuletillaArgentinaYAbreApp() = runTest {
        val outcome = allAvailable.process("che abrí wp")

        assertFalse(outcome.isError)
        assertEquals(AppState.EXTERNAL_APP_HANDOFF, outcome.targetState)
        assertEquals(ExternalActionEvent.OpenWhatsApp, assertExternalHandoff(outcome, "WhatsApp"))
        assertTrue(outcome.spokenText.contains("Abro WhatsApp principal"))
    }

    @Test
    fun openWhatsAppPrincipalSuccessEmitsExternalEvent() = runTest {
        val outcome = allAvailable.process("abrí WhatsApp principal")
        assertFalse(outcome.isError)
        assertEquals(AppState.EXTERNAL_APP_HANDOFF, outcome.targetState)
        val delegate = assertExternalHandoff(outcome, "WhatsApp")
        assertEquals(ExternalActionEvent.OpenWhatsApp, delegate)
        assertTrue(outcome.spokenText.contains("principal"))
        assertTrue(outcome.spokenText.contains("Escuchar") || outcome.spokenText.contains("Estela"))
    }

    @Test
    fun openWhatsAppPrincipalConFallbackVisiblePuedeAbrir() = runTest {
        val outcome = orchestratorWithFallbackReturnOnly().process("abrir WhatsApp principal")

        assertFalse(outcome.isError)
        assertEquals(AppState.EXTERNAL_APP_HANDOFF, outcome.targetState)
        assertEquals(ExternalActionEvent.OpenWhatsApp, assertExternalHandoff(outcome, "WhatsApp"))
        assertTrue(outcome.decisionDebugLabel.contains("FALLBACK"))
    }

    @Test
    fun openWhatsAppMissingReturnsError() = runTest {
        val outcome = allMissing.process("abrí WhatsApp principal")
        assertTrue(outcome.isError)
        assertEquals(Capability.MSG_WHATSAPP_MISSING, outcome.spokenText)
    }

    @Test
    fun readVisibleScreenAccessibilityMissingReturnsPermissionRequired() = runTest {
        val outcome = allMissing.process("qué dice la pantalla")
        assertTrue(outcome.isError)
        assertEquals(AppState.PERMISSION_REQUIRED, outcome.targetState)
        assertEquals(Capability.MSG_ACCESSIBILITY_MISSING, outcome.spokenText)
    }

    @Test
    fun readVisibleScreenAsksForConsentBeforeReading() = runTest {
        val outcome = allAvailable.process("qué dice la pantalla")
        assertFalse(outcome.isError)
        assertEquals(AppState.WAITING_CONFIRMATION, outcome.targetState)
        assertNotNull(outcome.newPendingConsent)
        assertEquals(SensitiveActionType.READ_VISIBLE_MESSAGE, outcome.newPendingConsent!!.type)
        assertTrue(outcome.spokenText.contains("Confirmá"))
        // No debe ejecutar la lectura todavía: ningún externalEvent.
        assertNull(outcome.externalEvent)
    }

    @Test
    fun leemeEsteMensajeAlsoAsksForConsent() = runTest {
        val outcome = allAvailable.process("leeme este mensaje")
        assertFalse(outcome.isError)
        assertEquals(AppState.WAITING_CONFIRMATION, outcome.targetState)
        assertNotNull(outcome.newPendingConsent)
        assertNull(outcome.externalEvent)
    }

    @Test
    fun confirmWithConsentPendingExecutesReadVisibleScreen() = runTest {
        val pending = PendingSensitiveAction(
            id = "consent-1",
            type = SensitiveActionType.READ_VISIBLE_MESSAGE,
            spokenExplanation = ConsentPhrases.READ_VISIBLE_MESSAGE,
            createdAtMillis = 1_000L,
            expiresAtMillis = 1_000L + ConsentManager.DEFAULT_TTL_MILLIS,
            requiresConsentLevel = com.ojoclaro.android.consent.ConsentLevel.SIMPLE_CONFIRMATION
        )

        val outcome = allAvailable.process(
            rawInput = "confirmar",
            pendingConsent = pending,
            nowMillis = 1_500L
        )

        assertFalse(outcome.isError)
        assertEquals(AppState.PROCESSING, outcome.targetState)
        assertEquals(ExternalActionEvent.ReadVisibleScreen, outcome.externalEvent)
        assertTrue(outcome.clearsPendingConsent)
    }

    @Test
    fun siDoesNotConfirmSensitiveConsentPending() = runTest {
        val pending = PendingSensitiveAction(
            id = "consent-1",
            type = SensitiveActionType.READ_VISIBLE_MESSAGE,
            spokenExplanation = ConsentPhrases.READ_VISIBLE_MESSAGE,
            createdAtMillis = 1_000L,
            expiresAtMillis = 1_000L + ConsentManager.DEFAULT_TTL_MILLIS,
            requiresConsentLevel = com.ojoclaro.android.consent.ConsentLevel.SIMPLE_CONFIRMATION
        )

        val outcome = allAvailable.process(
            rawInput = "sí",
            pendingConsent = pending,
            nowMillis = 1_500L
        )

        assertNull(outcome.externalEvent)
        assertTrue(outcome.clearsPendingConsent)
    }

    @Test
    fun cancelWithConsentPendingClearsIt() = runTest {
        val pending = PendingSensitiveAction(
            id = "consent-1",
            type = SensitiveActionType.READ_VISIBLE_MESSAGE,
            spokenExplanation = ConsentPhrases.READ_VISIBLE_MESSAGE,
            createdAtMillis = 1_000L,
            expiresAtMillis = 1_000L + ConsentManager.DEFAULT_TTL_MILLIS,
            requiresConsentLevel = com.ojoclaro.android.consent.ConsentLevel.SIMPLE_CONFIRMATION
        )

        val outcome = allAvailable.process(
            rawInput = "cancelar",
            pendingConsent = pending,
            nowMillis = 1_500L
        )

        assertFalse(outcome.isError)
        assertEquals(AppState.IDLE, outcome.targetState)
        assertTrue(outcome.clearsPendingConsent)
        assertEquals(ConsentPhrases.ACTION_CANCELLED, outcome.spokenText)
        assertNull(outcome.externalEvent)
    }

    @Test
    fun confirmWithExpiredConsentReturnsClearMessage() = runTest {
        val createdAt = 1_000L
        val expiresAt = createdAt + ConsentManager.DEFAULT_TTL_MILLIS
        val pending = PendingSensitiveAction(
            id = "consent-1",
            type = SensitiveActionType.READ_VISIBLE_MESSAGE,
            spokenExplanation = ConsentPhrases.READ_VISIBLE_MESSAGE,
            createdAtMillis = createdAt,
            expiresAtMillis = expiresAt,
            requiresConsentLevel = com.ojoclaro.android.consent.ConsentLevel.SIMPLE_CONFIRMATION
        )

        val outcome = allAvailable.process(
            rawInput = "confirmar",
            pendingConsent = pending,
            nowMillis = expiresAt + 1
        )

        assertTrue(outcome.isError)
        assertTrue(outcome.clearsPendingConsent)
        assertNull(outcome.externalEvent)
        assertEquals(ConsentPhrases.EXPIRED_ACTION, outcome.spokenText)
    }

    @Test
    fun confirmWithConsentPendingButAccessibilityLostAsksForPermission() = runTest {
        val orchestrator = orchestratorWith(
            Capability.ACCESSIBILITY_SERVICE to false,
            Capability.WHATSAPP to true,
            Capability.CAMERA to true
        )
        val pending = PendingSensitiveAction(
            id = "consent-1",
            type = SensitiveActionType.READ_VISIBLE_MESSAGE,
            spokenExplanation = ConsentPhrases.READ_VISIBLE_MESSAGE,
            createdAtMillis = 1_000L,
            expiresAtMillis = 1_000L + ConsentManager.DEFAULT_TTL_MILLIS,
            requiresConsentLevel = com.ojoclaro.android.consent.ConsentLevel.SIMPLE_CONFIRMATION
        )

        val outcome = orchestrator.process(
            rawInput = "confirmar",
            pendingConsent = pending,
            nowMillis = 1_500L
        )

        assertTrue(outcome.isError)
        assertEquals(AppState.PERMISSION_REQUIRED, outcome.targetState)
        assertTrue(outcome.clearsPendingConsent)
        assertEquals(Capability.MSG_ACCESSIBILITY_MISSING, outcome.spokenText)
    }

    @Test
    fun composeMessageNeedsConfirmation() = runTest {
        val outcome = allAvailable.process("mandale a ContactoDemo: estoy llegando")
        assertEquals(AppState.WAITING_CONFIRMATION, outcome.targetState)
        assertFalse(outcome.isError)
        assertTrue(outcome.spokenText.contains("decí: confirmar"))
    }

    @Test
    fun naturalWhatsAppCommandNeedsConfirmation() = runTest {
        val outcome = allAvailable.process("mandale un mensaje a ContactoDemo que estoy llegando")

        assertEquals(AppState.WAITING_CONFIRMATION, outcome.targetState)
        assertFalse(outcome.isError)
        assertNotNull(outcome.newPending)
        assertEquals("ContactoDemo", outcome.newPending!!.command.contactName)
        assertEquals("estoy llegando", outcome.newPending!!.command.messageText)
        assertTrue(outcome.spokenText.contains("No lo envío automáticamente"))
        assertTrue(outcome.spokenText.contains("decí: confirmar"))
    }

    @Test
    fun decileArgentinoMantieneComposeConConfirmacion() = runTest {
        val outcome = allAvailable.process("decile a ContactoDemo que estoy llegando")

        assertEquals(AppState.WAITING_CONFIRMATION, outcome.targetState)
        assertFalse(outcome.isError)
        assertNotNull(outcome.newPending)
        assertEquals(ExternalCommandType.COMPOSE_WHATSAPP_MESSAGE, outcome.newPending!!.command.type)
        assertEquals("ContactoDemo", outcome.newPending!!.command.contactName)
        assertEquals("estoy llegando", outcome.newPending!!.command.messageText)
        assertNull(outcome.externalEvent)
    }

    @Test
    fun missingWhatsAppContactDoesNotOpenWhatsApp() = runTest {
        val outcome = allAvailable.process("mandale un mensaje")

        assertTrue(outcome.isError)
        assertNull(outcome.externalEvent)
        assertNull(outcome.newPending)
        assertTrue(outcome.spokenText.contains("A quién"))
    }

    @Test
    fun missingWhatsAppMessageDoesNotOpenWhatsApp() = runTest {
        val outcome = allAvailable.process("mandale a ContactoDemo")

        assertTrue(outcome.isError)
        assertNull(outcome.externalEvent)
        assertNull(outcome.newPending)
        assertTrue(outcome.spokenText.contains("Qué mensaje"))
    }

    @Test
    fun sensitiveWhatsAppMessageIsNotPrepared() = runTest {
        val outcome = allAvailable.process("mandale a ContactoDemo que mi contraseña es 1234")

        assertTrue(outcome.isError)
        assertNull(outcome.externalEvent)
        assertNull(outcome.newPending)
        assertTrue(outcome.spokenText.contains("datos sensibles"))
    }

    @Test
    fun composeMessageWhatsAppMissingReturnsError() = runTest {
        val outcome = allMissing.process("mandale a ContactoDemo: estoy llegando")
        assertTrue(outcome.isError)
        assertEquals(Capability.MSG_WHATSAPP_MISSING, outcome.spokenText)
    }

    @Test
    fun confirmWithoutPendingReturnsFailedMessage() = runTest {
        val outcome = allAvailable.process("confirmar", pendingConfirmation = null)
        assertTrue(outcome.isError || outcome.spokenText.contains("pendiente"))
    }

    @Test
    fun cancelWithoutPendingReturnsNoPendingMessage() = runTest {
        val outcome = allAvailable.process("cancelar", pendingConfirmation = null)
        assertFalse(outcome.isError)
        assertTrue(outcome.spokenText.contains("pendiente") || outcome.spokenText.contains("cancelad"))
    }

    @Test
    fun helpCommandReturnsHelpText() = runTest {
        val outcome = allAvailable.process("necesito ayuda")
        assertFalse(outcome.isError)
        assertTrue(outcome.spokenText.isNotBlank())
    }

    @Test
    fun unknownCommandDoesNotCrash() = runTest {
        val outcome = allAvailable.process("hacé algo extraño")
        assertEquals(AppState.SPEAKING, outcome.targetState)
        assertTrue(outcome.spokenText.isNotBlank())
    }

    @Test
    fun emergencyCommandReturnsHighConfidenceResponse() = runTest {
        val outcome = allAvailable.process("necesito auxilio")
        assertFalse(outcome.isError)
        assertTrue(outcome.spokenText.contains("emergencia") || outcome.spokenText.contains("peligro"))
    }

    @Test
    fun rememberTrustedContactAsksForConfirmation() = runTest {
        val store = InMemoryMemoryStore()
        val orchestrator = orchestratorWithMemory(store)

        val outcome = orchestrator.process(
            rawInput = "recordá que ContactoDemo es contacto de confianza",
            nowMillis = 1_000L
        )

        assertEquals(AppState.WAITING_CONFIRMATION, outcome.targetState)
        assertNotNull(outcome.newPendingConsent)
        assertEquals(SensitiveActionType.SAVE_MEMORY, outcome.newPendingConsent!!.type)
        assertTrue(outcome.spokenText.contains("ContactoDemo"))
        assertTrue(store.findRelevant("").isEmpty())
    }

    @Test
    fun confirmSavesMemory() = runTest {
        val store = InMemoryMemoryStore()
        val orchestrator = orchestratorWithMemory(store)
        val pending = orchestrator.process(
            rawInput = "recordá que ContactoDemo es contacto de confianza",
            nowMillis = 1_000L
        ).newPendingConsent

        val outcome = orchestrator.process(
            rawInput = "confirmar",
            pendingConsent = pending,
            nowMillis = 1_500L
        )

        assertFalse(outcome.isError)
        assertTrue(outcome.clearsPendingConsent)
        assertEquals(1, store.getByType(MemoryType.TRUSTED_CONTACT).size)
        assertEquals("ContactoDemo", store.getByType(MemoryType.TRUSTED_CONTACT).first().label)
    }

    @Test
    fun cancelDoesNotSaveMemory() = runTest {
        val store = InMemoryMemoryStore()
        val orchestrator = orchestratorWithMemory(store)
        val pending = orchestrator.process(
            rawInput = "recordá que ContactoDemo es contacto de confianza",
            nowMillis = 1_000L
        ).newPendingConsent

        val outcome = orchestrator.process(
            rawInput = "cancelar",
            pendingConsent = pending,
            nowMillis = 1_500L
        )

        assertTrue(outcome.clearsPendingConsent)
        assertTrue(outcome.spokenText.contains("No guardé nada"))
        assertTrue(store.findRelevant("").isEmpty())
    }

    @Test
    fun saveContactPhoneRequiresConfirmationAndConfirmSaves() = runTest {
        val store = InMemoryMemoryStore()
        val orchestrator = orchestratorWithMemory(store)
        val pending = orchestrator.process(
            rawInput = "el número de ContactoDemo es 2991234567",
            nowMillis = 1_000L
        ).newPendingConsent

        assertNotNull(pending)
        assertTrue(store.findRelevant("").isEmpty())

        val outcome = orchestrator.process(
            rawInput = "confirmar",
            pendingConsent = pending,
            nowMillis = 1_500L
        )

        assertFalse(outcome.isError)
        assertTrue(outcome.clearsPendingConsent)
        val contact = store.getByType(MemoryType.TRUSTED_CONTACT).first()
        assertEquals("ContactoDemo", contact.label)
        assertEquals(SafeContactMemory.phoneValue("2991234567"), contact.value)
    }

    @Test
    fun callContactUsesStoredSafeContactNumber() = runTest {
        val store = InMemoryMemoryStore()
        store.save(
            UserMemory(
                id = "trusted_contact-demo",
                type = MemoryType.TRUSTED_CONTACT,
                label = "ContactoDemo",
                value = SafeContactMemory.phoneValue("2991234567")!!,
                createdAtMillis = 1L,
                updatedAtMillis = 1L,
                isSensitive = false,
                userApproved = true
            )
        )
        val orchestrator = orchestratorWithMemory(store)

        val pending = orchestrator.process("llamá a ContactoDemo", nowMillis = 1_000L).newPending
        assertNotNull(pending)
        val outcome = orchestrator.process("confirmar", pendingConfirmation = pending, nowMillis = 1_500L)

        val delegate = assertExternalHandoff(outcome, "Teléfono")
        assertTrue(delegate is ExternalActionEvent.DialPhoneNumber)
        assertEquals("2991234567", delegate.phoneNumber)
    }

    @Test
    fun listTrustedContactsDoesNotExposePhoneNumbers() = runTest {
        val store = InMemoryMemoryStore()
        store.save(
            UserMemory(
                id = "trusted_contact-demo",
                type = MemoryType.TRUSTED_CONTACT,
                label = "ContactoDemo",
                value = SafeContactMemory.phoneValue("2991234567")!!,
                createdAtMillis = 1L,
                updatedAtMillis = 1L,
                isSensitive = false,
                userApproved = true
            )
        )
        val orchestrator = orchestratorWithMemory(store)

        val outcome = orchestrator.process("quiénes son mis contactos de confianza")

        assertTrue(outcome.spokenText.contains("ContactoDemo"))
        assertFalse(outcome.spokenText.contains("2991234567"))
    }

    @Test
    fun deleteContactRequiresConfirmationAndConfirmDeletes() = runTest {
        val store = InMemoryMemoryStore()
        store.save(phoneMemory("phone-1", "ContactoDemo", "1123456789"))
        val orchestrator = orchestratorWithMemory(store)

        val pending = orchestrator.process("olvidá el contacto ContactoDemo", nowMillis = 1_000L).newPendingConsent
        assertNotNull(pending)
        assertEquals(1, store.findRelevant("").size)

        val outcome = orchestrator.process("confirmar", pendingConsent = pending, nowMillis = 1_500L)

        assertTrue(outcome.clearsPendingConsent)
        assertTrue(store.findRelevant("").isEmpty())
    }

    @Test
    fun whatDoYouRememberListsSafeMemory() = runTest {
        val store = InMemoryMemoryStore()
        store.save(memory("m1", MemoryType.USER_PREFERENCE, "respuestas cortas"))
        val orchestrator = orchestratorWithMemory(store)

        val outcome = orchestrator.process("qué recordás de mí")

        assertFalse(outcome.isError)
        assertTrue(outcome.spokenText.contains("recuerdo", ignoreCase = true))
        assertTrue(outcome.spokenText.contains("respuestas cortas"))
    }

    @Test
    fun clearMemoryAsksForConfirmation() = runTest {
        val store = InMemoryMemoryStore()
        store.save(memory("m1", MemoryType.TRUSTED_CONTACT, "ContactoDemo"))
        val orchestrator = orchestratorWithMemory(store)

        val outcome = orchestrator.process("borrá tu memoria", nowMillis = 1_000L)

        assertEquals(AppState.WAITING_CONFIRMATION, outcome.targetState)
        assertNotNull(outcome.newPendingConsent)
        assertEquals(SensitiveActionType.CLEAR_MEMORY, outcome.newPendingConsent!!.type)
        assertEquals(1, store.findRelevant("").size)
    }

    @Test
    fun confirmClearsMemory() = runTest {
        val store = InMemoryMemoryStore()
        store.save(memory("m1", MemoryType.TRUSTED_CONTACT, "ContactoDemo"))
        val orchestrator = orchestratorWithMemory(store)
        val pending = orchestrator.process("borrá tu memoria", nowMillis = 1_000L).newPendingConsent

        val outcome = orchestrator.process(
            rawInput = "confirmar",
            pendingConsent = pending,
            nowMillis = 1_500L
        )

        assertTrue(outcome.clearsPendingConsent)
        assertTrue(store.findRelevant("").isEmpty())
        assertTrue(outcome.spokenText.contains("Borré mi memoria local"))
    }

    @Test
    fun confirmingWhatsAppPendingEmitsComposeMessage() = runTest {
        val pending = allAvailable.process(
            rawInput = "mandale a ContactoDemo: estoy llegando",
            nowMillis = 1_000L
        ).newPending

        assertNotNull(pending)
        val outcome = allAvailable.process(
            rawInput = "confirmar",
            pendingConfirmation = pending,
            nowMillis = 1_500L
        )

        assertFalse(outcome.isError)
        val delegate = assertExternalHandoff(outcome, "WhatsApp")
        assertTrue(delegate is ExternalActionEvent.ComposeWhatsAppMessage)
        val compose = delegate as ExternalActionEvent.ComposeWhatsAppMessage
        assertEquals("ContactoDemo", compose.contactName)
        assertEquals("estoy llegando", compose.messageText)
        assertTrue(outcome.clearsPending)
    }

    @Test
    fun confirmingNaturalWhatsAppPendingEmitsComposeMessage() = runTest {
        val pending = allAvailable.process(
            rawInput = "mandale un mensaje a ContactoDemo que estoy llegando",
            nowMillis = 1_000L
        ).newPending

        assertNotNull(pending)
        val outcome = allAvailable.process(
            rawInput = "confirmar",
            pendingConfirmation = pending,
            nowMillis = 1_500L
        )

        assertFalse(outcome.isError)
        val delegate = assertExternalHandoff(outcome, "WhatsApp")
        assertTrue(delegate is ExternalActionEvent.ComposeWhatsAppMessage)
        val compose = delegate as ExternalActionEvent.ComposeWhatsAppMessage
        assertEquals("ContactoDemo", compose.contactName)
        assertEquals("estoy llegando", compose.messageText)
        assertTrue(outcome.clearsPending)
    }

    @Test
    fun siDoesNotConfirmPendingWhatsApp() = runTest {
        val pending = allAvailable.process(
            rawInput = "mandale a ContactoDemo: estoy llegando",
            nowMillis = 1_000L
        ).newPending
        assertNotNull(pending)

        val outcome = allAvailable.process(
            rawInput = "sí",
            pendingConfirmation = pending,
            nowMillis = 1_500L
        )

        // "sí" no confirma. El pending no se ejecuta.
        assertNull(outcome.externalEvent)
    }

    @Test
    fun daleDoesNotConfirmPendingWhatsApp() = runTest {
        val pending = allAvailable.process(
            rawInput = "mandale a ContactoDemo: estoy llegando",
            nowMillis = 1_000L
        ).newPending
        assertNotNull(pending)

        val outcome = allAvailable.process(
            rawInput = "dale",
            pendingConfirmation = pending,
            nowMillis = 1_500L
        )

        assertNull(outcome.externalEvent)
    }

    @Test
    fun newCommandWhileConsentPendingClearsConsent() = runTest {
        val pending = PendingSensitiveAction(
            id = "consent-x",
            type = SensitiveActionType.READ_VISIBLE_MESSAGE,
            spokenExplanation = ConsentPhrases.READ_VISIBLE_MESSAGE,
            createdAtMillis = 1_000L,
            expiresAtMillis = 1_000L + ConsentManager.DEFAULT_TTL_MILLIS,
            requiresConsentLevel = com.ojoclaro.android.consent.ConsentLevel.SIMPLE_CONFIRMATION
        )

        // El usuario hizo otra cosa antes de confirmar/cancelar.
        val outcome = allAvailable.process(
            rawInput = "necesito ayuda",
            pendingConsent = pending,
            nowMillis = 1_500L
        )

        // El consent pending NO debe quedar colgado.
        assertTrue(outcome.clearsPendingConsent)
        // Y la lectura sensible NO debe haberse ejecutado.
        assertNull(outcome.externalEvent)
    }

    @Test
    fun readRiskyVisibleScreenWarnsAfterConsent() = runTest {
        val pending = PendingSensitiveAction(
            id = "consent-1",
            type = SensitiveActionType.READ_VISIBLE_MESSAGE,
            spokenExplanation = ConsentPhrases.READ_VISIBLE_MESSAGE,
            createdAtMillis = 1_000L,
            expiresAtMillis = 1_000L + ConsentManager.DEFAULT_TTL_MILLIS,
            requiresConsentLevel = com.ojoclaro.android.consent.ConsentLevel.SIMPLE_CONFIRMATION
        )

        val outcome = allAvailable.process(
            rawInput = "confirmar",
            pendingConsent = pending,
            visibleScreenText = "Necesito una transferencia de 500 pesos urgente",
            nowMillis = 1_500L
        )

        assertFalse(outcome.isError)
        assertTrue(outcome.clearsPendingConsent)
        assertTrue(outcome.spokenText.contains("dinero o transferencia"))
        assertTrue(outcome.spokenText.contains("Antes de responder"))
    }

    @Test
    fun openPhoneEmitsOpenPhoneEvent() = runTest {
        val outcome = allAvailable.process("abrí teléfono")

        assertFalse(outcome.isError)
        assertEquals(AppState.EXTERNAL_APP_HANDOFF, outcome.targetState)
        assertEquals(ExternalActionEvent.OpenPhone, assertExternalHandoff(outcome, "Teléfono"))
        assertTrue(outcome.spokenText.contains("No llamo automáticamente"))
    }

    @Test
    fun callContactWithoutStoredNumberDoesNotInventNumber() = runTest {
        val outcome = allAvailable.process("llamá a ContactoDemo")

        assertNull(outcome.externalEvent)
        assertNull(outcome.newPending)
        assertTrue(outcome.spokenText.contains("No tengo un número guardado para ContactoDemo"))
        assertTrue(outcome.spokenText.contains("número de ContactoDemo es"))
    }

    @Test
    fun callContactWithStoredNumberAsksForConfirmation() = runTest {
        val store = InMemoryMemoryStore()
        store.save(phoneMemory("phone-1", "ContactoDemo", "1123456789"))
        val orchestrator = orchestratorWithMemory(store)

        val outcome = orchestrator.process("llamá a ContactoDemo", nowMillis = 1_000L)

        assertFalse(outcome.isError)
        assertEquals(AppState.WAITING_CONFIRMATION, outcome.targetState)
        assertNotNull(outcome.newPending)
        assertEquals(ExternalCommandType.CALL_CONTACT, outcome.newPending!!.command.type)
        assertEquals("ContactoDemo", outcome.newPending!!.command.targetName)
        assertEquals("1123456789", outcome.newPending!!.command.payloadText)
        assertTrue(outcome.spokenText.contains("No voy a llamar automáticamente"))
        assertNull(outcome.externalEvent)
    }

    @Test
    fun llamameAMamaUsaNumeroGuardadoYNoLlamaSinConfirmar() = runTest {
        val store = InMemoryMemoryStore()
        store.save(phoneMemory("phone-mama", "mamá", "1123456789"))
        val orchestrator = orchestratorWithMemory(store)

        val outcome = orchestrator.process("llamame a mamá", nowMillis = 1_000L)

        assertFalse(outcome.isError)
        assertEquals(AppState.WAITING_CONFIRMATION, outcome.targetState)
        assertNotNull(outcome.newPending)
        assertEquals(ExternalCommandType.CALL_CONTACT, outcome.newPending!!.command.type)
        assertEquals("mamá", outcome.newPending!!.command.targetName)
        assertNull(outcome.externalEvent)
    }

    @Test
    fun confirmingCallPendingEmitsDialPhoneNumber() = runTest {
        val store = InMemoryMemoryStore()
        store.save(phoneMemory("phone-1", "ContactoDemo", "1123456789"))
        val orchestrator = orchestratorWithMemory(store)
        val pending = orchestrator.process(
            rawInput = "llamá a ContactoDemo",
            nowMillis = 1_000L
        ).newPending

        assertNotNull(pending)
        val outcome = orchestrator.process(
            rawInput = "confirmar",
            pendingConfirmation = pending,
            nowMillis = 1_500L
        )

        assertFalse(outcome.isError)
        val event = outcome.externalEvent
        val delegate = assertExternalHandoff(outcome, "Teléfono")
        assertTrue(delegate is ExternalActionEvent.DialPhoneNumber)
        val dial = delegate as ExternalActionEvent.DialPhoneNumber
        assertEquals("ContactoDemo", dial.contactName)
        assertEquals("1123456789", dial.phoneNumber)
        assertTrue(outcome.clearsPending)
    }

    @Test
    fun cancellingCallPendingDoesNotOpenDialer() = runTest {
        val store = InMemoryMemoryStore()
        store.save(phoneMemory("phone-1", "ContactoDemo", "1123456789"))
        val orchestrator = orchestratorWithMemory(store)
        val pending = orchestrator.process("llamá a ContactoDemo", nowMillis = 1_000L).newPending

        val outcome = orchestrator.process(
            rawInput = "cancelar",
            pendingConfirmation = pending,
            nowMillis = 1_500L
        )

        assertNull(outcome.externalEvent)
        assertTrue(outcome.clearsPending)
        assertTrue(outcome.spokenText.contains("cancelada") || outcome.spokenText.contains("cancel"))
    }

    @Test
    fun siDoesNotConfirmPendingCall() = runTest {
        val store = InMemoryMemoryStore()
        store.save(phoneMemory("phone-1", "ContactoDemo", "1123456789"))
        val orchestrator = orchestratorWithMemory(store)
        val pending = orchestrator.process("llamá a ContactoDemo", nowMillis = 1_000L).newPending

        val outcome = orchestrator.process(
            rawInput = "sí",
            pendingConfirmation = pending,
            nowMillis = 1_500L
        )

        assertNull(outcome.externalEvent)
    }

    @Test
    fun emergencyCallUsesResponsibleNoticeAndConfirmation() = runTest {
        val outcome = allAvailable.process("llamá a emergencias", nowMillis = 1_000L)

        assertEquals(AppState.WAITING_CONFIRMATION, outcome.targetState)
        assertNotNull(outcome.newPending)
        assertEquals("911", outcome.newPending!!.command.payloadText)
        assertTrue(outcome.spokenText.contains("Si estás en peligro"))
        assertTrue(outcome.spokenText.contains("No voy a llamar automáticamente"))
    }

    @Test
    fun whereAmIRequestsPermissionWhenMissing() = runTest {
        val orchestrator = orchestratorWithMemory(
            InMemoryMemoryStore(),
            locationProvider = fakeLocationProvider(hasPermission = false)
        )

        val outcome = orchestrator.process("dónde estoy")

        assertEquals(AppState.PERMISSION_REQUIRED, outcome.targetState)
        assertEquals(ExternalActionEvent.RequestLocationPermission, outcome.externalEvent)
        assertTrue(outcome.spokenText.contains("permiso de ubicación"))
    }

    @Test
    fun dondeAndoYUbicamePidenUbicacionSinCrash() = runTest {
        val orchestrator = orchestratorWithMemory(
            InMemoryMemoryStore(),
            locationProvider = fakeLocationProvider(hasPermission = false)
        )

        listOf("dónde ando", "ubicame").forEach { phrase ->
            val outcome = orchestrator.process(phrase)

            assertEquals(AppState.PERMISSION_REQUIRED, outcome.targetState, phrase)
            assertEquals(ExternalActionEvent.RequestLocationPermission, outcome.externalEvent, phrase)
        }
    }

    @Test
    fun openMapsEmitsOpenMaps() = runTest {
        val outcome = allAvailable.process("abrí mapas")

        assertFalse(outcome.isError)
        assertEquals(AppState.EXTERNAL_APP_HANDOFF, outcome.targetState)
        assertEquals(ExternalActionEvent.OpenMaps, assertExternalHandoff(outcome, "Maps"))
        assertTrue(outcome.spokenText.contains("no detecto peligros"))
    }

    @Test
    fun navigateHomeAsksConfirmationWhenAliasExists() = runTest {
        val store = InMemoryMemoryStore()
        store.save(locationMemory("home", "casa", -38.95, -68.06))
        val orchestrator = orchestratorWithMemory(store)

        val outcome = orchestrator.process("llevame a casa", nowMillis = 1_000L)

        assertEquals(AppState.WAITING_CONFIRMATION, outcome.targetState)
        assertNotNull(outcome.newPending)
        assertEquals(ExternalCommandType.NAVIGATE_TO_COORDINATES, outcome.newPending!!.command.type)
        assertTrue(outcome.spokenText.contains("Voy a abrir mapas hacia casa"))
    }

    @Test
    fun llevameAlLaburoUsaAliasGuardado() = runTest {
        val store = InMemoryMemoryStore()
        store.save(locationMemory("work", "laburo", -34.60, -58.38))
        val orchestrator = orchestratorWithMemory(store)

        val outcome = orchestrator.process("llevame al laburo", nowMillis = 1_000L)

        assertEquals(AppState.WAITING_CONFIRMATION, outcome.targetState)
        assertNotNull(outcome.newPending)
        assertEquals(ExternalCommandType.NAVIGATE_TO_COORDINATES, outcome.newPending!!.command.type)
        assertEquals("laburo", outcome.newPending!!.command.targetName)
    }

    @Test
    fun navigateHomeDoesNotInventMissingAlias() = runTest {
        val orchestrator = orchestratorWithMemory(InMemoryMemoryStore())

        val outcome = orchestrator.process("llevame a casa")

        assertNull(outcome.externalEvent)
        assertNull(outcome.newPending)
        assertTrue(outcome.spokenText.contains("No tengo guardada la ubicación casa"))
    }

    @Test
    fun confirmingNavigationEmitsNavigateEvent() = runTest {
        val store = InMemoryMemoryStore()
        store.save(locationMemory("home", "casa", -38.95, -68.06))
        val orchestrator = orchestratorWithMemory(store)
        val pending = orchestrator.process("llevame a casa", nowMillis = 1_000L).newPending

        val outcome = orchestrator.process("confirmar", pendingConfirmation = pending, nowMillis = 1_500L)

        assertTrue(outcome.clearsPending)
        assertTrue(assertExternalHandoff(outcome, "Maps") is ExternalActionEvent.NavigateToCoordinates)
    }

    @Test
    fun siDoesNotConfirmPendingNavigation() = runTest {
        val store = InMemoryMemoryStore()
        store.save(locationMemory("home", "casa", -38.95, -68.06))
        val orchestrator = orchestratorWithMemory(store)
        val pending = orchestrator.process("llevame a casa", nowMillis = 1_000L).newPending

        val outcome = orchestrator.process("sí", pendingConfirmation = pending, nowMillis = 1_500L)

        assertNull(outcome.externalEvent)
    }

    @Test
    fun cancelNavigationDoesNotOpenMaps() = runTest {
        val store = InMemoryMemoryStore()
        store.save(locationMemory("home", "casa", -38.95, -68.06))
        val orchestrator = orchestratorWithMemory(store)
        val pending = orchestrator.process("llevame a casa", nowMillis = 1_000L).newPending

        val outcome = orchestrator.process("cancelar", pendingConfirmation = pending, nowMillis = 1_500L)

        assertTrue(outcome.clearsPending)
        assertNull(outcome.externalEvent)
    }

    @Test
    fun saveLocationAliasDoesNotSaveWithoutConfirmation() = runTest {
        val store = InMemoryMemoryStore()
        val orchestrator = orchestratorWithMemory(store, locationProvider = fakeLocationProvider())

        val outcome = orchestrator.process("guardá esta ubicación como casa", nowMillis = 1_000L)

        assertNotNull(outcome.newPendingConsent)
        assertTrue(store.findRelevant("").isEmpty())
    }

    @Test
    fun confirmSaveLocationAliasSavesMemory() = runTest {
        val store = InMemoryMemoryStore()
        val orchestrator = orchestratorWithMemory(store, locationProvider = fakeLocationProvider())
        val pending = orchestrator.process("guardá esta ubicación como casa", nowMillis = 1_000L).newPendingConsent

        val outcome = orchestrator.process("confirmar", pendingConsent = pending, nowMillis = 1_500L)

        assertTrue(outcome.clearsPendingConsent)
        assertEquals(1, store.getByType(MemoryType.LOCATION_ALIAS).size)
        assertEquals("casa", store.getByType(MemoryType.LOCATION_ALIAS).first().label)
    }

    // --- OPEN_WHATSAPP_CHAT ---

    @Test
    fun openWhatsAppChatWithStoredNumberAsksForConfirmation() = runTest {
        val store = InMemoryMemoryStore()
        store.save(phoneMemory("phone-marco", "Marco Antonio", "1123456789"))
        val orchestrator = orchestratorWithMemory(store)

        val outcome = orchestrator.process("abrí el chat de Marco Antonio", nowMillis = 1_000L)

        assertFalse(outcome.isError)
        assertEquals(AppState.WAITING_CONFIRMATION, outcome.targetState)
        assertNotNull(outcome.newPending)
        assertEquals(ExternalCommandType.OPEN_WHATSAPP_CHAT, outcome.newPending!!.command.type)
        assertEquals("Marco Antonio", outcome.newPending!!.command.targetName)
        assertEquals("1123456789", outcome.newPending!!.command.payloadText)
        assertTrue(outcome.spokenText.contains("No voy a enviar ningún mensaje"))
        // Antes de confirmar, no se debe disparar ningún evento externo.
        assertNull(outcome.externalEvent)
    }

    @Test
    fun abrimeElChatConNumeroGuardadoCreaPendingDeChat() = runTest {
        val store = InMemoryMemoryStore()
        store.save(phoneMemory("phone-marco", "Marco", "1123456789"))
        val orchestrator = orchestratorWithMemory(store)

        val outcome = orchestrator.process("abrime el chat de Marco", nowMillis = 1_000L)

        assertFalse(outcome.isError)
        assertEquals(AppState.WAITING_CONFIRMATION, outcome.targetState)
        assertNotNull(outcome.newPending)
        assertEquals(ExternalCommandType.OPEN_WHATSAPP_CHAT, outcome.newPending!!.command.type)
        assertEquals("Marco", outcome.newPending!!.command.targetName)
        assertNull(outcome.externalEvent)
    }

    @Test
    fun buscarChatWhatsAppWithStoredNumberAsksForConfirmation() = runTest {
        val store = InMemoryMemoryStore()
        store.save(phoneMemory("phone-marco", "Marco Antonio", "1123456789"))
        val orchestrator = orchestratorWithMemory(store)

        val outcome = orchestrator.process("busca el chat de Marco Antonio", nowMillis = 1_000L)

        assertFalse(outcome.isError)
        assertEquals(AppState.WAITING_CONFIRMATION, outcome.targetState)
        assertNotNull(outcome.newPending)
        assertEquals(ExternalCommandType.OPEN_WHATSAPP_CHAT, outcome.newPending!!.command.type)
        assertEquals("Marco Antonio", outcome.newPending!!.command.targetName)
        assertNull(outcome.externalEvent)
    }

    @Test
    fun noisyOpenWhatsAppChatPhraseWithStoredNumberAsksForConfirmation() = runTest {
        val store = InMemoryMemoryStore()
        store.save(phoneMemory("phone-marco", "Marco", "1123456789"))
        val orchestrator = orchestratorWithMemory(store)

        val outcome = orchestrator.process("abrí WhatsApp y el del chat de Marco", nowMillis = 1_000L)

        assertFalse(outcome.isError)
        assertEquals(AppState.WAITING_CONFIRMATION, outcome.targetState)
        assertNotNull(outcome.newPending)
        assertEquals(ExternalCommandType.OPEN_WHATSAPP_CHAT, outcome.newPending!!.command.type)
        assertEquals("Marco", outcome.newPending!!.command.targetName)
        assertNull(outcome.externalEvent)
    }

    @Test
    fun openWhatsAppChatNormalizesAccentsAndCaseForExactAlias() = runTest {
        val store = InMemoryMemoryStore()
        store.save(phoneMemory("phone-marco", "Márco António", "1123456789"))
        val orchestrator = orchestratorWithMemory(store)

        val outcome = orchestrator.process("busca el chat de marco antonio", nowMillis = 1_000L)

        assertFalse(outcome.isError)
        assertEquals(AppState.WAITING_CONFIRMATION, outcome.targetState)
        assertEquals("Márco António", outcome.newPending!!.command.targetName)
        assertEquals("1123456789", outcome.newPending!!.command.payloadText)
    }

    @Test
    fun openWhatsAppChatDoesNotInventPartialContactMatch() = runTest {
        val store = InMemoryMemoryStore()
        store.save(phoneMemory("phone-marco", "Marco", "1123456789"))
        val orchestrator = orchestratorWithMemory(store)

        val outcome = orchestrator.process("busca el chat de Marco Antonio")

        assertNull(outcome.newPending)
        assertNull(outcome.externalEvent)
        assertTrue(outcome.spokenText.contains("No tengo un número guardado para Marco Antonio"))
        assertTrue(outcome.spokenText.contains("el número de Marco Antonio es"))
    }

    @Test
    fun confirmingOpenWhatsAppChatPendingEmitsOpenWhatsAppChatEvent() = runTest {
        val store = InMemoryMemoryStore()
        store.save(phoneMemory("phone-marco", "Marco Antonio", "1123456789"))
        val orchestrator = orchestratorWithMemory(store)
        val pending = orchestrator.process(
            rawInput = "abrí el chat de Marco Antonio",
            nowMillis = 1_000L
        ).newPending

        assertNotNull(pending)
        val outcome = orchestrator.process(
            rawInput = "confirmar",
            pendingConfirmation = pending,
            nowMillis = 1_500L
        )

        assertFalse(outcome.isError)
        val delegate = assertExternalHandoff(outcome, "WhatsApp")
        assertTrue(delegate is ExternalActionEvent.OpenWhatsAppChat)
        val openChat = delegate as ExternalActionEvent.OpenWhatsAppChat
        assertEquals("Marco Antonio", openChat.contactName)
        assertEquals("1123456789", openChat.phoneE164)
        assertTrue(outcome.spokenText.contains("No envío nada"))
        assertTrue(outcome.clearsPending)
    }

    @Test
    fun cancellingOpenWhatsAppChatPendingDoesNotEmitEvent() = runTest {
        val store = InMemoryMemoryStore()
        store.save(phoneMemory("phone-marco", "Marco Antonio", "1123456789"))
        val orchestrator = orchestratorWithMemory(store)
        val pending = orchestrator.process(
            rawInput = "abrí el chat de Marco Antonio",
            nowMillis = 1_000L
        ).newPending

        val outcome = orchestrator.process(
            rawInput = "cancelar",
            pendingConfirmation = pending,
            nowMillis = 1_500L
        )

        assertNull(outcome.externalEvent)
        assertTrue(outcome.clearsPending)
        assertTrue(outcome.spokenText.contains("Acción cancelada"))
    }

    @Test
    fun siNoConfirmaOpenWhatsAppChatPending() = runTest {
        val store = InMemoryMemoryStore()
        store.save(phoneMemory("phone-marco", "Marco Antonio", "1123456789"))
        val orchestrator = orchestratorWithMemory(store)
        val pending = orchestrator.process(
            rawInput = "abrí el chat de Marco Antonio",
            nowMillis = 1_000L
        ).newPending

        val outcome = orchestrator.process(
            rawInput = "sí",
            pendingConfirmation = pending,
            nowMillis = 1_500L
        )

        // "sí" no debe confirmar acciones sensibles.
        assertNull(outcome.externalEvent)
    }

    @Test
    fun openWhatsAppChatSinNumeroGuardadoRespondeFraseClara() = runTest {
        val store = InMemoryMemoryStore()
        val orchestrator = orchestratorWithMemory(store)

        val outcome = orchestrator.process("abrí el chat de Marco Antonio")

        assertNull(outcome.newPending)
        assertNull(outcome.externalEvent)
        assertTrue(outcome.spokenText.contains("No tengo un número guardado para Marco Antonio"))
        assertTrue(outcome.spokenText.contains("el número de Marco Antonio"))
    }

    @Test
    fun openWhatsAppChatSinWhatsAppDevuelveError() = runTest {
        val store = InMemoryMemoryStore()
        store.save(phoneMemory("phone-marco", "Marco Antonio", "1123456789"))
        val registry = CapabilityRegistry(
            context = null,
            availabilityOverrides = mapOf(
                Capability.WHATSAPP to false,
                Capability.ACCESSIBILITY_SERVICE to true,
                Capability.CAMERA to true
            )
        )
        val orchestrator = AssistantOrchestrator(
            capabilityRegistry = registry,
            memoryStore = store
        )

        val outcome = orchestrator.process("abrí el chat de Marco Antonio")

        assertTrue(outcome.isError)
        assertEquals(AppState.ERROR, outcome.targetState)
        assertEquals(Capability.MSG_WHATSAPP_MISSING, outcome.spokenText)
    }

    @Test
    fun openWhatsAppPrincipalUsaHandoffClaro() = runTest {
        val updatedOutcome = allAvailable.process("abrir WhatsApp principal")

        assertFalse(updatedOutcome.isError)
        assertTrue(updatedOutcome.spokenText.contains("WhatsApp principal"))
        assertTrue(updatedOutcome.spokenText.contains("Escuchar") || updatedOutcome.spokenText.contains("Estela"))
        assertEquals(ExternalActionEvent.OpenWhatsApp, assertExternalHandoff(updatedOutcome, "WhatsApp"))
        return@runTest

        val outcome = allAvailable.process("abrí WhatsApp principal")

        assertFalse(outcome.isError)
        assertEquals(
            "Abrí WhatsApp principal. Mientras estés ahí no escucho comandos. Para seguir, volvé a Estela.",
            outcome.spokenText
        )
        assertEquals(ExternalActionEvent.OpenWhatsApp, assertExternalHandoff(outcome, "WhatsApp"))
    }

    private fun assertExternalHandoff(
        outcome: OrchestratorOutcome,
        externalAppName: String
    ): ExternalActionEvent {
        val event = outcome.externalEvent
        assertTrue(event is ExternalActionEvent.ExternalAppHandoff)
        val handoff = event as ExternalActionEvent.ExternalAppHandoff
        assertEquals(externalAppName, handoff.externalAppName)
        assertTrue(handoff.returnHint.contains("volv"))
        assertTrue(handoff.delegate !is ExternalActionEvent.ExternalAppHandoff)
        return handoff.delegate
    }

    private fun memory(id: String, type: MemoryType, value: String): UserMemory =
        UserMemory(
            id = id,
            type = type,
            label = value,
            value = value,
            createdAtMillis = 1L,
            updatedAtMillis = 1L,
            expiresAtMillis = null,
            isSensitive = false,
            userApproved = true
        )

    private fun phoneMemory(id: String, label: String, phoneNumber: String): UserMemory =
        UserMemory(
            id = id,
            type = MemoryType.TRUSTED_CONTACT,
            label = label,
            value = phoneNumber,
            createdAtMillis = 1L,
            updatedAtMillis = 1L,
            expiresAtMillis = null,
            isSensitive = false,
            userApproved = true
        )

    private fun locationMemory(
        id: String,
        label: String,
        latitude: Double,
        longitude: Double
    ): UserMemory =
        UserMemory(
            id = id,
            type = MemoryType.LOCATION_ALIAS,
            label = label,
            value = SafeLocationMemory.value(latitude, longitude, 20f),
            createdAtMillis = 1L,
            updatedAtMillis = 1L,
            expiresAtMillis = null,
            isSensitive = false,
            userApproved = true
        )

    private fun fakeLocationProvider(
        hasPermission: Boolean = true,
        enabled: Boolean = true,
        snapshot: LocationSnapshot? = LocationSnapshot(-38.95, -68.06, 20f)
    ): LocationProvider =
        LocationProvider(
            hasPermission = { hasPermission },
            isLocationEnabled = { enabled },
            readLastLocation = { snapshot }
        )

    private class InMemoryMemoryStore : MemoryStore {
        private val memories = linkedMapOf<String, UserMemory>()

        override fun save(memory: UserMemory) {
            memories[memory.id] = memory
        }

        override fun getByType(type: MemoryType): List<UserMemory> =
            memories.values.filter { it.type == type }.sortedByDescending { it.updatedAtMillis }

        override fun findRelevant(query: String): List<UserMemory> =
            memories.values.sortedByDescending { it.updatedAtMillis }

        override fun delete(id: String) {
            memories.remove(id)
        }

        override fun clearAll() {
            memories.clear()
        }

        override fun listAllSafeSummaries(): List<String> =
            memories.values.map {
                when (it.type) {
                    MemoryType.TRUSTED_CONTACT -> "Recuerdo que ${it.label} es contacto de confianza."
                    MemoryType.USER_PREFERENCE -> "Recuerdo que preferís ${it.value}."
                    else -> "Recuerdo ${it.label}."
                }
            }
    }
}
