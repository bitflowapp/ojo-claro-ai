package com.ojoclaro.android.presence

import com.ojoclaro.android.presence.AssistantPresenceStateMapper.Signals
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * V1.14 — presencia animada de Estela.
 *
 * Se testea la lógica PURA (el mapeo de señales al estado visual) y, por
 * source-scan, que la integración en el servicio respete los contratos:
 *  - WARNING es transitorio y siempre revierte (no deja estado colgado);
 *  - al terminar de hablar vuelve a CAMERA_ACTIVE si la cámara sigue, si no
 *    a IDLE;
 *  - el overlay sigue siendo clickeable y no rompe Camera/Instagram/WhatsApp.
 */
class AssistantPresenceV114Test {

    // --- A. Mapeo de señales → estado de reposo ---

    @Test
    fun mapperResolvesRestingStateByPriority() {
        assertEquals(AssistantVisualState.IDLE, AssistantPresenceStateMapper.resolve(Signals()))
        assertEquals(
            AssistantVisualState.LISTENING,
            AssistantPresenceStateMapper.resolve(Signals(listening = true))
        )
        assertEquals(
            AssistantVisualState.THINKING,
            AssistantPresenceStateMapper.resolve(Signals(processing = true))
        )
        assertEquals(
            AssistantVisualState.SPEAKING,
            AssistantPresenceStateMapper.resolve(Signals(speaking = true))
        )
        assertEquals(
            AssistantVisualState.CAMERA_ACTIVE,
            AssistantPresenceStateMapper.resolve(Signals(cameraActive = true))
        )
        // Prioridad: hablar gana a pensar, escuchar y cámara (lo más audible).
        assertEquals(
            AssistantVisualState.SPEAKING,
            AssistantPresenceStateMapper.resolve(
                Signals(speaking = true, processing = true, listening = true, cameraActive = true)
            )
        )
        // Pensar gana a escuchar y cámara.
        assertEquals(
            AssistantVisualState.THINKING,
            AssistantPresenceStateMapper.resolve(
                Signals(processing = true, listening = true, cameraActive = true)
            )
        )
        // Escuchar gana a cámara.
        assertEquals(
            AssistantVisualState.LISTENING,
            AssistantPresenceStateMapper.resolve(Signals(listening = true, cameraActive = true))
        )
    }

    // --- B. SPEAKING → CAMERA_ACTIVE / IDLE al terminar de hablar ---

    @Test
    fun speakingFallsBackToCameraActiveWhenCameraStaysOn() {
        // Hablando con la cámara abierta.
        val speakingWithCamera = Signals(speaking = true, cameraActive = true)
        assertEquals(
            AssistantVisualState.SPEAKING,
            AssistantPresenceStateMapper.resolve(speakingWithCamera)
        )
        // Termina de hablar (speaking=false): vuelve a CAMERA_ACTIVE.
        val afterSpeech = speakingWithCamera.copy(speaking = false)
        assertEquals(
            AssistantVisualState.CAMERA_ACTIVE,
            AssistantPresenceStateMapper.resolve(afterSpeech)
        )
    }

    @Test
    fun speakingFallsBackToIdleWhenCameraIsOff() {
        val speakingNoCamera = Signals(speaking = true, cameraActive = false)
        val afterSpeech = speakingNoCamera.copy(speaking = false)
        assertEquals(
            AssistantVisualState.IDLE,
            AssistantPresenceStateMapper.resolve(afterSpeech)
        )
    }

    // --- C. Estados y descripciones accesibles ---

    @Test
    fun everyStateHasAUsefulContentDescription() {
        assertEquals("Estela disponible", AssistantVisualState.IDLE.contentDescription)
        assertEquals("Estela escuchando", AssistantVisualState.LISTENING.contentDescription)
        assertEquals("Estela pensando", AssistantVisualState.THINKING.contentDescription)
        assertEquals("Estela hablando", AssistantVisualState.SPEAKING.contentDescription)
        assertEquals("Cámara de Estela activa", AssistantVisualState.CAMERA_ACTIVE.contentDescription)
        assertEquals("Estela necesita atención", AssistantVisualState.WARNING.contentDescription)
        // Las seis presencias de la misión existen.
        assertEquals(6, AssistantVisualState.entries.size)
    }

    // --- D. Integración: WARNING transitorio y wiring sin romper nada ---

