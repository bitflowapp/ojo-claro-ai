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
