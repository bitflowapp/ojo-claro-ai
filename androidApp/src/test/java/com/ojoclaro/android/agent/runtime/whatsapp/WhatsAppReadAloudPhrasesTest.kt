package com.ojoclaro.android.agent.runtime.whatsapp

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WhatsAppReadAloudPhrasesTest {

    @Test
    fun reconoceLecturaGenericaConAliasYAcentos() {
        listOf(
            "lee wp",
            "leé wp",
            "leer wp",
            "leeme wp",
            "leeme WhatsApp",
            "leé WhatsApp",
            "leeme lo de WhatsApp",
            "qué dice WhatsApp",
            "qué aparece en WhatsApp",
            "lee guasap",
            "leeme el guasap",
            "qué hay en el whatsapp"
        ).forEach { phrase ->
            assertTrue(
                WhatsAppReadAloudPhrases.matches(phrase),
                "'$phrase' debería reconocerse como leer WhatsApp"
            )
        }
    }

    @Test
    fun noReconoceAccionesDeWhatsApp() {
        listOf(
            "abrí WhatsApp",
            "abrime guasap",
            "mandale a Marco por WhatsApp",
            "mandá un WhatsApp a Sofi",
            "escribile a Juan por WhatsApp",
            "llamá por WhatsApp a Ana",
            "cómo mando una foto por WhatsApp",
            "mandá mi ubicación por WhatsApp"
        ).forEach { phrase ->
            assertFalse(
                WhatsAppReadAloudPhrases.matches(phrase),
                "'$phrase' es una acción y NO debería tratarse como lectura"
            )
        }
    }

    @Test
    fun noReconoceFrasesSinWhatsApp() {
        listOf(
            "leé la pantalla",
            "qué hay en pantalla",
            "leeme los mensajes",
            "qué chats hay",
            "hola",
            "",
            "   "
        ).forEach { phrase ->
            assertFalse(
                WhatsAppReadAloudPhrases.matches(phrase),
                "'$phrase' no menciona WhatsApp y NO debería matchear lectura de WhatsApp"
            )
        }
    }
}
