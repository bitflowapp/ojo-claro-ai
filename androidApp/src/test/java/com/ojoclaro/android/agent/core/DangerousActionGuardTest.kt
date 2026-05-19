package com.ojoclaro.android.agent.core

import com.ojoclaro.android.agent.AgentIntent
import com.ojoclaro.android.agent.AgentSlotName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

class DangerousActionGuardTest {

    private fun composeAction(
        message: String,
        contact: String = "Mama",
        rawCommand: String? = null,
        requiresConfirmation: Boolean = true
    ): AgentAction = AgentAction(
        id = "action-test",
        toolId = AgentToolId.WHATSAPP,
        intent = AgentIntent.COMPOSE_WHATSAPP_MESSAGE,
        slots = buildMap {
            put(AgentSlotName.CONTACT_NAME, contact)
            put(AgentSlotName.MESSAGE_TEXT, message)
            rawCommand?.let { put(AgentSlotName.RAW_COMMAND, it) }
        },
        risk = AgentRiskLevel.MEDIUM,
        requiresConfirmation = requiresConfirmation,
        spokenPreview = "Voy a preparar un WhatsApp.",
        confirmationPrompt = if (requiresConfirmation) "Decí: confirmar." else null
    )

    @Test
    fun `benign message returns Safe`() {
        val action = composeAction("estoy llegando en diez minutos")

        val verdict = DangerousActionGuard.review(action)

        assertTrue(verdict is DangerousActionGuard.Verdict.Safe)
    }

    @Test
    fun `message with pagar verb is elevated as financial`() {
        val action = composeAction("pagar 5000 ahora")

        val verdict = DangerousActionGuard.review(action)

        val elevated = verdict as? DangerousActionGuard.Verdict.Elevated
            ?: fail("expected Elevated, got $verdict")
        assertEquals("dangerous_financial_verb", elevated.reason)
        assertTrue(elevated.matchedVerbs.any { it.contains("pagar") })
    }

    @Test
    fun `message with transferi verb is elevated as financial`() {
        val action = composeAction("transferi diez mil al banco")

        val verdict = DangerousActionGuard.review(action)

        val elevated = verdict as? DangerousActionGuard.Verdict.Elevated
            ?: fail("expected Elevated")
        assertEquals("dangerous_financial_verb", elevated.reason)
    }

    @Test
    fun `message with borrar verb is elevated as destructive`() {
        val action = composeAction("borrar todo el archivo")

        val verdict = DangerousActionGuard.review(action)

        val elevated = verdict as? DangerousActionGuard.Verdict.Elevated
            ?: fail("expected Elevated")
        assertEquals("dangerous_destructive_verb", elevated.reason)
    }

    @Test
    fun `message with comprar verb is elevated`() {
        val action = composeAction("comprar el pasaje urgente")

        val verdict = DangerousActionGuard.review(action)

        assertTrue(verdict is DangerousActionGuard.Verdict.Elevated)
    }

    @Test
    fun `raw command with dangerous verb is detected even if message is clean`() {
        val action = composeAction(
            message = "decile que estoy en camino",
            rawCommand = "transferi mil pesos a juan"
        )

        val verdict = DangerousActionGuard.review(action)

        assertTrue(verdict is DangerousActionGuard.Verdict.Elevated)
    }

    @Test
    fun `apply elevates risk to HIGH and adds warning prefix`() {
        val original = composeAction("pagar 1000 al taller")
        val verdict = DangerousActionGuard.review(original)
            as DangerousActionGuard.Verdict.Elevated

        val updated = DangerousActionGuard.apply(original, verdict)

        assertEquals(AgentRiskLevel.HIGH, updated.risk)
        assertTrue(updated.requiresConfirmation)
        assertNotNull(updated.confirmationPrompt)
        assertTrue(updated.confirmationPrompt!!.contains("Atencion", ignoreCase = true) ||
            updated.confirmationPrompt!!.contains("Atención", ignoreCase = true))
        assertTrue(updated.confirmationPrompt!!.contains("pagar"))
    }

    @Test
    fun `apply is idempotent on prompt`() {
        val original = composeAction("pagar 1000 al taller")
        val verdict = DangerousActionGuard.review(original)
            as DangerousActionGuard.Verdict.Elevated

        val once = DangerousActionGuard.apply(original, verdict)
        val twice = DangerousActionGuard.apply(once, verdict)

        // Second application should NOT duplicate the warning header.
        val countAttention = once.confirmationPrompt!!.lowercase().split("atenci").size - 1
        val countAttentionTwice = twice.confirmationPrompt!!.lowercase().split("atenci").size - 1
        assertEquals(countAttention, countAttentionTwice)
    }

    @Test
    fun `partial word match is not detected as verb`() {
        // "pagarse" no debería matchear "pagar" como verbo aislado? — sí lo matchea
        // porque pagar es prefijo. Pero "comparar" NO debería matchear "comprar".
        // El test cubre el caso comparar/comprar.
        val action = composeAction("vamos a comparar precios")

        val verdict = DangerousActionGuard.review(action)

        assertTrue(verdict is DangerousActionGuard.Verdict.Safe, "comparar should not trigger comprar")
    }

    @Test
    fun `apply does not lower risk if already HIGH`() {
        val highAction = composeAction("transferir mil").copy(
            risk = AgentRiskLevel.HIGH
        )
        val verdict = DangerousActionGuard.review(highAction)
            as DangerousActionGuard.Verdict.Elevated

        val updated = DangerousActionGuard.apply(highAction, verdict)

        assertEquals(AgentRiskLevel.HIGH, updated.risk)
    }
}
