package com.ojoclaro.android.global

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * M3 — Contrato de CABLEADO del lifecycle del contexto de WhatsApp.
 *
 * La política y el adaptador se prueban en JVM (WhatsAppContextLifecycleTest /
 * WhatsAppContextLateEventTest). Este contrato bloquea, por inspección de fuente,
 * que el Service llame al adaptador en los puntos de lifecycle reales y —crucial—
 * que el cierre PER-TURN no borre el contexto (eso mataría la continuidad cruzada
 * dentro de un mismo chat: "de qué estábamos hablando" entre turnos).
 *
 * Sigue el patrón de los demás *ContractTest del paquete: lee el .kt y verifica
 * presencia/orden de fragmentos. Sin Android, sin device.
 */
class WhatsAppContextLifecycleWiringContractTest {

    private val service: String =
        File("src/main/java/com/ojoclaro/android/global/GlobalAssistantService.kt").readText()

    private val context: String =
        File("src/main/java/com/ojoclaro/android/agent/runtime/whatsapp/WhatsAppConversationContext.kt").readText()

    private val policy: String =
        File("src/main/java/com/ojoclaro/android/agent/runtime/whatsapp/WhatsAppContextLifecyclePolicy.kt").readText()

    @Test
    fun onDestroyClearsContextViaTeardownEvent() {
        val body = service.substringAfter("override fun onDestroy()")
            .substringBefore("private fun startContinuation")
        assertTrue(
            body.contains("WhatsAppContextLifecycle.handle(WhatsAppContextEvent.ServiceTeardown)"),
            "onDestroy debe invalidar el contexto de WhatsApp (ServiceTeardown)"
        )
    }

    @Test
    fun explicitCancelHandlerClearsContext() {
        val body = service.substringAfter("basicCommand=CANCEL handler=local_cancel")
            .substringBefore("return true")
        assertTrue(
            body.contains("WhatsAppContextLifecycle.handle(WhatsAppContextEvent.ExplicitCancel)"),
            "la cancelación explícita debe limpiar el contexto operativo"
        )
    }

    @Test
    fun modeExitClearsContext() {
        val body = service.substringAfter("isStopModeCommand(text) -> {")
            .substringBefore("stopMode()")
        assertTrue(
            body.contains("WhatsAppContextLifecycle.handle(WhatsAppContextEvent.ExplicitCancel)"),
            "salir del modo asistente debe limpiar el contexto operativo"
        )
    }

    @Test
    fun panicStopClearsContextOnlyWhenItIsACancel() {
        val body = service.substringAfter("VoiceCommandDispatcher.isStopCommand(text) -> {")
            .substringBefore("silence()")
        assertTrue(
            body.contains("if (VoiceCommandDispatcher.isBareCancelCommand(text))"),
            "el stop sólo limpia contexto si TAMBIÉN es cancelación"
        )
        assertTrue(
            body.contains("WhatsAppContextLifecycle.handle(WhatsAppContextEvent.ExplicitCancel)"),
            "«pará todo»/«frená todo» (stop+cancel) limpian el contexto"
        )
    }

    @Test
    fun recallInvalidatesStaleContextBeforeSpeaking() {
        val beforeRecall = service.substringAfter("if (isRecall) {")
            .substringBefore("speak(WhatsAppConversationContext.spokenRecall()")
        assertTrue(
            beforeRecall.contains("if (!isWhatsAppActiveContext())"),
            "el recuerdo debe chequear que WhatsApp siga siendo el contexto activo"
        )
        assertTrue(
            beforeRecall.contains("WhatsAppContextLifecycle.handle(WhatsAppContextEvent.LeftWhatsAppForeground)"),
            "si se dejó WhatsApp, invalidar ANTES de recordar (no aflorar chat viejo)"
        )
    }

    @Test
    fun forgetRoutesThroughPolicyNotRawClear() {
        val body = service.substringAfter("if (isForget) {")
            .substringBefore("return true")
        assertTrue(
            body.contains("WhatsAppContextLifecycle.handle(WhatsAppContextEvent.ExplicitForget)"),
            "olvidar debe ir por la política (ExplicitForget)"
        )
        assertFalse(
            body.contains("WhatsAppConversationContext.clear()"),
            "olvidar no debe llamar clear() crudo (debe pasar por el adaptador)"
        )
    }

    /**
     * CRÍTICO: el cierre PER-TURN NO debe limpiar el contexto operativo. Si lo
     * hiciera, se rompería la continuidad entre turnos dentro de un mismo chat
     * (la persona pregunta "¿de qué estábamos hablando?" en el turno siguiente).
     */
    @Test
    fun perTurnTeardownDoesNotClearWhatsAppContext() {
        val body = service.substringAfter("private fun completeOverlayVoiceTurn")
            .substringBefore("private fun ")
        assertFalse(
            body.contains("WhatsAppContextLifecycle"),
            "el cierre por turno NO debe tocar el lifecycle del contexto (mataría la continuidad)"
        )
        assertFalse(
            body.contains("WhatsAppConversationContext.clear"),
            "el cierre por turno NO debe limpiar el contexto de WhatsApp"
        )
    }

    @Test
    fun clearBumpsInvalidationEpochInSource() {
        val body = context.substringAfter("fun clear()").substringBefore("fun epoch()")
        assertTrue(body.contains("state = null"), "clear() debe vaciar el estado")
        assertTrue(
            body.contains("invalidationEpoch += 1L"),
            "clear() debe subir el epoch (detección de callbacks tardíos)"
        )
    }

    @Test
    fun policyClearsOnCancelHomeAndChatChange() {
        // Lock liviano del mapeo central; el comportamiento lo cubren los tests JVM.
        assertTrue(policy.contains("WhatsAppContextEvent.ExplicitCancel,"))
        assertTrue(policy.contains("WhatsAppContextEvent.LeftWhatsAppForeground,"))
        assertTrue(policy.contains("WhatsAppContextEvent.ChatChanged -> WhatsAppContextDecision.CLEAR_OPERATIONAL_CONTEXT"))
        assertTrue(policy.contains("WhatsAppContextDecision.IGNORE_STALE_EVENT"))
    }
}
