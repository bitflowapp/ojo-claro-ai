package com.ojoclaro.android.agent.runtime.whatsapp

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WhatsAppChatListPhrasesTest {

    @Test
    fun recognizesAllSpecPhrases() {
        listOf(
            "qué chats ves",
            "qué chat ves",
            "leeme los chats",
            "qué conversaciones aparecen",
            "qué contactos aparecen en WhatsApp"
        ).forEach { phrase ->
            assertTrue(
                WhatsAppChatListPhrases.isChatListCommand(phrase),
                "spec phrase '$phrase' should be recognized"
            )
        }
    }

    @Test
    fun isCaseAndAccentInsensitive() {
        assertTrue(WhatsAppChatListPhrases.isChatListCommand("¿QUÉ CHATS VES?"))
        assertTrue(WhatsAppChatListPhrases.isChatListCommand("que chats ves"))
        assertTrue(WhatsAppChatListPhrases.isChatListCommand("Qué Chat Ves"))
    }

    @Test
    fun blankIsNotAChatListCommand() {
        assertFalse(WhatsAppChatListPhrases.isChatListCommand(""))
        assertFalse(WhatsAppChatListPhrases.isChatListCommand("   "))
    }

    @Test
    fun doesNotConsumeRepeatLast() {
        listOf(
            "repetí",
            "repetir",
            "que dijiste",
            "qué acabás de decir"
        ).forEach { phrase ->
            assertFalse(
                WhatsAppChatListPhrases.isChatListCommand(phrase),
                "REPEAT_LAST phrase '$phrase' must not match chat list"
            )
        }
    }

    @Test
    fun doesNotConsumeScreenUnderstandingCommands() {
        listOf(
            "qué hay en pantalla",
            "resumí la pantalla",
            "dónde estoy",
            "qué puedo hacer acá",
            "leeme lo importante"
        ).forEach { phrase ->
            assertFalse(
                WhatsAppChatListPhrases.isChatListCommand(phrase),
                "Screen Understanding phrase '$phrase' must not match chat list"
            )
        }
    }

    @Test
    fun doesNotConsumeWhatsAppGuidedPhrases() {
        listOf(
            "estoy en WhatsApp",
            "qué puedo hacer en este chat",
            "cómo mando una foto",
            "cómo mando ubicación",
            "cómo le mando un mensaje"
        ).forEach { phrase ->
            assertFalse(
                WhatsAppChatListPhrases.isChatListCommand(phrase),
                "Guided phrase '$phrase' must not match chat list"
            )
        }
    }

    @Test
    fun doesNotConsumeLegacyWhatsAppActions() {
        listOf(
            "abrí WhatsApp",
            "mandale a Marco",
            "mandale un mensaje a Marco",
            "abrí el chat de Marco",
            "llamá a Marco"
        ).forEach { phrase ->
            assertFalse(
                WhatsAppChatListPhrases.isChatListCommand(phrase),
                "legacy WhatsApp phrase '$phrase' must not match chat list"
            )
        }
    }

    @Test
    fun doesNotConsumeControlPhrases() {
        listOf(
            "callate",
            "callar",
            "cancelar",
            "confirmar",
            "ayuda",
            "qué puedo hacer",
            "qué puedo decir"
        ).forEach { phrase ->
            assertFalse(
                WhatsAppChatListPhrases.isChatListCommand(phrase),
                "control phrase '$phrase' must not match chat list"
            )
        }
    }
}
