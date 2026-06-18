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

    // BUG 2 — etiquetas de foto/avatar/perfil/info/videollamada NO son filas de chat.
    @Test
    fun avatarOrProfileLabelsAreRejected() {
        listOf(
            "Foto de perfil de Sofi", "Foto de perfil", "imagen de perfil",
            "Profile photo", "avatar", "Ver perfil", "Info del contacto",
            "Información del contacto", "Foto de visualización única, abierta",
            "Videollamada", "video llamada"
        ).forEach {
            assertTrue(
                WhatsAppVisibleChatMatcher.isAvatarOrProfileLabel(it),
                "debería ser avatar/perfil: \"$it\""
            )
        }
        // el NOMBRE del contacto y la fila (nombre + preview/hora) NO son avatar.
        listOf("Sofi", "Sofi Romero", "Sofi: hola, ¿cómo estás? 14:30", "Familia").forEach {
            assertFalse(
                WhatsAppVisibleChatMatcher.isAvatarOrProfileLabel(it),
                "el nombre/fila NO debe marcarse como avatar: \"$it\""
            )
        }
    }

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
