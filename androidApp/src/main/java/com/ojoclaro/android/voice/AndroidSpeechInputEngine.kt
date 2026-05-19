package com.ojoclaro.android.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "OjoClaroVoice"

class AndroidSpeechInputEngine(
    context: Context,
    private val locale: Locale = Locale("es", "AR"),
    private val preferOnDevice: Boolean = true
) : SpeechInputEngine {

    override var listener: SpeechInputEngine.Listener? = null

    private val appContext = context.applicationContext
    private val listening = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var listeningMode: SpeechListeningMode = SpeechListeningMode.DEFAULT

    @Volatile
    private var recognizer: SpeechRecognizer? = null

    @Volatile
    private var engineFallbackPolicy: SpeechRecognitionEngineFallbackPolicy = newEngineFallbackPolicy()

    @Volatile
    private var recognizerGeneration: Long = 0L

    @Volatile
    private var receivedSpeechTextInAttempt: Boolean = false

    @Volatile
    private var noResultWatchdog: Runnable? = null

    @Volatile
    override var speechEngine: VoiceSpeechEngine = VoiceSpeechEngine.UNAVAILABLE
        private set

    override val isListening: Boolean
        get() = listening.get()

    init {
        recreateRecognizerForEngine(
            engineFallbackPolicy.currentAttempt?.speechEngine ?: VoiceSpeechEngine.UNAVAILABLE
        )
    }

    override fun startListening() {
        if (listening.getAndSet(true)) return

        engineFallbackPolicy = newEngineFallbackPolicy()
        val attempt = engineFallbackPolicy.currentAttempt
        if (attempt == null) {
            Log.w(
                TAG,
                "startListening aborted: no speech recognizer available (preferredLocale=${locale.toLanguageTag()})"
            )
            listening.set(false)
            listener?.onError(SpeechRecognizer.ERROR_CLIENT)
            return
        }
        beginAttempt(attempt)
    }

    private fun beginAttempt(attempt: SpeechRecognitionAttempt) {
        if (!listening.get()) return
        receivedSpeechTextInAttempt = false

        if (!recreateRecognizerForEngine(attempt.speechEngine)) {
            Log.w(
                TAG,
                "speech recognizer create failed engine=${attempt.speechEngine.logLabel()} language=${attempt.languageCandidate.safeLogLabel}"
            )
            handleStartFailure(SpeechRecognizer.ERROR_CLIENT)
            return
        }

        val engine = recognizer
        if (engine == null) {
            handleStartFailure(SpeechRecognizer.ERROR_CLIENT)
            return
        }

        Log.d(
            TAG,
            "startListening engine=${attempt.speechEngine.logLabel()} language=${attempt.languageCandidate.safeLogLabel} mode=$listeningMode preferOffline=${attempt.speechEngine == VoiceSpeechEngine.ON_DEVICE}"
        )
        val started = runCatching {
            engine.startListening(
                buildSpeechRecognitionIntent(
                    candidate = attempt.languageCandidate,
                    mode = listeningMode,
                    preferOffline = attempt.speechEngine == VoiceSpeechEngine.ON_DEVICE,
                    callingPackage = appContext.packageName
                )
            )
        }.onFailure { throwable ->
            Log.w(
                TAG,
                "engine.startListening threw engine=${attempt.speechEngine.logLabel()} language=${attempt.languageCandidate.safeLogLabel}",
                throwable
            )
        }.isSuccess

        if (started) {
            scheduleNoResultWatchdog(attempt, recognizerGeneration)
        } else {
            handleStartFailure(SpeechRecognizer.ERROR_CLIENT)
        }
    }

    override fun stopListening() {
        listening.set(false)
        cancelNoResultWatchdog()
        runCatching { recognizer?.cancel() }
    }

    override fun resetRecognizer() {
        listening.set(false)
        cancelNoResultWatchdog()
        engineFallbackPolicy = newEngineFallbackPolicy()
        recreateRecognizerForEngine(
            engineFallbackPolicy.currentAttempt?.speechEngine ?: VoiceSpeechEngine.UNAVAILABLE
        )
    }

    override fun setListeningMode(mode: SpeechListeningMode) {
        listeningMode = mode
    }

    override fun destroy() {
        listening.set(false)
        cancelNoResultWatchdog()
        recognizerGeneration += 1L
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
        speechEngine = VoiceSpeechEngine.UNAVAILABLE
    }

    private fun newEngineFallbackPolicy(): SpeechRecognitionEngineFallbackPolicy {
        val onDeviceAvailable = isOnDeviceRecognitionAvailable(appContext)
        val defaultAvailable = SpeechRecognizer.isRecognitionAvailable(appContext)
        Log.d(
            TAG,
            "speech fallback policy onDeviceAvailable=$onDeviceAvailable defaultAvailable=$defaultAvailable preferOnDevice=$preferOnDevice sdk=${Build.VERSION.SDK_INT}"
        )
        return SpeechRecognitionEngineFallbackPolicy(
            engineCandidates = buildSpeechRecognitionEngineCandidates(
                onDeviceAvailable = onDeviceAvailable,
                defaultAvailable = defaultAvailable,
                preferOnDevice = preferOnDevice
            ),
            languageCandidates = buildSpeechRecognitionLanguageCandidates(
                preferredLocale = locale,
                defaultLocale = Locale.getDefault()
            )
        )
    }

    private fun recreateRecognizerForEngine(requestedEngine: VoiceSpeechEngine): Boolean {
        cancelNoResultWatchdog()
        recognizerGeneration += 1L
        val generation = recognizerGeneration
        val previousRecognizer = recognizer
        recognizer = null
        speechEngine = VoiceSpeechEngine.UNAVAILABLE
        runCatching { previousRecognizer?.cancel() }
        runCatching { previousRecognizer?.destroy() }

        val nextRecognizer = when (requestedEngine) {
            VoiceSpeechEngine.ON_DEVICE -> runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
                } else {
                    null
                }
            }.getOrNull()
            VoiceSpeechEngine.PLATFORM_DEFAULT -> runCatching {
                SpeechRecognizer.createSpeechRecognizer(appContext)
            }.getOrNull()
            VoiceSpeechEngine.UNAVAILABLE -> null
        }

        recognizer = nextRecognizer
        speechEngine = if (nextRecognizer == null) {
            VoiceSpeechEngine.UNAVAILABLE
        } else {
            requestedEngine
        }
        Log.d(
            TAG,
            "recreateRecognizer resolved engine=${speechEngine.logLabel()} requested=${requestedEngine.logLabel()} recognizerNull=${nextRecognizer == null}"
        )
        nextRecognizer?.setRecognitionListener(createRecognitionListener(generation))
        return nextRecognizer != null
    }

    private fun createRecognitionListener(generation: Long): RecognitionListener =
        object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                if (!isCurrentRecognizerCallback(generation) || !listening.get()) return
                listener?.onReady()
            }

            override fun onBeginningOfSpeech() = Unit

            override fun onRmsChanged(rmsdB: Float) = Unit

            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() {
                Log.d(TAG, "onEndOfSpeech engine=${speechEngine.logLabel()}")
            }

            override fun onError(error: Int) {
                if (!isCurrentRecognizerCallback(generation)) return
                if (!listening.get()) {
                    Log.d(
                        TAG,
                        "ignoring speech error after cancellation code=$error name=${speechErrorName(error)}"
                    )
                    return
                }
                Log.w(
                    TAG,
                    "onError code=$error name=${speechErrorName(error)} engine=${speechEngine.logLabel()} language=${engineFallbackPolicy.currentAttempt?.languageCandidate?.safeLogLabel ?: "unknown"}"
                )
                handleRecognizerFailure(error)
            }

            override fun onResults(results: Bundle?) {
                if (!isCurrentRecognizerCallback(generation) || !listening.get()) return
                val result = chooseBestSpeechResult(speechResults(results))
                if (result == null) {
                    Log.w(
                        TAG,
                        "onResults empty engine=${speechEngine.logLabel()} language=${engineFallbackPolicy.currentAttempt?.languageCandidate?.safeLogLabel ?: "unknown"}"
                    )
                    handleEmptyResults()
                    return
                }
                receivedSpeechTextInAttempt = true
                cancelNoResultWatchdog()
                listening.set(false)
                listener?.onFinalText(result)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                if (!isCurrentRecognizerCallback(generation) || !listening.get()) return
                chooseBestSpeechResult(speechResults(partialResults))?.let {
                    receivedSpeechTextInAttempt = true
                    cancelNoResultWatchdog()
                    listener?.onPartialText(it)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        }

    private fun isCurrentRecognizerCallback(generation: Long): Boolean =
        generation == recognizerGeneration

    private fun handleRecognizerFailure(errorCode: Int?) {
        cancelNoResultWatchdog()
        handleFallbackDecision(
            engineFallbackPolicy.decisionAfterRecognizerError(
                errorCode = errorCode,
                hadSpeechTextInAttempt = receivedSpeechTextInAttempt
            )
        )
    }

    private fun handleEmptyResults() {
        cancelNoResultWatchdog()
        handleFallbackDecision(
            engineFallbackPolicy.decisionAfterEmptyResults(
                hadSpeechTextInAttempt = receivedSpeechTextInAttempt
            )
        )
    }

    private fun handleStartFailure(errorCode: Int?) {
        cancelNoResultWatchdog()
        handleFallbackDecision(engineFallbackPolicy.decisionAfterStartFailure(errorCode))
    }

    private fun handleFallbackDecision(decision: SpeechRecognitionFallbackDecision) {
        if (!listening.get()) return
        when (decision) {
            is SpeechRecognitionFallbackDecision.TryNext -> {
                logFallbackDecision(decision)
                if (decision.engineChanged &&
                    decision.nextAttempt.speechEngine == VoiceSpeechEngine.PLATFORM_DEFAULT
                ) {
                    listener?.onStatusMessage(VoiceSpeechErrorPolicy.ENGINE_FALLBACK_MESSAGE)
                }
                beginAttempt(decision.nextAttempt)
            }
            is SpeechRecognitionFallbackDecision.PropagateError -> {
                listening.set(false)
                cancelNoResultWatchdog()
                listener?.onError(decision.errorCode)
            }
            is SpeechRecognitionFallbackDecision.Exhausted -> {
                Log.w(
                    TAG,
                    "speech fallback exhausted reason=${decision.reason} originalError=${
                        decision.originalErrorCode?.let { speechErrorName(it) } ?: "NULL"
                    } engine=${decision.previousAttempt?.speechEngine?.logLabel() ?: "unknown"} language=${decision.previousAttempt?.languageCandidate?.safeLogLabel ?: "unknown"}"
                )
                listening.set(false)
                cancelNoResultWatchdog()
                runCatching { recognizer?.cancel() }
                listener?.onError(VoiceSpeechErrorPolicy.ERROR_CODE_ALL_FALLBACKS_EXHAUSTED)
            }
        }
    }

    private fun logFallbackDecision(decision: SpeechRecognitionFallbackDecision.TryNext) {
        val errorName = decision.errorCode?.let { speechErrorName(it) } ?: "NULL"
        val previous = decision.previousAttempt
        val next = decision.nextAttempt
        when {
            decision.retryingCurrentAttempt -> Log.i(
                TAG,
                "speech retry engine=${next.speechEngine.logLabel()} language=${next.languageCandidate.safeLogLabel} reason=${decision.reason} error=$errorName noMatchCount=${decision.consecutiveNoMatch}"
            )
            decision.engineChanged -> Log.i(
                TAG,
                "speech engine fallback reason=${decision.reason} error=$errorName from=${previous.speechEngine.logLabel()} language=${previous.languageCandidate.safeLogLabel} to=${next.speechEngine.logLabel()} language=${next.languageCandidate.safeLogLabel}"
            )
            decision.languageChanged -> Log.i(
                TAG,
                "speech language fallback reason=${decision.reason} error=$errorName engine=${next.speechEngine.logLabel()} from=${previous.languageCandidate.safeLogLabel} to=${next.languageCandidate.safeLogLabel}"
            )
            else -> Log.i(
                TAG,
                "speech fallback retry reason=${decision.reason} error=$errorName engine=${next.speechEngine.logLabel()} language=${next.languageCandidate.safeLogLabel}"
            )
        }
    }

    private fun scheduleNoResultWatchdog(
        attempt: SpeechRecognitionAttempt,
        generation: Long
    ) {
        cancelNoResultWatchdog()
        val watchdog = Runnable {
            if (!listening.get() ||
                !isCurrentRecognizerCallback(generation) ||
                receivedSpeechTextInAttempt
            ) {
                return@Runnable
            }
            Log.w(
                TAG,
                "speech watchdog timeout timeoutMillis=$NO_RESULT_WATCHDOG_TIMEOUT_MILLIS engine=${attempt.speechEngine.logLabel()} language=${attempt.languageCandidate.safeLogLabel}"
            )
            handleFallbackDecision(engineFallbackPolicy.decisionAfterWatchdogTimeout())
        }
        noResultWatchdog = watchdog
        mainHandler.postDelayed(watchdog, NO_RESULT_WATCHDOG_TIMEOUT_MILLIS)
    }

    private fun cancelNoResultWatchdog() {
        noResultWatchdog?.let { mainHandler.removeCallbacks(it) }
        noResultWatchdog = null
    }

    private fun speechResults(results: Bundle?): List<String>? =
        results
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)

    private fun VoiceSpeechEngine.logLabel(): String =
        when (this) {
            VoiceSpeechEngine.ON_DEVICE -> "ON_DEVICE"
            VoiceSpeechEngine.PLATFORM_DEFAULT -> "DEFAULT_SYSTEM_RECOGNIZER"
            VoiceSpeechEngine.UNAVAILABLE -> "UNAVAILABLE"
        }

    companion object {
        private const val NO_RESULT_WATCHDOG_TIMEOUT_MILLIS: Long = 7_000L
    }
}

