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
    SERVICE_DISCONNECTED,
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
    private val languageCandidatesByEngine: Map<VoiceSpeechEngine, List<SpeechRecognitionLanguageCandidate>>,
    private val repeatedNoMatchThreshold: Int = DEFAULT_REPEATED_NO_MATCH_THRESHOLD
) {
    private val engines = engineCandidates
        .filter { it != VoiceSpeechEngine.UNAVAILABLE }
        .distinct()

    constructor(
        engineCandidates: List<VoiceSpeechEngine>,
        languageCandidates: List<SpeechRecognitionLanguageCandidate>,
        repeatedNoMatchThreshold: Int = DEFAULT_REPEATED_NO_MATCH_THRESHOLD
    ) : this(
        engineCandidates = engineCandidates,
        languageCandidatesByEngine = engineCandidates
            .filter { it != VoiceSpeechEngine.UNAVAILABLE }
            .distinct()
            .associateWith { languageCandidates },
        repeatedNoMatchThreshold = repeatedNoMatchThreshold
    )

    private var engineIndex: Int = 0
    private var languageIndex: Int = 0
    private var consecutiveNoMatchOnCurrentEngine: Int = 0
    private val disabledEngines = mutableSetOf<VoiceSpeechEngine>()
    private val brokenLanguagesByEngine = mutableMapOf<VoiceSpeechEngine, MutableSet<String>>()

    init {
        require(engines.isEmpty() || engines.all { languageCandidatesFor(it).isNotEmpty() }) {
            "Speech language candidates cannot be empty."
        }
        require(repeatedNoMatchThreshold > 0) { "No-match threshold must be positive." }
    }

    val currentAttempt: SpeechRecognitionAttempt?
        get() {
            val engine = engines.getOrNull(engineIndex) ?: return null
            if (engine in disabledEngines) return null
            val candidate = languageCandidatesFor(engine).getOrNull(languageIndex) ?: return null
            if (isLanguageBroken(engine, candidate)) return null
            return SpeechRecognitionAttempt(
                speechEngine = engine,
                languageCandidate = candidate
            )
        }

    val allEngineCandidates: List<VoiceSpeechEngine>
        get() = engines

    val allLanguageCandidates: List<SpeechRecognitionLanguageCandidate>
        get() = languageCandidatesByEngine
            .values
            .flatten()
            .distinct()

    fun languageCandidatesForEngine(
        engine: VoiceSpeechEngine
    ): List<SpeechRecognitionLanguageCandidate> =
        languageCandidatesFor(engine)

    val disabledEngineCandidates: Set<VoiceSpeechEngine>
        get() = disabledEngines.toSet()

    fun isEngineDisabled(engine: VoiceSpeechEngine): Boolean =
        engine in disabledEngines

    fun isLanguageBroken(
        engine: VoiceSpeechEngine,
        candidate: SpeechRecognitionLanguageCandidate
    ): Boolean =
        candidate.languageKey() in brokenLanguagesByEngine[engine].orEmpty()

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
            SpeechRecognitionFailureReason.SERVICE_DISCONNECTED ->
                advanceAfterServiceDisconnected(reason = reason, errorCode = errorCode)
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
            disableEngine(previous.speechEngine)
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
            disableEngine(previous.speechEngine)
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
            disableEngine(previous.speechEngine)
            advanceToNextEngine(previous, reason, errorCode)
                ?: exhausted(previous, reason, errorCode)
        } else {
            advanceToNextLanguage(previous, reason, errorCode)
                ?: exhausted(previous, reason, errorCode)
        }
    }

    private fun advanceAfterServiceDisconnected(
        reason: SpeechRecognitionFailureReason,
        errorCode: Int?
    ): SpeechRecognitionFallbackDecision {
        val previous = currentAttempt
            ?: return exhausted(previousAttempt = null, reason = reason, errorCode = errorCode)

        consecutiveNoMatchOnCurrentEngine = 0
        markLanguageBroken(previous.speechEngine, previous.languageCandidate)
        return if (previous.speechEngine == VoiceSpeechEngine.ON_DEVICE) {
            disableEngine(previous.speechEngine)
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
        var nextEngineIndex = engineIndex + 1
        while (nextEngineIndex < engines.size) {
            val engine = engines[nextEngineIndex]
            val firstLanguageIndex = firstUsableLanguageIndex(engine)
            if (engine !in disabledEngines && firstLanguageIndex != null) {
                engineIndex = nextEngineIndex
                languageIndex = firstLanguageIndex
                consecutiveNoMatchOnCurrentEngine = 0
                val next = currentAttempt ?: return null
                return SpeechRecognitionFallbackDecision.TryNext(
                    previousAttempt = previous,
                    nextAttempt = next,
                    reason = reason,
                    errorCode = errorCode
                )
            }
            nextEngineIndex += 1
        }

        return null
    }

    private fun advanceToNextLanguage(
        previous: SpeechRecognitionAttempt,
        reason: SpeechRecognitionFailureReason,
        errorCode: Int?
    ): SpeechRecognitionFallbackDecision.TryNext? {
        val nextLanguageIndex = nextUsableLanguageIndex(previous.speechEngine, languageIndex + 1)
            ?: return null

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

    private fun disableEngine(engine: VoiceSpeechEngine) {
        disabledEngines += engine
    }

    private fun markLanguageBroken(
        engine: VoiceSpeechEngine,
        candidate: SpeechRecognitionLanguageCandidate
    ) {
        brokenLanguagesByEngine.getOrPut(engine) { mutableSetOf() } += candidate.languageKey()
    }

    private fun firstUsableLanguageIndex(engine: VoiceSpeechEngine): Int? =
        nextUsableLanguageIndex(engine, startIndex = 0)

    private fun nextUsableLanguageIndex(engine: VoiceSpeechEngine, startIndex: Int): Int? =
        languageCandidatesFor(engine)
            .withIndex()
            .firstOrNull { (index, candidate) ->
                index >= startIndex && !isLanguageBroken(engine, candidate)
            }
            ?.index

    private fun languageCandidatesFor(engine: VoiceSpeechEngine): List<SpeechRecognitionLanguageCandidate> =
        languageCandidatesByEngine[engine].orEmpty()

    private fun SpeechRecognitionLanguageCandidate.languageKey(): String =
        languageTag ?: "device-default"

    private fun failureReasonForRecognizerError(errorCode: Int?): SpeechRecognitionFailureReason =
        when (errorCode) {
            VoiceSpeechErrorPolicy.ERROR_CODE_LANGUAGE_NOT_SUPPORTED,
            VoiceSpeechErrorPolicy.ERROR_CODE_LANGUAGE_UNAVAILABLE,
            VoiceSpeechErrorPolicy.ERROR_CODE_CANNOT_CHECK_SUPPORT,
            VoiceSpeechErrorPolicy.ERROR_CODE_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS ->
                SpeechRecognitionFailureReason.LANGUAGE_UNAVAILABLE
            SpeechRecognizer.ERROR_NO_MATCH -> SpeechRecognitionFailureReason.NO_MATCH
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SpeechRecognitionFailureReason.SPEECH_TIMEOUT
            VoiceSpeechErrorPolicy.ERROR_CODE_SERVER_DISCONNECTED ->
                SpeechRecognitionFailureReason.SERVICE_DISCONNECTED
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
