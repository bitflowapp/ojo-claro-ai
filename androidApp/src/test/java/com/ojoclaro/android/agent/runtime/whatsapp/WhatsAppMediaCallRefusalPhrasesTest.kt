package com.ojoclaro.android.agent.runtime.whatsapp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** #4/#5 — clasificación de videollamada / audio / llamada "peladas". */
class WhatsAppMediaCallRefusalPhrasesTest {

    private fun k(p: String) = WhatsAppMediaCallRefusalPhrases.classify(p)

    @Test
    fun classifiesVideoAudioAndVoiceCall() {
        // BUG 1: TODAS las variantes de videollamada (alineado con WhatsAppCallPhrases).
        listOf(
            "hacé videollamada", "hacer videollamada", "videollamada", "videollamar",
            "quiero hacer una videollamada", "llamada de video", "llamada con video",
            "llamar por video", "llamalo por video"
        ).forEach {
            assertEquals(
                WhatsAppMediaCallRefusalPhrases.Kind.VIDEO_CALL, k(it),
                "videollamada no clasificada: \"$it\""
            )
        }
        assertEquals(WhatsAppMediaCallRefusalPhrases.Kind.AUDIO, k("mandale un audio"))
        assertEquals(WhatsAppMediaCallRefusalPhrases.Kind.AUDIO, k("grabá un audio"))
        assertEquals(WhatsAppMediaCallRefusalPhrases.Kind.AUDIO, k("mandá una nota de voz"))
        assertEquals(WhatsAppMediaCallRefusalPhrases.Kind.VOICE_CALL, k("llamá por whatsapp"))
    }

    @Test
    fun coversEveryWhatsAppCallVideoMarker() {
        // garantía dura: nada que WhatsAppCallPhrases reconozca como videollamada
        // puede escaparse del refusal (si no, caería al taskIntent viejo y armaría tap).
        listOf(
            "videollamada", "video llamada", "videollamar", "llamada de video",
            "llamalo por video", "llamala por video", "llamar por video", "llamada con video"
        ).forEach {
            assertNotNull(WhatsAppCallPhrases.parseVideoCall(it), "marker no parseado: \"$it\"")
            assertEquals(
                WhatsAppMediaCallRefusalPhrases.Kind.VIDEO_CALL, k(it),
                "marker de WhatsAppCallPhrases no cubierto por el refusal: \"$it\""
            )
        }
    }

    @Test
    fun doesNotClaimNonMediaUtterances() {
        assertNull(k("mandale eso"))
        assertNull(k("estoy llegando"))
        assertNull(k("leeme los mensajes"))
        assertNull(k("qué significa factura A"))
    }

    @Test
    fun refusalsAreSpecificAndPiiFree() {
        assertTrue(
            WhatsAppMediaCallRefusalPhrases.refusal(WhatsAppMediaCallRefusalPhrases.Kind.VIDEO_CALL)
                .contains("videollamada")
        )
        assertTrue(
            WhatsAppMediaCallRefusalPhrases.refusal(WhatsAppMediaCallRefusalPhrases.Kind.AUDIO)
                .contains("audio")
        )
        assertTrue(
            WhatsAppMediaCallRefusalPhrases.refusal(WhatsAppMediaCallRefusalPhrases.Kind.VOICE_CALL)
                .contains("llamada")
        )
        WhatsAppMediaCallRefusalPhrases.Kind.values().forEach {
            assertTrue(
                WhatsAppMediaCallRefusalPhrases.refusal(it).contains("Por seguridad"),
                "refusal must be a clear safety refusal: $it"
            )
        }
    }
}
