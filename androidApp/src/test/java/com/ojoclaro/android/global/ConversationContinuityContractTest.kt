package com.ojoclaro.android.global

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * V1.2 — continuidad conversacional del turno single-shot del overlay.
 *
 * Contrato (por inspección de fuente, mismo patrón que los contratos
 * outdoor): cuando Estela hace una pregunta (confirmación pendiente o
 * slot-fill), el turno NO se cierra tras el TTS: re-escucha la respuesta,
 * con presupuesto acotado para no reabrir el micrófono infinitamente.
 */
class ConversationContinuityContractTest {

    private val service: String =
        File("src/main/java/com/ojoclaro/android/global/GlobalAssistantService.kt").readText()

    @Test
    fun followUpListeningIsBudgeted() {
        assertTrue(
            service.contains("MAX_OVERLAY_FOLLOW_UP_TURNS = 3"),
            "el presupuesto de re-escuchas debe existir y ser finito"
        )
        assertTrue(
            service.contains("overlayFollowUpTurns < MAX_OVERLAY_FOLLOW_UP_TURNS"),
            "la re-escucha debe respetar el presupuesto"
        )
    }

    @Test
    fun followUpOnlyWhenEstelaAskedSomething() {
        assertTrue(
            service.contains("snapshot.pendingConfirmation != null ||"),
            "la confirmación pendiente debe habilitar la re-escucha"
        )
        assertTrue(
            service.contains("snapshot.agentState in EXPECTING_STATES ||"),
            "los estados de slot-fill deben habilitar la re-escucha"
        )
        assertTrue(
            service.contains("pendingWhatsAppSendDraft != null"),
            "un envío pendiente debe habilitar la re-escucha"
        )
        assertTrue(
            service.contains("awaitingFollowUp() &&"),
            "el gate de continuidad debe evaluarse antes de re-escuchar"
        )
    }

    @Test
    fun followUpBudgetResetsAtTurnBoundaries() {
        val starts = Regex("overlayFollowUpTurns = 0").findAll(service).count()
        assertTrue(
            starts >= 2,
            "el contador debe resetearse al iniciar y al cerrar el turno (hay $starts resets)"
        )
        assertTrue(
            service.contains("followUpTurns=\$overlayFollowUpTurns"),
            "el cierre de turno debe loguear cuántas re-escuchas hubo"
        )
    }

    @Test
    fun stopButtonStillClosesTheTurnImmediately() {
        // "Callar" (tts_stopped) cierra el turno aunque haya pregunta
        // pendiente: la persona pidió silencio y manda ella.
        assertTrue(service.contains("completeOverlayVoiceTurn(\"tts_stopped\")"))
    }
}
