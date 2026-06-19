package com.ojoclaro.android.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regresión FASE 1 (accesibilidad) — la "Acción sugerida" que ve/escucha una
 * persona no vidente en LastActionCard NO debe ser el nombre técnico del enum
 * (p. ej. "READ_VISIBLE_SCREEN"). [userActionLabel] lo traduce a algo hablable.
 */
class AgentIntentLabelTest {

    @Test
    fun everyIntentHasAHumanLabelWithoutTechnicalToken() {
        for (intent in AgentIntent.values()) {
            val label = intent.userActionLabel()
            assertTrue(label.isNotBlank(), "intent ${intent.name} no tiene etiqueta")
            assertFalse(
                label.contains("_"),
                "intent ${intent.name} expone guion bajo técnico: '$label'"
            )
            assertFalse(
                label.equals(intent.name, ignoreCase = true),
                "intent ${intent.name} no fue traducido a lenguaje humano"
            )
            // El token crudo en mayúsculas no debe aparecer dentro de la etiqueta.
            assertFalse(
                label.contains(intent.name, ignoreCase = false),
                "intent ${intent.name} aparece crudo en '$label'"
            )
        }
    }

    @Test
    fun keyIntentsReadNaturally() {
        assertEquals("Leer la pantalla", AgentIntent.READ_VISIBLE_SCREEN.userActionLabel())
        assertEquals("Abrir WhatsApp", AgentIntent.OPEN_WHATSAPP.userActionLabel())
        assertEquals("Abrir un chat de WhatsApp", AgentIntent.OPEN_WHATSAPP_CHAT.userActionLabel())
        assertEquals("Pedir ayuda", AgentIntent.HELP.userActionLabel())
        assertEquals("Cancelar", AgentIntent.CANCEL.userActionLabel())
    }

    @Test
    fun unknownIntentDoesNotExposeRawTokenAndStaysReassuring() {
        val label = AgentIntent.UNKNOWN.userActionLabel()
        assertFalse(label.contains("UNKNOWN"))
        assertFalse(label.contains("_"))
        assertTrue(label.isNotBlank())
    }
}
