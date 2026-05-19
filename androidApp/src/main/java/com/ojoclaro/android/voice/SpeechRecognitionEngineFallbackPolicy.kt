package com.ojoclaro.android.voice

import android.speech.SpeechRecognizer

internal data class SpeechRecognitionAttempt(
    val speechEngine: VoiceSpeechEngine,
    val languageCandidate: SpeechRecognitionLanguageCandidate
)

internal enum class SpeechRecognitionFailureReason {
    LANGUAGE_UNAVAILABLE,
    NO_MATCH,
    SPEECH_TIMEOUT,
    WATCHDOG_TIMEOUT,
    EMPTY_RESULTS,
    START_FAILED,
    UNHANDLED_ERROR
}

internal sealed class SpeechRecognitionFallbackDecision {
    data class TryNext(
        val previousAttempt: SpeechRecognitionAttempt,
        val nextAttempt: SpeechRecognitionAttempt,
        val reason: SpeechRecognitionFailureReason,
        val errorCode: Int?,
        val consecutiveNoMatch: Int = 0
    ) : SpeechRecognitionFallbackDecision() {
        val engineChanged: Boolean
            get() = previousAttempt.speechEngine != nextAttempt.speechEngine

        val languageChanged: Boolean
            get() = previousAttempt.languageCandidate != nextAttempt.languageCandidate

        val retryingCurrentAttempt: Boolean
            get() = previousAttempt == nextAttempt
    }

    data class PropagateError(
        val errorCode: Int?,
        val reason: SpeechRecognitionFailureReason
    ) : SpeechRecognitionFallbackDecision()

    data class Exhausted(
        val previousAttempt: SpeechRecognitionAttempt?,
        val reason: SpeechRecognitionFailureReason,
        val originalErrorCode: Int?
    ) : SpeechRecognitionFallbackDecision()
}

