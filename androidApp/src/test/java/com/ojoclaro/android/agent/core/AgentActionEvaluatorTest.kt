package com.ojoclaro.android.agent.core

import com.ojoclaro.android.agent.AgentIntent
import com.ojoclaro.android.agent.AgentSlot
import com.ojoclaro.android.agent.AgentSlotName
import com.ojoclaro.android.agent.AgentState
import com.ojoclaro.android.agent.ParsedAgentIntent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

class AgentActionEvaluatorTest {

    private val evaluator = AgentActionEvaluator()
    private val now = 1_700_000_000_000L

    private fun baseContext(
        screen: AgentScreenContext? = null,
        mode: AgentExecutionMode = AgentExecutionMode.ACCESSIBILITY_VOICE
    ) = AgentContextSnapshot(
        mode = mode,
        agentState = AgentState.IDLE,
        screen = screen,
        nowMillis = now
    )

    @Test
    fun `open whatsapp without contact returns NeedsSlot for contact name`() {
        // WHATSAPP_TOOL.requiredSlots = {CONTACT_NAME}. Sin contacto explícito,
        // el evaluator devuelve NeedsSlot para que la UI pregunte.
        val parsed = ParsedAgentIntent(
            intent = AgentIntent.OPEN_WHATSAPP,
            slots = listOf(AgentSlot(AgentSlotName.RAW_COMMAND, "abri whatsapp", 0.95f)),
            rawText = "abri whatsapp",
            confidence = 0.95f,
            requiresConfirmation = false
        )

        val decision = evaluator.evaluate(parsed, baseContext(), now)

        val needsSlot = decision as? AgentActionDecision.NeedsSlot
            ?: fail("expected NeedsSlot, got $decision")
        assertEquals(AgentToolId.WHATSAPP, needsSlot.toolId)
        assertEquals(AgentSlotName.CONTACT_NAME, needsSlot.slot)
        assertTrue(needsSlot.spokenPrompt.isNotBlank())
    }

    @Test
    fun `open whatsapp with contact returns NeedsConfirmation`() {
        val parsed = ParsedAgentIntent(
            intent = AgentIntent.OPEN_WHATSAPP,
            slots = listOf(
                AgentSlot(AgentSlotName.CONTACT_NAME, "Mama", 0.95f),
                AgentSlot(AgentSlotName.RAW_COMMAND, "abri whatsapp con mama", 0.95f)
            ),
            rawText = "abri whatsapp con mama",
            confidence = 0.95f,
            requiresConfirmation = false
        )

        val decision = evaluator.evaluate(parsed, baseContext(), now)

        val needs = decision as? AgentActionDecision.NeedsConfirmation
            ?: fail("expected NeedsConfirmation, got $decision")
        assertEquals(AgentToolId.WHATSAPP, needs.action.toolId)
        assertTrue(needs.action.requiresConfirmation)
        assertTrue(needs.spokenConfirmationPrompt.isNotBlank())
    }

    @Test
    fun `compose with contact and message returns NeedsConfirmation with action ready`() {
        val parsed = ParsedAgentIntent(
            intent = AgentIntent.COMPOSE_WHATSAPP_MESSAGE,
            slots = listOf(
                AgentSlot(AgentSlotName.CONTACT_NAME, "Mama", 0.92f),
                AgentSlot(AgentSlotName.MESSAGE_TEXT, "estoy llegando", 0.92f),
                AgentSlot(AgentSlotName.RAW_COMMAND, "mandale a mama estoy llegando", 0.92f)
            ),
            rawText = "mandale a mama estoy llegando",
            confidence = 0.92f,
            requiresConfirmation = true
        )

        val decision = evaluator.evaluate(parsed, baseContext(), now)
        val needs = decision as? AgentActionDecision.NeedsConfirmation
            ?: fail("expected NeedsConfirmation, got $decision")
        assertEquals(AgentIntent.COMPOSE_WHATSAPP_MESSAGE, needs.action.intent)
        assertEquals("Mama", needs.action.slotValue(AgentSlotName.CONTACT_NAME))
        assertEquals("estoy llegando", needs.action.slotValue(AgentSlotName.MESSAGE_TEXT))
        assertEquals(AgentRiskLevel.MEDIUM, needs.action.risk)
        assertTrue(needs.action.isReady)
    }

