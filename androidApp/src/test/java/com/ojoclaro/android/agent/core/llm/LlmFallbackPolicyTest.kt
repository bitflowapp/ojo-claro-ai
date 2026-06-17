package com.ojoclaro.android.agent.core.llm

import com.ojoclaro.android.agent.AgentIntent
import com.ojoclaro.android.agent.AgentSlotName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LlmFallbackPolicyTest {

    private val policy = LlmFallbackPolicy()

    @Test
    fun returnsNotAvailableWhenLlmDisabled() {
        val result = policy.evaluate(
            candidate = candidate(AgentIntent.OPEN_MAPS),
            rawText = "abrí mapas",
            screenIsHotZone = false,
            llmEnabled = false
        )
        assertTrue(result is LlmFallbackResult.NotAvailable)
    }

    @Test
    fun rejectsCandidateOnHotZoneScreen() {
        val result = policy.evaluate(
            candidate = candidate(AgentIntent.OPEN_MAPS),
            rawText = "abrí mapas",
            screenIsHotZone = true,
            llmEnabled = true
        )
        assertTrue(result is LlmFallbackResult.RejectedBySafety)
        result as LlmFallbackResult.RejectedBySafety
        assertEquals("screen_hot_zone", result.reason)
    }

    @Test
    fun rejectsLowConfidenceCandidate() {
        val result = policy.evaluate(
            candidate = candidate(AgentIntent.OPEN_MAPS, confidence = 0.2f),
            rawText = "abrí mapas",
            screenIsHotZone = false,
            llmEnabled = true
        )
        assertTrue(result is LlmFallbackResult.NotAvailable)
    }

    @Test
    fun rejectsUnknownToolName() {
        val result = policy.evaluate(
            candidate = candidate(
                intent = AgentIntent.OPEN_MAPS,
                toolName = "pay_bill"
            ),
            rawText = "x",
            screenIsHotZone = false,
            llmEnabled = true
        )
        assertTrue(result is LlmFallbackResult.RejectedBySafety)
        (result as LlmFallbackResult.RejectedBySafety).let {
            assertEquals("tool_not_whitelisted", it.reason)
        }
    }

    @Test
    fun acceptsWhitelistedIntentWithCleanMessage() {
        val result = policy.evaluate(
            candidate = candidate(
                intent = AgentIntent.COMPOSE_WHATSAPP_MESSAGE,
                slots = mapOf(
                    AgentSlotName.CONTACT_NAME to "Sofi",
                    AgentSlotName.MESSAGE_TEXT to "estoy llegando"
                ),
                toolName = "WHATSAPP"
            ),
            rawText = "mandale a Sofi que estoy llegando",
            screenIsHotZone = false,
            llmEnabled = true
        )
        assertTrue(result is LlmFallbackResult.Accepted)
        val parsed = (result as LlmFallbackResult.Accepted).parsed
        assertEquals(AgentIntent.COMPOSE_WHATSAPP_MESSAGE, parsed.intent)
        assertEquals("Sofi", parsed.slotValue(AgentSlotName.CONTACT_NAME))
        assertEquals("estoy llegando", parsed.slotValue(AgentSlotName.MESSAGE_TEXT))
    }

    @Test
    fun rejectsUnsafeMessagePayloadFromLlm() {
        val result = policy.evaluate(
            candidate = candidate(
                intent = AgentIntent.COMPOSE_WHATSAPP_MESSAGE,
                slots = mapOf(
                    AgentSlotName.CONTACT_NAME to "Sofi",
                    AgentSlotName.MESSAGE_TEXT to "mi clave es asd1234"
                )
            ),
            rawText = "x",
            screenIsHotZone = false,
            llmEnabled = true
        )
        assertTrue(result is LlmFallbackResult.RejectedBySafety)
        result as LlmFallbackResult.RejectedBySafety
        assertEquals("message_payload_unsafe", result.reason)
    }

    @Test
    fun rejectsIntentOutsideWhitelist() {
        // CREATE_REMINDER existe en AgentIntent pero NO está en LlmIntentWhitelist.
        val result = policy.evaluate(
            candidate = candidate(AgentIntent.CREATE_REMINDER),
            rawText = "recordame algo",
            screenIsHotZone = false,
            llmEnabled = true
        )
        assertTrue(result is LlmFallbackResult.RejectedBySafety)
        (result as LlmFallbackResult.RejectedBySafety).let {
            assertEquals("intent_not_whitelisted", it.reason)
        }
    }

    @Test
    fun safetyPolicyRejectionPropagates() {
        // CALL_CONTACT sin contacto, sin missingSlots — SafetyPolicy debería rechazar.
        val result = policy.evaluate(
            candidate = candidate(
                intent = AgentIntent.CALL_CONTACT,
                slots = emptyMap()
            ),
            rawText = "llamá",
            screenIsHotZone = false,
            llmEnabled = true
        )
        assertTrue(result is LlmFallbackResult.RejectedBySafety)
        val rejection = result as LlmFallbackResult.RejectedBySafety
        assertTrue(rejection.reason.startsWith("safety_policy_"))
    }

    @Test
    fun whitelistMatchesCanonicalIntentsOnly() {
        assertTrue(AgentIntent.OPEN_MAPS in LlmIntentWhitelist.ALLOWED)
        assertTrue(AgentIntent.COMPOSE_WHATSAPP_MESSAGE in LlmIntentWhitelist.ALLOWED)
        assertTrue(AgentIntent.OPEN_PHONE in LlmIntentWhitelist.ALLOWED)
        assertTrue(AgentIntent.UNKNOWN in LlmIntentWhitelist.ALLOWED)
        // intents fuera de scope, deben quedar fuera del whitelist
        assertTrue(AgentIntent.CREATE_REMINDER !in LlmIntentWhitelist.ALLOWED)
        assertTrue(AgentIntent.CREATE_ALARM !in LlmIntentWhitelist.ALLOWED)
        assertTrue(AgentIntent.PLAY_MUSIC !in LlmIntentWhitelist.ALLOWED)
    }

    @Test
    fun acceptedHelperBuildsParsedFromCandidate() {
        val c = candidate(AgentIntent.OPEN_MAPS)
        val accepted = LlmFallbackResult.acceptedFrom(
            candidate = c,
            rawText = "abrí mapas"
        )
        assertNotNull(accepted)
        assertEquals(AgentIntent.OPEN_MAPS, accepted.parsed.intent)
    }

    private fun candidate(
        intent: AgentIntent,
        confidence: Float = 0.8f,
        toolName: String? = null,
        slots: Map<String, String> = emptyMap()
    ) = LlmIntentCandidate(
        intent = intent,
        toolNameSuggested = toolName,
        confidence = confidence,
        slotsProposed = slots,
        rawModelText = ""
    )
}
