package com.ojoclaro.android.agent.runtime.whatsapp

import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppCallGuardrailPolicy.CallIntent
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppCallGuardrailPolicy.CallRequest
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppCallGuardrailPolicy.ConfirmationStrength
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppCallGuardrailPolicy.ContactResolution
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppCallGuardrailPolicy.Decision
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * WhatsApp Fluency (#6) — guardrails de llamadas (SOLO diseño). Verifica las
 * reglas; ninguna ejecuta una llamada real ([isExecutionEnabled] = false).
 */
class WhatsAppCallGuardrailPolicyTest {

    private fun decide(
        intent: CallIntent,
        contact: ContactResolution = ContactResolution.RESOLVED,
        confirmation: ConfirmationStrength = ConfirmationStrength.NONE,
        prior: Int = 0
    ): Decision = WhatsAppCallGuardrailPolicy.decide(CallRequest(intent, contact, confirmation, prior))

    @Test
    fun initiateBlocksAmbiguousOrMissingContact() {
        assertEquals(
            Decision.BLOCKED_AMBIGUOUS_CONTACT,
            decide(CallIntent.INITIATE_CALL, contact = ContactResolution.AMBIGUOUS, confirmation = ConfirmationStrength.STRONG, prior = 5)
        )
        assertEquals(
            Decision.BLOCKED_NO_CONTACT,
            decide(CallIntent.INITIATE_VIDEO_CALL, contact = ContactResolution.NOT_FOUND, confirmation = ConfirmationStrength.STRONG, prior = 5)
        )
    }

    @Test
    fun initiateNeverHappensOnWeakConfirmation() {
        assertEquals(
            Decision.BLOCKED_WEAK_CONFIRMATION,
            decide(CallIntent.INITIATE_CALL, confirmation = ConfirmationStrength.WEAK)
        )
        assertEquals(
            Decision.BLOCKED_WEAK_CONFIRMATION,
            decide(CallIntent.INITIATE_VIDEO_CALL, confirmation = ConfirmationStrength.WEAK, prior = 1)
        )
    }

    @Test
    fun initiateRequiresDoubleStrongConfirmation() {
        assertEquals(
            Decision.REQUIRES_FIRST_STRONG_CONFIRMATION,
            decide(CallIntent.INITIATE_CALL, confirmation = ConfirmationStrength.NONE)
        )
        assertEquals(
            Decision.REQUIRES_SECOND_STRONG_CONFIRMATION,
            decide(CallIntent.INITIATE_CALL, confirmation = ConfirmationStrength.STRONG, prior = 0)
        )
        assertEquals(
            Decision.WOULD_ALLOW,
            decide(CallIntent.INITIATE_CALL, confirmation = ConfirmationStrength.STRONG, prior = 1)
        )
    }

    @Test
    fun incomingNeedsOneExplicitConfirmation() {
        assertEquals(Decision.REQUIRES_CONFIRMATION, decide(CallIntent.ACCEPT_INCOMING, confirmation = ConfirmationStrength.NONE))
        assertEquals(Decision.REQUIRES_CONFIRMATION, decide(CallIntent.REJECT_INCOMING, confirmation = ConfirmationStrength.NONE))
        assertEquals(Decision.WOULD_ALLOW, decide(CallIntent.ACCEPT_INCOMING, confirmation = ConfirmationStrength.STRONG))
        assertEquals(Decision.WOULD_ALLOW, decide(CallIntent.REJECT_INCOMING, confirmation = ConfirmationStrength.WEAK))
    }

    @Test
    fun executionIsNeverEnabledThisSprint() {
        assertFalse(WhatsAppCallGuardrailPolicy.isExecutionEnabled())
        // Aun con todas las guardas OK, hoy se bloquea la ejecución real.
        assertTrue(WhatsAppCallGuardrailPolicy.blocksExecution(Decision.WOULD_ALLOW))
        assertTrue(WhatsAppCallGuardrailPolicy.blocksExecution(Decision.BLOCKED_WEAK_CONFIRMATION))
    }
}
