package com.ojoclaro.android.voice

import android.speech.SpeechRecognizer

class VoiceCommandController(
    private val engine: SpeechInputEngine,
    private val hasRecordAudioPermission: () -> Boolean,
    private val onPartialTextCallback: (String) -> Unit,
    private val onFinalTextCallback: (String) -> Unit,
    private val onErrorCallback: (String) -> Unit,
    private val onReadyCallback: () -> Unit,
    private val onStateChanged: (VoiceListeningState) -> Unit = {},
    private val onErrorCodeCallback: (Int?) -> Unit = {},
    private val retryScheduler: VoiceRetryScheduler = VoiceRetryScheduler { _, action ->
        action()
        VoiceRetryHandle {}
    }
) {

    private val lock = Any()
    private var state: VoiceListeningState = VoiceListeningState.IDLE
    private var autoListeningEnabled = false
    private var destroyed = false
    private var retryHandle: VoiceRetryHandle? = null
    private var retryGeneration = 0L
    private var consecutiveRecoverableErrors = 0
    private var keepSilentRecovery = true
    private var lastUsefulRecognitionText: String? = null
    private var dispatchedRecognitionText: String? = null
    private var expectingUserResponse = false

    val isListening: Boolean
        get() = currentState == VoiceListeningState.LISTENING || engine.isListening

    val currentState: VoiceListeningState
        get() = synchronized(lock) { state }

    val storesAudio: Boolean
        get() = false

    init {
        engine.listener = object : SpeechInputEngine.Listener {
            override fun onReady() {
                updateState(VoiceListeningState.LISTENING)
                onReadyCallback()
            }

            override fun onPartialText(text: String) {
                text.trim().takeIf { it.isNotBlank() }?.let { partial ->
                    lastUsefulRecognitionText = partial
                    onPartialTextCallback(partial)
                }
            }

            override fun onFinalText(text: String) {
                val finalText = text.trim().takeIf { it.isNotBlank() } ?: return
                dispatchRecognizedTextOnce(finalText)
            }

            override fun onError(errorCode: Int?) {
                handleRecognitionError(errorCode)
            }
        }
    }

    fun startListening() {
        synchronized(lock) {
            autoListeningEnabled = true
            keepSilentRecovery = true
            lastUsefulRecognitionText = null
            dispatchedRecognitionText = null
        }
        updateEngineListeningMode()
        startListeningIfAllowed()
    }

    fun setExpectingResponse(expecting: Boolean) {
        synchronized(lock) {
            if (expectingUserResponse == expecting) return@synchronized
            expectingUserResponse = expecting
            keepSilentRecovery = true
            consecutiveRecoverableErrors = 0
        }
        updateEngineListeningMode()
    }

    fun pauseForSpeech() {
        cancelRetry()
        updateState(VoiceListeningState.SPEAKING)
        engine.stopListening()
    }

    fun pauseListening() {
        cancelRetry()
        updateState(VoiceListeningState.IDLE)
        engine.stopListening()
    }

    fun resumeAfterSpeech() {
        val shouldResume = synchronized(lock) {
            !destroyed && autoListeningEnabled
        }
        if (!shouldResume) return
        consecutiveRecoverableErrors = 0
        updateState(VoiceListeningState.IDLE)
        startListeningIfAllowed()
    }

    fun stopForCommandAndResume() {
        synchronized(lock) {
            autoListeningEnabled = true
            keepSilentRecovery = true
            consecutiveRecoverableErrors = 0
        }
        cancelRetry()
        engine.stopListening()
        updateState(VoiceListeningState.WAITING_RETRY)
        scheduleRetry(FAST_RESTART_DELAY_MILLIS)
    }

    fun stopListening() {
        synchronized(lock) {
            autoListeningEnabled = false
            retryGeneration += 1L
        }
        cancelRetry()
        updateState(VoiceListeningState.STOPPED_BY_USER)
        engine.stopListening()
    }

    fun destroy() {
        synchronized(lock) {
            destroyed = true
            autoListeningEnabled = false
            retryGeneration += 1L
        }
        cancelRetry()
        updateState(VoiceListeningState.STOPPED_BY_USER)
        engine.destroy()
    }

    private fun startListeningIfAllowed() {
        if (!hasRecordAudioPermission()) {
            synchronized(lock) {
                autoListeningEnabled = false
            }
            updateState(VoiceListeningState.ERROR)
            onErrorCallback(MICROPHONE_PERMISSION_MESSAGE)
            return
        }

        synchronized(lock) {
            if (destroyed) return
            if (state == VoiceListeningState.LISTENING || engine.isListening) return
            if (state == VoiceListeningState.SPEAKING || state == VoiceListeningState.PROCESSING) return
            retryGeneration += 1L
        }
        cancelRetry()
        updateState(VoiceListeningState.LISTENING)
        engine.startListening()
    }

    private fun handleRecognitionError(errorCode: Int?) {
        onErrorCodeCallback(errorCode)
        consumePartialTextFor(errorCode)?.let { partialText ->
            dispatchRecognizedTextOnce(partialText)
            return
        }

        if (currentState == VoiceListeningState.PROCESSING) return

        if (isRecoverable(errorCode)) {
            handleRecoverableError()
        } else {
            synchronized(lock) {
                autoListeningEnabled = false
            }
            updateState(VoiceListeningState.ERROR)
            onErrorCallback(humanMessageFor(errorCode))
        }
    }

    private fun handleRecoverableError() {
        val expectingResponse = synchronized(lock) { expectingUserResponse }
        val shouldRetry = synchronized(lock) {
            !destroyed &&
                autoListeningEnabled &&
                state != VoiceListeningState.STOPPED_BY_USER &&
                state != VoiceListeningState.SPEAKING &&
                state != VoiceListeningState.PROCESSING &&
                state != VoiceListeningState.IDLE
        }
        if (!shouldRetry) return

        consecutiveRecoverableErrors += 1
        updateState(VoiceListeningState.WAITING_RETRY)
        if (!expectingResponse &&
            consecutiveRecoverableErrors == QUIET_FAILURES_BEFORE_HINT &&
            keepSilentRecovery
        ) {
            keepSilentRecovery = false
            onErrorCallback("Sigo escuchando.")
        }
        scheduleRetry(backoffDelayMillis(consecutiveRecoverableErrors))
    }

    private fun dispatchRecognizedTextOnce(text: String) {
        val finalText = text.trim().takeIf { it.isNotBlank() } ?: return
        if (dispatchedRecognitionText == finalText) return
        dispatchedRecognitionText = finalText
        lastUsefulRecognitionText = null
        cancelRetry()
        consecutiveRecoverableErrors = 0
        updateState(VoiceListeningState.PROCESSING)
        onFinalTextCallback(finalText)
    }

    private fun consumePartialTextFor(errorCode: Int?): String? {
        if (!shouldUsePartialTextForError(errorCode)) return null
        val partialText = lastUsefulRecognitionText
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return null
        lastUsefulRecognitionText = null
        return partialText
    }

    private fun scheduleRetry(delayMillis: Long) {
        cancelRetry()
        val generation = synchronized(lock) {
            retryGeneration += 1L
            retryGeneration
        }
        retryHandle = retryScheduler.schedule(delayMillis) {
            val shouldStart = synchronized(lock) {
                !destroyed &&
                    autoListeningEnabled &&
                    retryGeneration == generation &&
                    state != VoiceListeningState.SPEAKING &&
                    state != VoiceListeningState.PROCESSING &&
                    state != VoiceListeningState.STOPPED_BY_USER
            }
            if (shouldStart) {
                startListeningIfAllowed()
            }
        }
    }

    private fun cancelRetry() {
        retryHandle?.cancel()
        retryHandle = null
    }

    private fun updateState(next: VoiceListeningState) {
        val changed = synchronized(lock) {
            if (state == next) {
                false
            } else {
                state = next
                true
            }
        }
        if (changed) onStateChanged(next)
    }

    private fun isRecoverable(errorCode: Int?): Boolean {
        val expectingResponse = synchronized(lock) { expectingUserResponse }
        return errorCode == SpeechRecognizer.ERROR_NO_MATCH ||
            errorCode == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
            errorCode == SpeechRecognizer.ERROR_CLIENT ||
            errorCode == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
            (
                expectingResponse &&
                    (
                        errorCode == SpeechRecognizer.ERROR_NETWORK ||
                            errorCode == SpeechRecognizer.ERROR_NETWORK_TIMEOUT ||
                            errorCode == SpeechRecognizer.ERROR_SERVER
                    )
            )
    }

    private fun shouldUsePartialTextForError(errorCode: Int?): Boolean =
        errorCode == SpeechRecognizer.ERROR_NETWORK ||
            errorCode == SpeechRecognizer.ERROR_NETWORK_TIMEOUT ||
            errorCode == SpeechRecognizer.ERROR_NO_MATCH ||
            errorCode == SpeechRecognizer.ERROR_SPEECH_TIMEOUT

    private fun backoffDelayMillis(errorCount: Int): Long {
        val expectingResponse = synchronized(lock) { expectingUserResponse }
        return if (expectingResponse) {
            when (errorCount) {
                1 -> 700L
                2 -> 1_000L
                3 -> 1_500L
                else -> 2_000L
            }
        } else {
            when (errorCount) {
                1 -> 400L
                2 -> 800L
                3 -> 1_200L
                else -> 2_000L
            }
        }
    }

    private fun updateEngineListeningMode() {
        val mode = synchronized(lock) {
            if (expectingUserResponse) {
                SpeechListeningMode.EXPECTING_RESPONSE
            } else {
                SpeechListeningMode.DEFAULT
            }
        }
        engine.setListeningMode(mode)
    }

    private fun humanMessageFor(errorCode: Int?): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO ->
                "No pude usar el micrófono. Revisá el permiso y probá de nuevo."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                MICROPHONE_PERMISSION_MESSAGE
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                "No pude escuchar bien. Probá de nuevo en un momento."
            SpeechRecognizer.ERROR_SERVER ->
                "El servicio de voz del teléfono no respondió. Probá otra vez."
            else ->
                "No pude escuchar bien. Probá otra vez."
        }
    }

    companion object {
        const val MICROPHONE_PERMISSION_MESSAGE =
            "Para usar Ojo Claro por voz, activá el micrófono. No guardo audio."
        fun errorName(errorCode: Int?): String =
            when (errorCode) {
                SpeechRecognizer.ERROR_AUDIO -> "AUDIO"
                SpeechRecognizer.ERROR_CLIENT -> "CLIENT"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "INSUFFICIENT_PERMISSIONS"
                SpeechRecognizer.ERROR_NETWORK -> "NETWORK"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "NETWORK_TIMEOUT"
                SpeechRecognizer.ERROR_NO_MATCH -> "NO_MATCH"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "RECOGNIZER_BUSY"
                SpeechRecognizer.ERROR_SERVER -> "SERVER"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "NO_SPEECH"
                null -> "UNKNOWN"
                else -> "ERROR_$errorCode"
            }
        private const val FAST_RESTART_DELAY_MILLIS = 300L
        private const val QUIET_FAILURES_BEFORE_HINT = 4
    }
}