internal class SpeechRecognitionEngineFallbackPolicy(
    engineCandidates: List<VoiceSpeechEngine>,
    private val languageCandidates: List<SpeechRecognitionLanguageCandidate>,
    private val repeatedNoMatchThreshold: Int = DEFAULT_REPEATED_NO_MATCH_THRESHOLD
) {
    private val engines = engineCandidates
        .filter { it != VoiceSpeechEngine.UNAVAILABLE }
        .distinct()

    private var engineIndex: Int = 0
    private var languageIndex: Int = 0
    private var consecutiveNoMatchOnCurrentEngine: Int = 0

    init {
        require(languageCandidates.isNotEmpty()) { "Speech language candidates cannot be empty." }
        require(repeatedNoMatchThreshold > 0) { "No-match threshold must be positive." }
    }

    val currentAttempt: SpeechRecognitionAttempt?
        get() = engines.getOrNull(engineIndex)?.let { engine ->
            SpeechRecognitionAttempt(
                speechEngine = engine,
                languageCandidate = languageCandidates[languageIndex]
            )
        }

    val allEngineCandidates: List<VoiceSpeechEngine>
        get() = engines

    val allLanguageCandidates: List<SpeechRecognitionLanguageCandidate>
        get() = languageCandidates

    fun decisionAfterRecognizerError(
        errorCode: Int?,
        hadSpeechTextInAttempt: Boolean
    ): SpeechRecognitionFallbackDecision {
        val reason = failureReasonForRecognizerError(errorCode)
        return when (reason) {
            SpeechRecognitionFailureReason.LANGUAGE_UNAVAILABLE ->
                advanceAfterLanguageUnavailable(reason = reason, errorCode = errorCode)
            SpeechRecognitionFailureReason.NO_MATCH -> {
                if (hadSpeechTextInAttempt) {
                    SpeechRecognitionFallbackDecision.PropagateError(errorCode, reason)
                } else {
                    advanceAfterNoMatch(reason = reason, errorCode = errorCode)
                }
            }
            SpeechRecognitionFailureReason.SPEECH_TIMEOUT ->
                advanceAfterSilentFailure(reason = reason, errorCode = errorCode)
            SpeechRecognitionFailureReason.UNHANDLED_ERROR,
            SpeechRecognitionFailureReason.WATCHDOG_TIMEOUT,
            SpeechRecognitionFailureReason.EMPTY_RESULTS,
            SpeechRecognitionFailureReason.START_FAILED ->
                SpeechRecognitionFallbackDecision.PropagateError(errorCode, reason)
        }
    }

    fun decisionAfterEmptyResults(
        hadSpeechTextInAttempt: Boolean
    ): SpeechRecognitionFallbackDecision =
        if (hadSpeechTextInAttempt) {
            SpeechRecognitionFallbackDecision.PropagateError(
                errorCode = SpeechRecognizer.ERROR_NO_MATCH,
                reason = SpeechRecognitionFailureReason.EMPTY_RESULTS
            )
        } else {
            advanceAfterNoMatch(
                reason = SpeechRecognitionFailureReason.EMPTY_RESULTS,
                errorCode = SpeechRecognizer.ERROR_NO_MATCH
            )
        }

    fun decisionAfterWatchdogTimeout(): SpeechRecognitionFallbackDecision =
        advanceAfterSilentFailure(
            reason = SpeechRecognitionFailureReason.WATCHDOG_TIMEOUT,
            errorCode = null
        )

    fun decisionAfterStartFailure(errorCode: Int?): SpeechRecognitionFallbackDecision =
        advanceAfterSilentFailure(
            reason = SpeechRecognitionFailureReason.START_FAILED,
            errorCode = errorCode
        )

    private fun advanceAfterLanguageUnavailable(
        reason: SpeechRecognitionFailureReason,
        errorCode: Int?
    ): SpeechRecognitionFallbackDecision {
        val previous = currentAttempt
            ?: return exhausted(previousAttempt = null, reason = reason, errorCode = errorCode)

        consecutiveNoMatchOnCurrentEngine = 0
        return if (previous.speechEngine == VoiceSpeechEngine.ON_DEVICE) {
            advanceToNextEngine(previous, reason, errorCode)
                ?: exhausted(previous, reason, errorCode)
        } else {
            advanceToNextLanguage(previous, reason, errorCode)
                ?: exhausted(previous, reason, errorCode)
        }
    }

    private fun advanceAfterNoMatch(
        reason: SpeechRecognitionFailureReason,
        errorCode: Int?
    ): SpeechRecognitionFallbackDecision {
        val previous = currentAttempt
            ?: return exhausted(previousAttempt = null, reason = reason, errorCode = errorCode)

        consecutiveNoMatchOnCurrentEngine += 1
        if (previous.speechEngine == VoiceSpeechEngine.ON_DEVICE &&
            consecutiveNoMatchOnCurrentEngine < repeatedNoMatchThreshold
        ) {
            return SpeechRecognitionFallbackDecision.TryNext(
                previousAttempt = previous,
                nextAttempt = previous,
                reason = reason,
                errorCode = errorCode,
                consecutiveNoMatch = consecutiveNoMatchOnCurrentEngine
            )
        }

        return if (previous.speechEngine == VoiceSpeechEngine.ON_DEVICE) {
            advanceToNextEngine(previous, reason, errorCode)
                ?: exhausted(previous, reason, errorCode)
        } else {
            advanceToNextLanguage(previous, reason, errorCode)
                ?: exhausted(previous, reason, errorCode)
        }
    }

    private fun advanceAfterSilentFailure(
        reason: SpeechRecognitionFailureReason,
        errorCode: Int?
    ): SpeechRecognitionFallbackDecision {
        val previous = currentAttempt
            ?: return exhausted(previousAttempt = null, reason = reason, errorCode = errorCode)

        consecutiveNoMatchOnCurrentEngine = 0
        return if (previous.speechEngine == VoiceSpeechEngine.ON_DEVICE) {
            advanceToNextEngine(previous, reason, errorCode)
                ?: exhausted(previous, reason, errorCode)
        } else {
            advanceToNextLanguage(previous, reason, errorCode)
                ?: exhausted(previous, reason, errorCode)
        }
    }

    private fun advanceToNextEngine(
        previous: SpeechRecognitionAttempt,
        reason: SpeechRecognitionFailureReason,
        errorCode: Int?
    ): SpeechRecognitionFallbackDecision.TryNext? {
        val nextEngineIndex = engineIndex + 1
        if (nextEngineIndex >= engines.size) return null

        engineIndex = nextEngineIndex
        languageIndex = 0
        consecutiveNoMatchOnCurrentEngine = 0
        val next = currentAttempt ?: return null
        return SpeechRecognitionFallbackDecision.TryNext(
            previousAttempt = previous,
            nextAttempt = next,
            reason = reason,
            errorCode = errorCode
        )
    }

    private fun advanceToNextLanguage(
        previous: SpeechRecognitionAttempt,
        reason: SpeechRecognitionFailureReason,
        errorCode: Int?
    ): SpeechRecognitionFallbackDecision.TryNext? {
        val nextLanguageIndex = languageIndex + 1
        if (nextLanguageIndex >= languageCandidates.size) return null

        languageIndex = nextLanguageIndex
        consecutiveNoMatchOnCurrentEngine = 0
        val next = currentAttempt ?: return null
        return SpeechRecognitionFallbackDecision.TryNext(
            previousAttempt = previous,
            nextAttempt = next,
            reason = reason,
            errorCode = errorCode
        )
    }

    private fun exhausted(
        previousAttempt: SpeechRecognitionAttempt?,
        reason: SpeechRecognitionFailureReason,
        errorCode: Int?
    ): SpeechRecognitionFallbackDecision.Exhausted =
        SpeechRecognitionFallbackDecision.Exhausted(
            previousAttempt = previousAttempt,
            reason = reason,
            originalErrorCode = errorCode
        )

    private fun failureReasonForRecognizerError(errorCode: Int?): SpeechRecognitionFailureReason =
        when (errorCode) {
            VoiceSpeechErrorPolicy.ERROR_CODE_LANGUAGE_NOT_SUPPORTED,
            VoiceSpeechErrorPolicy.ERROR_CODE_LANGUAGE_UNAVAILABLE,
            VoiceSpeechErrorPolicy.ERROR_CODE_CANNOT_CHECK_SUPPORT,
            VoiceSpeechErrorPolicy.ERROR_CODE_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS ->
                SpeechRecognitionFailureReason.LANGUAGE_UNAVAILABLE
            SpeechRecognizer.ERROR_NO_MATCH -> SpeechRecognitionFailureReason.NO_MATCH
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SpeechRecognitionFailureReason.SPEECH_TIMEOUT
            else -> SpeechRecognitionFailureReason.UNHANDLED_ERROR
        }

    companion object {
        const val DEFAULT_REPEATED_NO_MATCH_THRESHOLD: Int = 2
    }
}

internal fun buildSpeechRecognitionEngineCandidates(
    onDeviceAvailable: Boolean,
    defaultAvailable: Boolean,
    preferOnDevice: Boolean
): List<VoiceSpeechEngine> =
    buildList {
        if (preferOnDevice && onDeviceAvailable) {
            add(VoiceSpeechEngine.ON_DEVICE)
        }
        if (defaultAvailable) {
            add(VoiceSpeechEngine.PLATFORM_DEFAULT)
        }
    }.distinct()
