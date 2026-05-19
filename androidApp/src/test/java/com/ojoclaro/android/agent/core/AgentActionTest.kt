package com.ojoclaro.android.agent.core

import com.ojoclaro.android.agent.AgentIntent
import com.ojoclaro.android.agent.AgentSlot
import com.ojoclaro.android.agent.AgentSlotName
import com.ojoclaro.android.agent.ParsedAgentIntent
import com.ojoclaro.android.agent.core.tool.AgentToolRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AgentActionTest {

    private val registry = AgentToolRegistry()
    private val whatsApp = registry.byId(AgentToolId.WHATSAPP)!!

    @Test
    fun `valid action keeps slots and is ready when no missing slots`() {
        val action = AgentAction(
            id = "a1",
            toolId = AgentToolId.WHATSAPP,
            intent = AgentIntent.COMPOSE_WHATSAPP_MESSAGE,
            slots = mapOf(AgentSlotName.CONTACT_NAME to "Mama"),
            risk = AgentRiskLevel.MEDIUM,
            requiresConfirmation = true,
            spokenPreview = "Voy a preparar WhatsApp.",
            confirmationPrompt = "Decí confirmar."
        )

        assertTrue(action.isReady)
        assertEquals("Mama", action.slotValue(AgentSlotName.CONTACT_NAME))
    }

    @Test
    fun `requiresConfirmation true without prompt fails fast`() {
        assertFailsWith<IllegalArgumentException> {
            AgentAction(
                id = "a1",
                toolId = AgentToolId.WHATSAPP,
                intent = AgentIntent.COMPOSE_WHATSAPP_MESSAGE,
                slots = emptyMap(),
                risk = AgentRiskLevel.MEDIUM,
                requiresConfirmation = true,
                spokenPreview = "Voy a preparar WhatsApp.",
                confirmationPrompt = null
            )
        }
    }

    @Test
    fun `blocked risk is rejected by constructor`() {
        assertFailsWith<IllegalArgumentException> {
            AgentAction(
                id = "a1",
                toolId = AgentToolId.GENERIC_APP,
                intent = AgentIntent.UNKNOWN,
                slots = emptyMap(),
                risk = AgentRiskLevel.BLOCKED,
                requiresConfirmation = false,
                spokenPreview = "x"
            )
        }
    }

    @Test
    fun `fromParsedIntent strips raw command slot and keeps real slots`() {
        val parsed = ParsedAgentIntent(
            intent = AgentIntent.COMPOSE_WHATSAPP_MESSAGE,
            slots = listOf(
                AgentSlot(AgentSlotName.CONTACT_NAME, "Juan", 0.9f),
                AgentSlot(AgentSlotName.MESSAGE_TEXT, "estoy llegando", 0.9f),
                AgentSlot(AgentSlotName.RAW_COMMAND, "mandale a Juan estoy llegando", 0.9f)
            ),
            rawText = "mandale a Juan estoy llegando",
            confidence = 0.9f,
            requiresConfirmation = true
        )

        val action = AgentAction.fromParsedIntent(
            parsed = parsed,
            tool = whatsApp,
            actionId = "action-1",
            spokenPreview = "Preparo WhatsApp para Juan.",
            confirmationPrompt = "Decí confirmar."
        )

        assertEquals(2, action.slots.size)
        assertNull(action.slots[AgentSlotName.RAW_COMMAND])
        assertEquals("Juan", action.slots[AgentSlotName.CONTACT_NAME])
        assertEquals("estoy llegando", action.slots[AgentSlotName.MESSAGE_TEXT])
        assertTrue(action.requiresConfirmation)
    }

    @Test
    fun `fromParsedIntent propagates missing slots from parser`() {
        val parsed = ParsedAgentIntent(
            intent = AgentIntent.COMPOSE_WHATSAPP_MESSAGE,
            slots = emptyList(),
            rawText = "mandale a alguien",
            confidence = 0.6f,
            missingSlots = listOf(AgentSlotName.CONTACT_NAME, AgentSlotName.MESSAGE_TEXT)
        )

        val action = AgentAction.fromParsedIntent(
            parsed = parsed,
            tool = whatsApp,
            actionId = "a2",
            spokenPreview = "Necesito más info.",
            confirmationPrompt = null,
            requiresConfirmation = false
        )

        assertFalse(action.isReady)
        assertTrue(AgentSlotName.CONTACT_NAME in action.missingSlots)
        assertTrue(AgentSlotName.MESSAGE_TEXT in action.missingSlots)
    }

    @Test
    fun `toPlanStep mirrors fields to plan step shape`() {
        val action = AgentAction(
            id = "a3",
            toolId = AgentToolId.PHONE,
            intent = AgentIntent.CALL_CONTACT,
            slots = mapOf(AgentSlotName.CONTACT_NAME to "Mama"),
            risk = AgentRiskLevel.MEDIUM,
            requiresConfirmation = true,
            spokenPreview = "Voy a abrir el marcador para Mama.",
            confirmationPrompt = "Para abrir, decí confirmar."
        )

        val step = action.toPlanStep()

        assertEquals(action.id, step.id)
        assertEquals(action.toolId, step.toolId)
        assertEquals(action.slots, step.slotValues)
        assertEquals(action.risk, step.risk)
        assertEquals(action.confirmationPrompt, step.confirmationPrompt)
        assertEquals(action.requiresConfirmation, step.requiresConfirmation)
    }
}
