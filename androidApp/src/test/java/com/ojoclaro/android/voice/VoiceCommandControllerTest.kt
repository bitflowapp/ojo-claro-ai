package com.ojoclaro.android.voice

import android.speech.SpeechRecognizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VoiceCommandControllerTest {

    @Test
    fun controllerDoesNotStoreAudio() {
        val controller = controllerWith(FakeSpeechInputEngine()).controller

        assertFalse(controller.storesAudio)
    }

    @Test
    fun missingMicrophonePermissionReturnsClearMessage() {
        val engine = FakeSpeechInputEngine()
        val errors = mutableListOf<String>()
        val controller = controllerWith(
            engine = engine,
            hasPermission = false,
            errors = errors
        ).controller

        controller.startListening()

        assertEquals(0, engine.startCount)
        assertEquals(VoiceListeningState.ERROR, controller.currentState)
        assertEquals(listOf(VoiceCommandController.MICROPHONE_PERMISSION_MESSAGE), errors)
        assertTrue(errors.single().contains("No tengo permiso", ignoreCase = true))
        assertTrue(errors.single().contains("botones", ignoreCase = true))
    }

    @Test
    fun startsListeningWhenEnabled() {
        val engine = FakeSpeechInputEngine()
        var ready = false
        val controller = controllerWith(
            engine = engine,
            onReady = { ready = true }
        ).controller

        controller.startListening()

        assertEquals(1, engine.startCount)
        assertTrue(controller.isListening)
        assertEquals(VoiceListeningState.LISTENING, controller.currentState)
        assertTrue(ready)
    }

    @Test
    fun doesNotStartTwiceWhenAlreadyListening() {
        val engine = FakeSpeechInputEngine()
        val controller = controllerWith(engine = engine).controller

        controller.startListening()
        controller.startListening()

        assertEquals(1, engine.startCount)
    }

    @Test
    fun finalTextIsForwardedWithoutAudioPersistence() {
        val engine = FakeSpeechInputEngine()
        val finals = mutableListOf<String>()
        val controller = controllerWith(
            engine = engine,
            finalTexts = finals
        ).controller

        controller.startListening()
        engine.emitFinalText("qué dice la pantalla")

        assertEquals(listOf("qué dice la pantalla"), finals)
        assertEquals(VoiceListeningState.PROCESSING, controller.currentState)
        assertFalse(controller.storesAudio)
    }

    @Test
    fun connectionErrorWithPartialTextProcessesUsefulTextOnce() {
        val engine = FakeSpeechInputEngine()
        val finals = mutableListOf<String>()
        val errors = mutableListOf<String>()
        val controller = controllerWith(
            engine = engine,
            finalTexts = finals,
            errors = errors
        ).controller

        controller.startListening()
        engine.emitPartialText("abrí WhatsApp y el del chat de Marco")
        engine.emitError(SpeechRecognizer.ERROR_NETWORK)
        engine.emitError(SpeechRecognizer.ERROR_NETWORK)

        assertEquals(listOf("abrí WhatsApp y el del chat de Marco"), finals)
        assertTrue(errors.isEmpty())
        assertEquals(VoiceListeningState.PROCESSING, controller.currentState)
    }

    @Test
    fun connectionErrorWithoutPartialTextReturnsFallbackMessage() {
        val engine = FakeSpeechInputEngine()
        val errors = mutableListOf<String>()
        val controller = controllerWith(
            engine = engine,
            errors = errors
        ).controller

        controller.startListening()
        engine.emitError(SpeechRecognizer.ERROR_NETWORK)

        assertEquals(listOf("No pude escuchar bien. Probá de nuevo en un momento."), errors)
        assertEquals(VoiceListeningState.ERROR, controller.currentState)
    }

    @Test
    fun retriesAfterNoMatchWithBackoff() {
        val engine = FakeSpeechInputEngine()
        val scheduler = FakeRetryScheduler()
        val errors = mutableListOf<String>()
        val controller = controllerWith(
            engine = engine,
            scheduler = scheduler,
            errors = errors
        ).controller

        controller.startListening()
        engine.emitError(SpeechRecognizer.ERROR_NO_MATCH)

        assertEquals(VoiceListeningState.WAITING_RETRY, controller.currentState)
        assertEquals(listOf(400L), scheduler.delays())
        assertTrue(errors.isEmpty())

        scheduler.runNext()

        assertEquals(2, engine.startCount)
        assertEquals(VoiceListeningState.LISTENING, controller.currentState)
    }

    @Test
    fun retriesAfterSpeechTimeoutWithBackoff() {
        val engine = FakeSpeechInputEngine()
        val scheduler = FakeRetryScheduler()
        val controller = controllerWith(
            engine = engine,
            scheduler = scheduler
        ).controller

        controller.startListening()
        engine.emitError(SpeechRecognizer.ERROR_SPEECH_TIMEOUT)

        assertEquals(listOf(400L), scheduler.delays())
        scheduler.runNext()
        assertEquals(2, engine.startCount)
    }

    @Test
    fun expectingResponseUsesExtendedModeAndRetriesNoSpeech() {
        val engine = FakeSpeechInputEngine()
        val scheduler = FakeRetryScheduler()
        val errors = mutableListOf<String>()
        val controller = controllerWith(
            engine = engine,
            scheduler = scheduler,
            errors = errors
        ).controller

        controller.setExpectingResponse(true)
        controller.startListening()
        engine.emitError(SpeechRecognizer.ERROR_SPEECH_TIMEOUT)

        assertEquals(SpeechListeningMode.EXPECTING_RESPONSE, engine.mode)
        assertEquals(VoiceListeningState.WAITING_RETRY, controller.currentState)
        assertEquals(listOf(700L), scheduler.delays())
        assertTrue(errors.isEmpty())

        scheduler.runNext()

        assertEquals(2, engine.startCount)
        assertEquals(VoiceListeningState.LISTENING, controller.currentState)
    }

    @Test
    fun expectingResponseTreatsNetworkErrorAsRecoverableWithoutClearingContext() {
        val engine = FakeSpeechInputEngine()
        val scheduler = FakeRetryScheduler()
        val errors = mutableListOf<String>()
        val controller = controllerWith(
            engine = engine,
            scheduler = scheduler,
            errors = errors
        ).controller

        controller.setExpectingResponse(true)
        controller.startListening()
        engine.emitError(SpeechRecognizer.ERROR_NETWORK)

        assertEquals(VoiceListeningState.WAITING_RETRY, controller.currentState)
        assertEquals(listOf(700L), scheduler.delays())
        assertTrue(errors.isEmpty())
    }

    @Test
    fun expectingResponseDoesNotRepeatFallbackHintInRetryLoop() {
        val engine = FakeSpeechInputEngine()
        val scheduler = FakeRetryScheduler()
        val errors = mutableListOf<String>()
        val controller = controllerWith(
            engine = engine,
            scheduler = scheduler,
            errors = errors
        ).controller

        controller.setExpectingResponse(true)
        controller.startListening()
        repeat(5) {
            engine.emitError(SpeechRecognizer.ERROR_NO_MATCH)
            scheduler.runNext()
        }

        assertTrue(errors.isEmpty())
        assertTrue(engine.startCount > 1)
    }

    @Test
    fun errorCodeIsExposedForDebug() {
        val engine = FakeSpeechInputEngine()
        val errorCodes = mutableListOf<Int?>()
        val controller = controllerWith(
            engine = engine,
            errorCodes = errorCodes
        ).controller

        controller.startListening()
        engine.emitError(SpeechRecognizer.ERROR_SPEECH_TIMEOUT)

        assertEquals(listOf<Int?>(SpeechRecognizer.ERROR_SPEECH_TIMEOUT), errorCodes)
        assertEquals("NO_SPEECH", VoiceCommandController.errorName(SpeechRecognizer.ERROR_SPEECH_TIMEOUT))
    }

    @Test
    fun recoverableErrorsOnlySpeakAfterSeveralFailures() {
        val engine = FakeSpeechInputEngine()
        val scheduler = FakeRetryScheduler()
        val errors = mutableListOf<String>()
        val controller = controllerWith(
            engine = engine,
            scheduler = scheduler,
            errors = errors
        ).controller

        controller.startListening()
        repeat(3) {
            engine.emitError(SpeechRecognizer.ERROR_NO_MATCH)
            scheduler.runNext()
        }

        assertTrue(errors.isEmpty())

        engine.emitError(SpeechRecognizer.ERROR_NO_MATCH)

        assertEquals(listOf("Sigo escuchando."), errors)
    }

    @Test
    fun doesNotRetryWhenStoppedByUser() {
        val engine = FakeSpeechInputEngine()
        val scheduler = FakeRetryScheduler()
        val controller = controllerWith(
            engine = engine,
            scheduler = scheduler
        ).controller

        controller.startListening()
        controller.stopListening()
        engine.emitError(SpeechRecognizer.ERROR_NO_MATCH)

        assertEquals(VoiceListeningState.STOPPED_BY_USER, controller.currentState)
        assertTrue(scheduler.pendingCount() == 0)
    }

    @Test
    fun callarCancelsListeningAndSchedulesFastResume() {
        val engine = FakeSpeechInputEngine()
        val scheduler = FakeRetryScheduler()
        val controller = controllerWith(
            engine = engine,
            scheduler = scheduler
        ).controller
        var ttsStopped = false
        val dispatcher = VoiceCommandDispatcher(
            executeCommand = {},
            stopSpeechNow = {
                ttsStopped = true
                controller.stopForCommandAndResume()
            }
        )

        controller.startListening()
        dispatcher.onPartialText("callar por favor")

        assertTrue(ttsStopped)
        assertTrue(engine.stopCount > 0)
        assertEquals(VoiceListeningState.WAITING_RETRY, controller.currentState)
        assertEquals(listOf(300L), scheduler.delays())
    }

    @Test
    fun resumesListeningAfterSpokenResponseFinishes() {
        val engine = FakeSpeechInputEngine()
        val controller = controllerWith(engine = engine).controller

        controller.startListening()
        controller.pauseForSpeech()

        assertEquals(VoiceListeningState.SPEAKING, controller.currentState)
        assertTrue(engine.stopCount > 0)

        controller.resumeAfterSpeech()

        assertEquals(2, engine.startCount)
        assertEquals(VoiceListeningState.LISTENING, controller.currentState)
    }

    /**
     * Reproduce el bucle observado en QA Samsung: TTS empieza a hablar mientras el
     * mic seguia activo; el mic capturaba el audio del propio TTS y disparaba
     * frases basura. La fix es que `pauseForSpeech` corte el engine y que
     * llamadas a `startListening` durante SPEAKING NO reabran el mic.
     */
    @Test
    fun startListeningDuringSpeakingDoesNotReopenMic() {
        val engine = FakeSpeechInputEngine()
        val controller = controllerWith(engine = engine).controller

        controller.startListening()
        val startsBeforePause = engine.startCount
        controller.pauseForSpeech()
        // Simulamos un disparador externo (ej: un re-render que llama startListening).
        controller.startListening()

        // No deberia haber un nuevo start mientras esta en SPEAKING.
        assertEquals(startsBeforePause, engine.startCount)
        assertEquals(VoiceListeningState.SPEAKING, controller.currentState)
    }

    @Test
    fun recoverableErrorAfterPauseForSpeechDoesNotRestartMicrophone() {
        val engine = FakeSpeechInputEngine()
        val scheduler = FakeRetryScheduler()
        val controller = controllerWith(
            engine = engine,
            scheduler = scheduler
        ).controller

        controller.startListening()
        controller.pauseForSpeech()
        engine.emitError(SpeechRecognizer.ERROR_CLIENT)

        assertEquals(VoiceListeningState.SPEAKING, controller.currentState)
        assertEquals(1, engine.startCount)
        assertEquals(0, scheduler.pendingCount())
    }

    @Test
    fun recoverableErrorAfterPauseListeningDoesNotRestartMicrophone() {
        val engine = FakeSpeechInputEngine()
        val scheduler = FakeRetryScheduler()
        val controller = controllerWith(
            engine = engine,
            scheduler = scheduler
        ).controller

        controller.startListening()
        controller.pauseListening()
        engine.emitError(SpeechRecognizer.ERROR_NO_MATCH)

        assertEquals(VoiceListeningState.IDLE, controller.currentState)
        assertEquals(1, engine.startCount)
        assertEquals(0, scheduler.pendingCount())
    }

    private data class Harness(
        val controller: VoiceCommandController,
        val engine: FakeSpeechInputEngine,
        val scheduler: FakeRetryScheduler
    )

    private fun controllerWith(
        engine: FakeSpeechInputEngine = FakeSpeechInputEngine(),
        scheduler: FakeRetryScheduler = FakeRetryScheduler(),
        hasPermission: Boolean = true,
        partialTexts: MutableList<String> = mutableListOf(),
        finalTexts: MutableList<String> = mutableListOf(),
        errors: MutableList<String> = mutableListOf(),
        errorCodes: MutableList<Int?> = mutableListOf(),
        onReady: () -> Unit = {}
    ): Harness =
        Harness(
            controller = VoiceCommandController(
                engine = engine,
                hasRecordAudioPermission = { hasPermission },
                onPartialTextCallback = partialTexts::add,
                onFinalTextCallback = finalTexts::add,
                onErrorCallback = errors::add,
                onReadyCallback = onReady,
                onErrorCodeCallback = errorCodes::add,
                retryScheduler = scheduler
            ),
            engine = engine,
            scheduler = scheduler
        )

    private class FakeSpeechInputEngine : SpeechInputEngine {
        override var listener: SpeechInputEngine.Listener? = null
        override var isListening: Boolean = false
            private set
        var startCount: Int = 0
            private set
        var stopCount: Int = 0
            private set
        var mode: SpeechListeningMode = SpeechListeningMode.DEFAULT
            private set

        override fun startListening() {
            startCount += 1
            isListening = true
            listener?.onReady()
        }

        override fun stopListening() {
            stopCount += 1
            isListening = false
        }

        override fun destroy() {
            isListening = false
        }

        override fun setListeningMode(mode: SpeechListeningMode) {
            this.mode = mode
        }

        fun emitFinalText(text: String) {
            isListening = false
            listener?.onFinalText(text)
        }

        fun emitPartialText(text: String) {
            listener?.onPartialText(text)
        }

        fun emitError(errorCode: Int) {
            isListening = false
            listener?.onError(errorCode)
        }
    }

    private class FakeRetryScheduler : VoiceRetryScheduler {
        private data class Scheduled(
            val delayMillis: Long,
            val action: () -> Unit,
            var cancelled: Boolean = false
        )

        private val scheduled = mutableListOf<Scheduled>()

        override fun schedule(delayMillis: Long, action: () -> Unit): VoiceRetryHandle {
            val item = Scheduled(delayMillis, action)
            scheduled += item
            return VoiceRetryHandle { item.cancelled = true }
        }

        fun delays(): List<Long> =
            scheduled.filterNot { it.cancelled }.map { it.delayMillis }

        fun pendingCount(): Int =
            scheduled.count { !it.cancelled }

        fun runNext() {
            val item = scheduled.firstOrNull { !it.cancelled } ?: return
            item.cancelled = true
            item.action()
        }
    }
}
