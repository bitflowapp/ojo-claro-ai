package com.ojoclaro.android.agent.runtime.whatsapp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WhatsAppDangerousCommandParserTest {

    @Test
    fun parsesVoiceCallOnlyWithWhatsAppMarker() {
        val i = WhatsAppDangerousCommandParser.parse("llamá a Juan por WhatsApp")
        assertTrue(i is WhatsAppDangerousIntent.Call)
        assertEquals("juan", (i as WhatsAppDangerousIntent.Call).contactQuery)
    }

    @Test
    fun plainPhoneCallNotClaimed() {
        // sin marca de WhatsApp: lo maneja la ruta telefónica, no esta.
        assertNull(WhatsAppDangerousCommandParser.parse("llamá a Juan"))
    }

    @Test
    fun parsesAudioToContact() {
        val i = WhatsAppDangerousCommandParser.parse("mandale un audio a Juan")
        assertTrue(i is WhatsAppDangerousIntent.SendAudio)
        assertEquals("juan", (i as WhatsAppDangerousIntent.SendAudio).contactQuery)
        val i2 = WhatsAppDangerousCommandParser.parse("enviale una nota de voz a Marcos")
        assertTrue(i2 is WhatsAppDangerousIntent.SendAudio)
    }

    @Test
    fun videoCallNotClaimedHere() {
        assertNull(WhatsAppDangerousCommandParser.parse("hacé una videollamada con Juan"))
        assertNull(WhatsAppDangerousCommandParser.parse("videollamada con Juan"))
    }

    @Test
    fun unrelatedAndBlankNotClaimed() {
        assertNull(WhatsAppDangerousCommandParser.parse(""))
        assertNull(WhatsAppDangerousCommandParser.parse("leeme los chats"))
        assertNull(WhatsAppDangerousCommandParser.parse("abrí WhatsApp con Juan"))
    }
}
