package com.ojoclaro.android.agent.situation

import com.ojoclaro.android.agent.core.AgentRiskLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SituationConfirmedActionAdapterTest {

    private fun pending(
        intent: SituationIntent,
        originalCommand: String = "",
        target: String = "",
        payload: Map<String, String> = emptyMap()
    ): PendingAction = PendingAction(
        label = "accion pendiente",
        intentName = intent.name,
        riskLevel = AgentRiskLevel.MEDIUM,
        confirmationPrompt = "Para continuar, deci: confirmar.",
        expiresAt = 999_999L,
        originalCommand = originalCommand,
        target = target,
        payload = payload
    )

    private fun execute(
        intent: SituationIntent,
        pendingAction: PendingAction?
    ): SituationUiEffect.Execute = SituationUiEffect.Execute(
        intent = intent,
        reason = "confirmado",
        nextState = SituationState.EXECUTING_GUIDED_ACTION,
        pendingAction = pendingAction
    )

    @Test
    fun write_message_valido_devuelve_action_con_command_for_execution() {
        val pending = pending(
            intent = SituationIntent.WRITE_MESSAGE,
            target = "Sofi",
            payload = mapOf("contact" to "Sofi", "message" to "llego en 15")
        )

        val action = SituationConfirmedActionAdapter.fromExecuteEffect(
            execute(SituationIntent.WRITE_MESSAGE, pending)
        )

        assertEquals(SituationIntent.WRITE_MESSAGE, action?.intent)
        assertEquals("avisale a Sofi que llego en 15", action?.commandForExecution)
        assertEquals(pending, action?.pendingAction)
        assertEquals(true, action?.alreadyConfirmed)
    }

    @Test
    fun write_message_sin_pending_action_devuelve_null() {
        val action = SituationConfirmedActionAdapter.fromExecuteEffect(
            execute(SituationIntent.WRITE_MESSAGE, pendingAction = null)
        )

        assertNull(action)
    }

    @Test
    fun write_message_inseguro_devuelve_null() {
        val pending = pending(
            intent = SituationIntent.WRITE_MESSAGE,
            target = "Sofi",
            payload = mapOf("contact" to "Sofi", "message" to "el token es abc")
        )

        val action = SituationConfirmedActionAdapter.fromExecuteEffect(
            execute(SituationIntent.WRITE_MESSAGE, pending)
        )

        assertNull(action)
    }

    @Test
    fun open_app_confirmado_devuelve_action_con_comando_original() {
        val pending = pending(
            intent = SituationIntent.OPEN_APP,
            originalCommand = "abri WhatsApp",
            target = "WhatsApp"
        )

        val action = SituationConfirmedActionAdapter.fromExecuteEffect(
            execute(SituationIntent.OPEN_APP, pending)
        )

        assertEquals(SituationIntent.OPEN_APP, action?.intent)
        assertEquals("abri WhatsApp", action?.commandForExecution)
    }

    @Test
    fun call_contact_confirmado_devuelve_action_con_comando_original() {
        val pending = pending(
            intent = SituationIntent.CALL_CONTACT,
            originalCommand = "llama a Sofi",
            target = "Sofi"
        )

        val action = SituationConfirmedActionAdapter.fromExecuteEffect(
            execute(SituationIntent.CALL_CONTACT, pending)
        )

        assertEquals(SituationIntent.CALL_CONTACT, action?.intent)
        assertEquals("llama a Sofi", action?.commandForExecution)
    }

    @Test
    fun intent_no_soportado_devuelve_null() {
        val pending = pending(intent = SituationIntent.READ_SCREEN)

        val action = SituationConfirmedActionAdapter.fromExecuteEffect(
            execute(SituationIntent.READ_SCREEN, pending)
        )

        assertNull(action)
    }

    @Test
    fun pending_action_sin_comando_reconstruible_devuelve_null() {
        val pending = pending(intent = SituationIntent.OPEN_APP)

        val action = SituationConfirmedActionAdapter.fromExecuteEffect(
            execute(SituationIntent.OPEN_APP, pending)
        )

        assertNull(action)
    }
}
