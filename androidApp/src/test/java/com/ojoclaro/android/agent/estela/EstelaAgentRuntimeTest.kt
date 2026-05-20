package com.ojoclaro.android.agent.estela

import com.ojoclaro.android.external.ExternalActionEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EstelaAgentRuntimeTest {

    @Test
    fun abrirWhatsAppGeneraPlanOpenExternalApp() {
        val result = runtime().handle("abrí WhatsApp")

        assertTrue(result.handled)
        assertIs<EstelaIntent.OpenApp>(result.intent)
        assertTrue(result.plan!!.steps.any { it == EstelaPlanStep.OpenExternalApp("WhatsApp") })
        assertIs<ExternalActionEvent.ExternalAppHandoff>(result.externalAction)
    }

    @Test
    fun leerPantallaGeneraPlanReadVisibleScreen() {
        val result = runtime().handle("leé la pantalla")

        assertTrue(result.handled)
        assertEquals(EstelaIntent.ReadScreen, result.intent)
        assertTrue(result.plan!!.steps.any { it == EstelaPlanStep.ReadVisibleScreen })
        assertEquals(ExternalActionEvent.ReadVisibleScreen, result.externalAction)
    }

    @Test
    fun abrirChatVisibleExtraeMarcoAntonio() {
        val result = runtimeWithWhatsAppContext().handle(
            "ahora abrí el chat que ves en pantalla de Marco Antonio"
        )

        val intent = assertIs<EstelaIntent.OpenVisibleChat>(result.intent)
        assertEquals("Marco Antonio", intent.targetName)
        assertTrue(result.plan!!.steps.any { it == EstelaPlanStep.FindVisibleTarget("Marco Antonio") })
        assertNotNull(result.pendingConfirmation)
    }

    @Test
    fun confirmarEjecutaPendingConfirmationSiExiste() {
        val runtime = runtimeWithWhatsAppContext()
        runtime.handle("ahora abrí el chat que ves en pantalla de Marco Antonio")

        val result = runtime.handle("confirmar")

        assertTrue(result.handled)
        assertEquals(EstelaIntent.Confirm, result.intent)
        assertNull(result.context.pendingConfirmation)
        assertNull(result.context.pendingPlan)
        assertTrue(result.liveStates.contains(EstelaLiveState.Executing))
        assertTrue(result.liveStates.contains(EstelaLiveState.Completed))
    }

    @Test
    fun cancelarCancelaPendingPlan() {
        val runtime = runtimeWithWhatsAppContext()
        runtime.handle("ahora abrí el chat que ves en pantalla de Marco Antonio")

        val result = runtime.handle("cancelar")

        assertTrue(result.handled)
        assertEquals(EstelaIntent.Cancel, result.intent)
        assertNull(result.context.pendingConfirmation)
        assertNull(result.context.pendingPlan)
        assertTrue(result.context.cancelled)
        assertTrue(result.liveStates.contains(EstelaLiveState.Cancelled))
    }

    @Test
    fun comprasQuedanBloqueadasPorSafetyPolicy() {
        val result = runtime().handle("comprame café")

        assertTrue(result.handled)
        assertFalse(result.safetyDecision!!.allowed)
        assertEquals(EstelaLiveState.BlockedForSafety, result.liveStates.last())
        assertEquals(EstelaSafetyPolicy.BLOCKED_DIRECT_ACTION_TEXT, result.spokenText)
    }

    @Test
    fun llamarAPapaUsaDialerPreparadoSinLlamadaDirecta() {
        val result = runtime().handle("llamá a papá")

        val intent = assertIs<EstelaIntent.DialContact>(result.intent)
        assertEquals("papa", intent.contactName)
        assertTrue(result.plan!!.requiresConfirmation)
        assertTrue(result.plan.steps.any { it == EstelaPlanStep.OpenDialer("papa") })
        assertNull(result.externalAction)
    }

    @Test
    fun mandarMensajeGeneraPreparacionConConfirmacionSinAutoEnvio() {
        val result = runtime().handle("mandale a Sofi que llego en 10")

        val intent = assertIs<EstelaIntent.ComposeMessage>(result.intent)
        assertEquals("Sofi", intent.target)
        assertEquals("llego en 10", intent.message)
        assertTrue(result.plan!!.requiresConfirmation)
        assertTrue(result.plan.steps.any { it == EstelaPlanStep.PrepareMessage("Sofi", "llego en 10") })
        assertNull(result.externalAction)
    }

    @Test
    fun contextoConservaLastExternalAppDespuesDeAbrirWhatsApp() {
        val result = runtime().handle("abrí WhatsApp")

        assertEquals("WhatsApp", result.context.currentExternalApp)
        assertEquals("WhatsApp", result.context.lastExternalApp)
        assertNotNull(result.context.lastExternalHandoffAt)
    }

    @Test
    fun followUpUsaCurrentExternalAppWhatsAppParaInterpretarAhora() {
        val runtime = runtime()
        runtime.handle("abrí WhatsApp")

        val result = runtime.handle("ahora abrí el chat que ves en pantalla de Marco Antonio")

        assertEquals("WhatsApp", result.context.currentExternalApp)
        assertIs<EstelaIntent.OpenVisibleChat>(result.intent)
        assertTrue(result.context.pendingPlan!!.userGoal.startsWith("Abrir chat visible en WhatsApp"))
    }

    @Test
    fun siNoEntiendeCaeAlFlujoLegacy() {
        val result = runtime().handle("esto no es un comando conocido")

        assertFalse(result.handled)
        assertTrue(result.fallbackToLegacy)
        assertIs<EstelaIntent.Unknown>(result.intent)
    }

    @Test
    fun safetyPolicyExigeConfirmacionParaChatVisibleYPrepararMensaje() {
        val planner = EstelaSimplePlanner()
        val policy = EstelaSafetyPolicy()
        val context = EstelaAgentContext(currentExternalApp = "WhatsApp")
        val chat = planner.plan("ahora abrí el chat que ves en pantalla de Marco Antonio", context)
        val message = planner.plan("mandale a Sofi que llego en 10", context)

        assertTrue(policy.evaluate("x", chat.intent, chat.plan).requiresConfirmation)
        assertTrue(policy.evaluate("x", message.intent, message.plan).requiresConfirmation)
    }

    @Test
    fun safetyPolicyBloqueaComprasYPagos() {
        val policy = EstelaSafetyPolicy()

        assertFalse(policy.evaluateRawText("comprame café").allowed)
        assertFalse(policy.evaluateRawText("pagá esto").allowed)
    }

    @Test
    fun liveStatePasaPorPlanningYWaitingConfirmationOExecuting() {
        val open = runtime().handle("abrí WhatsApp")
        assertEquals(
            listOf(
                EstelaLiveState.Understanding,
                EstelaLiveState.Planning,
                EstelaLiveState.Executing,
                EstelaLiveState.Completed
            ),
            open.liveStates
        )

        val pending = runtimeWithWhatsAppContext().handle(
            "ahora abrí el chat que ves en pantalla de Marco Antonio"
        )
        assertEquals(
            listOf(
                EstelaLiveState.Understanding,
                EstelaLiveState.Planning,
                EstelaLiveState.WaitingConfirmation
            ),
            pending.liveStates
        )
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