internal data class SpeechEngineSelection(
    val speechEngine: VoiceSpeechEngine,
    val preferOffline: Boolean
)

internal fun chooseSpeechEngine(
    onDeviceAvailable: Boolean,
    defaultAvailable: Boolean,
    preferOnDevice: Boolean
): SpeechEngineSelection =
    when {
        preferOnDevice && onDeviceAvailable -> SpeechEngineSelection(
            speechEngine = VoiceSpeechEngine.ON_DEVICE,
            preferOffline = true
        )
        defaultAvailable -> SpeechEngineSelection(
            speechEngine = VoiceSpeechEngine.PLATFORM_DEFAULT,
            preferOffline = false
        )
        else -> SpeechEngineSelection(
            speechEngine = VoiceSpeechEngine.UNAVAILABLE,
            preferOffline = false
        )
    }

internal fun buildSpeechRecognitionIntent(
    locale: Locale,
    mode: SpeechListeningMode,
    preferOffline: Boolean,
    callingPackage: String?
): Intent =
    buildSpeechRecognitionIntent(
        candidate = SpeechRecognitionLanguageCandidate.ForcedLocale(locale),
        mode = mode,
        preferOffline = preferOffline,
        callingPackage = callingPackage
    )

internal fun buildSpeechRecognitionIntent(
    candidate: SpeechRecognitionLanguageCandidate,
    mode: SpeechListeningMode,
    preferOffline: Boolean,
    callingPackage: String?
): Intent =
    buildSpeechRecognitionIntentConfig(
        candidate = candidate,
        mode = mode,
        preferOffline = preferOffline,
        callingPackage = callingPackage
    ).let { config ->
        Intent(config.action).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, config.languageModel)
            config.languageTag?.let { languageTag ->
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
                putExtra(
                    RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE,
                    config.onlyReturnLanguagePreference
                )
            }
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, config.partialResults)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, config.maxResults)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, config.preferOffline)
            if (!config.callingPackage.isNullOrBlank()) {
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, config.callingPackage)
            }
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS,
                config.minimumLengthMillis
            )
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                config.completeSilenceMillis
            )
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                config.possiblyCompleteSilenceMillis
            )
        }
    }