    @Test
    fun `missing contact slot returns NeedsSlot`() {
        val parsed = ParsedAgentIntent(
            intent = AgentIntent.COMPOSE_WHATSAPP_MESSAGE,
            slots = emptyList(),
            rawText = "mandar mensaje",
            confidence = 0.6f,
            missingSlots = listOf(AgentSlotName.CONTACT_NAME)
        )

        val decision = evaluator.evaluate(parsed, baseContext(), now)
        val needsSlot = decision as? AgentActionDecision.NeedsSlot
            ?: fail("expected NeedsSlot, got $decision")
        assertEquals(AgentToolId.WHATSAPP, needsSlot.toolId)
        assertEquals(AgentSlotName.CONTACT_NAME, needsSlot.slot)
    }

    @Test
    fun `screen hot zone blocks general actions`() {
        // READ_VISIBLE_SCREEN sale de SafetyPolicy con requiresConfirmation=true:
        // si no lo declarara, SafetyPolicy lo rechaza por "missing_required_confirmation"
        // antes de llegar al hot-zone check. Acá probamos específicamente el camino
        // donde la pantalla bancaria bloquea la acción.
        val parsed = ParsedAgentIntent(
            intent = AgentIntent.READ_VISIBLE_SCREEN,
            slots = listOf(AgentSlot(AgentSlotName.RAW_COMMAND, "leeme la pantalla", 0.95f)),
            rawText = "leeme la pantalla",
            confidence = 0.95f,
            requiresConfirmation = true
        )
        val screen = AgentScreenContext(
            packageName = "com.bank.example",
            isSensitive = true,
            isBankingScreen = true
        )

        val decision = evaluator.evaluate(parsed, baseContext(screen = screen), now)
        val rejected = decision as? AgentActionDecision.Rejected
            ?: fail("expected Rejected, got $decision")
        assertEquals("screen_hot_zone", rejected.reason)
    }

    @Test
    fun `repeat last is allowed during hot zone`() {
        val parsed = ParsedAgentIntent(
            intent = AgentIntent.REPEAT_LAST,
            slots = listOf(AgentSlot(AgentSlotName.RAW_COMMAND, "repeti", 0.97f)),
            rawText = "repeti",
            confidence = 0.97f
        )
        val screen = AgentScreenContext(
            packageName = "com.bank.example",
            isSensitive = true,
            isBankingScreen = true
        )

        val decision = evaluator.evaluate(parsed, baseContext(screen = screen), now)
        // Tool REPEAT_LAST risk is NONE and requiresConfirmation = false → Allowed.
        val allowed = decision as? AgentActionDecision.Allowed
            ?: fail("expected Allowed, got $decision")
        assertEquals(AgentToolId.REPEAT_LAST, allowed.action.toolId)
    }

    @Test
    fun `message payload with credit card is rejected`() {
        val parsed = ParsedAgentIntent(
            intent = AgentIntent.COMPOSE_WHATSAPP_MESSAGE,
            slots = listOf(
                AgentSlot(AgentSlotName.CONTACT_NAME, "Juan", 0.9f),
                AgentSlot(AgentSlotName.MESSAGE_TEXT, "mi tarjeta es 4111 1111 1111 1111", 0.9f),
                AgentSlot(AgentSlotName.RAW_COMMAND, "mandale a Juan mi tarjeta 4111...", 0.9f)
            ),
            rawText = "mandale a Juan mi tarjeta 4111 1111 1111 1111",
            confidence = 0.9f,
            requiresConfirmation = true
        )

        val decision = evaluator.evaluate(parsed, baseContext(), now)
        val rejected = decision as? AgentActionDecision.Rejected
            ?: fail("expected Rejected, got $decision")
        // SafetyPolicy.gate detecta el contenido sensible primero — el evaluator
        // re-empaqueta con prefijo "safety_". El resultado es el mismo: rechazo.
        assertTrue(
            rejected.reason == "message_payload_unsafe" ||
                rejected.reason == "safety_message_payload_unsafe",
            "expected message_payload_unsafe variant, got ${rejected.reason}"
        )
    }

    @Test
    fun `unknown intent is rejected with friendly text`() {
        val parsed = ParsedAgentIntent(
            intent = AgentIntent.UNKNOWN,
            slots = emptyList(),
            rawText = "xyz inintelegible",
            confidence = 0.1f
        )

        val decision = evaluator.evaluate(parsed, baseContext(), now)
        val rejected = decision as? AgentActionDecision.Rejected
            ?: fail("expected Rejected, got $decision")
        assertEquals("unknown_intent", rejected.reason)
        assertTrue(rejected.spokenText.isNotBlank())
    }

