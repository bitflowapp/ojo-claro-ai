package com.ojoclaro.android.voice

import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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
    fun engineFallbackCandidatesTryOnDeviceThenDefaultRecognizer() {
        val engines = buildSpeechRecognitionEngineCandidates(
            onDeviceAvailable = true,
            defaultAvailable = true,
            preferOnDevice = true
        )

        assertEquals(
            listOf(VoiceSpeechEngine.ON_DEVICE, VoiceSpeechEngine.PLATFORM_DEFAULT),
            engines
        )
    }

    @Test
    fun onDeviceLanguageNotSupportedFallsBackToDefaultSystemRecognizer() {
        val policy = newEngineFallbackPolicy()

        val decision = assertTryNext(
            policy.decisionAfterRecognizerError(
                errorCode = VoiceSpeechErrorPolicy.ERROR_CODE_LANGUAGE_NOT_SUPPORTED,
                hadSpeechTextInAttempt = false
            )
        )

        assertTrue(decision.engineChanged)
        assertEquals(VoiceSpeechEngine.ON_DEVICE, decision.previousAttempt.speechEngine)
        assertEquals(VoiceSpeechEngine.PLATFORM_DEFAULT, decision.nextAttempt.speechEngine)
        assertEquals("es-AR", decision.nextAttempt.languageCandidate.languageTag)
    }

    @Test
    fun onDeviceLanguageUnavailableFallsBackToDefaultSystemRecognizer() {
        val policy = newEngineFallbackPolicy()

        val decision = assertTryNext(
            policy.decisionAfterRecognizerError(
                errorCode = VoiceSpeechErrorPolicy.ERROR_CODE_LANGUAGE_UNAVAILABLE,
                hadSpeechTextInAttempt = false
            )
        )

        assertTrue(decision.engineChanged)
        assertEquals(VoiceSpeechEngine.PLATFORM_DEFAULT, decision.nextAttempt.speechEngine)
        assertEquals("es-AR", decision.nextAttempt.languageCandidate.languageTag)
    }

    @Test
    fun onDeviceRepeatedNoMatchFallsBackToDefaultSystemRecognizer() {
        val policy = newEngineFallbackPolicy()

        val first = assertTryNext(
            policy.decisionAfterRecognizerError(
                errorCode = SpeechRecognizer.ERROR_NO_MATCH,
                hadSpeechTextInAttempt = false
            )
        )
        val second = assertTryNext(
            policy.decisionAfterRecognizerError(
                errorCode = SpeechRecognizer.ERROR_NO_MATCH,
                hadSpeechTextInAttempt = false
            )
        )

        assertTrue(first.retryingCurrentAttempt)
        assertEquals(1, first.consecutiveNoMatch)
        assertTrue(second.engineChanged)
        assertEquals(VoiceSpeechEngine.PLATFORM_DEFAULT, second.nextAttempt.speechEngine)
        assertEquals("es-AR", second.nextAttempt.languageCandidate.languageTag)
    }

    @Test
    fun onDeviceNoResultWatchdogFallsBackToDefaultSystemRecognizer() {
        val policy = newEngineFallbackPolicy()

        val decision = assertTryNext(policy.decisionAfterWatchdogTimeout())

        assertTrue(decision.engineChanged)
        assertEquals(SpeechRecognitionFailureReason.WATCHDOG_TIMEOUT, decision.reason)
        assertEquals(VoiceSpeechEngine.PLATFORM_DEFAULT, decision.nextAttempt.speechEngine)
        assertEquals("es-AR", decision.nextAttempt.languageCandidate.languageTag)
    }

    @Test
    fun defaultSystemRecognizerUsesLanguageFallbackCandidates() {
        val policy = newEngineFallbackPolicy(onDeviceAvailable = false)

        val decision = assertTryNext(
            policy.decisionAfterRecognizerError(
                errorCode = VoiceSpeechErrorPolicy.ERROR_CODE_LANGUAGE_UNAVAILABLE,
                hadSpeechTextInAttempt = false
            )
        )

        assertFalse(decision.engineChanged)
        assertTrue(decision.languageChanged)
        assertEquals(VoiceSpeechEngine.PLATFORM_DEFAULT, decision.nextAttempt.speechEngine)
        assertEquals("es-419", decision.nextAttempt.languageCandidate.languageTag)
    }

    @Test
    fun engineFallbackDoesNotReturnFinalErrorUntilEnginesAndLanguagesAreExhausted() {
        val policy = newEngineFallbackPolicy()

        assertTryNext(
            policy.decisionAfterRecognizerError(
                errorCode = VoiceSpeechErrorPolicy.ERROR_CODE_LANGUAGE_NOT_SUPPORTED,
                hadSpeechTextInAttempt = false
            )
        )
        repeat(policy.allLanguageCandidates.size - 1) {
            assertTryNext(
                policy.decisionAfterRecognizerError(
                    errorCode = VoiceSpeechErrorPolicy.ERROR_CODE_LANGUAGE_UNAVAILABLE,
                    hadSpeechTextInAttempt = false
                )
            )
        }

        val exhausted = policy.decisionAfterRecognizerError(
            errorCode = VoiceSpeechErrorPolicy.ERROR_CODE_LANGUAGE_UNAVAILABLE,
            hadSpeechTextInAttempt = false
        )

        assertTrue(exhausted is SpeechRecognitionFallbackDecision.Exhausted)
        assertEquals(
            SpeechRecognitionFailureReason.LANGUAGE_UNAVAILABLE,
            (exhausted as SpeechRecognitionFallbackDecision.Exhausted).reason
        )
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
    fun languageFallbackCandidatesPreferArgentineSpanishFirst() {
        val candidates = buildSpeechRecognitionLanguageCandidates(defaultLocale = Locale("pt", "BR"))

        assertEquals("es-AR", candidates.first().languageTag)
    }

    @Test
    fun languageFallbackMovesFromEsArToLatinAmericanSpanish() {
        val policy = newLanguageFallbackPolicy()

        val next = policy.nextCandidateAfter(VoiceSpeechErrorPolicy.ERROR_CODE_LANGUAGE_NOT_SUPPORTED)

        assertEquals("es-419", next?.languageTag)
    }

    @Test
    fun languageFallbackMovesToSpainSpanishAfterLatinAmericanSpanish() {
        val policy = newLanguageFallbackPolicy()

        policy.nextCandidateAfter(VoiceSpeechErrorPolicy.ERROR_CODE_LANGUAGE_NOT_SUPPORTED)
        val next = policy.nextCandidateAfter(VoiceSpeechErrorPolicy.ERROR_CODE_LANGUAGE_NOT_SUPPORTED)

        assertEquals("es-ES", next?.languageTag)
    }

    @Test
    fun languageFallbackMovesToGenericSpanishAfterSpainSpanish() {
        val policy = newLanguageFallbackPolicy()

        repeat(3) {
            policy.nextCandidateAfter(VoiceSpeechErrorPolicy.ERROR_CODE_LANGUAGE_NOT_SUPPORTED)
        }

        assertEquals("es", policy.currentCandidate.languageTag)
    }

    @Test
    fun languageFallbackUsesDefaultLocaleAfterSpanishCandidates() {
        val policy = newLanguageFallbackPolicy(defaultLocale = Locale("pt", "BR"))

        repeat(4) {
            policy.nextCandidateAfter(VoiceSpeechErrorPolicy.ERROR_CODE_LANGUAGE_NOT_SUPPORTED)
        }

        assertEquals("pt-BR", policy.currentCandidate.languageTag)
    }

    @Test
    fun languageFallbackEndsWithDeviceDefaultWithoutForcedLanguage() {
        val policy = newLanguageFallbackPolicy(defaultLocale = Locale("pt", "BR"))

        repeat(5) {
            policy.nextCandidateAfter(VoiceSpeechErrorPolicy.ERROR_CODE_LANGUAGE_NOT_SUPPORTED)
        }
        val config = buildSpeechRecognitionIntentConfig(
            candidate = policy.currentCandidate,
            mode = SpeechListeningMode.DEFAULT,
            preferOffline = false,
            callingPackage = "com.ojoclaro.android"
        )

        assertNull(config.languageTag)
        assertEquals(SpeechRecognitionLanguageCandidate.DeviceDefault, policy.currentCandidate)
    }

    @Test
    fun languageFallbackDoesNotExposeFinalErrorUntilCandidatesAreExhausted() {
        val policy = newLanguageFallbackPolicy(defaultLocale = Locale("pt", "BR"))
        val fallbackCount = policy.allCandidates.size - 1

        repeat(fallbackCount) {
            assertNotNull(policy.nextCandidateAfter(VoiceSpeechErrorPolicy.ERROR_CODE_LANGUAGE_NOT_SUPPORTED))
        }

        assertNull(policy.nextCandidateAfter(VoiceSpeechErrorPolicy.ERROR_CODE_LANGUAGE_NOT_SUPPORTED))
    }

    @Test
    fun languageFallbackDoesNotTrustNullEmptyOrIncompleteSupportedLanguageLists() {
        val expected = listOf("es-AR", "es-419", "es-ES", "es", "pt-BR", null)

        assertEquals(
            expected,
            buildSpeechRecognitionLanguageCandidates(
                defaultLocale = Locale("pt", "BR"),
                supportedLanguages = null
            ).languageTags()
        )
        assertEquals(
            expected,
            buildSpeechRecognitionLanguageCandidates(
                defaultLocale = Locale("pt", "BR"),
                supportedLanguages = emptyList()
            ).languageTags()
        )
        assertEquals(
            expected,
            buildSpeechRecognitionLanguageCandidates(
                defaultLocale = Locale("pt", "BR"),
                supportedLanguages = listOf("en-US")
            ).languageTags()
        )
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

    private fun newLanguageFallbackPolicy(
        defaultLocale: Locale = Locale("pt", "BR")
    ): SpeechLanguageFallbackPolicy =
        SpeechLanguageFallbackPolicy(
            buildSpeechRecognitionLanguageCandidates(defaultLocale = defaultLocale)
        )

    private fun newEngineFallbackPolicy(
        onDeviceAvailable: Boolean = true,
        defaultAvailable: Boolean = true,
        defaultLocale: Locale = Locale("pt", "BR")
    ): SpeechRecognitionEngineFallbackPolicy =
        SpeechRecognitionEngineFallbackPolicy(
            engineCandidates = buildSpeechRecognitionEngineCandidates(
                onDeviceAvailable = onDeviceAvailable,
                defaultAvailable = defaultAvailable,
                preferOnDevice = true
            ),
            languageCandidates = buildSpeechRecognitionLanguageCandidates(defaultLocale = defaultLocale)
        )

    private fun assertTryNext(
        decision: SpeechRecognitionFallbackDecision
    ): SpeechRecognitionFallbackDecision.TryNext {
        assertTrue(decision is SpeechRecognitionFallbackDecision.TryNext)
        return decision as SpeechRecognitionFallbackDecision.TryNext
    }

    private fun List<SpeechRecognitionLanguageCandidate>.languageTags(): List<String?> =
        map { it.languageTag }
}