internal data class SpeechRecognitionIntentConfig(
    val action: String,
    val languageModel: String,
    val languageTag: String?,
    val onlyReturnLanguagePreference: Boolean,
    val partialResults: Boolean,
    val maxResults: Int,
    val preferOffline: Boolean,
    val callingPackage: String?,
    val minimumLengthMillis: Long,
    val completeSilenceMillis: Long,
    val possiblyCompleteSilenceMillis: Long
)

internal fun buildSpeechRecognitionIntentConfig(
    locale: Locale,
    mode: SpeechListeningMode,
    preferOffline: Boolean,
    callingPackage: String?
): SpeechRecognitionIntentConfig =
    buildSpeechRecognitionIntentConfig(
        candidate = SpeechRecognitionLanguageCandidate.ForcedLocale(locale),
        mode = mode,
        preferOffline = preferOffline,
        callingPackage = callingPackage
    )

internal fun buildSpeechRecognitionIntentConfig(
    candidate: SpeechRecognitionLanguageCandidate,
    mode: SpeechListeningMode,
    preferOffline: Boolean,
    callingPackage: String?
): SpeechRecognitionIntentConfig {
    val timing = timingFor(mode)
    return SpeechRecognitionIntentConfig(
        action = RecognizerIntent.ACTION_RECOGNIZE_SPEECH,
        languageModel = RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
        languageTag = candidate.languageTag,
        onlyReturnLanguagePreference = false,
        partialResults = true,
        maxResults = 3,
        preferOffline = preferOffline,
        callingPackage = callingPackage?.takeIf { it.isNotBlank() },
        minimumLengthMillis = timing.minimumLengthMillis,
        completeSilenceMillis = timing.completeSilenceMillis,
        possiblyCompleteSilenceMillis = timing.possiblyCompleteSilenceMillis
    )
}

