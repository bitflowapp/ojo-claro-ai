package com.ojoclaro.android.agent.core.runtime

import com.ojoclaro.android.agent.AgentIntent
import com.ojoclaro.android.agent.AgentSlotName
import com.ojoclaro.android.agent.core.AgentAction
import com.ojoclaro.android.agent.core.AgentRiskLevel
import com.ojoclaro.android.agent.core.AgentToolId
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgentRuntimeBridgeFeedbackTest {

    private fun whatsAppAction(): AgentAction = AgentAction(
        id = "a-1",
        toolId = AgentToolId.WHATSAPP,
        intent = AgentIntent.COMPOSE_WHATSAPP_MESSAGE,
        slots = mapOf(
            AgentSlotName.CONTACT_NAME to "Mama",
            AgentSlotName.MESSAGE_TEXT to "estoy llegando"
        ),
        risk = AgentRiskLevel.MEDIUM,
        requiresConfirmation = true,
        spokenPreview = "Voy a preparar un WhatsApp.",
        confirmationPrompt = "Decí confirmar."
    )

    @Test
    fun `generic pending phrase is short and mentions confirmar y cancelar`() {
        val phrase = AgentRuntimeBridgeFeedback.GENERIC_PENDING
        assertTrue(phrase.length <= 140, "phrase too long: ${phrase.length}")
        assertTrue(phrase.contains("confirmar", ignoreCase = true))
        assertTrue(phrase.contains("cancelar", ignoreCase = true))
    }

    @Test
    fun `confirmed phrase for whatsapp never claims send happened`() {
        val phrase = AgentRuntimeBridgeFeedback.confirmed(whatsAppAction())
        assertTrue(phrase.isNotBlank())
        assertFalse(phrase.contains("enviado", ignoreCase = true))
        assertFalse(phrase.contains("ya se envió", ignoreCase = true))
        assertTrue(phrase.contains("No envío", ignoreCase = true))
    }

    @Test
    fun `confirmed phrase for phone clarifies that user dispatches the call`() {
        val phoneAction = whatsAppAction().copy(toolId = AgentToolId.PHONE)
        val phrase = AgentRuntimeBridgeFeedback.confirmed(phoneAction)
        assertTrue(phrase.contains("La llamada la disparás vos"))
    }

    @Test
    fun `no pending phrases are concrete and short`() {
        assertTrue(AgentRuntimeBridgeFeedback.NO_PENDING_CONFIRMATION.isNotBlank())
        assertTrue(AgentRuntimeBridgeFeedback.NO_PENDING_CANCELLATION.isNotBlank())
        assertTrue(AgentRuntimeBridgeFeedback.CANCELLED.isNotBlank())
        assertTrue(AgentRuntimeBridgeFeedback.EXPIRED.isNotBlank())
        assertTrue(AgentRuntimeBridgeFeedback.NO_PENDING_CONFIRMATION.length <= 80)
    }
}
