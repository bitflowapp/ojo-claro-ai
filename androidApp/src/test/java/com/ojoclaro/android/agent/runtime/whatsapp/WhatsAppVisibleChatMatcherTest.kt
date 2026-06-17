package com.ojoclaro.android.agent.runtime.whatsapp

import com.ojoclaro.android.agent.core.screen.ScreenElement
import com.ojoclaro.android.agent.core.screen.ScreenElementRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WhatsAppVisibleChatMatcherTest {

    private val matcher = WhatsAppVisibleChatMatcher()

    @Test
    fun normalizaAcentosYMayusculas() {
        assertEquals(
            "jose maria",
            WhatsAppVisibleChatMatcher.normalizeName("JOSÉ   María")
        )
    }

    @Test
    fun eligeCoincidenciaVisibleConTextoAlrededor() {
        val match = matcher.findBest(
            targetName = "Marco Antonio",
            elements = listOf(
                text("Sofía"),
                text("Marco Antonio · 2 mensajes"),
                text("Marcos")
            )
        )

        assertNotNull(match)
        assertEquals("Marco Antonio", match.displayName)
        assertTrue(match.score >= WhatsAppVisibleChatMatcher.MIN_CLEAR_SCORE)
    }

    @Test
    fun toleraInicialDelApellido() {
        val match = matcher.findBest(
            targetName = "Marco Antonio",
            elements = listOf(text("Marco A."))
        )

        assertNotNull(match)
    }

    @Test
    fun rechazaElementosDeAccionSensible() {
        assertTrue(WhatsAppVisibleChatMatcher.isSensitiveActionLabel("Enviar a Marco Antonio"))
        assertTrue(WhatsAppVisibleChatMatcher.isSensitiveActionLabel("Llamar a Marco Antonio"))
        assertFalse(WhatsAppVisibleChatMatcher.isSensitiveActionLabel("Marco Antonio"))

        val match = matcher.findBest(
            targetName = "Marco Antonio",
            elements = listOf(button("Enviar a Marco Antonio"))
        )

        assertNull(match)
    }

    @Test
    fun devuelveNullCuandoNoHayCoincidencia() {
        val match = matcher.findBest(
            targetName = "Marco Antonio",
            elements = listOf(text("Sofía"), text("Lucía"))
        )

        assertNull(match)
    }

    private fun text(label: String): ScreenElement =
        ScreenElement(
            label = label,
            role = ScreenElementRole.TEXT,
            isInteractive = false
        )

    private fun button(label: String): ScreenElement =
        ScreenElement(
            label = label,
            role = ScreenElementRole.BUTTON,
            isInteractive = true
        )
}
