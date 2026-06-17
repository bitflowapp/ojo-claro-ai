package com.ojoclaro.android.agent.runtime.whatsapp

import com.ojoclaro.android.agent.core.screen.ScreenQueryPhrases
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Cubre las frases naturales reales reportadas en la prueba física (Fase 2A.1):
 * el usuario dice "leeme los chat que aparecen en pantalla", "último mensaje de
 * WhatsApp", etc. y NO deben caer en "no entendí".
 */
class WhatsAppNaturalReadPhrasesTest {

    @Test
    fun listaDeChatsReconoceVariantesNaturales() {
        listOf(
            "leeme los chat",
            "leeme los chats que aparecen",
            "leeme los chat que aparecen",
            "leeme los chat que aparecen en pantalla",
            "leeme los chats que aparecen en pantalla",
            "qué contactos aparecen",
            "qué contactos hay",
            "leeme las conversaciones",
            "qué conversaciones aparecen"
        ).forEach { phrase ->
            assertTrue(
                WhatsAppChatListPhrases.isChatListCommand(phrase),
                "'$phrase' debería ser comando de lista de chats"
            )
        }
    }

    @Test
    fun listaDeChatsCanonizaAliasHablado() {
        assertTrue(WhatsAppChatListPhrases.isChatListCommand("qué contactos hay en wp"))
        assertTrue(WhatsAppChatListPhrases.isChatListCommand("qué contactos aparecen en guasap"))
    }

    @Test
    fun mensajesReconoceVariantesDentroDeChat() {
        listOf(
            "leeme esta conversación",
            "lee esta conversación",
            "qué dice esta conversación",
            "qué mensajes aparecen",
            "qué mensajes se ven",
            "leeme este WhatsApp"
        ).forEach { phrase ->
            assertEquals(
                WhatsAppMessageReadMode.ALL,
                WhatsAppMessageReadPhrases.classify(phrase),
                "'$phrase' debería leer todos los mensajes"
            )
        }
    }

    @Test
    fun mensajesReconoceUltimoConAlias() {
        listOf(
            "último WhatsApp",
            "último mensaje de WhatsApp",
            "último mensaje de wp"
        ).forEach { phrase ->
            assertEquals(
                WhatsAppMessageReadMode.LAST,
                WhatsAppMessageReadPhrases.classify(phrase),
                "'$phrase' debería leer el último mensaje"
            )
        }
    }

    @Test
    fun pantallaReconoceLecturaGenerica() {
        listOf(
            "leeme lo que aparece",
            "leeme lo que aparece en pantalla"
        ).forEach { phrase ->
            assertNotNull(
                ScreenQueryPhrases.classify(phrase),
                "'$phrase' debería ser lectura de pantalla"
            )
        }
    }

    @Test
    fun noHayCruceEntreListaYMensajes() {
        // Lista de chats NO debe clasificarse como lectura de mensajes...
        assertNull(WhatsAppMessageReadPhrases.classify("leeme los chat que aparecen en pantalla"))
        // ...y la lectura de mensajes NO debe matchear lista de chats.
        assertTrue(!WhatsAppChatListPhrases.isChatListCommand("leeme esta conversación"))
    }
}