internal fun dispatchFinalSpeechResults(
    candidates: List<String>?,
    listener: SpeechInputEngine.Listener?
) {
    val result = chooseBestSpeechResult(candidates)

    if (result == null) {
        listener?.onError(SpeechRecognizer.ERROR_NO_MATCH)
    } else {
        listener?.onFinalText(result)
    }
}

internal fun chooseBestSpeechResult(candidates: List<String>?): String? =
    candidates
        ?.firstOrNull { it.isNotBlank() }
        ?.trim()
        ?.takeIf { it.isNotBlank() }

private data class RecognitionTiming(
    val minimumLengthMillis: Long,
    val completeSilenceMillis: Long,
    val possiblyCompleteSilenceMillis: Long
)

private fun timingFor(mode: SpeechListeningMode): RecognitionTiming =
    when (mode) {
        SpeechListeningMode.DEFAULT -> RecognitionTiming(
            minimumLengthMillis = 5_000L,
            completeSilenceMillis = 1_400L,
            possiblyCompleteSilenceMillis = 900L
        )
        SpeechListeningMode.EXPECTING_RESPONSE -> RecognitionTiming(
            minimumLengthMillis = 12_000L,
            completeSilenceMillis = 2_800L,
            possiblyCompleteSilenceMillis = 2_000L
        )
    }

