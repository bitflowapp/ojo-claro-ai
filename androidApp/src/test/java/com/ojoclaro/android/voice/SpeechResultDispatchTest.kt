package com.ojoclaro.android.voice

import android.speech.SpeechRecognizer
import kotlin.test.Test
import kotlin.test.assertEquals

class SpeechResultDispatchTest {

    @Test
    fun dispatchesBestFinalSpeechResult() {
        val listener = RecordingListener()

        dispatchFinalSpeechResults(
            candidates = listOf("  ", "que puedo decir"),
            listener = listener
        )

        assertEquals(listOf("que puedo decir"), listener.finalTexts)
        assertEquals(emptyList<Int?>(), listener.errors)
    }

    @Test
    fun prefersWhatsappCommandCandidateOverGenericFirstCandidate() {
        val selected = chooseBestSpeechResult(
            listOf("abrir una app", "abri wp", "whatsapp")
        )

        assertEquals("abri wp", selected)
    }

    @Test
    fun prefersWhatsappCommandOverSimilarNonCommandCandidate() {
        val selected = chooseBestSpeechResult(
            listOf("abri wasabi", "abri WhatsApp")
        )

        assertEquals("abri WhatsApp", selected)
    }

    @Test
    fun prefersLocalUtilityCommandCandidate() {
        val selected = chooseBestSpeechResult(
            listOf("leer algo", "leer la pantalla")
        )

        assertEquals("leer la pantalla", selected)
    }

    @Test
    fun prefersOutdoorCommandCandidateOverGarbledFirstCandidate() {
        // Caso físico real (Moto G15): el SR devolvió primero una variante
        // que no parseaba y la frase útil quedó como candidato secundario.
        val selected = chooseBestSpeechResult(
            listOf("primero que tengo", "describi lo que tengo enfrente")
        )

        assertEquals("describi lo que tengo enfrente", selected)
    }

    @Test
    fun tieBetweenCommandCandidatesPrefersRecognizerOrder() {
        // Caso físico real (Moto G15 13:07): "describir lo que tengo enfrente"
        // (candidato 1, máxima confianza del SR) y "de escribir lo que tengo
        // enfrente" (candidato 3) empataban en score y ganaba el último.
        val selected = chooseBestSpeechResult(
            listOf(
                "describir lo que tengo enfrente",
                "describir lo que tengo frente",
                "de escribir lo que tengo enfrente"
            )
        )

        assertEquals("describir lo que tengo enfrente", selected)
    }

    @Test
    fun prefersNavigationCandidateOverGenericText() {
        val selected = chooseBestSpeechResult(
            listOf("llevame algo", "llevame a la plaza san martin")
        )

        assertEquals("llevame a la plaza san martin", selected)
    }

    @Test
    fun emptyFinalSpeechResultsBecomeNoMatchError() {
        val listener = RecordingListener()

        dispatchFinalSpeechResults(
            candidates = emptyList(),
            listener = listener
        )

        assertEquals(emptyList<String>(), listener.finalTexts)
        assertEquals(listOf<Int?>(SpeechRecognizer.ERROR_NO_MATCH), listener.errors)
    }

    private class RecordingListener : SpeechInputEngine.Listener {
        val finalTexts = mutableListOf<String>()
        val errors = mutableListOf<Int?>()

        override fun onReady() = Unit

        override fun onPartialText(text: String) = Unit

        override fun onFinalText(text: String) {
            finalTexts += text
        }

        override fun onError(errorCode: Int?) {
            errors += errorCode
        }
    }
}
