package com.ojoclaro.android.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SafetyPolicyTest {

    private val parser = LocalIntentParser()

    @Test
    fun aceptaIntentBasico() {
        val parsed = parser.parse("qué puedo decir")
        val decision = SafetyPolicy.gate(parsed)
        assertIs<SafetyDecision.Accept>(decision)
    }

    @Test
    fun aceptaRepeatLastSinPedirConfirmacion() {
        val parsed = parser.parse("repetí")
        assertEquals(AgentIntent.REPEAT_LAST, parsed.intent)
        val decision = SafetyPolicy.gate(parsed)
        assertIs<SafetyDecision.Accept>(decision)
    }

    @Test
    fun aceptaComposeConSlotsCompletos() {
        val parsed = parser.parse("mandale a ContactoDemo que estoy llegando")
        val decision = SafetyPolicy.gate(parsed)
        assertIs<SafetyDecision.Accept>(decision)
    }

    @Test
    fun aceptaCallConContactoDemo() {
        val parsed = parser.parse("llamá a ContactoDemo")
        val decision = SafetyPolicy.gate(parsed)
        assertIs<SafetyDecision.Accept>(decision)
    }

    @Test
    fun rechazaIntentFueraDeWhitelist() {
        // Construimos a mano un parsed con un intent inválido para simular
        // una propuesta de IA futura mal portada.
        val rogue = ParsedAgentIntent(
            intent = AgentIntent.CREATE_REMINDER, // todavía no permitido
            slots = listOf(AgentSlot(AgentSlotName.RAW_COMMAND, "x", 1f)),
            rawText = "x",
            confidence = 0.9f
        )
        val decision = SafetyPolicy.gate(rogue)
        val rejection = assertIs<SafetyDecision.Reject>(decision)
        assertEquals("intent_not_allowed", rejection.reason)
    }

    @Test
    fun rechazaConfianzaInvalida() {
        val parsed = ParsedAgentIntent(
            intent = AgentIntent.HELP,
            slots = emptyList(),
            rawText = "x",
            confidence = 1.5f
        )
        val decision = SafetyPolicy.gate(parsed)
        val rejection = assertIs<SafetyDecision.Reject>(decision)
        assertEquals("invalid_confidence", rejection.reason)
    }

    @Test
    fun rechazaComposeSinContactoDemoYaConfirmado() {
        // Intent COMPOSE pero sin contactName y sin missingSlots.
        // Esto solo puede ocurrir si una capa externa (IA) trampea.
        val malformed = ParsedAgentIntent(
            intent = AgentIntent.COMPOSE_WHATSAPP_MESSAGE,
            slots = listOf(
                AgentSlot(AgentSlotName.MESSAGE_TEXT, "hola", 0.9f)
            ),
            rawText = "x",
            confidence = 0.9f,
            requiresConfirmation = true
        )
        val decision = SafetyPolicy.gate(malformed)
        val rejection = assertIs<SafetyDecision.Reject>(decision)
        assertEquals("compose_missing_contact", rejection.reason)
    }

    @Test
    fun rechazaCallSinContactoDemoYaConfirmado() {
        val malformed = ParsedAgentIntent(
            intent = AgentIntent.CALL_CONTACT,
            slots = listOf(AgentSlot(AgentSlotName.RAW_COMMAND, "x", 0.9f)),
            rawText = "x",
            confidence = 0.9f,
            requiresConfirmation = true
        )
        val decision = SafetyPolicy.gate(malformed)
        val rejection = assertIs<SafetyDecision.Reject>(decision)
        assertEquals("call_missing_contact", rejection.reason)
    }

    @Test
    fun rechazaMensajeConContrasena() {
        val malformed = ParsedAgentIntent(
            intent = AgentIntent.COMPOSE_WHATSAPP_MESSAGE,
            slots = listOf(
                AgentSlot(AgentSlotName.CONTACT_NAME, "ContactoDemo", 0.9f),
                AgentSlot(AgentSlotName.MESSAGE_TEXT, "mi password es secreto123", 0.9f)
            ),
            rawText = "x",
            confidence = 0.9f,
            requiresConfirmation = true
        )
        val decision = SafetyPolicy.gate(malformed)
        val rejection = assertIs<SafetyDecision.Reject>(decision)
        assertEquals("message_payload_unsafe", rejection.reason)
        assertTrue(rejection.spokenExplanation.contains("sensibles"))
    }

    @Test
    fun rechazaCallSinConfirmacionYsinSlotsFaltantes() {
        // CALL con slots completos pero sin requiresConfirmation = true.
        // Una capa externa quería ejecutarlo solo. La policy lo bloquea.
        val malformed = ParsedAgentIntent(
            intent = AgentIntent.CALL_CONTACT,
            slots = listOf(
                AgentSlot(AgentSlotName.CONTACT_NAME, "ContactoDemo", 0.9f)
            ),
            rawText = "x",
            confidence = 0.9f,
            requiresConfirmation = false
        )
        val decision = SafetyPolicy.gate(malformed)
        val rejection = assertIs<SafetyDecision.Reject>(decision)
        assertEquals("missing_required_confirmation", rejection.reason)
    }

    @Test
    fun aceptaUnknownPorqueElManagerLoManeja() {
        val parsed = parser.parse("hacé algo raro")
        val decision = SafetyPolicy.gate(parsed)
        assertIs<SafetyDecision.Accept>(decision)
    }
}
