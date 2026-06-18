package com.ojoclaro.android.voice

import com.ojoclaro.android.agent.runtime.conversation.EstelaCompanionPhrases
import com.ojoclaro.android.outdoor.OutdoorPhrases
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/**
 * V1.4 — wake words: "Hola Estela, ¿cómo estás?" jamás puede caer en
 * "no entiendo" (fallo físico real 2026-06-11).
 */
class WakeWordStripperTest {

    @Test
    fun stripsWakeWordsAndGreetings() {
        assertEquals("¿cómo estás?", WakeWordStripper.strip("Hola Estela, ¿cómo estás?"))
        assertEquals("dónde estoy", WakeWordStripper.strip("Estela, dónde estoy"))
        assertEquals("quiero ir a la plaza", WakeWordStripper.strip("Estela, quiero ir a la plaza"))
        assertEquals("estás ahí", WakeWordStripper.strip("che estela estás ahí"))
        assertEquals("cómo andás", WakeWordStripper.strip("hola cómo andás"))
        assertEquals("leé la pantalla", WakeWordStripper.strip("Ojo Claro leé la pantalla"))
    }

    @Test
    fun pureGreetingsAreNeverStrippedToNothing() {
        assertEquals("hola", WakeWordStripper.strip("hola"))
        assertEquals("Estela", WakeWordStripper.strip("Estela"))
        assertEquals("hola estela", WakeWordStripper.strip("hola estela"))
    }

    @Test
    fun midSentenceNamesAreUntouched() {
        assertEquals(
            "mandale a estela que llego tarde",
            WakeWordStripper.strip("mandale a estela que llego tarde")
        )
    }

    @Test
    fun realPhysicalPhrasesEndToEnd() {
        // Pipeline real: strip → companion / outdoor.
        assertNotNull(
            EstelaCompanionPhrases.respond(WakeWordStripper.strip("Hola Estela, ¿cómo estás?")),
            "\"Hola Estela, ¿cómo estás?\" debe tener respuesta cálida"
        )
        assertNotNull(EstelaCompanionPhrases.respond(WakeWordStripper.strip("che estela estás ahí")))
        assertNotNull(EstelaCompanionPhrases.respond(WakeWordStripper.strip("hola qué tal")))
        assertIs<OutdoorPhrases.Command.WhereAmI>(
            OutdoorPhrases.parse(WakeWordStripper.strip("Estela, dónde estoy")),
            "\"Estela, dónde estoy\" debe ir a GPS, no a charla"
        )
        assertIs<OutdoorPhrases.Command.NavigateTo>(
            OutdoorPhrases.parse(WakeWordStripper.strip("Estela, quiero ir a la plaza"))
        )
        assertIs<OutdoorPhrases.Command.WhereAmI>(
            OutdoorPhrases.parse(WakeWordStripper.strip("Estela, estoy perdido"))
        )
    }
}
