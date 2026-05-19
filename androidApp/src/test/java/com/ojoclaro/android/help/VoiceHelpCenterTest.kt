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

    @Test
    fun ayudaContextualCambiaSegunPantalla() {
        val normal = VoiceHelpCenter.contextualSpokenHelp(VoiceHelpContext.DEFAULT)
        val whatsapp = VoiceHelpCenter.contextualSpokenHelp(VoiceHelpContext.WHATSAPP)
        val confirmation = VoiceHelpCenter.contextualSpokenHelp(VoiceHelpContext.WAITING_CONFIRMATION)
        val robotOff = VoiceHelpCenter.contextualSpokenHelp(VoiceHelpContext.ROBOT_OFF)
        val voiceError = VoiceHelpCenter.contextualSpokenHelp(VoiceHelpContext.VOICE_ERROR)
        val accessibilityOff = VoiceHelpCenter.contextualSpokenHelp(VoiceHelpContext.ACCESSIBILITY_OFF)

        assertTrue(normal.contains("pantalla", ignoreCase = true))
        assertTrue(whatsapp.contains("chats", ignoreCase = true))
        assertTrue(whatsapp.contains("cancelar", ignoreCase = true))
        assertTrue(confirmation.contains("confirm", ignoreCase = true))
        assertTrue(confirmation.contains("cancelar", ignoreCase = true))
        assertTrue(robotOff.contains("encender robot", ignoreCase = true))
        assertTrue(voiceError.contains("repetir", ignoreCase = true))
        assertTrue(accessibilityOff.contains("Accesibilidad", ignoreCase = true))
    }
}
