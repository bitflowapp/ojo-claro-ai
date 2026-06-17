package com.ojoclaro.android.agent.situation

import com.ojoclaro.android.agent.core.AgentRiskLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SituationContextFactoryTest {

    private val factory = SituationContextFactory()

    private fun build(
        rawCommand: String = "leeme la pantalla",
        currentStateName: String? = "IDLE",
        currentAppPackage: String? = null,
        activeRequestId: Long = 5L,
        mutedThroughRequestId: Long = 2L,
        lastAssistantMessage: String = "hola",
        runtimeSnapshot: SituationRuntimeSnapshot = SituationRuntimeSnapshot.empty()
    ): SituationContext = factory.fromVoiceCommand(
        rawCommand = rawCommand,
        currentStateName = currentStateName,
        currentAppPackage = currentAppPackage,
        activeRequestId = activeRequestId,
        mutedThroughRequestId = mutedThroughRequestId,
        lastAssistantMessage = lastAssistantMessage,
        runtimeSnapshot = runtimeSnapshot,
        timestamp = 1_000L
    )

    private fun goal(): ActiveGoal = ActiveGoal(
        description = "avisarle a Sofi",
        intent = SituationIntent.WRITE_MESSAGE,
        createdAt = 0L
    )

    private fun pendingAction(): PendingAction = PendingAction(
        label = "preparar un mensaje",
        intentName = SituationIntent.WRITE_MESSAGE.name,
        riskLevel = AgentRiskLevel.MEDIUM,
        confirmationPrompt = "Para continuar, decí: confirmar.",
        expiresAt = 999_999L
    )

    // --- Tests base ----------------------------------------------------------

    @Test
    fun crea_contexto_con_raw_y_normalized_command() {
        val ctx = build(rawCommand = "  Leeme La Pantalla  ")
        assertEquals("  Leeme La Pantalla  ", ctx.rawCommand)
        assertEquals("leeme la pantalla", ctx.normalizedCommand)
        assertEquals(InputSource.VOICE, ctx.source)
        assertEquals(SituationIntent.UNKNOWN, ctx.situationIntent)
        assertEquals(1f, ctx.confidence)
        assertNull(ctx.screenContext)
    }

    @Test
    fun package_whatsapp_produce_in_whatsapp() {
        assertEquals(EnvironmentHint.IN_WHATSAPP, build(currentAppPackage = "com.whatsapp").environmentHint)
    }

    @Test
    fun package_null_produce_unknown() {
        assertEquals(EnvironmentHint.UNKNOWN, build(currentAppPackage = null).environmentHint)
    }

    @Test
    fun active_request_id_se_copia() {
        assertEquals(17L, build(activeRequestId = 17L).activeRequestId)
    }

    @Test
    fun estado_no_mapeable_cae_a_idle_con_memoria_vacia() {
        // "SCANNING" es un AppState válido pero NO un SituationState: cae a IDLE.
        assertEquals(SituationState.IDLE, build(currentStateName = "SCANNING").situationState)
        // Un nombre que sí es SituationState se respeta.
        assertEquals(SituationState.LISTENING, build(currentStateName = "LISTENING").situationState)
    }

    // --- Tests de memoria runtime (Fase 4) -----------------------------------

    @Test
    fun runtime_snapshot_con_active_goal_se_copia_al_contexto() {
        val g = goal()
        val ctx = build(runtimeSnapshot = SituationRuntimeSnapshot(activeGoal = g))
        assertEquals(g, ctx.activeGoal)
    }

    @Test
    fun runtime_snapshot_con_pending_action_se_copia_y_needs_confirmation_true() {
        val p = pendingAction()
        val ctx = build(runtimeSnapshot = SituationRuntimeSnapshot(pendingAction = p))
        assertEquals(p, ctx.pendingAction)
        assertTrue(ctx.needsConfirmation)
    }

    @Test
    fun sin_pending_action_needs_confirmation_false() {
        assertFalse(build().needsConfirmation)
    }

    @Test
    fun runtime_snapshot_con_companion_mode_se_copia_al_contexto() {
        val ctx = build(runtimeSnapshot = SituationRuntimeSnapshot(companionModeActive = true))
        assertTrue(ctx.companionModeActive)
    }

    @Test
    fun muted_through_request_id_mayor_tiene_prioridad() {
        // El parámetro es mayor.
        val ctxA = build(
            mutedThroughRequestId = 20L,
            runtimeSnapshot = SituationRuntimeSnapshot(mutedThroughRequestId = 5L)
        )
        assertEquals(20L, ctxA.mutedThroughRequestId)
        // El snapshot es mayor.
        val ctxB = build(
            mutedThroughRequestId = 3L,
            runtimeSnapshot = SituationRuntimeSnapshot(mutedThroughRequestId = 99L)
        )
        assertEquals(99L, ctxB.mutedThroughRequestId)
    }

    @Test
    fun last_assistant_message_del_snapshot_se_usa_si_el_parametro_viene_blank() {
        val ctx = build(
            lastAssistantMessage = "   ",
            runtimeSnapshot = SituationRuntimeSnapshot(lastAssistantMessage = "te acompaño")
        )
        assertEquals("te acompaño", ctx.lastAssistantMessage)
    }

    @Test
    fun last_assistant_message_del_parametro_gana_si_no_esta_blank() {
        val ctx = build(
            lastAssistantMessage = "mensaje fresco",
            runtimeSnapshot = SituationRuntimeSnapshot(lastAssistantMessage = "mensaje viejo")
        )
        assertEquals("mensaje fresco", ctx.lastAssistantMessage)
    }

    @Test
    fun snapshot_no_vacio_impone_su_situation_state() {
        // Con memoria no vacía, su estado manda por sobre currentStateName.
        val ctx = build(
            currentStateName = "IDLE",
            runtimeSnapshot = SituationRuntimeSnapshot(
                situationState = SituationState.WAITING_CONFIRMATION,
                pendingAction = pendingAction()
            )
        )
        assertEquals(SituationState.WAITING_CONFIRMATION, ctx.situationState)
    }
}