private fun isOnDeviceRecognitionAvailable(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(context) }.getOrDefault(false)

internal fun speechErrorName(code: Int): String =
    when (code) {
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "ERROR_NETWORK_TIMEOUT"
        SpeechRecognizer.ERROR_NETWORK -> "ERROR_NETWORK"
        SpeechRecognizer.ERROR_AUDIO -> "ERROR_AUDIO"
        SpeechRecognizer.ERROR_SERVER -> "ERROR_SERVER"
        SpeechRecognizer.ERROR_CLIENT -> "ERROR_CLIENT"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "ERROR_SPEECH_TIMEOUT"
        SpeechRecognizer.ERROR_NO_MATCH -> "ERROR_NO_MATCH"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "ERROR_RECOGNIZER_BUSY"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "ERROR_INSUFFICIENT_PERMISSIONS"
        10 -> "ERROR_TOO_MANY_REQUESTS"
        11 -> "ERROR_SERVER_DISCONNECTED"
        12 -> "ERROR_LANGUAGE_NOT_SUPPORTED"
        13 -> "ERROR_LANGUAGE_UNAVAILABLE"
        14 -> "ERROR_CANNOT_CHECK_SUPPORT"
        15 -> "ERROR_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS"
        VoiceSpeechErrorPolicy.ERROR_CODE_ALL_FALLBACKS_EXHAUSTED -> "ERROR_ALL_FALLBACKS_EXHAUSTED"
        else -> "ERROR_UNKNOWN($code)"
    }
