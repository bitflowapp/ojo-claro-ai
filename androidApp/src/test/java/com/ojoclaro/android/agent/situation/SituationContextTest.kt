package com.ojoclaro.android.agent.situation

import com.ojoclaro.android.agent.core.AgentExecutionMode
import com.ojoclaro.android.agent.core.AgentRiskLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SituationContextTest {

    private fun baseContext(
        confidence: Float = 0.8f,
        recentTurns: List<TurnSummary> = emptyList(),
        activeRequestId: Long = 7L
    ): SituationContext = SituationContext(
        rawCommand = "avisale a sofi que llego tarde",
        normalizedCommand = "avisale a sofi que llego tarde",
        source = InputSource.VOICE,
        confidence = confidence,
        timestamp = 1_000L,
        situationIntent = SituationIntent.WRITE_MESSAGE,
        activeGoal = ActiveGoal(
            description = "avisarle a Sofi",
            intent = SituationIntent.WRITE_MESSAGE,
            createdAt = 1_000L
        ),
        pendingAction = PendingAction(
            label = "preparar WhatsApp para Sofi",
            intentName = "COMPOSE_WHATSAPP_MESSAGE",
            riskLevel = AgentRiskLevel.MEDIUM,
            confirmationPrompt = "Para preparar el WhatsApp, decí: confirmar.",
            expiresAt = 999_999L
        ),
        currentAppPackage = "com.ojoclaro.android",
        environmentHint = EnvironmentHint.IN_OJOCLARO,
        screenContext = null,
        riskLevel = AgentRiskLevel.MEDIUM,
        needsConfirmation = true,
        isPrivacyHotZone = false,
        lastAssistantMessage = "¿Lo confirmás?",
        recentTurns = recentTurns,
        situationState = SituationState.WAITING_CONFIRMATION,
        activeRequestId = activeRequestId,
        mutedThroughRequestId = 0L,
        cancellationState = CancellationState.NONE,
        userMode = AgentExecutionMode.ACCESSIBILITY_VOICE,
        companionModeActive = true
    )

    private fun turn(index: Int): TurnSummary = TurnSummary(
        role = TurnRole.USER,
        shortText = "turno $index",
        intent = SituationIntent.UNKNOWN,
        timestamp = index.toLong()
    )

    @Test
    fun confidence_fuera_de_rango_lanza_error() {
        assertFailsWith<IllegalArgumentException> { baseContext(confidence = -0.1f) }
        assertFailsWith<IllegalArgumentException> { baseContext(confidence = 1.1f) }
    }

    @Test
    fun recent_turns_con_mas_de_cinco_lanza_error() {
        assertFailsWith<IllegalArgumentException> {
            baseContext(recentTurns = (1..6).map { turn(it) })
        }
    }

    @Test
    fun with_recent_turn_mantiene_solo_cinco() {
        var ctx = baseContext(recentTurns = (1..5).map { turn(it) })
        ctx = ctx.withRecentTurn(turn(6))
        assertEquals(5, ctx.recentTurns.size)
        // El más viejo (turno 1) se descartó; el más nuevo (turno 6) quedó.
        assertEquals("turno 2", ctx.recentTurns.first().shortText)
        assertEquals("turno 6", ctx.recentTurns.last().shortText)
    }

    @Test
    fun turn_summary_demasiado_largo_lanza_error() {
        assertFailsWith<IllegalArgumentException> {
            TurnSummary(
                role = TurnRole.ASSISTANT,
                shortText = "x".repeat(TurnSummary.MAX_SHORT_TEXT_CHARS + 1),
                intent = SituationIntent.UNKNOWN,
                timestamp = 0L
            )
        }
    }

    @Test
    fun cleared_for_cancellation_limpia_active_goal() {
        val cleared = baseContext().clearedForCancellation(now = 5_000L)
        assertNull(cleared.activeGoal)
    }

    @Test
    fun cleared_for_cancellation_limpia_pending_action() {
        val cleared = baseContext().clearedForCancellation(now = 5_000L)
        assertNull(cleared.pendingAction)
    }

    @Test
    fun cleared_for_cancellation_setea_hard_cancel() {
        val cleared = baseContext().clearedForCancellation(now = 5_000L)
        assertEquals(CancellationState.HARD_CANCEL, cleared.cancellationState)
    }

    @Test
    fun cleared_for_cancellation_setea_estado_cancelled() {
        val cleared = baseContext().clearedForCancellation(now = 5_000L)
        assertEquals(SituationState.CANCELLED, cleared.situationState)
    }

    @Test
    fun cleared_for_cancellation_muted_igual_a_active_request_id() {
        val cleared = baseContext(activeRequestId = 42L).clearedForCancellation(now = 5_000L)
        assertEquals(42L, cleared.mutedThroughRequestId)
    }

    @Test
    fun cleared_for_cancellation_deja_companion_mode_en_false() {
        val cleared = baseContext().clearedForCancellation(now = 5_000L)
        assertFalse(cleared.companionModeActive)
        assertFalse(cleared.needsConfirmation)
        assertEquals(5_000L, cleared.timestamp)
    }

    @Test
    fun with_user_turn_agrega_un_turno_de_rol_user() {
        val ctx = baseContext().withUserTurnFromCurrentCommand()
        assertEquals(1, ctx.recentTurns.size)
        val added = ctx.recentTurns.last()
        assertEquals(TurnRole.USER, added.role)
        assertEquals(ctx.situationIntent, added.intent)
        assertEquals(ctx.timestamp, added.timestamp)
        assertEquals(ctx.rawCommand, added.shortText)
    }

    @Test
    fun with_user_turn_trunca_el_texto_a_240_caracteres() {
        val longCommand = "x".repeat(400)
        val ctx = baseContext().copy(rawCommand = longCommand).withUserTurnFromCurrentCommand()
        assertEquals(TurnSummary.MAX_SHORT_TEXT_CHARS, ctx.recentTurns.last().shortText.length)
    }

    @Test
    fun with_user_turn_mantiene_maximo_cinco_turnos() {
        val ctx = baseContext(recentTurns = (1..5).map { turn(it) })
            .withUserTurnFromCurrentCommand()
        assertEquals(5, ctx.recentTurns.size)
        // El más viejo se descartó; el turno del comando actual quedó último.
        assertEquals("turno 2", ctx.recentTurns.first().shortText)
        assertEquals(baseContext().rawCommand, ctx.recentTurns.last().shortText)
    }

    @Test
    fun with_user_turn_con_raw_command_blank_no_agrega_turno() {
        val ctx = baseContext().copy(rawCommand = "   ").withUserTurnFromCurrentCommand()
        assertTrue(ctx.recentTurns.isEmpty())
    }

    // --- Fase 7: PendingAction con payload ----------------------------------

    @Test
    fun pending_action_acepta_original_command_target_y_payload() {
        val pending = PendingAction(
            label = "abrir una app",
            intentName = SituationIntent.OPEN_APP.name,
            riskLevel = AgentRiskLevel.LOW,
            confirmationPrompt = "¿Querés que abra WhatsApp?",
            expiresAt = 999_999L,
            originalCommand = "abrí WhatsApp",
            target = "WhatsApp",
            payload = mapOf("intent" to "OPEN_APP", "target" to "WhatsApp")
        )
        assertEquals("abrí WhatsApp", pending.originalCommand)
        assertEquals("WhatsApp", pending.target)
        assertEquals("WhatsApp", pending.payload["target"])
    }

    @Test
    fun pending_action_original_command_demasiado_largo_lanza() {
        assertFailsWith<IllegalArgumentException> {
            PendingAction(
                label = "x",
                intentName = SituationIntent.OPEN_APP.name,
                riskLevel = AgentRiskLevel.LOW,
                confirmationPrompt = "x",
                expiresAt = 1L,
                originalCommand = "x".repeat(PendingAction.MAX_ORIGINAL_COMMAND_CHARS + 1)
            )
        }
    }

    @Test
    fun pending_action_target_demasiado_largo_lanza() {
        assertFailsWith<IllegalArgumentException> {
            PendingAction(
                label = "x",
                intentName = SituationIntent.OPEN_APP.name,
                riskLevel = AgentRiskLevel.LOW,
                confirmationPrompt = "x",
                expiresAt = 1L,
                target = "x".repeat(PendingAction.MAX_TARGET_CHARS + 1)
            )
        }
    }

    @Test
    fun pending_action_payload_con_key_blank_lanza() {
        assertFailsWith<IllegalArgumentException> {
            PendingAction(
                label = "x",
                intentName = SituationIntent.OPEN_APP.name,
                riskLevel = AgentRiskLevel.LOW,
                confirmationPrompt = "x",
                expiresAt = 1L,
                payload = mapOf("  " to "valor")
            )
        }
    }

    @Test
    fun pending_action_payload_con_value_demasiado_largo_lanza() {
        assertFailsWith<IllegalArgumentException> {
            PendingAction(
                label = "x",
                intentName = SituationIntent.OPEN_APP.name,
                riskLevel = AgentRiskLevel.LOW,
                confirmationPrompt = "x",
                expiresAt = 1L,
                payload = mapOf("k" to "x".repeat(PendingAction.MAX_PAYLOAD_VALUE_CHARS + 1))
            )
        }
    }

    @Test
    fun pending_action_payload_con_demasiadas_entradas_lanza() {
        assertFailsWith<IllegalArgumentException> {
            PendingAction(
                label = "x",
                intentName = SituationIntent.OPEN_APP.name,
                riskLevel = AgentRiskLevel.LOW,
                confirmationPrompt = "x",
                expiresAt = 1L,
                payload = (1..PendingAction.MAX_PAYLOAD_ENTRIES + 1)
                    .associate { "k$it" to "v$it" }
            )
        }
    }

    @Test
    fun command_for_execution_usa_original_command() {
        val pending = PendingAction(
            label = "abrir una app",
            intentName = SituationIntent.OPEN_APP.name,
            riskLevel = AgentRiskLevel.LOW,
            confirmationPrompt = "¿Querés que abra WhatsApp?",
            expiresAt = 1L,
            originalCommand = "abrí WhatsApp",
            target = "WhatsApp"
        )
        assertEquals("abrí WhatsApp", pending.commandForExecution())
    }

    @Test
    fun command_for_execution_reconstruye_open_app_con_target() {
        val pending = PendingAction(
            label = "abrir una app",
            intentName = SituationIntent.OPEN_APP.name,
            riskLevel = AgentRiskLevel.LOW,
            confirmationPrompt = "¿Querés que abra WhatsApp?",
            expiresAt = 1L,
            originalCommand = "",
            target = "WhatsApp"
        )
        assertEquals("abrí WhatsApp", pending.commandForExecution())
    }

    @Test
    fun command_for_execution_reconstruye_call_contact_con_target() {
        val pending = PendingAction(
            label = "abrir el marcador",
            intentName = SituationIntent.CALL_CONTACT.name,
            riskLevel = AgentRiskLevel.MEDIUM,
            confirmationPrompt = "¿Querés que abra el marcador para llamar a Sofi?",
            expiresAt = 1L,
            originalCommand = "",
            target = "Sofi"
        )
        assertEquals("llamá a Sofi", pending.commandForExecution())
    }

    @Test
    fun command_for_execution_devuelve_blank_si_no_puede_reconstruir() {
        val pending = PendingAction(
            label = "x",
            intentName = SituationIntent.OPEN_APP.name,
            riskLevel = AgentRiskLevel.LOW,
            confirmationPrompt = "x",
            expiresAt = 1L,
            originalCommand = "",
            target = ""
        )
        assertTrue(pending.commandForExecution().isBlank())
    }

    @Test
    fun message_command_for_execution_usa_original_command() {
        val pending = PendingAction(
            label = "preparar un mensaje",
            intentName = SituationIntent.WRITE_MESSAGE.name,
            riskLevel = AgentRiskLevel.MEDIUM,
            confirmationPrompt = "¿Querés que prepare el mensaje?",
            expiresAt = 1L,
            originalCommand = "avisale a Sofi que llego tarde",
            target = "Sofi",
            payload = mapOf("contact" to "Sofi", "message" to "llego tarde")
        )
        assertEquals("avisale a Sofi que llego tarde", pending.messageCommandForExecution())
    }

    @Test
    fun message_command_for_execution_reconstruye_con_contact_y_message() {
        val pending = PendingAction(
            label = "preparar un mensaje",
            intentName = SituationIntent.WRITE_MESSAGE.name,
            riskLevel = AgentRiskLevel.MEDIUM,
            confirmationPrompt = "¿Querés que prepare el mensaje?",
            expiresAt = 1L,
            payload = mapOf("contact" to "Sofi", "message" to "llego tarde")
        )
        assertEquals("avisale a Sofi que llego tarde", pending.messageCommandForExecution())
    }

    @Test
    fun message_command_for_execution_devuelve_blank_si_falta_contact_o_message() {
        val sinContact = PendingAction(
            label = "preparar un mensaje",
            intentName = SituationIntent.WRITE_MESSAGE.name,
            riskLevel = AgentRiskLevel.MEDIUM,
            confirmationPrompt = "¿Querés que prepare el mensaje?",
            expiresAt = 1L,
            payload = mapOf("message" to "llego tarde")
        )
        val sinMessage = PendingAction(
            label = "preparar un mensaje",
            intentName = SituationIntent.WRITE_MESSAGE.name,
            riskLevel = AgentRiskLevel.MEDIUM,
            confirmationPrompt = "¿Querés que prepare el mensaje?",
            expiresAt = 1L,
            payload = mapOf("contact" to "Sofi")
        )
        assertTrue(sinContact.messageCommandForExecution().isBlank())
        assertTrue(sinMessage.messageCommandForExecution().isBlank())
    }
}
