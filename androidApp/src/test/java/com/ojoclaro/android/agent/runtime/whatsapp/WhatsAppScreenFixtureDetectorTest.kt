package com.ojoclaro.android.agent.runtime.whatsapp

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Cobertura del detector dirigida por fixtures sintéticos: el fix de inChat
 * funciona sin Accesibilidad viva. Cada pantalla → veredicto esperado.
 */
class WhatsAppScreenFixtureDetectorTest {

    private val detector = WhatsAppScreenDetector()

    @Test
    fun chatOpenEmptyComposerIsInChat() {
        val s = detector.detect(WhatsAppScreenFixtures.chatOpenEmptyComposer())
        assertTrue(s.isOpen)
        assertTrue(s.isInChat, "composer vacío en .Conversation debe ser inChat")
    }

    @Test
    fun chatOpenWithDraftIsInChat() {
        val s = detector.detect(WhatsAppScreenFixtures.chatOpenWithDraft())
        assertTrue(s.isInChat, "borrador escrito no debe romper inChat")
        assertTrue(s.hasMessageField)
    }

    @Test
    fun keyboardOpenWithDraftIsInChat() {
        val s = detector.detect(WhatsAppScreenFixtures.keyboardOpenWithDraft())
        assertTrue(s.isInChat, "teclado abierto (campo + enviar) en .Conversation debe ser inChat")
    }

    @Test
    fun chatListIsNotInChat() {
        val s = detector.detect(WhatsAppScreenFixtures.chatList())
        assertTrue(s.isOpen)
        assertFalse(s.isInChat, "la lista de chats no es un chat abierto")
    }

    @Test
    fun loginVerifyIsNotInChat() {
        val s = detector.detect(WhatsAppScreenFixtures.loginVerify())
        assertFalse(s.isInChat, "login/verificación no es un chat")
    }

    @Test
    fun unknownWhatsAppScreenIsOpenButNotInChat() {
        val s = detector.detect(WhatsAppScreenFixtures.unknownWhatsAppScreen())
        assertTrue(s.isOpen, "el paquete es WhatsApp → está abierto")
        assertFalse(s.isInChat, "pantalla desconocida de WhatsApp no es un chat")
    }
}
