package com.ojoclaro.android.voice

import android.speech.RecognizerIntent
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AndroidSpeechInputEngineTest {

    @Test
    fun prefersOnDeviceRecognizerWhenAvailable() {
        val selection = chooseSpeechEngine(
            onDeviceAvailable = true,
            defaultAvailable = true,
            preferOnDevice = true
        )

        assertEquals(VoiceSpeechEngine.ON_DEVICE, selection.speechEngine)
        assertTrue(selection.preferOffline)
    }

    @Test
    fun fallsBackToPlatformRecognizerWhenOnDeviceIsUnavailable() {
        val selection = chooseSpeechEngine(
            onDeviceAvailable = false,
            defaultAvailable = true,
            preferOnDevice = true
        )

        assertEquals(VoiceSpeechEngine.PLATFORM_DEFAULT, selection.speechEngine)
        assertFalse(selection.preferOffline)
    }

    @Test
    fun reportsUnavailableWhenNoRecognizerExists() {
        val selection = chooseSpeechEngine(
            onDeviceAvailable = false,
            defaultAvailable = false,
            preferOnDevice = true
        )

        assertEquals(VoiceSpeechEngine.UNAVAILABLE, selection.speechEngine)
        assertFalse(selection.preferOffline)
    }

    @Test
    fun recognitionIntentConfigEnablesSpanishPartialResultsAndOfflinePreference() {
        val config = buildSpeechRecognitionIntentConfig(
            locale = Locale("es", "AR"),
            mode = SpeechListeningMode.DEFAULT,
            preferOffline = true,
            callingPackage = "com.ojoclaro.android"
        )

        assertEquals(RecognizerIntent.ACTION_RECOGNIZE_SPEECH, config.action)
        assertEquals(RecognizerIntent.LANGUAGE_MODEL_FREE_FORM, config.languageModel)
        assertEquals("es-AR", config.languageTag)
        assertFalse(config.onlyReturnLanguagePreference)
        assertTrue(config.partialResults)
        assertEquals(3, config.maxResults)
        assertTrue(config.preferOffline)
        assertEquals("com.ojoclaro.android", config.callingPackage)
        assertTrue(config.minimumLengthMillis >= 5_000L)
        assertTrue(config.completeSilenceMillis in 1_000L..2_000L)
    }

    @Test
    fun expectingResponseIntentConfigAllowsLongerListening() {
        val normal = buildSpeechRecognitionIntentConfig(
            locale = Locale("es", "AR"),
            mode = SpeechListeningMode.DEFAULT,
            preferOffline = false,
            callingPackage = ""
        )
        val expecting = buildSpeechRecognitionIntentConfig(
            locale = Locale("es", "AR"),
            mode = SpeechListeningMode.EXPECTING_RESPONSE,
            preferOffline = false,
            callingPackage = ""
        )

        assertNull(normal.callingPackage)
        assertTrue(expecting.minimumLengthMillis > normal.minimumLengthMillis)
        assertTrue(expecting.completeSilenceMillis > normal.completeSilenceMillis)
        assertTrue(expecting.possiblyCompleteSilenceMillis > normal.possiblyCompleteSilenceMillis)
    }
}
