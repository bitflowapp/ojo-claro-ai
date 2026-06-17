package com.ojoclaro.android.agent.runtime.whatsapp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VisibleScreenCommandParserTest {

    @Test
    fun detectaAbrirChatVisibleYExtraeNombre() {
        val command = VisibleScreenCommandParser.parse(
            "Estela, ahora abrí el chat que ves en pantalla de Marco Antonio"
        )

        assertTrue(command is VisibleScreenCommand.OpenVisibleChat)
        assertEquals("Marco Antonio", command.targetName)
    }

    @Test
    fun detectaTocarNombreVisible() {
        val command = VisibleScreenCommandParser.parse("tocá Marco Antonio")

        assertTrue(command is VisibleScreenCommand.OpenVisibleChat)
        assertEquals("Marco Antonio", command.targetName)
    }

    @Test
    fun rechazaAccionesSensiblesComoTarget() {
        assertNull(VisibleScreenCommandParser.parse("tocá enviar"))
        assertNull(VisibleScreenCommandParser.parse("abrí el chat de llamar"))
        assertNull(VisibleScreenCommandParser.parse("tocá borrar"))
    }
}
