package com.ojoclaro.android.voice

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * H2 (red team) — una sesión de reconocimiento consume A LO SUMO un final, y SOLO
 * mientras está escuchando. Un final que llega en PROCESSING/SPEAKING/IDLE/
 * STOPPED, un segundo final, o un callback de una sesión vieja, NO se despachan.
 *
 * Defensa en profundidad en DOS capas reales:
 *  - Controller ([VoiceCommandController]): fence por estado (== LISTENING) +
 *    consumo único por sesión. Cubre A/B/C/E/F/G.
 *  - Engine ([AndroidSpeechInputEngine]): fence por generación de recognizer +
 *    flag `listening` (isCurrentRecognizerCallback). Cubre el cambio de sesión
 *    (D): un callback de un recognizer viejo se descarta antes del controller.
 *
 * No tautológico: ejercita el VoiceCommandController real con un engine falso que
 * dispara callbacks; la mutación que quita el fence (FASE 4) lo pone en rojo.
 */
class VoiceRecognitionSessionFenceTest {

    // A. dos finales en la misma sesión → solo el primero se consume.
    @Test fun fenceA_secondFinalInSameSessionIsIgnored() {
        val h = harness()
        h.controller.startListening()
        h.engine.emitFinal("cancelá")
        h.engine.emitFinal("respondéle que ya voy")
        assertEquals(listOf("cancelá"), h.finals, "solo el primer final puede consumirse")
    }

    // B. final durante TTS (pauseForSpeech) → cero despacho.
    @Test fun fenceB_finalDuringSpeakingIsIgnored() {
        val h = harness()
        h.controller.startListening()
        h.controller.pauseForSpeech()
        h.engine.emitFinal("abrí WhatsApp")
        assertTrue(h.finals.isEmpty(), "final durante SPEAKING no se despacha: ${h.finals}")
    }

    // C. final durante pausa (pauseListening) → cero despacho.
    @Test fun fenceC_finalDuringIdlePauseIsIgnored() {
        val h = harness()
        h.controller.startListening()
        h.controller.pauseListening()
        h.engine.emitFinal("respondéle que ya voy")
        assertTrue(h.finals.isEmpty(), "final durante IDLE no se despacha: ${h.finals}")
    }

    // E. final válido → se procesa exactamente una vez.
    @Test fun fenceE_validFinalProcessedExactlyOnce() {
        val h = harness()
        h.controller.startListening()
        h.engine.emitFinal("abrí WhatsApp")
        assertEquals(listOf("abrí WhatsApp"), h.finals)
        assertEquals(VoiceListeningState.PROCESSING, h.controller.currentState)
    }

    // F. final duplicado idéntico → a lo sumo una vez.
    @Test fun fenceF_duplicateIdenticalFinalProcessedAtMostOnce() {
        val h = harness()
        h.controller.startListening()
        h.engine.emitFinal("abrí WhatsApp")
        h.engine.emitFinal("abrí WhatsApp")
        assertEquals(1, h.finals.size, "duplicado no debe procesarse dos veces: ${h.finals}")
    }

    // G. cancelación/home → final tardío peligroso no cambia estado ni despacha.
    @Test fun fenceG_lateDangerousFinalAfterCancelDoesNotDispatch() {
        val h = harness()
        h.controller.startListening()
        h.controller.stopListening()
        h.engine.emitFinal("transferile plata")
        assertTrue(h.finals.isEmpty(), "final tardío peligroso ignorado: ${h.finals}")
        assertEquals(VoiceListeningState.STOPPED_BY_USER, h.controller.currentState)
    }

    // D. cambio de sesión: un final tardío de la sesión A (generación vieja) se
    // descarta en el engine y NUNCA contamina la sesión B.
    @Test fun fenceD_staleFinalFromOldSessionDoesNotContaminateNewSession() {
        val h = harness()
        h.controller.startListening()
        val genA = h.engine.currentGeneration // sesión A
        h.controller.stopListening()
        h.controller.startListening() // sesión B (nueva generación)
        h.engine.emitFinalForGeneration(genA, "transferile plata") // tardío de A
        assertTrue(h.finals.isEmpty(), "final viejo de A no debe llegar al controller: ${h.finals}")
        h.engine.emitFinal("abrí WhatsApp") // final real de B
        assertEquals(listOf("abrí WhatsApp"), h.finals, "B procesa su propio final, sin A")
    }

    // D (lock de fuente): el engine real descarta callbacks de generación vieja o
    // cuando ya no está escuchando, ANTES de invocar al listener del controller.
    @Test fun engineGuardsResultsByGenerationAndListening_sourceLock() {
        val src = locate("androidApp/src/main/java/com/ojoclaro/android/voice/AndroidSpeechInputEngine.kt").readText()
        val onResultsIdx = src.indexOf("override fun onResults")
        assertTrue(onResultsIdx > 0, "onResults no encontrado")
        val onResultsBody = src.substring(onResultsIdx, minOf(src.length, onResultsIdx + 220))
        assertTrue(
            onResultsBody.contains("isCurrentRecognizerCallback(generation)") &&
                onResultsBody.contains("listening.get()"),
            "onResults debe descartar callbacks de generación vieja / no-listening: $onResultsBody"
        )
    }

    private fun locate(relative: String): File {
        var current = File(System.getProperty("user.dir"))
        while (true) {
            val direct = File(current, relative.removePrefix("androidApp/"))
            if (direct.exists()) return direct
            val module = File(current, relative)
            if (module.exists()) return module
            current = current.parentFile ?: break
        }
        return File(relative)
    }

    private class Harness(
        val controller: VoiceCommandController,
        val engine: FakeEngine,
        val finals: MutableList<String>
    )

    private fun harness(): Harness {
        val engine = FakeEngine()
        val finals = mutableListOf<String>()
        val controller = VoiceCommandController(
            engine = engine,
            hasRecordAudioPermission = { true },
            onPartialTextCallback = {},
            onFinalTextCallback = finals::add,
            onErrorCallback = {},
            onReadyCallback = {},
            retryScheduler = VoiceRetryScheduler { _, action -> action(); VoiceRetryHandle {} }
        )
        return Harness(controller, engine, finals)
    }

    /**
     * Engine falso FIEL al contrato del real: cada startListening incrementa la
     * generación y pone listening=true; stop la pone en false. Los emisores
     * "fieles" descartan callbacks de generación vieja o cuando no se escucha,
     * igual que [AndroidSpeechInputEngine.isCurrentRecognizerCallback].
     */
    private class FakeEngine : SpeechInputEngine {
        override var listener: SpeechInputEngine.Listener? = null
        override var speechEngine: VoiceSpeechEngine = VoiceSpeechEngine.PLATFORM_DEFAULT
        override var isListening: Boolean = false
            private set
        var currentGeneration: Long = 0L
            private set

        override fun startListening() {
            currentGeneration += 1L
            isListening = true
            listener?.onReady()
        }
        override fun stopListening() { isListening = false }
        override fun resetRecognizer() { isListening = false }
        override fun destroy() { isListening = false }
        override fun setListeningMode(mode: SpeechListeningMode) {}

        // Emisor "directo" para la generación/estado actuales (final real de la sesión viva).
        fun emitFinal(text: String) { listener?.onFinalText(text) }

        // Emisor "tardío": replica el fence del engine real — solo entrega si la
        // generación coincide con la actual Y sigue escuchando.
        fun emitFinalForGeneration(generation: Long, text: String) {
            if (generation == currentGeneration && isListening) listener?.onFinalText(text)
        }
    }
}
