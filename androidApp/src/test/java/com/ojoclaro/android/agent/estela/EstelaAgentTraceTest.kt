package com.ojoclaro.android.agent.estela

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EstelaAgentTraceTest {

    @Test
    fun abrirWhatsAppGeneraTraceCompletoHastaActionDispatched() {
        val result = runtime().handle("abri WhatsApp")

        assertEquals(
            listOf(
                EstelaAgentTraceEvent.CommandReceived,
                EstelaAgentTraceEvent.IntentDetected,
                EstelaAgentTraceEvent.PlanCreated,
                EstelaAgentTraceEvent.SafetyEvaluated,
                EstelaAgentTraceEvent.ActionDispatched,
                EstelaAgentTraceEvent.ContextUpdated,
                EstelaAgentTraceEvent.Completed
            ),
            result.trace.events
        )
        assertEquals("OpenApp(WhatsApp)", result.trace.detectedIntent)
        assertEquals(EstelaRiskLevel.LOW, result.trace.riskLevel)
        assertEquals("ExternalAppHandoff(WhatsApp -> OpenWhatsApp)", result.trace.externalAction)
    }

    @Test
    fun compraBloqueadaGeneraBlockedForSafety() {
        val result = runtime().handle("comprame cafe")

        assertTrue(result.handled)
        assertTrue(result.trace.events.contains(EstelaAgentTraceEvent.BlockedForSafety))
        assertEquals("direct_sensitive_action", result.trace.blockedReason)
        assertNull(result.externalAction)
    }

    @Test
    fun abrirChatAhoraUsaContextoWhatsAppSiExiste() {
        val runtime = runtime()
        runtime.handle("abri WhatsApp")

        val result = runtime.handle("ahora abri el chat que ves en pantalla de Marco Antonio")

        assertEquals("WhatsApp", result.context.currentExternalApp)
        assertTrue(result.context.lastPlanSummary!!.contains("FindVisibleTarget(Marco Antonio)"))
        assertEquals("OpenVisibleChat(Marco Antonio)", result.trace.detectedIntent)
        assertTrue(result.plan!!.userGoal.startsWith("Abrir chat visible en WhatsApp"))
    }

    @Test
    fun cancelarLimpiaPendingPlanYPendingConfirmation() {
        val runtime = runtimeWithWhatsAppContext()
        runtime.handle("ahora abri el chat que ves en pantalla de Marco Antonio")

        val result = runtime.handle("cancelar")

        assertNull(result.context.pendingPlan)
        assertNull(result.context.pendingConfirmation)
        assertTrue(result.trace.events.contains(EstelaAgentTraceEvent.Cancelled))
    }

    @Test
    fun confirmarSinPendingNoEjecutaNadaPeligroso() {
        val result = runtime().handle("confirmar")

        assertTrue(result.handled)
        assertNull(result.externalAction)
        assertTrue(result.trace.events.contains(EstelaAgentTraceEvent.Error))
        assertEquals(EstelaLiveState.ErrorRecoverable, result.trace.liveStateTransitions.last())
    }

    @Test
    fun sessionMemoryConservaLastExternalAppDespuesDeAbrirWhatsApp() {
        val runtime = runtime()
        runtime.handle("abri WhatsApp")

        val snapshot = runtime.contextSnapshot()

        assertEquals("WhatsApp", snapshot.currentExternalApp)
        assertEquals("WhatsApp", snapshot.lastExternalApp)
        assertEquals("OpenApp(WhatsApp)", snapshot.lastIntent)
        assertNotNull(snapshot.lastSuccessfulActionAt)
    }

    @Test
    fun fallbackToLegacyQuedaMarcadoCuandoNoEntiende() {
        val result = runtime().handle("esto no matchea el runtime")

        assertFalse(result.handled)
        assertTrue(result.trace.fallbackToLegacy)
        assertTrue(result.trace.events.contains(EstelaAgentTraceEvent.FallbackToLegacy))
    }

    @Test
    fun traceNoGuardaTextoSensibleInnecesario() {
        val result = runtime().handle("mandale a Sofi que mi clave es 1234")
        val trace = result.trace

        assertFalse(trace.rawUserText.contains("1234"))
        assertFalse(trace.rawUserText.contains("clave", ignoreCase = true))
        assertFalse(trace.normalizedUserText.contains("1234"))
        assertFalse(trace.planSummary.orEmpty().contains("1234"))
        assertEquals("ComposeMessage(target=Sofi)", trace.detectedIntent)
        assertTrue(trace.events.contains(EstelaAgentTraceEvent.BlockedForSafety))
    }

    @Test
    fun safetyDecisionApareceEnTrace() {
        val result = runtime().handle("abri WhatsApp")

        assertNotNull(result.trace.safetyDecision)
        assertTrue(result.trace.safetyDecision!!.allowed)
    }

    @Test
    fun liveStateTransitionsSeRegistranEnOrdenLogico() {
        val result = runtime().handle("abri WhatsApp")

        assertEquals(
            listOf(
                EstelaLiveState.Understanding,
                EstelaLiveState.Planning,
                EstelaLiveState.Executing,
                EstelaLiveState.Completed
            ),
            result.trace.liveStateTransitions
        )
    }

    @Test
    fun simulationModeDevuelveIntentPlanSafetyYAction() {
        val simulator = EstelaAgentSimulator(runtime())

        val result = simulator.simulate("abri WhatsApp")

        assertEquals("OpenApp(WhatsApp)", result.trace.detectedIntent)
        assertTrue(result.trace.planSummary!!.contains("OpenExternalApp(WhatsApp)"))
        assertTrue(result.trace.safetyDecision!!.allowed)
        assertEquals("ExternalAppHandoff(WhatsApp -> OpenWhatsApp)", result.trace.externalAction)
    }

    private fun runtime(): EstelaAgentRuntime =
        EstelaAgentRuntime(clockMillis = { 1234L })

    private fun runtimeWithWhatsAppContext(): EstelaAgentRuntime =
        runtime().also {
            it.replaceContext(
                EstelaAgentContext(
                    currentExternalApp = "WhatsApp",
                    lastExternalApp = "WhatsApp",
                    followUpMode = EstelaFollowUpMode.EXTERNAL_APP
                )
            )
        }
}