    @Test
    fun `risk word in command escalates confirmation requirement`() {
        // Caso: usuario dice "llamar al banco" → CALL_CONTACT con contacto = "al banco".
        val parsed = ParsedAgentIntent(
            intent = AgentIntent.CALL_CONTACT,
            slots = listOf(
                AgentSlot(AgentSlotName.CONTACT_NAME, "al banco", 0.7f),
                AgentSlot(AgentSlotName.RAW_COMMAND, "llamar al banco", 0.7f)
            ),
            rawText = "llamar al banco",
            confidence = 0.7f,
            requiresConfirmation = true
        )

        val decision = evaluator.evaluate(parsed, baseContext(), now)
        val needs = decision as? AgentActionDecision.NeedsConfirmation
            ?: fail("expected NeedsConfirmation, got $decision")
        // Tool PHONE.risk = MEDIUM. Comando con "banco" escala a HIGH.
        assertEquals(AgentRiskLevel.HIGH, needs.action.risk)
        assertNotNull(needs.action.confirmationPrompt)
    }

    @Test
    fun `precomputed screen risk warnings escalate confirmation even when command is clean`() {
        val parsed = ParsedAgentIntent(
            intent = AgentIntent.OPEN_WHATSAPP,
            slots = listOf(AgentSlot(AgentSlotName.RAW_COMMAND, "abri whatsapp", 0.95f)),
            rawText = "abri whatsapp",
            confidence = 0.95f,
            requiresConfirmation = false
        )
        val screen = AgentScreenContext(
            packageName = "ar.com.galicia",
            shortSummary = "Saldo disponible 100000"
        )
        // Construimos el snapshot vía AgentContext para que precompute warnings.
        val context = AgentContext.build(
            mode = AgentExecutionMode.ACCESSIBILITY_VOICE,
            agentState = AgentState.IDLE,
            nowMillis = now,
            screen = screen,
            commandRawText = "abri whatsapp"
        )

        val decision = evaluator.evaluate(parsed, context, now)

        // La pantalla bancaria activa hot-zone → cualquier acción excepto
        // las safe-intents es rejected.
        val rejected = decision as? AgentActionDecision.Rejected
            ?: fail("expected Rejected because screen is banking hot zone, got $decision")
        assertEquals("screen_hot_zone", rejected.reason)
    }

    @Test
    fun `dangerous verb in compose message elevates risk to HIGH`() {
        val parsed = ParsedAgentIntent(
            intent = AgentIntent.COMPOSE_WHATSAPP_MESSAGE,
            slots = listOf(
                AgentSlot(AgentSlotName.CONTACT_NAME, "Juan", 0.9f),
                AgentSlot(AgentSlotName.MESSAGE_TEXT, "transferi 5000 al alias", 0.9f),
                AgentSlot(AgentSlotName.RAW_COMMAND, "mandale a juan transferi 5000", 0.9f)
            ),
            rawText = "mandale a juan transferi 5000",
            confidence = 0.9f,
            requiresConfirmation = true
        )

        val decision = evaluator.evaluate(parsed, baseContext(), now)

        val needs = decision as? AgentActionDecision.NeedsConfirmation
            ?: fail("expected NeedsConfirmation, got $decision")
        assertEquals(AgentRiskLevel.HIGH, needs.action.risk)
        // El confirmationPrompt debe mencionar el verbo peligroso.
        assertTrue(needs.spokenConfirmationPrompt.contains("transferi", ignoreCase = true))
    }

    @Test
    fun `compose with benign message keeps MEDIUM risk`() {
        val parsed = ParsedAgentIntent(
            intent = AgentIntent.COMPOSE_WHATSAPP_MESSAGE,
            slots = listOf(
                AgentSlot(AgentSlotName.CONTACT_NAME, "Mama", 0.92f),
                AgentSlot(AgentSlotName.MESSAGE_TEXT, "estoy llegando", 0.92f),
                AgentSlot(AgentSlotName.RAW_COMMAND, "mandale a mama estoy llegando", 0.92f)
            ),
            rawText = "mandale a mama estoy llegando",
            confidence = 0.92f,
            requiresConfirmation = true
        )

        val decision = evaluator.evaluate(parsed, baseContext(), now)
        val needs = decision as? AgentActionDecision.NeedsConfirmation
            ?: fail("expected NeedsConfirmation")
        assertEquals(AgentRiskLevel.MEDIUM, needs.action.risk)
    }
}
