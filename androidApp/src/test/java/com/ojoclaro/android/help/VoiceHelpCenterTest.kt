package com.ojoclaro.android.help

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VoiceHelpCenterTest {

    @Test
    fun ayudaVivaSoloPrometeFuncionesReales() {
        val help = VoiceHelpCenter.spokenHelp()

        assertTrue(help.contains("WhatsApp", ignoreCase = true))
        assertTrue(help.contains("leer pantalla", ignoreCase = true))
        assertTrue(help.contains("guiarte", ignoreCase = true))
        assertTrue(help.contains("camara", ignoreCase = true))
        assertTrue(help.contains("repetir", ignoreCase = true))
        assertTrue(help.contains("cancelar", ignoreCase = true))
        assertFalse(help.contains("Spotify", ignoreCase = true))
        assertFalse(help.contains("hotword", ignoreCase = true))
        assertFalse(help.contains("llamadas automáticas", ignoreCase = true))
        assertFalse(help.contains("envío automático", ignoreCase = true))
    }

    @Test
    fun ayudaAclaraQueWhatsAppNoEnviaSolo() {
        val help = VoiceHelpCenter.spokenHelp()

        assertTrue(help.contains("no envio solo", ignoreCase = true))
    }
}
