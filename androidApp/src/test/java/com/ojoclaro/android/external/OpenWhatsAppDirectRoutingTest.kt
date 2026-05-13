package com.ojoclaro.android.external

import com.ojoclaro.android.agent.AgentIntent
import com.ojoclaro.android.agent.AgentSlotName
import com.ojoclaro.android.agent.LocalIntentParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins routing del bug Samsung 2026-05-13.
 *
 * Antes: "abrí WhatsApp" / "abrir wp" / "abrí el wasap" entraban al flujo guiado
 * (LocalIntentParser devolvia OPEN_WHATSAPP con missingSlot=WHATSAPP_ACTION) y la
 * UI quedaba en "Pendiente: acción de WhatsApp" sin abrir la app.
 *
 * Ahora:
 *  - LocalIntentParser devuelve OPEN_WHATSAPP SIN missing slots para esos
 *    comandos directos.
 *  - CommandRouter los reconoce como ExternalCommandType.OPEN_WHATSAPP.
 *  - El handler de external_command los toma y abre WhatsApp via Intent.
 *  - SafeAiFallback NO se involucra porque el parser local lo resolvio.
 */
class OpenWhatsAppDirectRoutingTest {

    private val parser = LocalIntentParser()
    private val router = CommandRouter()

    @Test
    fun parserReturnsOpenWhatsAppWithoutMissingSlotForDirectCommands() {
        val phrases = listOf(
            "abrí WhatsApp",
            "Abrir WhatsApp",
            "Abre WhatsApp",
            "abrí wp",
            "abrir wp",
            "abrí el WhatsApp",
            "abrir el WhatsApp",
            "abrí el wasap",
            "abrí el guasap",
            "abrir el wpp"
        )
        for (phrase in phrases) {
            val parsed = parser.parse(phrase)
            assertEquals(AgentIntent.OPEN_WHATSAPP, parsed.intent, "intent wrong for: $phrase")
            assertTrue(
                parsed.missingSlots.isEmpty(),
                "should NOT request slot WHATSAPP_ACTION for direct command: $phrase, got ${parsed.missingSlots}"
            )
            // No debe haber missing slot que despues atrape el agent_conversation.
            assertTrue(
                AgentSlotName.WHATSAPP_ACTION !in parsed.missingSlots,
                "WHATSAPP_ACTION leaked in missing slots: $phrase"
            )
        }
    }

    @Test
    fun commandRouterRecognisesDirectOpenWhatsAppCommands() {
        val phrases = listOf(
            "abrí WhatsApp",
            "abrir WhatsApp",
            "abrí wp",
            "abrir el WhatsApp",
            "abrí el wasap"
        )
        for (phrase in phrases) {
            val parsed = router.parse(phrase)
            assertEquals(
                ExternalCommandType.OPEN_WHATSAPP,
                parsed.type,
                "router didn't recognise: $phrase, got ${parsed.type}"
            )
        }
    }

    @Test
    fun directOpenCommandDoesNotMatchComposeWhatsAppMessage() {
        // Defensa: asegurar que el flujo de redaccion (mandale a X) sigue separado
        // del flujo de "abrí WhatsApp" — el primero NO debe abrir la app sin
        // confirmacion, el segundo NO debe escribir mensajes.
        val composeRoute = router.parse("mandale a Marco que estoy yendo")
        assertEquals(ExternalCommandType.COMPOSE_WHATSAPP_MESSAGE, composeRoute.type)

        val openRoute = router.parse("abrí WhatsApp")
        assertEquals(ExternalCommandType.OPEN_WHATSAPP, openRoute.type)
    }

    @Test
    fun trailingNoiseDoesNotBreakDirectOpen() {
        // Si el SpeechRecognizer devuelve "abrí whatsapp" sin signos, debe seguir
        // funcionando igual.
        listOf("abri whatsapp", "abrir whatsapp", "abre whatsapp").forEach { phrase ->
            val parsed = parser.parse(phrase)
            assertEquals(AgentIntent.OPEN_WHATSAPP, parsed.intent, phrase)
            assertTrue(parsed.missingSlots.isEmpty(), phrase)
        }
    }
}
