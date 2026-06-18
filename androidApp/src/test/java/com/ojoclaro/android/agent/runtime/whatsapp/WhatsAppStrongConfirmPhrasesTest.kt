package com.ojoclaro.android.agent.runtime.whatsapp

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Frases fuertes exactas: "sí"/"dale" nunca confirman, y cada acción tiene su
 * propia frase (confirmar un envío no confirma una llamada).
 */
class WhatsAppStrongConfirmPhrasesTest {

    @Test
    fun strongSendPhrasesMatch() {
        listOf("mandalo ahora", "enviar ahora", "envialo ahora", "manda ahora", "mandalo ya")
            .forEach { assertTrue(WhatsAppStrongConfirmPhrases.isStrongSend(it), "send: $it") }
    }

    @Test
    fun strongCallAndVideoAndAudioPhrasesMatch() {
        listOf("llamar ahora", "llamalo ahora", "llama ahora")
            .forEach { assertTrue(WhatsAppStrongConfirmPhrases.isStrongCall(it), "call: $it") }
        listOf("videollamar ahora", "video llamar ahora")
            .forEach { assertTrue(WhatsAppStrongConfirmPhrases.isStrongVideoCall(it), "video: $it") }
        listOf("mandar audio ahora", "enviar audio ahora")
            .forEach { assertTrue(WhatsAppStrongConfirmPhrases.isStrongAudioSend(it), "audio: $it") }
        assertTrue(WhatsAppStrongConfirmPhrases.isStartRecording("empezar grabación"))
        assertTrue(WhatsAppStrongConfirmPhrases.isStopRecording("terminar grabación"))
    }

    @Test
    fun weakAffirmativesNeverStrong() {
        listOf("si", "sí", "dale", "ok", "okey", "bueno", "ajá", "claro", "dale dale")
            .forEach {
                assertTrue(WhatsAppStrongConfirmPhrases.isWeak(it), "weak: $it")
                assertFalse(WhatsAppStrongConfirmPhrases.isStrongSend(it), "send not from weak: $it")
                assertFalse(WhatsAppStrongConfirmPhrases.isStrongCall(it), "call not from weak: $it")
            }
        assertFalse(WhatsAppStrongConfirmPhrases.isWeak("mandalo ahora"))
    }

    @Test
    fun phrasesAreActionSpecific() {
        assertFalse(WhatsAppStrongConfirmPhrases.isStrongCall("mandalo ahora"))
        assertFalse(WhatsAppStrongConfirmPhrases.isStrongSend("llamar ahora"))
        assertFalse(WhatsAppStrongConfirmPhrases.isStrongVideoCall("llamar ahora"))
        assertTrue(WhatsAppStrongConfirmPhrases.isStrongConfirmFor(WhatsAppActionType.CALL, "llamar ahora"))
        assertFalse(WhatsAppStrongConfirmPhrases.isStrongConfirmFor(WhatsAppActionType.SEND_MESSAGE, "llamar ahora"))
        assertTrue(WhatsAppStrongConfirmPhrases.isStrongConfirmFor(WhatsAppActionType.VIDEO_CALL, "videollamar ahora"))
        // forbidden / non-confirmable action types never match
        assertFalse(WhatsAppStrongConfirmPhrases.isStrongConfirmFor(WhatsAppActionType.DELETE_CHAT, "mandalo ahora"))
    }
}
