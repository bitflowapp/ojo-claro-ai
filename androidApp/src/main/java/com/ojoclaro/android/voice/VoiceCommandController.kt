package com.ojoclaro.android.voice

import android.speech.SpeechRecognizer
import com.ojoclaro.android.performance.RobotLoopInstrumentation
import com.ojoclaro.android.performance.RobotLoopLogResult
import com.ojoclaro.android.performance.RobotLoopLogStage
import com.ojoclaro.android.performance.RobotLoopSafeLogEvent

class VoiceCommandController(
    private val engine: SpeechInputEngine,
    private val hasRecordAudioPermission: () -> Boolean,
    private val onPartialTextCallback: (String) -> Unit,
    private val onFinalTextCallback: (String) -> Unit,
    private val onErrorCallback: (String) -> Unit,
    private val onReadyCallback: () -> Unit,
    private val onStateChanged: (VoiceListeningState) -> Unit = {},
    private val onErrorCodeCallback: (Int?) -> Unit = {},
    private val onDiagnosticCallback: (VoiceListeningDiagnostic) -> Unit = {},
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
    private var currentSession: VoiceListeningSession? = null
    private var nextSessionId = 0L
    private var consecutiveNoMatch = 0
    private var consecutiveTimeouts = 0

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
                publishDiagnostic(VoiceHearingStatus.LISTENING)
                onReadyCallback()
            }

            override fun onPartialText(text: String) {
                val partial = text.trim().takeIf { it.isNotBlank() } ?: return
                val safePartial = synchronized(lock) {
                    currentSession = (currentSession ?: newSessionLocked()).recordPartial(partial)
                    lastUsefulRecognitionText = currentSession?.bestPartialCandidate
                    currentSession?.lastPartialTextRedacted.orEmpty()
                }
                if (safePartial.isNotBlank()) {
                    onPartialTextCallback(safePartial)
                }
                publishDiagnostic(VoiceHearingStatus.LISTENING)
            }

            override fun onFinalText(text: String) {
                handleFinalRecognitionText(text)
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
        publishDiagnostic(VoiceHearingStatus.IDLE)
    }

    fun pauseListening() {
        cancelRetry()
        updateState(VoiceListeningState.IDLE)
        engine.stopListening()
        publishDiagnostic(VoiceHearingStatus.IDLE)
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
        publishDiagnostic(VoiceHearingStatus.IDLE)
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
        publishDiagnostic(VoiceHearingStatus.IDLE)
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
            currentSession = newSessionLocked()
            lastUsefulRecognitionText = null
            dispatchedRecognitionText = null
        }
        cancelRetry()
        updateState(VoiceListeningState.LISTENING)
        publishDiagnostic(VoiceHearingStatus.LISTENING)
        engine.startListening()
    }

    private fun handleFinalRecognitionText(text: String) {
        val decision = synchronized(lock) {
            val updated = (currentSession ?: newSessionLocked()).recordFinal(text)
            val result = updated.textForSubmission(text)
            currentSession = if (result.usedPartial) updated.markUsedPartial() else updated
            result
        }
        val finalText = decision.text.trim().takeIf { it.isNotBlank() } ?: return
        dispatchRecognizedTextOnce(finalText, usedPartial = decision.usedPartial)
    }

    private fun handleRecognitionError(errorCode: Int?) {
        onErrorCodeCallback(errorCode)
        val category = VoiceSpeechErrorPolicy.categoryFor(errorCode)
        synchronized(lock) {
            currentSession = (currentSession ?: newSessionLocked()).recordError(
                errorCode = errorCode,
                retryCount = consecutiveRecoverableErrors + 1,
                shouldAutoRestart = VoiceSpeechErrorPolicy.shouldAutoRestart(category)
            )
            if (category == SpeechErrorCategory.NO_MATCH) consecutiveNoMatch += 1
            if (category == SpeechErrorCategory.SPEECH_TIMEOUT) consecutiveTimeouts += 1
        }
        if (VoiceSpeechErrorPolicy.shouldResetRecognizer(category)) {
            engine.resetRecognizer()
        }
        consumePartialTextFor(errorCode)?.let { partialText ->
            synchronized(lock) {
                currentSession = currentSession?.markUsedPartial()
            }
            dispatchRecognizedTextOnce(partialText, usedPartial = true)
            return
        }

        if (currentState == VoiceListeningState.PROCESSING) return

        if (isRecoverable(category)) {
            handleRecoverableError(category)
        } else {
            synchronized(lock) {
                autoListeningEnabled = false
            }
            updateState(VoiceListeningState.ERROR)
            recordSpeechRecognizerLog(
                result = RobotLoopLogResult.NOT_UNDERSTOOD,
                category = category,
                usedPartial = false
            )
            publishDiagnostic(VoiceSpeechErrorPolicy.hearingStatusFor(category))
            onErrorCallback(VoiceSpeechErrorPolicy.humanMessageFor(category))
        }
    }

    private fun handleRecoverableError(category: SpeechErrorCategory) {
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
        recordSpeechRecognizerLog(
            result = RobotLoopLogResult.NOT_UNDERSTOOD,
            category = category,
            usedPartial = false
        )
        publishDiagnostic(VoiceSpeechErrorPolicy.hearingStatusFor(category))
        if (shouldSpeakRecoverableHint(category, expectingResponse)) {
            keepSilentRecovery = false
            onErrorCallback(VoiceSpeechErrorPolicy.humanMessageFor(category))
        }
        scheduleRetry(backoffDelayMillis(consecutiveRecoverableErrors))
    }

    private fun dispatchRecognizedTextOnce(text: String, usedPartial: Boolean) {
        val finalText = text.trim().takeIf { it.isNotBlank() } ?: return
        if (dispatchedRecognitionText == finalText) return
        dispatchedRecognitionText = finalText
        lastUsefulRecognitionText = null
        cancelRetry()
        consecutiveRecoverableErrors = 0
        consecutiveNoMatch = 0
        consecutiveTimeouts = 0
        recordSpeechRecognizerLog(
            result = RobotLoopLogResult.UNDERSTOOD,
            category = null,
            usedPartial = usedPartial
        )
        updateState(VoiceListeningState.PROCESSING)
        publishDiagnostic(if (usedPartial) VoiceHearingStatus.USING_PARTIAL else VoiceHearingStatus.IDLE)
        onFinalTextCallback(finalText)
    }

    private fun consumePartialTextFor(errorCode: Int?): String? {
        if (!shouldUsePartialTextForError(errorCode)) return null
        val decision = synchronized(lock) {
            currentSession?.partialForError()
        } ?: return null
        val partialText = decision.text.trim().takeIf { it.isNotBlank() } ?: return null
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

    private fun newSessionLocked(): VoiceListeningSession {
        nextSessionId += 1L
        return VoiceListeningSession(
            sessionId = nextSessionId,
            startedAt = System.currentTimeMillis(),
            consecutiveNoMatch = consecutiveNoMatch,
            consecutiveTimeouts = consecutiveTimeouts,
            wasSpeakingWhenStarted = state == VoiceListeningState.SPEAKING,
            wasRobotEnabled = autoListeningEnabled
        )
    }

    private fun isRecoverable(category: SpeechErrorCategory): Boolean =
        category != SpeechErrorCategory.INSUFFICIENT_PERMISSIONS

    private fun shouldUsePartialTextForError(errorCode: Int?): Boolean =
        errorCode == SpeechRecognizer.ERROR_NETWORK ||
            errorCode == SpeechRecognizer.ERROR_NETWORK_TIMEOUT ||
            errorCode == SpeechRecognizer.ERROR_NO_MATCH ||
            errorCode == SpeechRecognizer.ERROR_SPEECH_TIMEOUT

    private fun shouldSpeakRecoverableHint(
        category: SpeechErrorCategory,
        expectingResponse: Boolean
    ): Boolean {
        if (expectingResponse) return false
        return when (category) {
            SpeechErrorCategory.NO_MATCH,
            SpeechErrorCategory.SPEECH_TIMEOUT -> true
            SpeechErrorCategory.RECOGNIZER_BUSY,
            SpeechErrorCategory.CLIENT,
            SpeechErrorCategory.NETWORK,
            SpeechErrorCategory.UNKNOWN -> consecutiveRecoverableErrors >= QUIET_FAILURES_BEFORE_HINT &&
                keepSilentRecovery
            SpeechErrorCategory.INSUFFICIENT_PERMISSIONS -> false
        }
    }

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

    private fun publishDiagnostic(status: VoiceHearingStatus) {
        val session = synchronized(lock) { currentSession } ?: return
        onDiagnosticCallback(session.diagnostic(status, engine.speechEngine))
    }

    private fun recordSpeechRecognizerLog(
        result: RobotLoopLogResult,
        category: SpeechErrorCategory?,
        usedPartial: Boolean
    ) {
        val session = synchronized(lock) { currentSession } ?: return
        RobotLoopInstrumentation.recordSafeLog(
            RobotLoopSafeLogEvent(
                stage = RobotLoopLogStage.VOICE_COMMAND,
                result = result,
                durationMillis = (System.currentTimeMillis() - session.startedAt).coerceAtLeast(0L),
                handler = "speech_recognizer",
                commandRedacted = true,
                sessionId = session.sessionId,
                errorCategory = category?.name,
                hasPartial = session.hasPartialCandidate || session.lastPartialTextRedacted.isNotBlank(),
                usedPartial = usedPartial,
                speechEngine = engine.speechEngine.safeLabel
            )
        )
    }

    companion object {
        const val MICROPHONE_PERMISSION_MESSAGE =
            "No tengo permiso para usar el micrófono. Podés activarlo desde ajustes o usar los botones de la pantalla."

        fun errorName(errorCode: Int?): String =
            VoiceSpeechErrorPolicy.categoryFor(errorCode).name

        private const val FAST_RESTART_DELAY_MILLIS = 300L
        private const val QUIET_FAILURES_BEFORE_HINT = 4
    }
}
