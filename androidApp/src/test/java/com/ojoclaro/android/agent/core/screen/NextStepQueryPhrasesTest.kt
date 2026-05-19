package com.ojoclaro.android.agent.core.screen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NextStepQueryPhrasesTest {

    @Test
    fun recognizesWhatNowVariants() {
        listOf(
            "qué hago ahora",
            "que hago ahora",
            "Qué hago acá",
            "qué toco",
            "qué tengo que tocar",
            "que tengo que apretar",
            "Cómo sigo?",
            "como continuo"
        ).forEach { phrase ->
            assertEquals(
                NextStepQueryKind.WHAT_NOW,
                NextStepQueryPhrases.classify(phrase),
                "Should classify '$phrase' as WHAT_NOW"
            )
        }
    }

    @Test
    fun recognizesWhereButtonVariants() {
        listOf(
            "dónde está el botón",
            "donde queda el boton",
            "dónde toco",
            "donde aprieto",
            "Dónde toco para continuar?",
            "donde toco para seguir"
        ).forEach { phrase ->
            assertEquals(
                NextStepQueryKind.WHERE_IS_BUTTON,
                NextStepQueryPhrases.classify(phrase),
                "Should classify '$phrase' as WHERE_IS_BUTTON"
            )
        }
    }

    @Test
    fun recognizesHelpWithScreenVariants() {
        listOf(
            "ayudame con esta pantalla",
            "Ayudame con la pantalla",
            "explicame esta pantalla",
            "guiame con esta pantalla",
            "ayuda con esta pantalla"
        ).forEach { phrase ->
            assertEquals(
                NextStepQueryKind.HELP_WITH_SCREEN,
                NextStepQueryPhrases.classify(phrase),
                "Should classify '$phrase' as HELP_WITH_SCREEN"
            )
        }
    }

    @Test
    fun doesNotConflictWithExistingScreenQueryPhrases() {
        // Estas frases pertenecen a ScreenQueryPhrases (legacy). El advisor
        // NO debe consumirlas — el legacy se queda con ellas.
        listOf(
            "donde estoy",
            "que app es esta",
            "que puedo hacer aca",
            "que puedo hacer ahora",
            "que opciones tengo",
            "que botones hay",
            "que hay para tocar",
            "resumi la pantalla",
            "que hay en pantalla",
            "leeme lo importante"
        ).forEach { phrase ->
            assertNull(
                NextStepQueryPhrases.classify(phrase),
                "NextStepQueryPhrases should NOT consume legacy phrase '$phrase'"
            )
        }
    }

    @Test
    fun ignoresUnknownPhrases() {
        listOf(
            "hola robot",
            "abrí WhatsApp",
            "mandale a Sofi",
            "repetí",
            "",
            "   "
        ).forEach { phrase ->
            assertNull(
                NextStepQueryPhrases.classify(phrase),
                "Should return null for '$phrase'"
            )
        }
    }
}
