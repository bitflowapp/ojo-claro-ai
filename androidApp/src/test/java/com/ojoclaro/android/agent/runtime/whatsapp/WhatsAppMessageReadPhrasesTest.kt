package com.ojoclaro.android.agent.runtime.whatsapp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WhatsAppMessageReadPhrasesTest {

    @Test
    fun clasificaLecturaDeTodosLosMensajes() {
        listOf(
            "qué mensajes hay",
            "qué mensaje hay",
            "leé los mensajes",
            "leeme los mensajes",
            "leé este chat",
            "qué dice el chat"
        ).forEach { phrase ->
            assertEquals(
                WhatsAppMessageReadMode.ALL,
                WhatsAppMessageReadPhrases.classify(phrase),
                "'$phrase' debería ser ALL"
            )
        }
    }

    // BUG 1 — el sufijo de app ("de wp"/"de whatsapp") no debe romper el match.
    @Test
    fun clasificaConSufijoDeAppWp() {
        listOf(
            "leeme los mensajes de wp",
            "leé los mensajes de whatsapp",
            "qué mensajes hay en wp",
            "leé este chat de wp"
        ).forEach { phrase ->
            assertEquals(
                WhatsAppMessageReadMode.ALL,
                WhatsAppMessageReadPhrases.classify(phrase),
                "'$phrase' debería ser ALL"
            )
        }
        assertEquals(
            WhatsAppMessageReadMode.LAST,
            WhatsAppMessageReadPhrases.classify("leeme el último mensaje de wp"),
            "último de wp debería ser LAST"
        )
    }

    @Test
    fun clasificaUltimoMensaje() {
        listOf(
            "último mensaje",
            "el último mensaje",
            "leé el último mensaje",
            "qué dice el último mensaje"
        ).forEach { phrase ->
            assertEquals(
                WhatsAppMessageReadMode.LAST,
                WhatsAppMessageReadPhrases.classify(phrase),
                "'$phrase' debería ser LAST"
            )
        }
    }

    @Test
    fun noClasificaListaDeChatsNiPantallaNiAcciones() {
        listOf(
            "qué chats ves",
            "leeme los chats",
            "qué hay en pantalla",
            "abrí WhatsApp",
            "mandale a Marco",
            "enviá el mensaje",
            "abrí el primer chat",
            "hola"
        ).forEach { phrase ->
            assertNull(
                WhatsAppMessageReadPhrases.classify(phrase),
                "'$phrase' no debería ser comando de lectura de mensajes"
            )
        }
    }
}
