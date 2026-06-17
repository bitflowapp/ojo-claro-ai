package com.ojoclaro.android.agent.runtime.whatsapp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * WhatsApp Anxiety Hardening — el narrador de estado. Frases CORTAS, calmas y
 * content-free. Cubre I.5 ("qué pasó" recap del último estado), I.7 (ayuda
 * contextual) e I.8 (orientación útil cuando la persona está perdida).
 */
class WhatsAppStateNarratorTest {

    private fun snapshot(
        isOpen: Boolean = false,
        isInChat: Boolean = false,
        isUnknown: Boolean = false,
        hasPendingReply: Boolean = false,
        pendingStep: Int = 0,
        hasPendingSendDraft: Boolean = false,
        recentNotificationCount: Int = 0,
        safeMode: Boolean = false,
        namesWhatsApp: Boolean = false
    ) = WhatsAppStateNarrator.Snapshot(
        isOpen, isInChat, isUnknown, hasPendingReply, pendingStep,
        hasPendingSendDraft, recentNotificationCount, safeMode, namesWhatsApp
    )

    @Test
    fun orientationInsideChat() {
        val text = WhatsAppStateNarrator.orientation(snapshot(isOpen = true, isInChat = true))
        assertTrue(text.contains("chat", ignoreCase = true), text)
    }

    @Test
    fun orientationInChatList() {
        val text = WhatsAppStateNarrator.orientation(snapshot(isOpen = true, isInChat = false))
        assertTrue(text.contains("lista de chats", ignoreCase = true), text)
    }

    @Test
    fun orientationWhenClosedAndNamed() {
        val text = WhatsAppStateNarrator.orientation(snapshot(namesWhatsApp = true))
        assertTrue(text.contains("abrí WhatsApp", ignoreCase = true), text)
    }

    @Test
    fun orientationUnknownScreenIsCautious() {
        val text = WhatsAppStateNarrator.orientation(snapshot(isUnknown = true))
        assertTrue(text.contains("no voy a tocar nada", ignoreCase = true), text)
    }

    @Test
    fun orientationWithPendingReassuresNothingSent() {
        val text = WhatsAppStateNarrator.orientation(
            snapshot(isOpen = true, isInChat = true, hasPendingReply = true, pendingStep = 2)
        )
        assertTrue(text.contains("No envié nada", ignoreCase = true), text)
        assertTrue(text.contains("mandalo", ignoreCase = true), text)
        assertTrue(text.contains("cancelar", ignoreCase = true), text)
    }

    @Test
    fun orientationIsAlwaysNonBlankGuidance() {
        // "estoy perdido" sin más contexto: igual orienta, nunca queda mudo.
        assertTrue(WhatsAppStateNarrator.orientation(snapshot()).isNotBlank())
    }

    @Test
    fun whatHappenedRecapsLastResponseAndReassures() {
        val text = WhatsAppStateNarrator.whatHappened("Abrí WhatsApp.", snapshot(isOpen = true))
        assertTrue(text.contains("Abrí WhatsApp", ignoreCase = true), text)
        assertTrue(text.contains("No toqué nada", ignoreCase = true), text)
    }

    @Test
    fun whatHappenedWithoutLastResponseStillReassures() {
        val text = WhatsAppStateNarrator.whatHappened(null, snapshot())
        assertTrue(text.isNotBlank())
        assertTrue(text.contains("No toqué nada", ignoreCase = true), text)
    }

    @Test
    fun whatHappenedWithPendingExplainsHowToProceed() {
        val text = WhatsAppStateNarrator.whatHappened(
            "Confirmo envío a este chat. ¿Lo mando ahora?",
            snapshot(isInChat = true, hasPendingReply = true, pendingStep = 2)
        )
        assertTrue(text.contains("No envié nada", ignoreCase = true), text)
        assertTrue(text.contains("mandalo", ignoreCase = true), text)
    }

    @Test
    fun contextualHelpInChatOffersChatActions() {
        val text = WhatsAppStateNarrator.contextualHelp(snapshot(isOpen = true, isInChat = true))
        assertTrue(text.contains("mensajes", ignoreCase = true), text)
        assertTrue(text.contains("cancelar", ignoreCase = true), text)
    }

    @Test
    fun contextualHelpWithPendingExplainsSendOrCancel() {
        val text = WhatsAppStateNarrator.contextualHelp(
            snapshot(isOpen = true, isInChat = true, hasPendingReply = true, pendingStep = 2)
        )
        assertTrue(text.contains("sin enviar", ignoreCase = true), text)
        assertTrue(text.contains("cancelar", ignoreCase = true), text)
    }

    @Test
    fun safeModeEnabledCopyIsCalm() {
        val text = WhatsAppStateNarrator.safeModeEnabled(hasPending = false, pendingStep = 0)
        assertTrue(text.contains("Modo seguro activado", ignoreCase = true), text)
        assertTrue(text.contains("confirmar", ignoreCase = true), text)
    }

    @Test
    fun safeModeEnabledWithPendingRestatesIt() {
        val text = WhatsAppStateNarrator.safeModeEnabled(hasPending = true, pendingStep = 2)
        assertTrue(text.contains("mensaje preparado", ignoreCase = true), text)
        assertTrue(text.contains("mandalo", ignoreCase = true), text)
    }

    @Test
    fun narratorNeverInventsAContactNameOrContent() {
        // Garantía content-free: el narrador solo trabaja con flags; ninguna de
        // sus salidas debe contener marcadores de contenido de chat.
        val samples = listOf(
            WhatsAppStateNarrator.orientation(snapshot(isOpen = true, isInChat = true, hasPendingReply = true, pendingStep = 1)),
            WhatsAppStateNarrator.contextualHelp(snapshot(isOpen = true)),
            WhatsAppStateNarrator.whatHappened(null, snapshot())
        )
        samples.forEach { assertFalse(it.contains("@"), it) }
    }

    @Test
    fun phaseReflectsConversationState() {
        assertEquals(
            WhatsAppStateNarrator.ConversationPhase.AWAITING_SEND_CONFIRMATION,
            WhatsAppStateNarrator.phaseOf(snapshot(isInChat = true, hasPendingReply = true, pendingStep = 2))
        )
        assertEquals(
            WhatsAppStateNarrator.ConversationPhase.AWAITING_SEND_CONFIRMATION,
            WhatsAppStateNarrator.phaseOf(snapshot(hasPendingSendDraft = true, pendingStep = 3))
        )
        assertEquals(
            WhatsAppStateNarrator.ConversationPhase.PREPARING_REPLY,
            WhatsAppStateNarrator.phaseOf(snapshot(hasPendingReply = true, pendingStep = 1))
        )
        assertEquals(
            WhatsAppStateNarrator.ConversationPhase.IN_CHAT,
            WhatsAppStateNarrator.phaseOf(snapshot(isOpen = true, isInChat = true))
        )
        assertEquals(
            WhatsAppStateNarrator.ConversationPhase.IN_WHATSAPP,
            WhatsAppStateNarrator.phaseOf(snapshot(isOpen = true))
        )
        assertEquals(
            WhatsAppStateNarrator.ConversationPhase.AWAY,
            WhatsAppStateNarrator.phaseOf(snapshot())
        )
    }
}
