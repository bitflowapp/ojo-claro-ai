package com.ojoclaro.android.voice

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * WhatsApp Fluency — "esperá" (HOLD) y "te cortaste / no me escuchaste"
 * (REENGAGE). Read-only y disjuntos de sí/mandalo/cancelar.
 */
class VoiceFluencyPhrasesTest {

    @Test
    fun holdVariantsMatch() {
        listOf("esperá", "esperá esperá", "esperame", "dame un segundo", "dame un momento",
            "un momento", "un segundo", "aguantame", "pera un toque", "momentito")
            .forEach { assertTrue(VoiceFluencyPhrases.isHold(it), "hold: $it") }
    }

    @Test
    fun reengageVariantsMatch() {
        listOf("te cortaste", "se cortó", "no me escuchaste", "no me oíste", "estás ahí",
            "me escuchás", "no te escuché", "seguís ahí")
            .forEach { assertTrue(VoiceFluencyPhrases.isReengage(it), "reengage: $it") }
    }

    @Test
    fun fluencyNeverStealsAConfirmation() {
        listOf("sí", "dale", "ok", "mandalo", "enviá", "confirmo enviar",
            "no", "cancelar", "no mandes", "me equivoqué", "pará", "repetí", "mandalo ahora")
            .forEach {
                assertFalse(VoiceFluencyPhrases.isHold(it), "hold stole: $it")
                assertFalse(VoiceFluencyPhrases.isReengage(it), "reengage stole: $it")
            }
    }

    @Test
    fun holdAndReengageAreDisjoint() {
        listOf("esperá", "dame un segundo", "un momento").forEach {
            assertFalse(VoiceFluencyPhrases.isReengage(it), "hold leaked into reengage: $it")
        }
        listOf("te cortaste", "no me escuchaste").forEach {
            assertFalse(VoiceFluencyPhrases.isHold(it), "reengage leaked into hold: $it")
        }
    }
}
