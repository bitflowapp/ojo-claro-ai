package com.ojoclaro.android.agent.runtime.whatsapp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WhatsAppOrdinalChatParserTest {

    @Test
    fun ordinalEnPalabra() {
        assertEquals(0, WhatsAppOrdinalChatParser.parse("abrí el primer chat"))
        assertEquals(0, WhatsAppOrdinalChatParser.parse("abrime el primer chat"))
        assertEquals(1, WhatsAppOrdinalChatParser.parse("abrí el segundo chat"))
        assertEquals(1, WhatsAppOrdinalChatParser.parse("abrí la segunda conversación"))
        assertEquals(2, WhatsAppOrdinalChatParser.parse("abrí el tercer contacto"))
        assertEquals(4, WhatsAppOrdinalChatParser.parse("abrí el quinto chat"))
    }

    @Test
    fun ordinalEnNumero() {
        assertEquals(0, WhatsAppOrdinalChatParser.parse("abrí el chat número 1"))
        assertEquals(1, WhatsAppOrdinalChatParser.parse("abrí el chat 2"))
        assertEquals(2, WhatsAppOrdinalChatParser.parse("abrí chat número 3"))
    }

    @Test
    fun nuevasFormasSprintWhatsApp() {
        // "de arriba" => primero.
        assertEquals(0, WhatsAppOrdinalChatParser.parse("abrí el de arriba"))
        assertEquals(0, WhatsAppOrdinalChatParser.parse("abrí el chat de arriba"))
        // "entrar al primero".
        assertEquals(0, WhatsAppOrdinalChatParser.parse("entrar al primero"))
        // Número en palabra.
        assertEquals(1, WhatsAppOrdinalChatParser.parse("abrí el chat número dos"))
        assertEquals(2, WhatsAppOrdinalChatParser.parse("abrí el chat número tres"))
    }

    @Test
    fun rechazaComandosQueNoSonOrdinales() {
        listOf(
            "abrí WhatsApp",
            "abrí el chat de Marco",
            "qué chats ves",
            "leé los mensajes",
            "bajá",
            "abrí el chat número 99",
            "hola"
        ).forEach { phrase ->
            assertNull(
                WhatsAppOrdinalChatParser.parse(phrase),
                "'$phrase' no debería ser apertura por ordinal"
            )
        }
    }
}
