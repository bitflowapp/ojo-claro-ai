package com.ojoclaro.android.domain

import com.ojoclaro.android.agent.AgentIntent
import com.ojoclaro.android.agent.AgentState
import com.ojoclaro.android.external.ExternalActionEvent
import com.ojoclaro.android.global.GlobalAssistantCapability
import com.ojoclaro.android.global.GlobalAssistantCapabilityGate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgentExecutionPolicyTest {
    private val policy = AgentExecutionPolicy()

    @Test
    fun unknownDoesNotExecuteExternalApp() {
        val decision = policy.decideWhatsAppGuidedStart(
            input(
                intent = AgentIntent.UNKNOWN,
                capability = continuationReady()
            )
        )

        assertFalse(decision is AgentDecision.ExecuteExternalAction)
        assertTrue(decision is AgentDecision.RetryListening)
    }

    @Test
    fun fallbackTextForbidsExternalExecutionAfterNoEntendi() {
        assertTrue(policy.forbidsExternalExecutionAfterFallback("No entendi. Proba de nuevo."))
        assertTrue(policy.forbidsExternalExecutionAfterFallback("No escuche bien. Proba de nuevo."))
        assertTrue(policy.forbidsExternalExecutionAfterFallback("No pude conectar."))
    }

    @Test
    fun abrirWpSinContinuationRealEntraAGuidedMode() {
        val decision = policy.decideWhatsAppGuidedStart(
            input(
                intent = AgentIntent.OPEN_WHATSAPP,
                capability = GlobalAssistantCapabilityGate.unavailable("mic_unavailable")
            )
        )

        assertTrue(decision is AgentDecision.AskQuestion)
        assertEquals(AgentState.WAITING_WHATSAPP_ACTION, decision.targetState)
        assertTrue(decision.spokenText.contains("WhatsApp principal"))
    }

    @Test
    fun abrirWpConContinuationRealPuedeAbrirWhatsAppConGlobalMode() {
        val decision = policy.decideWhatsAppGuidedStart(
            input(
                intent = AgentIntent.OPEN_WHATSAPP,
                capability = continuationReady()
            )
        )

        assertTrue(decision is AgentDecision.ExecuteExternalAction)
        val handoff = decision.externalEvent as ExternalActionEvent.ExternalAppHandoff
        assertEquals("WhatsApp", handoff.externalAppName)
        assertEquals(ExternalActionEvent.OpenWhatsApp, handoff.delegate)
    }

    @Test
    fun appPrincipalPuedeAbrirConFallbackVisible() {
        val decision = policy.decidePrincipalAppOpen(
            externalAppName = "WhatsApp",
            spokenText = "Abro WhatsApp principal.",
            delegate = ExternalActionEvent.OpenWhatsApp,
            capability = fallbackOnly()
        )

        assertTrue(decision is AgentDecision.ExecuteExternalAction)
        assertEquals("EXECUTE_EXTERNAL_WITH_FALLBACK_RETURN", decision.debugLabel)
    }

    @Test
    fun appPrincipalSinFallbackNoAbreAppExterna() {
        val decision = policy.decidePrincipalAppOpen(
            externalAppName = "WhatsApp",
            spokenText = "Abro WhatsApp principal.",
            delegate = ExternalActionEvent.OpenWhatsApp,
            capability = GlobalAssistantCapabilityGate.unavailable("no_return")
        )

        assertFalse(decision is AgentDecision.ExecuteExternalAction)
        assertTrue(decision is AgentDecision.StayInApp)
    }

    @Test
    fun siYDaleNoSonConfirmacionesDePolitica() {
        val noPending = input(
            originalText = "si",
            normalizedText = "si",
            intent = AgentIntent.CONFIRM,
            hasPendingAction = false,
            capability = continuationReady()
        )

        assertFalse(noPending.hasPendingAction)
        assertEquals(AgentIntent.CONFIRM, noPending.detectedIntent)
    }

    private fun input(
        originalText: String = "abri wp",
        normalizedText: String = "abrir whatsapp",
        intent: AgentIntent,
        hasPendingAction: Boolean = false,
        capability: GlobalAssistantCapability
    ): AgentExecutionInput =
        AgentExecutionInput(
            originalText = originalText,
            normalizedText = normalizedText,
            detectedIntent = intent,
            currentState = AgentState.IDLE,
            hasPendingAction = hasPendingAction,
            externalContinuation = capability
        )

    private fun continuationReady(): GlobalAssistantCapability =
        GlobalAssistantCapabilityGate.fromFlags(
            foregroundServiceReady = true,
            notificationReady = true,
            overlayReady = true,
            microphoneContinuationReady = true,
            fallbackReturnReady = true
        )

    private fun fallbackOnly(): GlobalAssistantCapability =
        GlobalAssistantCapabilityGate.fromFlags(
            foregroundServiceReady = true,
            notificationReady = true,
            overlayReady = false,
            microphoneContinuationReady = false,
            fallbackReturnReady = true
        )
}
