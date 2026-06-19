package com.ojoclaro.android.agent.runtime.whatsapp

import com.ojoclaro.android.agent.core.screen.ScreenContextProvider
import com.ojoclaro.android.agent.core.screen.ScreenElement
import com.ojoclaro.android.agent.core.screen.ScreenElementRole
import com.ojoclaro.android.agent.core.screen.ScreenSnapshot
import com.ojoclaro.android.agent.payments.PaymentGuidePhrases
import com.ojoclaro.android.agent.runtime.conversation.SafeLlmFallbackPolicy
import com.ojoclaro.android.agent.runtime.conversation.SafeLlmPhrases
import com.ojoclaro.android.agent.runtime.conversation.SafeLlmRoute
import com.ojoclaro.android.agent.runtime.conversation.SafeLlmSignals
import com.ojoclaro.android.voice.VoiceCommandDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * FASE 5 — Carreras y CONTEXTO VIEJO del flujo WhatsApp guiado.
 *
 * Cada evento tardío / cambio de pantalla debe: descartarse o fallar seguro, no
 * hablar contenido viejo, no preparar borrador, no conservar destinatario, no
 * ejecutar acción y no dejar estado residual.
 *
 * Usa los componentes REALES: [WhatsAppConversationContext] (clear/overwrite/
 * redacción), [WhatsAppVisibleChatMatcher] (stateless: re-resuelve contra la
 * pantalla actual) y [WhatsAppGuidedWorkflowUseCase] (overlay/no-WA → seguro).
 * El "ignorar callback tardío" se materializa como: tras cancelar/home/perder
 * foreground el contexto queda vacío, así un callback tardío no tiene nada viejo
 * que usar.
 */
class WhatsAppGuidedFlowRaceTest {

    @BeforeTest fun setUp() = WhatsAppConversationContext.clear()

    @AfterTest fun tearDown() = WhatsAppConversationContext.clear()

    private fun dangerous(p: String): Boolean =
        WhatsAppCriticalGuard.isCritical(p) ||
            WhatsAppForbiddenCommandParser.parse(p) != null ||
            WhatsAppMediaCallRefusalPhrases.classify(p) != null ||
            WhatsAppDangerousCommandParser.parse(p) != null ||
            PaymentGuidePhrases.classify(p) == PaymentGuidePhrases.Kind.SENSITIVE_BLOCK

    private fun route(p: String): SafeLlmRoute = SafeLlmFallbackPolicy.decide(
        SafeLlmSignals(
            conversational = false, whatsAppActive = true, namesWhatsApp = true,
            looksDangerous = dangerous(p), looksLikeMessageContent = false,
            looksLikeReplyHelp = SafeLlmPhrases.isReplyHelp(p),
            wantsChatContent = SafeLlmPhrases.wantsChatContent(p),
            looksLikeSafeQuestion = SafeLlmPhrases.isSafeQuestion(p)
        )
    )

    private val matcher = WhatsAppVisibleChatMatcher()

    private fun chatListElements(vararg names: String): List<ScreenElement> =
        names.map { ScreenElement(it, ScreenElementRole.BUTTON, isInteractive = true) }

    @Test fun r1_cancel_thenLateChatSelectedCallback_noOldContext() {
        WhatsAppConversationContext.noteDestination("Ana Prueba", null, 1L)
        assertTrue(VoiceCommandDispatcher.isBareCancelCommand("cancelá"))
        WhatsAppConversationContext.clear() // cancelar limpia
        // callback tardío de "chat seleccionado": al chequear el contexto, no hay nada.
        assertNull(WhatsAppConversationContext.current(), "no debe quedar destinatario viejo")
    }

    @Test fun r2_openChatB_afterA_lateReadAUsesCurrentNotStaleA() {
        WhatsAppConversationContext.noteDestination("Ana Prueba", null, 1L)
        WhatsAppConversationContext.noteDestination("José Demo", null, 2L) // abrió B
        // un callback tardío de lectura de A consulta el contexto y obtiene B, no A.
        assertEquals("José Demo", WhatsAppConversationContext.current()?.chatLabelRedacted)
        assertFalse(WhatsAppConversationContext.spokenRecall().contains("Ana Prueba"), "no debe recordar A")
    }

