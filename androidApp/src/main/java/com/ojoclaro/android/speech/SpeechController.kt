package com.ojoclaro.android.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.ojoclaro.android.BuildConfig
import com.ojoclaro.android.voice.EstelaVoiceProfile
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Centralized TextToSpeech controller for the Android app.
 */
class SpeechController(
    context: Context,
    private val onSpeechStarted: () -> Unit = {},
    private val onSpeechFinished: () -> Unit = {},
    private val onSpeechStopped: () -> Unit = {}
) {
    private data class SpeechRequest(
        val text: String,
        val utteranceId: String,
        val generation: Long
    )

    private val lock = Any()
    private val speaking = AtomicBoolean(false)

    private var tts: TextToSpeech? = null
    private var ready = false
    private var closed = false
    private var pendingRequest: SpeechRequest? = null
    private var lastSpokenKey: String = ""
    private var lastSpokenAtMillis: Long = 0L
    private var generation: Long = 0L
    private var activeUtteranceId: String? = null

    private val utteranceListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            if (isActiveUtterance(utteranceId)) {
                speaking.set(true)
                logTts("tts_started=true utteranceId=${utteranceId.orEmpty()}")
                onSpeechStarted()
            }
        }

        override fun onDone(utteranceId: String?) {
            if (isActiveUtterance(utteranceId)) {
                speaking.set(false)
                clearActiveUtterance(utteranceId)
                logTts("tts_completed=true utteranceId=${utteranceId.orEmpty()}")
                onSpeechFinished()
            }
        }

        @Deprecated("Deprecated in Android framework, still required by TextToSpeech.")
        override fun onError(utteranceId: String?) {
            if (isActiveUtterance(utteranceId)) {
                speaking.set(false)
                clearActiveUtterance(utteranceId)
                logTts("tts_error=framework_on_error utteranceId=${utteranceId.orEmpty()}")
                onSpeechFinished()
            }
        }

        override fun onStop(utteranceId: String?, interrupted: Boolean) {
            if (isActiveUtterance(utteranceId)) {
                speaking.set(false)
                clearActiveUtterance(utteranceId)
                logTts("tts_stopped=true interrupted=$interrupted utteranceId=${utteranceId.orEmpty()}")
                onSpeechStopped()
            }
        }
    }

    init {
        tts = TextToSpeech(context.applicationContext, ::onInitialized)
        tts?.setOnUtteranceProgressListener(utteranceListener)
    }

    fun speak(text: String, force: Boolean = false) {
        val normalized = text.trim()
        if (normalized.isBlank()) return

        val now = System.currentTimeMillis()
        val key = normalized.lowercase(Locale.ROOT)

        val request = synchronized(lock) {
            if (closed) return
            if (!force && key == lastSpokenKey && now - lastSpokenAtMillis < DEDUP_WINDOW_MILLIS) {
                return
            }

            generation += 1L
            val nextRequest = SpeechRequest(
                text = normalized,
                utteranceId = "ojo-claro-speech-$generation-$now",
                generation = generation
            )

            lastSpokenKey = key
            lastSpokenAtMillis = now
            pendingRequest = if (ready) null else nextRequest
            nextRequest.takeIf { ready }
        }

        request?.let(::speakNow)
    }

    fun stop() {
        val shouldNotifyStopped: Boolean
        val engine = synchronized(lock) {
            generation += 1L
            pendingRequest = null
            shouldNotifyStopped = activeUtteranceId != null || speaking.get()
            speaking.set(false)
            activeUtteranceId = null
            tts
        }
        engine?.stop()
        if (shouldNotifyStopped) {
            onSpeechStopped()
        }
    }

    fun shutdown() {
        val engine = synchronized(lock) {
            if (closed) {
                null
            } else {
                closed = true
                ready = false
                pendingRequest = null
                speaking.set(false)
                activeUtteranceId = null
                tts.also { tts = null }
            }
        }

        engine?.stop()
        engine?.shutdown()
    }

    val isSpeaking: Boolean
        get() = !closed && (speaking.get() || tts?.isSpeaking == true)

    private fun onInitialized(status: Int) {
        val initializationFailed: Boolean
        val request = synchronized(lock) {
            if (closed) {
                initializationFailed = false
                return
            }

            if (status != TextToSpeech.SUCCESS) {
                ready = false
                pendingRequest = null
                activeUtteranceId = null
                speaking.set(false)
                initializationFailed = true
                null
            } else {
                val engine = tts
                if (engine == null) {
                    ready = false
                    pendingRequest = null
                    activeUtteranceId = null
                    speaking.set(false)
                    initializationFailed = true
                    null
                } else {
                    configureLocale(engine)
                    configureVoiceProfile(engine)
                    ready = true
                    initializationFailed = false

                    pendingRequest
                        ?.takeIf { it.generation == generation }
                        .also { pendingRequest = null }
                }
            }
        }

        if (initializationFailed) {
            logTts("tts_error=initialization_failed")
            onSpeechFinished()
        }
        request?.let(::speakNow)
    }

    private fun speakNow(request: SpeechRequest) {
        val engine = synchronized(lock) {
            if (closed || !ready || request.generation != generation) {
                return
            }
            tts
        } ?: return

        synchronized(lock) {
            speaking.set(false)
            activeUtteranceId = request.utteranceId
        }
        engine.stop()
        val result = engine.speak(
            request.text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            request.utteranceId
        )
        logTts(
            "tts_requested=true accepted=${result == TextToSpeech.SUCCESS} utteranceId=${request.utteranceId}"
        )
        if (result != TextToSpeech.SUCCESS) {
            synchronized(lock) {
                activeUtteranceId = null
                speaking.set(false)
            }
            logTts("tts_error=speak_result_$result utteranceId=${request.utteranceId}")
            onSpeechFinished()
        }
    }

    private fun isActiveUtterance(utteranceId: String?): Boolean =
        synchronized(lock) {
            utteranceId != null && utteranceId == activeUtteranceId && !closed
        }

    private fun clearActiveUtterance(utteranceId: String?) {
        synchronized(lock) {
            if (utteranceId == activeUtteranceId) {
                activeUtteranceId = null
            }
        }
    }

    private fun configureLocale(engine: TextToSpeech) {
        val preferred = Locale("es", "AR")
        val preferredResult = engine.setLanguage(preferred)
        if (preferredResult != TextToSpeech.LANG_MISSING_DATA &&
            preferredResult != TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            return
        }

        val latinFallback = Locale("es", "US")
        val fallbackResult = engine.setLanguage(latinFallback)
        if (fallbackResult == TextToSpeech.LANG_MISSING_DATA ||
            fallbackResult == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            engine.setLanguage(Locale("es"))
        }
    }

    private fun configureVoiceProfile(engine: TextToSpeech) {
        engine.setSpeechRate(EstelaVoiceProfile.SPEECH_RATE)
        engine.setPitch(EstelaVoiceProfile.PITCH)
        selectPremiumVoice(engine)
    }

    /**
     * V1.6 — elige la mejor voz en español disponible OFFLINE: es-AR primero,
     * después es-419/es-US/es-ES, priorizando calidad del engine. Solo se
     * loguea nombre/calidad/locale de la voz, nunca datos del usuario. Sin
     * candidatas, queda la voz por defecto.
     */
    private fun selectPremiumVoice(engine: TextToSpeech) {
        val best = runCatching { engine.voices }.getOrNull()
            ?.filter { voice ->
                voice.locale?.language == "es" && !voice.isNetworkConnectionRequired
            }
            ?.maxWithOrNull(compareBy({ voiceLocaleRank(it.locale) }, { it.quality }))
            ?: return
        val applied = runCatching { engine.setVoice(best) == TextToSpeech.SUCCESS }
            .getOrDefault(false)
        logTts(
            "voiceSelected=${best.name} quality=${best.quality} " +
                "locale=${best.locale} applied=$applied"
        )
    }

    private fun voiceLocaleRank(locale: Locale?): Int = when {
        locale == null -> 0
        locale.country == "AR" -> 4
        locale.toLanguageTag().startsWith("es-419") -> 3
        locale.country == "US" -> 3
        locale.country == "ES" -> 2
        else -> 1
    }

    private fun logTts(message: String) {
        if (BuildConfig.DEBUG) {
            Log.i(SCREEN_DIAGNOSTIC_TAG, message)
        }
    }

    companion object {
        private const val DEDUP_WINDOW_MILLIS = 5_000L
        private const val SCREEN_DIAGNOSTIC_TAG = "EstelaScreenDiagnostic"
    }
}
