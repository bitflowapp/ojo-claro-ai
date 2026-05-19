package com.ojoclaro.android.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
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

    @Volatile
    private var listeningMode: SpeechListeningMode = SpeechListeningMode.DEFAULT

    @Volatile
    private var recognizer: SpeechRecognizer? = null

    @Volatile
    override var speechEngine: VoiceSpeechEngine = VoiceSpeechEngine.UNAVAILABLE
        private set

    override val isListening: Boolean
        get() = listening.get()

    init {
        recreateRecognizer()
    }

    override fun startListening() {
        val engine = recognizer
        if (engine == null) {
            Log.w(
                TAG,
                "startListening aborted: recognizer is null (engine=$speechEngine, locale=${locale.toLanguageTag()})"
            )
            listener?.onError(SpeechRecognizer.ERROR_CLIENT)
            return
        }

        if (listening.getAndSet(true)) return
        Log.d(
            TAG,
            "startListening engine=$speechEngine locale=${locale.toLanguageTag()} mode=$listeningMode preferOffline=${speechEngine == VoiceSpeechEngine.ON_DEVICE}"
        )
        runCatching {
            engine.startListening(
                buildSpeechRecognitionIntent(
                    locale = locale,
                    mode = listeningMode,
                    preferOffline = speechEngine == VoiceSpeechEngine.ON_DEVICE,
                    callingPackage = appContext.packageName
                )
            )
        }.onFailure { throwable ->
            listening.set(false)
            Log.w(TAG, "engine.startListening threw; emitting ERROR_CLIENT", throwable)
            listener?.onError(SpeechRecognizer.ERROR_CLIENT)
        }
    }

    override fun stopListening() {
        listening.set(false)
        runCatching { recognizer?.cancel() }
    }

    override fun resetRecognizer() {
        listening.set(false)
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
        speechEngine = VoiceSpeechEngine.UNAVAILABLE
        recreateRecognizer()
    }

    override fun setListeningMode(mode: SpeechListeningMode) {
        listeningMode = mode
    }

    override fun destroy() {
        listening.set(false)
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
        speechEngine = VoiceSpeechEngine.UNAVAILABLE
    }

    private fun recreateRecognizer() {
        val onDeviceAvailable = isOnDeviceRecognitionAvailable(appContext)
        val defaultAvailable = SpeechRecognizer.isRecognitionAvailable(appContext)
        Log.d(
            TAG,
            "recreateRecognizer onDeviceAvailable=$onDeviceAvailable defaultAvailable=$defaultAvailable preferOnDevice=$preferOnDevice sdk=${Build.VERSION.SDK_INT}"
        )
        val selection = chooseSpeechEngine(
            onDeviceAvailable = onDeviceAvailable,
            defaultAvailable = defaultAvailable,
            preferOnDevice = preferOnDevice
        )
        val nextRecognizer = when (selection.speechEngine) {
            VoiceSpeechEngine.ON_DEVICE -> runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
                } else {
                    null
                }
            }.getOrNull() ?: runCatching { SpeechRecognizer.createSpeechRecognizer(appContext) }.getOrNull()
            VoiceSpeechEngine.PLATFORM_DEFAULT -> runCatching {
                SpeechRecognizer.createSpeechRecognizer(appContext)
            }.getOrNull()
            VoiceSpeechEngine.UNAVAILABLE -> null
        }

        recognizer = nextRecognizer
        speechEngine = when {
            nextRecognizer == null -> VoiceSpeechEngine.UNAVAILABLE
            selection.speechEngine == VoiceSpeechEngine.ON_DEVICE &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> VoiceSpeechEngine.ON_DEVICE
            else -> VoiceSpeechEngine.PLATFORM_DEFAULT
        }
        Log.d(TAG, "recreateRecognizer resolved speechEngine=$speechEngine recognizerNull=${nextRecognizer == null}")
        nextRecognizer?.setRecognitionListener(createRecognitionListener())
    }

    private fun createRecognitionListener(): RecognitionListener =
        object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                listening.set(true)
                listener?.onReady()
            }

            override fun onBeginningOfSpeech() = Unit

            override fun onRmsChanged(rmsdB: Float) = Unit

            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() {
                listening.set(false)
            }

            override fun onError(error: Int) {
                listening.set(false)
                Log.w(
                    TAG,
                    "onError code=$error name=${speechErrorName(error)} engine=$speechEngine locale=${locale.toLanguageTag()}"
                )
                listener?.onError(error)
            }

            override fun onResults(results: Bundle?) {
                listening.set(false)
                dispatchFinalSpeechResults(
                    candidates = speechResults(results),
                    listener = listener
                )
            }

            override fun onPartialResults(partialResults: Bundle?) {
                chooseBestSpeechResult(speechResults(partialResults))?.let { listener?.onPartialText(it) }
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        }

    private fun speechResults(results: Bundle?): List<String>? =
        results
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
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
    buildSpeechRecognitionIntentConfig(
        locale = locale,
        mode = mode,
        preferOffline = preferOffline,
        callingPackage = callingPackage
    ).let { config ->
        Intent(config.action).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, config.languageModel)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, config.languageTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, config.languageTag)
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, config.onlyReturnLanguagePreference)
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
    val languageTag: String,
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
): SpeechRecognitionIntentConfig {
    val timing = timingFor(mode)
    return SpeechRecognitionIntentConfig(
        action = RecognizerIntent.ACTION_RECOGNIZE_SPEECH,
        languageModel = RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
        languageTag = locale.toLanguageTag(),
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
        else -> "ERROR_UNKNOWN($code)"
    }
