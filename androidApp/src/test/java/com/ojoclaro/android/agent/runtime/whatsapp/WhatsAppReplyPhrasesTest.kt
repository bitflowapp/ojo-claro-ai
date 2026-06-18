package com.ojoclaro.android.agent.runtime.whatsapp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WhatsAppReplyPhrasesTest {

    @Test
    fun extractsReplyText() {
        assertEquals("prueba de Estela", WhatsAppReplyPhrases.extractReply("respondé prueba de Estela"))
        listOf(
            "respondéle que ya voy",
            "respondele que ya voy",
            "contestale que ya voy",
            "contestále que ya voy"
        ).forEach { phrase ->
            assertEquals("ya voy", WhatsAppReplyPhrases.extractReply(phrase), "reply route: $phrase")
        }
        assertEquals("hola", WhatsAppReplyPhrases.extractReply("decile hola"))
        assertEquals("buenas tardes", WhatsAppReplyPhrases.extractReply("escribí buenas tardes"))
        assertEquals("prueba", WhatsAppReplyPhrases.extractReply("mandale prueba"))
        // "contestale QUE ya salí" => mensaje "ya salí".
        assertEquals("ya salí", WhatsAppReplyPhrases.extractReply("contestale que ya salí"))
        // Prefijo "Estela," tolerado.
        assertEquals("prueba de Estela", WhatsAppReplyPhrases.extractReply("Estela, respondé prueba de Estela"))
    }

    @Test
    fun rejectsNonReplyOrComposeWithContact() {
        // Compose con contacto = NO es respuesta al chat abierto.
        assertNull(WhatsAppReplyPhrases.extractReply("mandale a Marco que hola"))
        // Verbo sin texto.
        assertNull(WhatsAppReplyPhrases.extractReply("respondé"))
        // No son comandos de respuesta.
        assertNull(WhatsAppReplyPhrases.extractReply("leeme los chats"))
        assertNull(WhatsAppReplyPhrases.extractReply("abrí el primer chat"))
        assertNull(WhatsAppReplyPhrases.extractReply("bajá"))
        assertNull(WhatsAppReplyPhrases.extractReply("hola"))
    }

    @Test
    fun step1AcceptsSimpleYes() {
        listOf("sí", "si", "dale", "quiero", "mandalo", "confirmo").forEach {
            assertTrue(WhatsAppReplyPhrases.isConfirmStep1(it), "step1 yes: $it")
        }
        assertFalse(WhatsAppReplyPhrases.isConfirmStep1("no"))
        assertFalse(WhatsAppReplyPhrases.isConfirmStep1("cancelar"))
    }

    @Test
    fun step2RequiresStrongConfirm() {
        listOf("mandalo", "sí mandalo", "enviá", "lo mando", "confirmo").forEach {
            assertTrue(WhatsAppReplyPhrases.isStrongSendConfirm(it), "strong: $it")
        }
        // Bare yes NO alcanza para enviar de verdad.
        assertFalse(WhatsAppReplyPhrases.isStrongSendConfirm("sí"))
        assertFalse(WhatsAppReplyPhrases.isStrongSendConfirm("dale"))
    }

    @Test
    fun cancelWordsDetected() {
        listOf(
            "no", "cancelar", "cancelá", "pará", "basta", "me equivoqué",
            "me arrepentí", "no mandes", "dejalo"
        ).forEach {
            assertTrue(WhatsAppReplyPhrases.isCancel(it), "cancel: $it")
        }
        assertFalse(WhatsAppReplyPhrases.isCancel("mandalo"))
    }

    @Test
    fun extractReplyStripsFillers() {
        assertEquals("estoy llegando", WhatsAppReplyPhrases.extractReply("respondé ehh estoy llegando"))
        assertEquals("estoy llegando", WhatsAppReplyPhrases.extractReply("decile o sea estoy llegando"))
        // Solo muletillas → no hay mensaje (el caller pide la frase).
        assertNull(WhatsAppReplyPhrases.extractReply("respondé ehh"))
    }

    @Test
    fun replyAttemptDetectsVerbEvenWhenFillerOnly() {
        assertTrue(WhatsAppReplyPhrases.isReplyAttempt("respondé ehh"))
        assertTrue(WhatsAppReplyPhrases.isReplyAttempt("respondé estoy llegando"))
        assertFalse(WhatsAppReplyPhrases.isReplyAttempt("leeme los chats"))
        assertFalse(WhatsAppReplyPhrases.isReplyAttempt("mandale a Marco que hola"))
        assertFalse(WhatsAppReplyPhrases.isReplyAttempt("respondé"))
    }

    @Test
    fun noMeEquivoqueCancels() {
        assertTrue(WhatsAppReplyPhrases.isCancel("no, me equivoqué"))
        assertTrue(WhatsAppReplyPhrases.isCancel("no me equivoqué"))
    }
}
