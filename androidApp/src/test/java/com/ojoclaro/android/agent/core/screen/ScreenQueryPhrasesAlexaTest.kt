package com.ojoclaro.android.agent.core.screen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Hardening Alexa-like — grupo C "leer pantalla". Garantiza que TODAS las
 * variantes de la spec resuelven a un modo de lectura local (nunca null →
 * fallback). Robusto a tildes/mayúsculas/puntuación.
 */
class ScreenQueryPhrasesAlexaTest {

    @Test
    fun specReadScreenVariantsClassify() {
        listOf(
            "leer pantalla",
            "leeme la pantalla",
            "qué dice la pantalla",
            "qué hay en pantalla",
            "qué puedo tocar",
            "leeme esto",
            "leé esto",
            "leeme lo que dice",
            "describime la pantalla",
            "¿Qué dice la pantalla?",
            "LEEME LA PANTALLA"
        ).forEach { phrase ->
            assertNotNull(
                ScreenQueryPhrases.classify(phrase),
                "Debería clasificar como lectura de pantalla: \"$phrase\""
            )
        }
    }

    @Test
    fun leemeEstoMapsToShortSummary() {
        assertEquals(ScreenSummaryMode.SHORT, ScreenQueryPhrases.classify("leeme esto"))
    }

    @Test
    fun unrelatedPhrasesDoNotClassify() {
        // No debe robar frases que tienen otra ruta local dedicada. (Nota:
        // "dónde estoy" SÍ existe en este clasificador como WHERE_AM_I, pero en
        // el routing real outdoor lo intercepta antes como GPS, así que no se
        // incluye acá.)
        listOf(
            "abrir whatsapp",
            "describir entorno",
            "llamar a emergencias",
            "repetir"
        ).forEach { phrase ->
            assertNull(
                ScreenQueryPhrases.classify(phrase),
                "No debería clasificar como pantalla: \"$phrase\""
            )
        }
    }
}
