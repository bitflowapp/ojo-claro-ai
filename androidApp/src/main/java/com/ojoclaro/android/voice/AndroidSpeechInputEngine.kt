package com.ojoclaro.android.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class AndroidSpeechInputEngine(
    context: Context,
    private val locale: Locale = Locale("es", "AR")
) : SpeechInputEngine {

    override var listener: SpeechInputEngine.Listener? = null

    private val appContext = context.applicationContext
    private val recognizer: SpeechRecognizer? = if (SpeechRecognizer.isRecognitionAvailable(appContext)) {
        SpeechRecognizer.createSpeechRecognizer(appContext)
    } else {
        null
    }
    private val listening = AtomicBoolean(false)
    @Volatile
    private var listeningMode: SpeechListeningMode = SpeechListeningMode.DEFAULT

    override val isListening: Boolean
        get() = listening.get()

    init {
        recognizer?.setRecognitionListener(object : RecognitionListener {
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
        })
    }

    override fun startListening() {
        val engine = recognizer
        if (engine == null) {
            listener?.onError(SpeechRecognizer.ERROR_CLIENT)
            return
        }

        if (listening.getAndSet(true)) return
        runCatching {
            engine.startListening(recognizerIntent())
        }.onFailure {
            listening.set(false)
            listener?.onError(SpeechRecognizer.ERROR_CLIENT)
        }
    }

    override fun stopListening() {
        listening.set(false)
        runCatching { recognizer?.cancel() }
    }

    override fun setListeningMode(mode: SpeechListeningMode) {
        listeningMode = mode
    }

    override fun destroy() {
        listening.set(false)
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
    }

    private fun recognizerIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, locale.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            val timing = timingFor(listeningMode)
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS,
                timing.minimumLengthMillis
            )
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                timing.completeSilenceMillis
            )
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                timing.possiblyCompleteSilenceMillis
            )
        }

    private fun speechResults(results: Bundle?): List<String>? =
        results
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)

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
