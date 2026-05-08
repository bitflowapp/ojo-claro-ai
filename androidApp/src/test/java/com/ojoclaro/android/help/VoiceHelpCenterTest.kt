package com.ojoclaro.android.help

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VoiceHelpCenterTest {

    @Test
    fun ayudaVivaSoloPrometeFuncionesReales() {
        val help = VoiceHelpCenter.spokenHelp()

        assertTrue(help.contains("WhatsApp", ignoreCase = true))
        assertTrue(help.contains("confirmación", ignoreCase = true))
        assertTrue(help.contains("Teléfono", ignoreCase = true))
        assertTrue(help.contains("marcador seguro", ignoreCase = true))
        assertTrue(help.contains("cámara", ignoreCase = true))
        assertTrue(help.contains("repetir", ignoreCase = true))
        assertTrue(help.contains("cancelar", ignoreCase = true))
        assertTrue(help.contains("IA flexible", ignoreCase = true))
        assertFalse(help.contains("Spotify", ignoreCase = true))
        assertFalse(help.contains("hotword", ignoreCase = true))
        assertFalse(help.contains("llamadas automáticas", ignoreCase = true))
        assertFalse(help.contains("envío automático", ignoreCase = true))
    }

    @Test
    fun ayudaAclaraQueWhatsAppNoEnviaSolo() {
        val help = VoiceHelpCenter.spokenHelp()

        assertTrue(help.contains("no los envío solo", ignoreCase = true))
    }
}
