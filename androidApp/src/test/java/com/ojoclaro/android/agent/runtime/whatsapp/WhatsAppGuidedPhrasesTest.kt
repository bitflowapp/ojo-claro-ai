package com.ojoclaro.android.agent.runtime.whatsapp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WhatsAppGuidedPhrasesTest {

    @Test
    fun recognizesAmIInWhatsApp() {
        assertEquals(
            WhatsAppGuidedCommand.AmIInWhatsApp,
            WhatsAppGuidedPhrases.classify("¿estoy en WhatsApp?")
        )
        assertEquals(
            WhatsAppGuidedCommand.AmIInWhatsApp,
            WhatsAppGuidedPhrases.classify("Este es WhatsApp")
        )
    }

    @Test
    fun recognizesWhatCanIDoHere() {
        assertEquals(
            WhatsAppGuidedCommand.WhatCanIDoHere,
            WhatsAppGuidedPhrases.classify("¿qué puedo hacer en este chat?")
        )
        assertEquals(
            WhatsAppGuidedCommand.WhatCanIDoHere,
            WhatsAppGuidedPhrases.classify("qué opciones tengo en este chat")
        )
    }

    @Test
    fun recognizesHowDoISendPhoto() {
        assertEquals(
            WhatsAppGuidedCommand.HowDoISendPhoto,
            WhatsAppGuidedPhrases.classify("¿cómo mando una foto?")
        )
        assertEquals(
            WhatsAppGuidedCommand.HowDoISendPhoto,
            WhatsAppGuidedPhrases.classify("cómo le mando una foto")
        )
        assertEquals(
            WhatsAppGuidedCommand.HowDoISendPhoto,
            WhatsAppGuidedPhrases.classify("cómo mando una imagen")
        )
    }

    @Test
    fun recognizesHowDoISendLocation() {
        assertEquals(
            WhatsAppGuidedCommand.HowDoISendLocation,
            WhatsAppGuidedPhrases.classify("¿cómo mando ubicación?")
        )
        assertEquals(
            WhatsAppGuidedCommand.HowDoISendLocation,
            WhatsAppGuidedPhrases.classify("cómo comparto ubicación")
        )
    }

    @Test
    fun recognizesHowDoISendMessage() {
        assertEquals(
            WhatsAppGuidedCommand.HowDoISendMessage,
            WhatsAppGuidedPhrases.classify("¿cómo le mando un mensaje?")
        )
        assertEquals(
            WhatsAppGuidedCommand.HowDoISendMessage,
            WhatsAppGuidedPhrases.classify("cómo escribo en WhatsApp")
                ?: WhatsAppGuidedPhrases.classify("cómo escribo acá")
        )
    }

    @Test
    fun doesNotConsumeRepeatLastCommands() {
        listOf(
            "repetí",
            "repetir",
            "que dijiste",
            "que me dijiste",
            "qué acabás de decir"
        ).forEach { phrase ->
            assertNull(
                WhatsAppGuidedPhrases.classify(phrase),
                "REPEAT_LAST phrase '$phrase' should never be classified as WhatsApp guided command"
            )
        }
    }

    @Test
    fun doesNotConsumeScreenUnderstandingCommands() {
        listOf(
            "qué hay en pantalla",
            "resumí la pantalla",
            "dónde estoy",
            "qué puedo hacer acá",
            "leeme lo importante",
            "qué dice la pantalla"
        ).forEach { phrase ->
            assertNull(
                WhatsAppGuidedPhrases.classify(phrase),
                "Screen understanding phrase '$phrase' should not be classified as WhatsApp guided"
            )
        }
    }

    @Test
    fun doesNotConsumeWhatsAppActionCommands() {
        // El flujo seguro de WhatsApp (orchestrator) procesa estos. El guided
        // workflow NO debe interceptarlos — los pasamos al flujo existente.
        listOf(
            "abrí WhatsApp",
            "mandale un mensaje a Sofi",
            "abrí el chat de Sofi",
            "llamá a Sofi"
        ).forEach { phrase ->
            assertNull(
                WhatsAppGuidedPhrases.classify(phrase),
                "WhatsApp action '$phrase' must NOT be intercepted by guided workflow"
            )
        }
    }

    @Test
    fun doesNotConsumeStopHelpAffirmativeNoise() {
        listOf(
            "callar",
            "callate",
            "sí",
            "ok",
            "dale",
            "ayuda",
            "qué puedo hacer"
        ).forEach { phrase ->
            assertNull(WhatsAppGuidedPhrases.classify(phrase))
        }
    }

    @Test
    fun blankInputReturnsNull() {
        assertNull(WhatsAppGuidedPhrases.classify(""))
        assertNull(WhatsAppGuidedPhrases.classify("   "))
    }

    @Test
    fun caseAndAccentInsensitive() {
        assertEquals(
            WhatsAppGuidedCommand.HowDoISendPhoto,
            WhatsAppGuidedPhrases.classify("COMO MANDO UNA FOTO")
        )
        assertEquals(
            WhatsAppGuidedCommand.HowDoISendPhoto,
            WhatsAppGuidedPhrases.classify("como mando una foto")
        )
    }
}
