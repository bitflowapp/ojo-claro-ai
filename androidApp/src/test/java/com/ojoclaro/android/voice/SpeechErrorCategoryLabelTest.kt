package com.ojoclaro.android.voice

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regresión FASE 1 (accesibilidad / copy) — el aviso de voz del Home NO debe
 * mostrar el token técnico de la categoría de error (p. ej. "NO_MATCH",
 * "SPEECH_TIMEOUT"). La misión prohíbe explícitamente exponer "NO_MATCH" a la
 * persona usuaria. [humanLabel] y [voiceErrorCategoryHumanLabel] lo traducen.
 */
class SpeechErrorCategoryLabelTest {

    @Test
    fun everyCategoryHasHumanLabelWithoutTechnicalToken() {
        for (category in SpeechErrorCategory.values()) {
            val label = category.humanLabel()
            assertTrue(label.isNotBlank(), "categoría ${category.name} sin etiqueta")
            assertFalse(label.contains("_"), "categoría ${category.name} expone guion bajo: '$label'")
            assertFalse(
                label.contains(category.name, ignoreCase = false),
                "categoría ${category.name} aparece cruda en '$label'"
            )
        }
    }

    @Test
    fun tokenIsHumanizedAndNeverEchoesForbiddenJargon() {
        // El token más frecuente para una persona no vidente: no la escuché.
        val noMatch = voiceErrorCategoryHumanLabel("NO_MATCH")
        assertEquals(SpeechErrorCategory.NO_MATCH.humanLabel(), noMatch)
        assertFalse(noMatch.contains("NO_MATCH"))
        assertFalse(noMatch.contains("_"))

        // Tokens que la misión marca como jerga prohibida en copy de usuario.
        for (forbidden in listOf("NO_MATCH", "SPEECH_TIMEOUT", "RECOGNIZER_BUSY", "SERVICE_DISCONNECTED")) {
            val human = voiceErrorCategoryHumanLabel(forbidden)
            assertFalse(human.contains(forbidden), "se filtró '$forbidden' en '$human'")
            assertFalse(human.contains("_"), "se filtró guion bajo en '$human'")
        }
    }

    @Test
    fun tokenLookupIsCaseInsensitive() {
        assertEquals(
            SpeechErrorCategory.NETWORK.humanLabel(),
            voiceErrorCategoryHumanLabel("network")
        )
    }

    @Test
    fun unknownOrGarbageTokenFallsBackToSafeGenericNotTheRawToken() {
        val garbage = "algo-raro-12345"
        val human = voiceErrorCategoryHumanLabel(garbage)
        assertEquals(SpeechErrorCategory.UNKNOWN.humanLabel(), human)
        assertFalse(human.contains(garbage))
        assertTrue(human.isNotBlank())
    }
}
