package com.ojoclaro.android.agent.situation

import com.ojoclaro.android.agent.core.AgentExecutionMode
import com.ojoclaro.android.agent.core.AgentRiskLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SituationBrainTest {

    private val brain = SituationBrain()

    private fun baseContext(
        command: String,
        state: SituationState = SituationState.IDLE,
        activeGoal: ActiveGoal? = null,
        pendingAction: PendingAction? = null,
        companionModeActive: Boolean = false,
        activeRequestId: Long = 10L,
        timestamp: Long = 1_000L
    ): SituationContext = SituationContext(
        rawCommand = command,
        normalizedCommand = command.trim().lowercase(),
        source = InputSource.VOICE,
        confidence = 0.9f,
        timestamp = timestamp,
        situationIntent = SituationIntent.UNKNOWN,
        activeGoal = activeGoal,
        pendingAction = pendingAction,
        currentAppPackage = null,
        environmentHint = EnvironmentHint.UNKNOWN,
        screenContext = null,
        riskLevel = AgentRiskLevel.NONE,
        needsConfirmation = pendingAction != null,
        isPrivacyHotZone = false,
        lastAssistantMessage = "",
        recentTurns = emptyList(),
        situationState = state,
        activeRequestId = activeRequestId,
        mutedThroughRequestId = 0L,
        cancellationState = CancellationState.NONE,
        userMode = AgentExecutionMode.ACCESSIBILITY_VOICE,
        companionModeActive = companionModeActive
    )

    private fun samplePendingAction(timestamp: Long = 1_000L): PendingAction = PendingAction(
        label = "preparar un mensaje",
        intentName = SituationIntent.WRITE_MESSAGE.name,
        riskLevel = AgentRiskLevel.MEDIUM,
        confirmationPrompt = "Para continuar, decí: confirmar.",
        expiresAt = timestamp + 120_000L
    )

    private fun writeMessageGoal(createdAt: Long, ttlMillis: Long = 300_000L): ActiveGoal =
        ActiveGoal(
            description = "avisarle a Sofi que llego tarde",
            intent = SituationIntent.WRITE_MESSAGE,
            createdAt = createdAt,
            ttlMillis = ttlMillis
        )

    // 1 -----------------------------------------------------------------------
    @Test
    fun hard_cancel_limpia_todo_y_devuelve_cancel() {
        val ctx = baseContext(
            command = "callate",
            activeGoal = writeMessageGoal(createdAt = 1_000L),
            pendingAction = samplePendingAction(),
            companionModeActive = true,
            activeRequestId = 33L
        )
        val result = brain.process(ctx)

        assertTrue(result.decision is SituationDecision.Cancel)
        assertEquals(SituationState.CANCELLED, result.updatedContext.situationState)
        assertNull(result.updatedContext.activeGoal)
        assertNull(result.updatedContext.pendingAction)
        assertFalse(result.updatedContext.companionModeActive)
        assertEquals(33L, result.updatedContext.mutedThroughRequestId)
        assertEquals(CancellationState.HARD_CANCEL, result.updatedContext.cancellationState)
    }

    // 2 -----------------------------------------------------------------------
    @Test
    fun unsafe_request_devuelve_reject_bloqueado() {
        val result = brain.process(baseContext("transferí plata desde el banco"))
        val decision = result.decision
        assertTrue(decision is SituationDecision.Reject)
        assertEquals(AgentRiskLevel.BLOCKED, decision.riskLevel)
        assertEquals(SituationState.ERROR_RECOVERY, result.updatedContext.situationState)
    }

    // 3 -----------------------------------------------------------------------
    @Test
    fun help_me_work_activa_companion_mode_y_habla() {
        val result = brain.process(baseContext("estoy trabajando ayudame"))
        val decision = result.decision
        assertTrue(decision is SituationDecision.Speak)
        assertTrue(result.updatedContext.companionModeActive)
        assertEquals(SituationState.SPEAKING, result.updatedContext.situationState)
    }

    // 4 -----------------------------------------------------------------------
    @Test
    fun read_screen_devuelve_execute_intent_en_reading_screen() {
        val result = brain.process(baseContext("leeme la pantalla"))
        val decision = result.decision
        assertTrue(decision is SituationDecision.ExecuteIntent)
        assertEquals(SituationIntent.READ_SCREEN, decision.intent)
        assertEquals(SituationState.READING_SCREEN, decision.nextState)
        assertEquals(SituationState.READING_SCREEN, result.updatedContext.situationState)
    }

    // 5 -----------------------------------------------------------------------
    @Test
    fun write_message_devuelve_ask_confirmation() {
        val result = brain.process(baseContext("avisale a Sofi que llego tarde"))
        val decision = result.decision
        assertTrue(decision is SituationDecision.AskConfirmation)
        assertNotNull(result.updatedContext.pendingAction)
        assertTrue(result.updatedContext.needsConfirmation)
        assertEquals(SituationState.WAITING_CONFIRMATION, result.updatedContext.situationState)
    }

    // 6 -----------------------------------------------------------------------
    @Test
    fun call_contact_devuelve_ask_confirmation() {
        val result = brain.process(baseContext("llamá a Sofi"))
        assertTrue(result.decision is SituationDecision.AskConfirmation)
        assertEquals(SituationState.WAITING_CONFIRMATION, result.updatedContext.situationState)
    }

    // 7 -----------------------------------------------------------------------
    @Test
    fun unknown_sin_objetivo_devuelve_speak_de_fallback() {
        val result = brain.process(baseContext("banana azul volador"))
        val decision = result.decision
        assertTrue(decision is SituationDecision.Speak)
        assertTrue(decision.message.isNotBlank())
        assertEquals(SituationState.SPEAKING, result.updatedContext.situationState)
    }

    // 8 -----------------------------------------------------------------------
    @Test
    fun unknown_con_objetivo_activo_continua_el_goal() {
        // Fase 8: el goal tiene contact pero le falta message. El usuario
        // entrega el mensaje y el Brain completa el slot, generando
        // AskConfirmation y "consumiendo" el goal en una pendingAction.
        val goal = ActiveGoal(
            description = "avisarle a Sofi",
            intent = SituationIntent.WRITE_MESSAGE,
            createdAt = 1_000L,
            slotsFilled = mapOf("contact" to "Sofi"),
            slotsMissing = setOf("message")
        )
        val ctx = baseContext(
            command = "que salgo en 10",
            state = SituationState.SPEAKING,
            activeGoal = goal,
            timestamp = 1_000L
        )
        val result = brain.process(ctx)
        assertTrue(result.decision is SituationDecision.AskConfirmation)
        // El goal se completó y se consumió en la pendingAction.
        assertNull(result.updatedContext.activeGoal)
        assertNotNull(result.updatedContext.pendingAction)
    }

    // 9 -----------------------------------------------------------------------
    @Test
    fun objetivo_expirado_se_limpia_y_no_se_continua() {
        val expiredGoal = writeMessageGoal(createdAt = 0L, ttlMillis = 1_000L)
        val ctx = baseContext(
            command = "decile que salgo en 10",
            activeGoal = expiredGoal,
            timestamp = 1_000_000L // muy posterior al TTL
        )
        val result = brain.process(ctx)
        assertNull(result.updatedContext.activeGoal)
        assertFalse(result.decision is SituationDecision.ContinueGoal)
    }

    // 10 ----------------------------------------------------------------------
    @Test
    fun confirmacion_de_accion_pendiente_devuelve_execute_intent() {
        val ctx = baseContext(
            command = "sí",
            state = SituationState.WAITING_CONFIRMATION,
            pendingAction = samplePendingAction()
        )
        val result = brain.process(ctx)
        val decision = result.decision
        assertTrue(decision is SituationDecision.ExecuteIntent)
        assertEquals(SituationState.EXECUTING_GUIDED_ACTION, decision.nextState)
        assertEquals(SituationState.EXECUTING_GUIDED_ACTION, result.updatedContext.situationState)
        assertNull(result.updatedContext.pendingAction)
    }

    // 11 ----------------------------------------------------------------------
    @Test
    fun cancelacion_de_accion_pendiente_devuelve_cancel() {
        val ctx = baseContext(
            command = "cancelá",
            state = SituationState.WAITING_CONFIRMATION,
            pendingAction = samplePendingAction()
        )
        val result = brain.process(ctx)
        assertTrue(result.decision is SituationDecision.Cancel)
        assertEquals(SituationState.CANCELLED, result.updatedContext.situationState)
        assertNull(result.updatedContext.pendingAction)
    }

    // 12 ----------------------------------------------------------------------
    @Test
    fun transicion_ilegal_no_crashea_y_devuelve_reject() {
        // pendingAction + estado IDLE: confirmar exige IDLE -> EXECUTING_GUIDED_ACTION,
        // que es ilegal. El Brain no debe lanzar; debe degradar a Reject/ERROR_RECOVERY.
        val ctx = baseContext(
            command = "sí",
            state = SituationState.IDLE,
            pendingAction = samplePendingAction()
        )
        val result = brain.process(ctx)
        assertTrue(result.decision is SituationDecision.Reject)
        assertEquals(SituationState.ERROR_RECOVERY, result.updatedContext.situationState)
    }

    // Extra: comando vacío -------------------------------------------------------
    @Test
    fun comando_vacio_devuelve_ignore() {
        val result = brain.process(baseContext("   "))
        assertEquals(SituationDecision.Ignore, result.decision)
    }

    // --- Fase 5: alimentación de recentTurns ---------------------------------

    private fun turn(index: Int): TurnSummary = TurnSummary(
        role = TurnRole.USER,
        shortText = "turno $index",
        intent = SituationIntent.UNKNOWN,
        timestamp = index.toLong()
    )

    @Test
    fun process_agrega_user_turn_con_intent_clasificado() {
        val result = brain.process(baseContext("leeme la pantalla"))
        val turns = result.updatedContext.recentTurns
        assertEquals(1, turns.size)
        assertEquals(TurnRole.USER, turns.last().role)
        // El turno usa la intención clasificada, no UNKNOWN.
        assertEquals(SituationIntent.READ_SCREEN, turns.last().intent)
        assertEquals("leeme la pantalla", turns.last().shortText)
    }

    @Test
    fun process_respeta_limite_de_240_caracteres_en_el_turno() {
        val largo = "x".repeat(400)
        val result = brain.process(baseContext(largo))
        assertEquals(
            TurnSummary.MAX_SHORT_TEXT_CHARS,
            result.updatedContext.recentTurns.last().shortText.length
        )
    }

    @Test
    fun process_no_deja_recent_turns_pasar_de_cinco() {
        val ctx = baseContext("leeme la pantalla")
            .copy(recentTurns = (1..5).map { turn(it) })
        val result = brain.process(ctx)
        assertEquals(5, result.updatedContext.recentTurns.size)
        // El más viejo se descartó; el turno del comando actual quedó último.
        assertEquals("turno 2", result.updatedContext.recentTurns.first().shortText)
        assertEquals("leeme la pantalla", result.updatedContext.recentTurns.last().shortText)
    }

    @Test
    fun dos_llamadas_consecutivas_conservan_historial() {
        val r1 = brain.process(baseContext("leeme la pantalla"))
        val r2 = brain.process(
            baseContext("resumime la pantalla")
                .copy(recentTurns = r1.updatedContext.recentTurns)
        )
        assertEquals(2, r2.updatedContext.recentTurns.size)
        assertEquals(SituationIntent.READ_SCREEN, r2.updatedContext.recentTurns.first().intent)
        assertEquals(SituationIntent.SUMMARIZE_SCREEN, r2.updatedContext.recentTurns.last().intent)
    }

    @Test
    fun hard_cancel_queda_registrado_como_turno() {
        val result = brain.process(baseContext("callate"))
        assertTrue(result.decision is SituationDecision.Cancel)
        // El turno de "callate" queda registrado con intención EMERGENCY_STOP.
        assertEquals(1, result.updatedContext.recentTurns.size)
        assertEquals(SituationIntent.EMERGENCY_STOP, result.updatedContext.recentTurns.last().intent)
    }

    @Test
    fun read_screen_devuelve_execute_intent_y_turno_read_screen() {
        val result = brain.process(baseContext("leeme la pantalla"))
        assertTrue(result.decision is SituationDecision.ExecuteIntent)
        assertEquals(SituationIntent.READ_SCREEN, result.updatedContext.recentTurns.last().intent)
    }

    @Test
    fun summarize_screen_devuelve_execute_intent_y_turno_summarize() {
        val result = brain.process(baseContext("resumime la pantalla"))
        assertTrue(result.decision is SituationDecision.ExecuteIntent)
        assertEquals(SituationIntent.SUMMARIZE_SCREEN, result.updatedContext.recentTurns.last().intent)
    }

    @Test
    fun explain_what_i_see_devuelve_execute_intent_y_turno_explain() {
        val result = brain.process(baseContext("qué estoy viendo"))
        assertTrue(result.decision is SituationDecision.ExecuteIntent)
        assertEquals(
            SituationIntent.EXPLAIN_WHAT_I_SEE,
            result.updatedContext.recentTurns.last().intent
        )
    }

    // --- Fase 6: confirmación de acciones ------------------------------------

    private fun pendingActionFor(intentName: String): PendingAction = PendingAction(
        label = "acción pendiente",
        intentName = intentName,
        riskLevel = AgentRiskLevel.MEDIUM,
        confirmationPrompt = "Para continuar, decí: confirmar.",
        expiresAt = 999_999L
    )

    @Test
    fun pending_open_app_mas_si_devuelve_execute_intent_open_app() {
        val ctx = baseContext(
            command = "sí",
            state = SituationState.WAITING_CONFIRMATION,
            pendingAction = pendingActionFor(SituationIntent.OPEN_APP.name)
        )
        val result = brain.process(ctx)
        val decision = result.decision
        assertTrue(decision is SituationDecision.ExecuteIntent)
        assertEquals(SituationIntent.OPEN_APP, decision.intent)
        assertEquals(SituationState.EXECUTING_GUIDED_ACTION, decision.nextState)
        assertNull(result.updatedContext.pendingAction)
    }

    @Test
    fun pending_call_contact_mas_dale_devuelve_execute_intent_call_contact() {
        val ctx = baseContext(
            command = "dale",
            state = SituationState.WAITING_CONFIRMATION,
            pendingAction = pendingActionFor(SituationIntent.CALL_CONTACT.name)
        )
        val result = brain.process(ctx)
        val decision = result.decision
        assertTrue(decision is SituationDecision.ExecuteIntent)
        assertEquals(SituationIntent.CALL_CONTACT, decision.intent)
    }

    @Test
    fun pending_guide_user_mas_confirmo_devuelve_execute_intent_guide_user() {
        val ctx = baseContext(
            command = "confirmo",
            state = SituationState.WAITING_CONFIRMATION,
            pendingAction = pendingActionFor(SituationIntent.GUIDE_USER.name)
        )
        val result = brain.process(ctx)
        val decision = result.decision
        assertTrue(decision is SituationDecision.ExecuteIntent)
        assertEquals(SituationIntent.GUIDE_USER, decision.intent)
    }

    @Test
    fun pending_action_mas_cancela_devuelve_cancel_y_limpia_pending() {
        val ctx = baseContext(
            command = "cancelá",
            state = SituationState.WAITING_CONFIRMATION,
            pendingAction = pendingActionFor(SituationIntent.OPEN_APP.name)
        )
        val result = brain.process(ctx)
        assertTrue(result.decision is SituationDecision.Cancel)
        assertNull(result.updatedContext.pendingAction)
    }

    @Test
    fun pending_action_con_intent_desconocido_mas_si_no_crashea_y_devuelve_reject() {
        val ctx = baseContext(
            command = "sí",
            state = SituationState.WAITING_CONFIRMATION,
            pendingAction = pendingActionFor("INTENT_QUE_NO_EXISTE")
        )
        val result = brain.process(ctx)
        assertTrue(result.decision is SituationDecision.Reject)
    }

    @Test
    fun pending_action_mas_comando_ambiguo_devuelve_speak_pidiendo_confirmacion() {
        val pending = pendingActionFor(SituationIntent.OPEN_APP.name)
        val ctx = baseContext(
            command = "mmm a ver",
            state = SituationState.WAITING_CONFIRMATION,
            pendingAction = pending
        )
        val result = brain.process(ctx)
        val decision = result.decision
        assertTrue(decision is SituationDecision.Speak)
        assertEquals(pending.confirmationPrompt, decision.message)
        // La acción pendiente sigue viva: la conversación quedó trabada.
        assertNotNull(result.updatedContext.pendingAction)
    }

    @Test
    fun ask_confirmation_para_open_app_crea_pending_action_con_intent_name_open_app() {
        val result = brain.process(baseContext("abrí WhatsApp"))
        val decision = result.decision
        assertTrue(decision is SituationDecision.AskConfirmation)
        assertEquals(SituationIntent.OPEN_APP.name, decision.pendingAction.intentName)
    }

    @Test
    fun ask_confirmation_para_call_contact_crea_pending_action_con_intent_name_call_contact() {
        val result = brain.process(baseContext("llamá a Sofi"))
        val decision = result.decision
        assertTrue(decision is SituationDecision.AskConfirmation)
        assertEquals(SituationIntent.CALL_CONTACT.name, decision.pendingAction.intentName)
    }

    @Test
    fun write_message_sigue_generando_ask_confirmation_en_el_brain() {
        val result = brain.process(baseContext("avisale a Sofi que llego tarde"))
        val decision = result.decision
        assertTrue(decision is SituationDecision.AskConfirmation)
        assertEquals(SituationIntent.WRITE_MESSAGE.name, decision.pendingAction.intentName)
    }

    @Test
    fun write_message_completo_genera_ask_confirmation_con_payload_contact_message() {
        val result = brain.process(baseContext("avisale a Sofi que llego tarde"))
        val decision = result.decision
        assertTrue(decision is SituationDecision.AskConfirmation)
        val pending = decision.pendingAction
        assertEquals(SituationIntent.WRITE_MESSAGE.name, pending.intentName)
        assertEquals("Sofi", pending.payload["contact"])
        assertEquals("llego tarde", pending.payload["message"])
    }

    @Test
    fun situation_intent_from_pending_action_mapea_y_devuelve_null_si_no_existe() {
        assertEquals(
            SituationIntent.OPEN_APP,
            situationIntentFromPendingAction(pendingActionFor(SituationIntent.OPEN_APP.name))
        )
        assertNull(situationIntentFromPendingAction(pendingActionFor("NO_EXISTE")))
    }

    // --- Fase 7: PendingAction con payload + ejecución confirmada ------------

    private fun richPendingAction(
        intentName: String,
        originalCommand: String,
        target: String
    ): PendingAction = PendingAction(
        label = "acción pendiente",
        intentName = intentName,
        riskLevel = AgentRiskLevel.MEDIUM,
        confirmationPrompt = "Para continuar, decí: confirmar.",
        expiresAt = 999_999L,
        originalCommand = originalCommand,
        target = target
    )

    @Test
    fun ask_confirmation_open_app_crea_pending_action_con_original_command_y_target() {
        val result = brain.process(baseContext("abrí WhatsApp"))
        val decision = result.decision
        assertTrue(decision is SituationDecision.AskConfirmation)
        assertEquals("abrí WhatsApp", decision.pendingAction.originalCommand)
        assertEquals("WhatsApp", decision.pendingAction.target)
    }

    @Test
    fun ask_confirmation_call_contact_crea_pending_action_con_original_command_y_target() {
        val result = brain.process(baseContext("llamá a Sofi"))
        val decision = result.decision
        assertTrue(decision is SituationDecision.AskConfirmation)
        assertEquals("llamá a Sofi", decision.pendingAction.originalCommand)
        assertEquals("Sofi", decision.pendingAction.target)
    }

    @Test
    fun confirmar_pending_open_app_devuelve_execute_intent_con_pending_action_no_null() {
        val pending = richPendingAction(SituationIntent.OPEN_APP.name, "abrí WhatsApp", "WhatsApp")
        val result = brain.process(
            baseContext(
                command = "sí",
                state = SituationState.WAITING_CONFIRMATION,
                pendingAction = pending
            )
        )
        val decision = result.decision
        assertTrue(decision is SituationDecision.ExecuteIntent)
        assertEquals(SituationIntent.OPEN_APP, decision.intent)
        assertNotNull(decision.pendingAction)
        assertEquals("abrí WhatsApp", decision.pendingAction.originalCommand)
        // La memoria queda limpia, la decisión transporta la copia.
        assertNull(result.updatedContext.pendingAction)
    }

    @Test
    fun confirmar_pending_call_contact_devuelve_execute_intent_con_pending_action_no_null() {
        val pending = richPendingAction(SituationIntent.CALL_CONTACT.name, "llamá a Sofi", "Sofi")
        val result = brain.process(
            baseContext(
                command = "dale",
                state = SituationState.WAITING_CONFIRMATION,
                pendingAction = pending
            )
        )
        val decision = result.decision
        assertTrue(decision is SituationDecision.ExecuteIntent)
        assertEquals(SituationIntent.CALL_CONTACT, decision.intent)
        assertNotNull(decision.pendingAction)
        assertEquals("llamá a Sofi", decision.pendingAction.originalCommand)
        assertNull(result.updatedContext.pendingAction)
    }

    @Test
    fun confirmar_pending_write_message_con_si_devuelve_execute_intent_con_pending_action_no_null() {
        val pending = PendingAction(
            label = "preparar un mensaje",
            intentName = SituationIntent.WRITE_MESSAGE.name,
            riskLevel = AgentRiskLevel.MEDIUM,
            confirmationPrompt = "¿Querés que prepare el mensaje?",
            expiresAt = 999_999L,
            originalCommand = "avisale a Sofi que llego tarde",
            target = "Sofi",
            payload = mapOf("contact" to "Sofi", "message" to "llego tarde")
        )
        val result = brain.process(
            baseContext(
                command = "sí",
                state = SituationState.WAITING_CONFIRMATION,
                pendingAction = pending
            )
        )
        val decision = result.decision
        assertTrue(decision is SituationDecision.ExecuteIntent)
        assertEquals(SituationIntent.WRITE_MESSAGE, decision.intent)
        assertNotNull(decision.pendingAction)
        assertEquals("Sofi", decision.pendingAction.payload["contact"])
        assertEquals("llego tarde", decision.pendingAction.payload["message"])
        assertNull(result.updatedContext.pendingAction)
    }

    // --- Fase 8: ActiveGoal multi-turno con slot-filling --------------------

    private fun goalWithSlots(
        intent: SituationIntent,
        filled: Map<String, String> = emptyMap(),
        missing: Set<String> = emptySet(),
        createdAt: Long = 1_000L
    ): ActiveGoal = ActiveGoal(
        description = "objetivo de test",
        intent = intent,
        createdAt = createdAt,
        slotsFilled = filled,
        slotsMissing = missing
    )

    @Test
    fun avisale_a_sofi_pide_mensaje_y_guarda_goal() {
        val result = brain.process(baseContext("avisale a Sofi"))
        val decision = result.decision
        assertTrue(decision is SituationDecision.Speak)
        assertTrue(decision.message.contains("Sofi"))
        val goal = result.updatedContext.activeGoal
        assertNotNull(goal)
        assertEquals(SituationIntent.WRITE_MESSAGE, goal.intent)
        assertEquals("Sofi", goal.slotsFilled["contact"])
        assertTrue("message" in goal.slotsMissing)
    }

    @Test
    fun continuacion_write_message_con_que_llego_en_15_pide_confirmacion() {
        val goal = goalWithSlots(
            SituationIntent.WRITE_MESSAGE,
            filled = mapOf("contact" to "Sofi"),
            missing = setOf("message")
        )
        val result = brain.process(
            baseContext(
                command = "que llego en 15",
                state = SituationState.SPEAKING,
                activeGoal = goal
            )
        )
        val decision = result.decision
        assertTrue(decision is SituationDecision.AskConfirmation)
        // El goal se consumió y la pendingAction transporta el payload.
        assertNull(result.updatedContext.activeGoal)
        val pending = decision.pendingAction
        assertEquals("Sofi", pending.payload["contact"])
        assertEquals("llego en 15", pending.payload["message"])
    }

    @Test
    fun write_message_multi_turno_genera_ask_confirmation_con_payload_contact_message() {
        val goal = goalWithSlots(
            SituationIntent.WRITE_MESSAGE,
            filled = mapOf("contact" to "Sofi"),
            missing = setOf("message")
        )
        val result = brain.process(
            baseContext(
                command = "que llego tarde",
                state = SituationState.SPEAKING,
                activeGoal = goal
            )
        )
        val decision = result.decision
        assertTrue(decision is SituationDecision.AskConfirmation)
        val pending = decision.pendingAction
        assertEquals("Sofi", pending.payload["contact"])
        assertEquals("llego tarde", pending.payload["message"])
    }

    @Test
    fun decile_a_sofi_que_llego_tarde_pide_confirmacion_completa() {
        val result = brain.process(baseContext("decile a Sofi que llego tarde"))
        val decision = result.decision
        assertTrue(decision is SituationDecision.AskConfirmation)
        val pending = decision.pendingAction
        assertEquals(SituationIntent.WRITE_MESSAGE.name, pending.intentName)
        assertEquals("Sofi", pending.payload["contact"])
        assertEquals("llego tarde", pending.payload["message"])
    }

    @Test
    fun llama_a_solo_pide_contacto_y_guarda_goal_call_contact() {
        val result = brain.process(baseContext("llamá a"))
        val decision = result.decision
        assertTrue(decision is SituationDecision.Speak)
        val goal = result.updatedContext.activeGoal
        assertNotNull(goal)
        assertEquals(SituationIntent.CALL_CONTACT, goal.intent)
        assertTrue("contact" in goal.slotsMissing)
    }

    @Test
    fun continuacion_call_contact_con_sofi_pide_confirmacion() {
        val goal = goalWithSlots(
            SituationIntent.CALL_CONTACT,
            missing = setOf("contact")
        )
        val result = brain.process(
            baseContext(
                command = "Sofi",
                state = SituationState.SPEAKING,
                activeGoal = goal
            )
        )
        val decision = result.decision
        assertTrue(decision is SituationDecision.AskConfirmation)
        assertEquals("Sofi", decision.pendingAction.target)
        assertNull(result.updatedContext.activeGoal)
    }

    @Test
    fun active_goal_call_contact_mas_cancela_devuelve_cancel() {
        val goal = goalWithSlots(SituationIntent.CALL_CONTACT, missing = setOf("contact"))
        val result = brain.process(
            baseContext(
                command = "cancelá",
                state = SituationState.SPEAKING,
                activeGoal = goal
            )
        )
        assertTrue(result.decision is SituationDecision.Cancel)
        assertNull(result.updatedContext.activeGoal)
    }

    @Test
    fun abri_solo_pide_app_y_guarda_goal_open_app() {
        val result = brain.process(baseContext("abrí"))
        val decision = result.decision
        assertTrue(decision is SituationDecision.Speak)
        val goal = result.updatedContext.activeGoal
        assertNotNull(goal)
        assertEquals(SituationIntent.OPEN_APP, goal.intent)
        assertTrue("target" in goal.slotsMissing)
    }

    @Test
    fun continuacion_open_app_con_whatsapp_pide_confirmacion() {
        val goal = goalWithSlots(
            SituationIntent.OPEN_APP,
            missing = setOf("target")
        )
        val result = brain.process(
            baseContext(
                command = "WhatsApp",
                state = SituationState.SPEAKING,
                activeGoal = goal
            )
        )
        val decision = result.decision
        assertTrue(decision is SituationDecision.AskConfirmation)
        assertEquals("WhatsApp", decision.pendingAction.target)
        assertNull(result.updatedContext.activeGoal)
    }

    @Test
    fun active_goal_expirado_no_continua() {
        val expired = ActiveGoal(
            description = "viejo",
            intent = SituationIntent.WRITE_MESSAGE,
            createdAt = 0L,
            ttlMillis = 1_000L,
            slotsFilled = mapOf("contact" to "Sofi"),
            slotsMissing = setOf("message")
        )
        val result = brain.process(
            baseContext(
                command = "que llego en 15",
                state = SituationState.SPEAKING,
                activeGoal = expired,
                timestamp = 1_000_000L
            )
        )
        // El goal estaba expirado: se descarta y NO se continúa.
        assertNull(result.updatedContext.activeGoal)
        assertFalse(result.decision is SituationDecision.AskConfirmation)
    }

    @Test
    fun si_no_completa_active_goal_incompleto() {
        val goal = goalWithSlots(SituationIntent.CALL_CONTACT, missing = setOf("contact"))
        val result = brain.process(
            baseContext(
                command = "sí",
                state = SituationState.SPEAKING,
                activeGoal = goal
            )
        )
        // El slot sigue sin completarse.
        val stillThere = result.updatedContext.activeGoal
        assertNotNull(stillThere)
        assertTrue("contact" in stillThere.slotsMissing)
    }

    @Test
    fun ask_confirmation_completo_via_goal_tiene_payload_contact_message() {
        // Una sola línea: el Brain detecta WRITE_MESSAGE completo y arma el
        // payload directamente, sin necesidad de un segundo turno.
        val result = brain.process(baseContext("decile a Sofi que llego en 15"))
        val decision = result.decision
        assertTrue(decision is SituationDecision.AskConfirmation)
        val pending = decision.pendingAction
        assertEquals("Sofi", pending.target)
        assertEquals("avisale a Sofi que llego en 15", pending.originalCommand)
        assertEquals("Sofi", pending.payload["contact"])
        assertEquals("llego en 15", pending.payload["message"])
    }
}