    @Test fun r3_draftPrep_thenHome_lateContinueHasNoPending() {
        WhatsAppConversationContext.notePendingAction("send_message", 1L)
        WhatsAppConversationContext.noteDraftLen(9, 1L)
        WhatsAppConversationContext.clear() // volver al home
        assertNull(WhatsAppConversationContext.current(), "callback de continuación no encuentra pending")
    }

    @Test fun r4_readMessages_thenForegroundLost_lateResultHasNoContext() {
        WhatsAppConversationContext.noteMessageCount(4, 1L)
        WhatsAppConversationContext.clear() // WhatsApp dejó foreground
        assertNull(WhatsAppConversationContext.current())
        assertTrue(WhatsAppConversationContext.spokenRecall().contains("No tengo contexto", ignoreCase = true))
    }

    @Test fun r5_overlayOrNonWhatsApp_doesNotConfuseAsConversation() {
        val overlay = ScreenSnapshot(
            packageName = "com.example.notes",
            text = "Recordatorio: comprar pan",
            elements = listOf(ScreenElement("Comprar pan", ScreenElementRole.TEXT, isInteractive = false)),
            capturedAtMillis = 0L
        )
        val useCase = WhatsAppGuidedWorkflowUseCase(
            provider = ScreenContextProvider { overlay },
            isAccessibilityReady = { true }
        )
        val r = useCase.handle("qué puedo hacer en este chat")
        assertTrue(r is WhatsAppGuidedResponse.NotInWhatsApp, "overlay/no-WA no es un chat")
        assertFalse((r as WhatsAppGuidedResponse.NotInWhatsApp).spokenText.contains("comprar pan", ignoreCase = true))
    }

    @Test fun r6_help_withStalePendingInContext_isNotContaminated() {
        WhatsAppConversationContext.notePendingAction("send_message", 1L)
        // "ayuda" se reconoce como ayuda con independencia del pending viejo.
        assertTrue(VoiceCommandDispatcher.isHelpCommand("ayuda"))
        // y el recall reafirma que no envía nada por su cuenta.
        assertTrue(WhatsAppConversationContext.spokenRecall().contains("No voy a enviar nada", ignoreCase = true))
    }

    @Test fun r7_ordinalSelection_listChanges_resolvesAgainstCurrentListOnly() {
        // El parser sólo da un índice; el matcher es STATELESS: re-resuelve contra
        // la lista ACTUAL. Un ordinal viejo nunca toca un chat de otra lista.
        assertEquals(0, WhatsAppOrdinalChatParser.parse("abrí el primer chat"))
        val listA = chatListElements("Ana Prueba", "José Demo")
        val listB = chatListElements("Luz Test", "CONTACTO_B")
        assertTrue(matcher.findBest("Ana Prueba", listA) != null, "Ana está en A")
        assertNull(matcher.findBest("Ana Prueba", listB), "Ana NO está en B: no reutiliza posición vieja")
    }

    @Test fun r8_cancel_thenLateDangerousFinal_alwaysBlocked() {
        WhatsAppConversationContext.clear()
        // Un final peligroso tardío SIEMPRE se bloquea local, sin importar el timing.
        listOf("transferile plata", "tocá enviar", "mandá audio", "borrá el chat").forEach { p ->
            assertEquals(SafeLlmRoute.BLOCK_DANGEROUS, route(p), "tardío peligroso bloqueado: \"$p\"")
        }
    }

    @Test fun r9_switchChat_clearsThenReNotes_noResidualFromPrevious() {
        WhatsAppConversationContext.noteDestination("Ana Prueba", "2991234567", 1L)
        WhatsAppConversationContext.clear()
        WhatsAppConversationContext.noteDestination("Luz Test", null, 2L)
        val snap = WhatsAppConversationContext.current()
        assertEquals("Luz Test", snap?.chatLabelRedacted)
        assertNull(snap?.phoneEnding, "no debe quedar el teléfono del chat anterior")
    }
}
