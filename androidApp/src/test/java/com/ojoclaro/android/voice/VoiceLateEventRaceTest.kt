package com.ojoclaro.android.voice

import android.speech.SpeechRecognizer
import com.ojoclaro.android.performance.RobotLoopInstrumentation
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * FASE 4 — Carreras y eventos TARDÍOS del ciclo de voz (deterministas, engine y
 * scheduler falsos). Verifica las invariantes duras del piloto:
 *
 *   - ningún resultado/acción TARDÍA se ejecuta tras una cancelación;
 *   - no hay TTS/mensaje inesperado tras detener;
 *   - no hay doble recognizer;
 *   - no queda estado zombie tras start/stop rápidos.
 *
 * Complementa [VoiceSessionLifecycleTest] con permutaciones específicas de
 * timing (timeout/red/barge-in seguidos de stop y de un final tardío).
 */
class VoiceLateEventRaceTest {

    @AfterTest
    fun tearDown() {
        RobotLoopInstrumentation.safeLogsEnabled = true
        RobotLoopInstrumentation.localSafeLogSink = null
        RobotLoopInstrumentation.clear()
    }

    @Test fun r1_cancelThenLateFinal_ignored() {
        val h = harness()
        h.controller.startListening()
        h.controller.stopListening()
        h.engine.emitFinal("abrir whatsapp")
        assertTrue(h.finals.isEmpty(), "final tardío tras cancel: ${h.finals}")
        assertEquals(VoiceListeningState.STOPPED_BY_USER, h.controller.currentState)
    }

    @Test fun r2_timeoutThenStopThenLateFinal_ignored() {
        val h = harness()
        h.controller.startListening()
        h.engine.emitError(SpeechRecognizer.ERROR_SPEECH_TIMEOUT) // WAITING_RETRY
        h.controller.stopListening()
        h.engine.emitFinal("transferile plata") // tardío + peligroso
        assertTrue(h.finals.isEmpty(), "final tardío tras timeout+stop: ${h.finals}")
        assertEquals(VoiceListeningState.STOPPED_BY_USER, h.controller.currentState)
    }

    @Test fun r3_networkErrorThenStopThenLateFinal_ignored() {
        val h = harness()
        h.controller.startListening()
        h.engine.emitError(SpeechRecognizer.ERROR_NETWORK)
        h.controller.stopListening()
        h.engine.emitFinal("mandá una foto")
        assertTrue(h.finals.isEmpty(), "final tardío tras error de red+stop: ${h.finals}")
    }

    @Test fun r4_bargeInThenStopThenLateFinal_ignored() {
        val h = harness()
        h.controller.startListening()
        h.controller.stopForCommandAndResume() // barge-in: WAITING_RETRY + retry rápido
        h.controller.stopListening() // el usuario igual frena del todo
        h.engine.emitFinal("pagale a juan")
        assertTrue(h.finals.isEmpty(), "final tardío tras barge-in+stop: ${h.finals}")
        assertEquals(VoiceListeningState.STOPPED_BY_USER, h.controller.currentState)
    }

    @Test fun r5_lateErrorAfterStop_noSpokenMessageNoRetry() {
        val h = harness()
        h.controller.startListening()
        h.controller.stopListening()
        h.engine.emitError(SpeechRecognizer.ERROR_NO_MATCH)
        assertTrue(h.errors.isEmpty(), "no debe hablar un error tras stop: ${h.errors}")
        assertEquals(0, h.scheduler.pendingCount(), "no debe reintentar tras stop")
        assertEquals(VoiceListeningState.STOPPED_BY_USER, h.controller.currentState)
    }

    @Test fun r6_rapidStopStartCycles_noZombieEndsListening() {
        val h = harness()
        repeat(4) {
            h.controller.startListening()
            h.controller.stopListening()
        }
        h.controller.startListening()
        assertEquals(VoiceListeningState.LISTENING, h.controller.currentState)
        // Un start por ciclo (5 starts), nunca dos recognizers a la vez.
        assertEquals(5, h.engine.startCount)
    }

    @Test fun r7_doubleStartThenFinal_dispatchedOnce() {
        val h = harness()
        h.controller.startListening()
        h.controller.startListening() // segundo toque: no abre otro recognizer
        assertEquals(1, h.engine.startCount)
        h.engine.emitFinal("abrir whatsapp")
        assertEquals(listOf("abrir whatsapp"), h.finals)
    }

    @Test fun r8_lateFinalDuringSpeakingDoesNotProcessThenStopStaysClean() {
        // Tras pauseForSpeech el motor está cortado; si llega un final viejo y el
        // usuario YA frenó, no se procesa. (El caso SPEAKING puro lo cubre el
        // fence de TTS por utteranceId en SpeechController.)
        val h = harness()
        h.controller.startListening()
        h.controller.pauseForSpeech()
        h.controller.stopListening()
        h.engine.emitFinal("borrá el chat")
        assertTrue(h.finals.isEmpty(), "final tardío peligroso ignorado: ${h.finals}")
    }

    // -------------------------------------------------------------------------
    private class Harness(
        val controller: VoiceCommandController,
        val engine: FakeEngine,
        val scheduler: FakeRetryScheduler,
        val finals: MutableList<String>,
        val errors: MutableList<String>
    )

    private fun harness(): Harness {
        val engine = FakeEngine()
        val scheduler = FakeRetryScheduler()
        val finals = mutableListOf<String>()
        val errors = mutableListOf<String>()
        val controller = VoiceCommandController(
            engine = engine,
            hasRecordAudioPermission = { true },
            onPartialTextCallback = {},
            onFinalTextCallback = finals::add,
            onErrorCallback = errors::add,
            onReadyCallback = {},
            retryScheduler = scheduler
        )
        return Harness(controller, engine, scheduler, finals, errors)
    }

    private class FakeEngine : SpeechInputEngine {
        override var listener: SpeechInputEngine.Listener? = null
        override var speechEngine: VoiceSpeechEngine = VoiceSpeechEngine.PLATFORM_DEFAULT
        override var isListening: Boolean = false
            private set
        var startCount: Int = 0
            private set

        override fun startListening() { startCount += 1; isListening = true; listener?.onReady() }
        override fun stopListening() { isListening = false }
        override fun resetRecognizer() { isListening = false }
        override fun destroy() { isListening = false }
        override fun setListeningMode(mode: SpeechListeningMode) {}

        fun emitFinal(t: String) { isListening = false; listener?.onFinalText(t) }
        fun emitError(c: Int) { isListening = false; listener?.onError(c) }
    }

    private class FakeRetryScheduler : VoiceRetryScheduler {
        private data class Scheduled(val delayMillis: Long, val action: () -> Unit, var cancelled: Boolean = false)
        private val scheduled = mutableListOf<Scheduled>()
        override fun schedule(delayMillis: Long, action: () -> Unit): VoiceRetryHandle {
            val item = Scheduled(delayMillis, action)
            scheduled += item
            return VoiceRetryHandle { item.cancelled = true }
        }
        fun pendingCount(): Int = scheduled.count { !it.cancelled }
    }
}
