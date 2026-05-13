package com.ojoclaro.android.voice

import android.speech.SpeechRecognizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VoiceListeningSessionTest {

    @Test
    fun storesSafePartialCandidate() {
        val session = newSession().recordPartial(" abrir WhatsApp ")

        assertEquals("abrir WhatsApp", session.lastPartialTextRedacted)
        assertEquals("abrir WhatsApp", session.bestPartialCandidate)
        assertFalse(session.partialWasRedacted)
    }

    @Test
    fun redactsSensitivePartialAndDoesNotKeepCandidate() {
        val session = newSession().recordPartial("mi clave es 1234")

        assertEquals(VoiceListeningSession.REDACTED_TEXT, session.lastPartialTextRedacted)
        assertEquals("", session.bestPartialCandidate)
        assertTrue(session.partialWasRedacted)
    }

    @Test
    fun usesPartialWhenFinalIsEmpty() {
        val decision = newSession()
            .recordPartial("qué hay en pantalla")
            .textForSubmission("")

        assertEquals("qué hay en pantalla", decision.text)
        assertTrue(decision.usedPartial)
    }

    @Test
    fun prefersCleanPartialOverNoisyFinalForSameLowRiskCommand() {
        val decision = newSession()
            .recordPartial("abrir WhatsApp")
            .recordFinal("abrir ure Max")
            .textForSubmission("abrir ure Max")

        assertEquals("abrir WhatsApp", decision.text)
        assertTrue(decision.usedPartial)
    }

    @Test
    fun clearGlobalFinalCommandWinsOverEarlierPartial() {
        val decision = newSession()
            .recordPartial("abrir WhatsApp")
            .recordFinal("resetear")
            .textForSubmission("resetear")

        assertEquals("resetear", decision.text)
        assertFalse(decision.usedPartial)
    }

    @Test
    fun doesNotUsePartialForSensitiveActions() {
        val session = newSession()
            .recordPartial("mandale mensaje a Marco")

        assertEquals("", session.bestPartialCandidate)
        assertFalse(isSafePartialCandidate("mandale mensaje a Marco"))
    }

    @Test
    fun finalSensitiveTextIsRedactedInSessionButCanStillBeSubmittedByCaller() {
        val session = newSession().recordFinal("mi banco muestra CBU 123")
        val decision = session.textForSubmission("mi banco muestra CBU 123")

        assertEquals(VoiceListeningSession.REDACTED_TEXT, session.finalText)
        assertTrue(session.finalWasRedacted)
        assertEquals("mi banco muestra CBU 123", decision.text)
        assertFalse(decision.usedPartial)
    }

    @Test
    fun countsNoMatchAndTimeoutsSeparately() {
        val first = newSession().recordError(
            errorCode = SpeechRecognizer.ERROR_NO_MATCH,
            retryCount = 1,
            shouldAutoRestart = true
        )
        val second = first.recordError(
            errorCode = SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
            retryCount = 2,
            shouldAutoRestart = true
        )

        assertEquals(1, first.consecutiveNoMatch)
        assertEquals(0, first.consecutiveTimeouts)
        assertEquals(1, second.consecutiveNoMatch)
        assertEquals(1, second.consecutiveTimeouts)
    }

    @Test
    fun errorPolicyMapsRecognizerCodes() {
        assertEquals(SpeechErrorCategory.NO_MATCH, VoiceSpeechErrorPolicy.categoryFor(SpeechRecognizer.ERROR_NO_MATCH))
        assertEquals(SpeechErrorCategory.SPEECH_TIMEOUT, VoiceSpeechErrorPolicy.categoryFor(SpeechRecognizer.ERROR_SPEECH_TIMEOUT))
        assertEquals(SpeechErrorCategory.RECOGNIZER_BUSY, VoiceSpeechErrorPolicy.categoryFor(SpeechRecognizer.ERROR_RECOGNIZER_BUSY))
        assertEquals(SpeechErrorCategory.NETWORK, VoiceSpeechErrorPolicy.categoryFor(SpeechRecognizer.ERROR_NETWORK))
        assertEquals(SpeechErrorCategory.CLIENT, VoiceSpeechErrorPolicy.categoryFor(SpeechRecognizer.ERROR_CLIENT))
        assertEquals(
            SpeechErrorCategory.INSUFFICIENT_PERMISSIONS,
            VoiceSpeechErrorPolicy.categoryFor(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
        )
    }

    @Test
    fun timeoutCopyDiffersFromNoMatchCopy() {
        val noMatch = VoiceSpeechErrorPolicy.humanMessageFor(SpeechErrorCategory.NO_MATCH)
        val timeout = VoiceSpeechErrorPolicy.humanMessageFor(SpeechErrorCategory.SPEECH_TIMEOUT)

        assertFalse(noMatch == timeout)
        assertTrue(timeout.contains("después del tono", ignoreCase = true))
    }

    private fun newSession(): VoiceListeningSession =
        VoiceListeningSession(
            sessionId = 1L,
            startedAt = 1_000L,
            wasSpeakingWhenStarted = false,
            wasRobotEnabled = true
        )
}
