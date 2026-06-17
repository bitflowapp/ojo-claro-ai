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

    @Test
    fun errorPolicyMapsApi31PlusCodes() {
        assertEquals(
            SpeechErrorCategory.TOO_MANY_REQUESTS,
            VoiceSpeechErrorPolicy.categoryFor(VoiceSpeechErrorPolicy.ERROR_CODE_TOO_MANY_REQUESTS)
        )
        assertEquals(
            SpeechErrorCategory.SERVICE_DISCONNECTED,
            VoiceSpeechErrorPolicy.categoryFor(VoiceSpeechErrorPolicy.ERROR_CODE_SERVER_DISCONNECTED)
        )
        assertEquals(
            SpeechErrorCategory.LANGUAGE_UNAVAILABLE,
            VoiceSpeechErrorPolicy.categoryFor(VoiceSpeechErrorPolicy.ERROR_CODE_LANGUAGE_NOT_SUPPORTED)
        )
        assertEquals(
            SpeechErrorCategory.LANGUAGE_UNAVAILABLE,
            VoiceSpeechErrorPolicy.categoryFor(VoiceSpeechErrorPolicy.ERROR_CODE_LANGUAGE_UNAVAILABLE)
        )
        assertEquals(
            SpeechErrorCategory.SERVICE_UNAVAILABLE,
            VoiceSpeechErrorPolicy.categoryFor(VoiceSpeechErrorPolicy.ERROR_CODE_CANNOT_CHECK_SUPPORT)
        )
        assertEquals(
            SpeechErrorCategory.SERVICE_UNAVAILABLE,
            VoiceSpeechErrorPolicy.categoryFor(
                VoiceSpeechErrorPolicy.ERROR_CODE_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS
            )
        )
    }

    @Test
    fun unmappedErrorCodeStillFallsThroughToUnknown() {
        assertEquals(SpeechErrorCategory.UNKNOWN, VoiceSpeechErrorPolicy.categoryFor(null))
        assertEquals(SpeechErrorCategory.UNKNOWN, VoiceSpeechErrorPolicy.categoryFor(99))
    }

    @Test
    fun api31PlusCategoriesProduceDedicatedHumanMessages() {
        val unknownMessage = VoiceSpeechErrorPolicy.humanMessageFor(SpeechErrorCategory.UNKNOWN)
        val tooMany = VoiceSpeechErrorPolicy.humanMessageFor(SpeechErrorCategory.TOO_MANY_REQUESTS)
        val disconnected = VoiceSpeechErrorPolicy.humanMessageFor(SpeechErrorCategory.SERVICE_DISCONNECTED)
        val languageMissing = VoiceSpeechErrorPolicy.humanMessageFor(SpeechErrorCategory.LANGUAGE_UNAVAILABLE)
        val serviceMissing = VoiceSpeechErrorPolicy.humanMessageFor(SpeechErrorCategory.SERVICE_UNAVAILABLE)

        assertFalse(tooMany == unknownMessage)
        assertFalse(disconnected == unknownMessage)
        assertFalse(languageMissing == unknownMessage)
        assertFalse(serviceMissing == unknownMessage)

        assertTrue(tooMany.contains("ocupado", ignoreCase = true))
        assertTrue(disconnected.contains("servicio de voz", ignoreCase = true))
        assertTrue(languageMissing.contains("Servicios de voz de Google", ignoreCase = true))
        assertTrue(languageMissing.contains("dictado por voz", ignoreCase = true))
        assertTrue(serviceMissing.contains("servicio", ignoreCase = true))
    }

    @Test
    fun legacyErrorPolicyMessagesAreUnchanged() {
        assertEquals(
            "El micrófono se ocupó un momento. Probá de nuevo.",
            VoiceSpeechErrorPolicy.humanMessageFor(SpeechErrorCategory.RECOGNIZER_BUSY)
        )
        assertEquals(
            "El servicio de voz no respondió. Sigo con comandos locales.",
            VoiceSpeechErrorPolicy.humanMessageFor(SpeechErrorCategory.NETWORK)
        )
        assertEquals(
            "Reinicio el micrófono y vuelvo a escuchar.",
            VoiceSpeechErrorPolicy.humanMessageFor(SpeechErrorCategory.CLIENT)
        )
        assertEquals(
            "No pude escuchar bien. Probá otra vez.",
            VoiceSpeechErrorPolicy.humanMessageFor(SpeechErrorCategory.UNKNOWN)
        )
    }

    @Test
    fun serviceDisconnectedTriggersRecognizerReset() {
        assertTrue(VoiceSpeechErrorPolicy.shouldResetRecognizer(SpeechErrorCategory.SERVICE_DISCONNECTED))
        assertFalse(VoiceSpeechErrorPolicy.shouldResetRecognizer(SpeechErrorCategory.TOO_MANY_REQUESTS))
        assertFalse(VoiceSpeechErrorPolicy.shouldResetRecognizer(SpeechErrorCategory.LANGUAGE_UNAVAILABLE))
        assertFalse(VoiceSpeechErrorPolicy.shouldResetRecognizer(SpeechErrorCategory.SERVICE_UNAVAILABLE))
    }

    @Test
    fun onlyInsufficientPermissionsSkipsAutoRestart() {
        assertFalse(VoiceSpeechErrorPolicy.shouldAutoRestart(SpeechErrorCategory.INSUFFICIENT_PERMISSIONS))
        assertTrue(VoiceSpeechErrorPolicy.shouldAutoRestart(SpeechErrorCategory.LANGUAGE_UNAVAILABLE))
        assertTrue(VoiceSpeechErrorPolicy.shouldAutoRestart(SpeechErrorCategory.SERVICE_UNAVAILABLE))
        assertTrue(VoiceSpeechErrorPolicy.shouldAutoRestart(SpeechErrorCategory.SERVICE_DISCONNECTED))
        assertTrue(VoiceSpeechErrorPolicy.shouldAutoRestart(SpeechErrorCategory.TOO_MANY_REQUESTS))
    }

    private fun newSession(): VoiceListeningSession =
        VoiceListeningSession(
            sessionId = 1L,
            startedAt = 1_000L,
            wasSpeakingWhenStarted = false,
            wasRobotEnabled = true
        )
}