    @Test
    fun warningIsTransientAndRevertsToRestingState() {
        val service = gasSource()
        // El warning se programa y SIEMPRE revierte recalculando con las
        // señales vigentes (no deja la presencia colgada en WARNING).
        val warningFn = service
            .substringAfter("private fun flashPresenceWarning")
            .substringBefore("// ---")
        assertTrue(warningFn.contains("AssistantVisualState.WARNING"), "muestra WARNING")
        assertTrue(
            warningFn.contains("delay(PRESENCE_WARNING_MILLIS)"),
            "el warning dura un tiempo acotado"
        )
        assertTrue(
            warningFn.contains("AssistantPresenceStateMapper.resolve(presenceSignals)"),
            "revierte al estado de reposo vigente, no queda colgado"
        )
        assertTrue(service.contains("PRESENCE_WARNING_MILLIS = 1_600L"))
    }

    @Test
    fun presenceIsWiredAtVoiceTtsAndCameraCallSitesWithoutBreakingFlows() {
        val service = gasSource()
        // Voz: escuchar, pensar (texto reconocido), hablar.
        assertTrue(service.contains("it.copy(listening = true"), "LISTENING al escuchar")
        assertTrue(
            service.contains("it.copy(listening = false, processing = true)"),
            "THINKING al recibir texto"
        )
        assertTrue(service.contains("it.copy(speaking = true"), "SPEAKING al hablar")
        assertTrue(service.contains("it.copy(speaking = false)"), "vuelve del SPEAKING")
        // Cámara: encendida/apagada.
        assertTrue(service.contains("it.copy(cameraActive = true)"), "CAMERA_ACTIVE al abrir")
        assertTrue(service.contains("it.copy(cameraActive = false)"), "deja cámara al cerrar")
        // Warnings de seguridad (pagos, sensibles, permiso/cámara).
        assertTrue(
            Regex("flashPresenceWarning\\(\\)").findAll(service).count() >= 4,
            "destellos de alerta en pagos/sensibles/permiso/cámara"
        )

        // No rompe los dispatch existentes: cámara sigue entre tareas y outdoor.
        val cameraIdx = service.indexOf("if (handleCameraAssistCommand(text)) return")
        val outdoorIdx = service.indexOf("if (handleOutdoorCommand(text)) return")
        assertTrue(cameraIdx in 1 until outdoorIdx, "V1.13 intacto")
        // Instagram sigue antes de tareas (V1.12 intacto).
        val igIdx = service.indexOf("if (handleInstagramTaskCommand(text)) return")
        val taskIdx = service.indexOf("if (handleTaskAssistCommand(text)) return")
        assertTrue(igIdx in 1 until taskIdx, "V1.12 intacto")
    }

    @Test
    fun presenceViewStaysClickableLightweightAndReleasesAnimators() {
        val view = File(
            "src/main/java/com/ojoclaro/android/presence/AssistantPresenceView.kt"
        ).readText()
        // Un solo animador, liberado en detach, pausado al ocultarse.
        assertEquals(
            1,
            Regex("ValueAnimator\\.ofFloat").findAll(view).count(),
            "un único animador"
        )
        assertTrue(view.contains("animator.cancel()"), "libera el animador en detach")
        assertTrue(view.contains("onDetachedFromWindow"))
        assertTrue(view.contains("onVisibilityChanged"))
        assertTrue(view.contains("ANIMATOR_DURATION_SCALE"), "respeta reduced motion")
        assertTrue(view.contains("isClickable = true"), "sigue clickeable para abrir el panel")
        // Sin assets pesados ni dependencias raras.
        assertFalse(view.contains("Lottie"), "sin Lottie")
        assertFalse(view.contains("import android.graphics.Bitmap"), "sin bitmaps pesados")

        // El servicio reemplazó el botón plano por la presencia, conservando
        // el click para expandir el panel.
        val accessibility = File(
            "src/main/java/com/ojoclaro/android/accessibility/OjoClaroAccessibilityService.kt"
        ).readText()
        val collapsed = accessibility
            .substringAfter("private fun buildCollapsedOverlay")
            .substringBefore("private fun setPresenceStateInternal")
        assertTrue(collapsed.contains("AssistantPresenceView"))
        assertTrue(collapsed.contains("setOnClickListener"), "el overlay no se come el click de abrir")
    }

    private fun gasSource(): String =
        File("src/main/java/com/ojoclaro/android/global/GlobalAssistantService.kt").readText()
}
