package com.ojoclaro.android.agent.core.generic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GenericAppSafetyGateTest {

    private val gate = GenericAppSafetyGate()

    @Test
    fun banksAreAlwaysBlocked() {
        val request = GenericAppActionRequest(
            packageName = "com.bbva.app",
            capability = GenericAppCapability.OPEN_APP,
            description = "abrí mi banco"
        )
        val decision = gate.evaluate(request)
        assertTrue(decision is GenericAppGateDecision.Blocked)
        (decision as GenericAppGateDecision.Blocked).let {
            assertEquals("package_is_banking_or_payment", it.reason)
        }
    }

    @Test
    fun paymentAppsAreBlocked() {
        val request = GenericAppActionRequest(
            packageName = "com.mercadopago.wallet",
            capability = GenericAppCapability.OPEN_APP,
            description = "x"
        )
        val decision = gate.evaluate(request)
        assertTrue(decision is GenericAppGateDecision.Blocked)
    }

    @Test
    fun tapButtonIsAlwaysBlocked() {
        val request = GenericAppActionRequest(
            packageName = "com.spotify.music",
            capability = GenericAppCapability.TAP_BUTTON,
            description = "tocá Play"
        )
        val decision = gate.evaluate(request)
        assertTrue(decision is GenericAppGateDecision.Blocked)
        (decision as GenericAppGateDecision.Blocked).let {
            assertEquals("capability_not_allowed_in_v1", it.reason)
        }
    }

    @Test
    fun typeIntoFieldIsAlwaysBlocked() {
        val request = GenericAppActionRequest(
            packageName = "com.spotify.music",
            capability = GenericAppCapability.TYPE_INTO_FIELD,
            description = "escribí algo"
        )
        assertTrue(gate.evaluate(request) is GenericAppGateDecision.Blocked)
    }

    @Test
    fun submitFormIsAlwaysBlocked() {
        val request = GenericAppActionRequest(
            packageName = "com.spotify.music",
            capability = GenericAppCapability.SUBMIT_FORM,
            description = "x"
        )
        assertTrue(gate.evaluate(request) is GenericAppGateDecision.Blocked)
    }

    @Test
    fun paymentCapabilityAlwaysBlocked() {
        val request = GenericAppActionRequest(
            packageName = "com.example.shop",
            capability = GenericAppCapability.PAY,
            description = "comprá"
        )
        assertTrue(gate.evaluate(request) is GenericAppGateDecision.Blocked)
    }

    @Test
    fun genericAutomationAlwaysBlocked() {
        val request = GenericAppActionRequest(
            packageName = "com.example.app",
            capability = GenericAppCapability.GENERIC_AUTOMATION,
            description = "auto"
        )
        assertTrue(gate.evaluate(request) is GenericAppGateDecision.Blocked)
    }

    @Test
    fun openSafeAppRequiresConfirmation() {
        val request = GenericAppActionRequest(
            packageName = "com.example.calendar",
            capability = GenericAppCapability.OPEN_APP,
            description = "abrí el calendario"
        )
        val decision = gate.evaluate(request)
        assertTrue(decision is GenericAppGateDecision.AllowedWithConfirmation)
        val allowed = decision as GenericAppGateDecision.AllowedWithConfirmation
        assertTrue(allowed.spokenConfirmationPrompt.contains("confirmar", ignoreCase = true))
    }

    @Test
    fun summarizeScreenRequiresConfirmation() {
        val request = GenericAppActionRequest(
            packageName = "com.example.app",
            capability = GenericAppCapability.SUMMARIZE_SCREEN,
            description = "resumí"
        )
        val decision = gate.evaluate(request)
        assertTrue(decision is GenericAppGateDecision.AllowedWithConfirmation)
    }
}

class GenericAppExecutionPolicyTest {

    @Test
    fun executionPolicyDisabledByDefault() {
        val policy = GenericAppExecutionPolicy()
        assertEquals(false, policy.executionEnabled)
    }

    @Test
    fun safeRequestBecomesGuidanceOnlyWhenExecutionDisabled() {
        val policy = GenericAppExecutionPolicy(executionEnabled = false)
        val decision = policy.decide(
            GenericAppActionRequest(
                packageName = "com.example.app",
                capability = GenericAppCapability.OPEN_APP,
                description = "abrí"
            )
        )
        assertTrue(decision is GenericAppExecutionDecision.GuidanceOnly)
    }

    @Test
    fun dangerousCapabilityRefusedEvenIfEnabled() {
        val policy = GenericAppExecutionPolicy(executionEnabled = true)
        val decision = policy.decide(
            GenericAppActionRequest(
                packageName = "com.example.app",
                capability = GenericAppCapability.TAP_BUTTON,
                description = "tocá"
            )
        )
        assertTrue(decision is GenericAppExecutionDecision.Refused)
        (decision as GenericAppExecutionDecision.Refused).let {
            assertEquals("capability_in_v1_blocklist", it.reason)
        }
    }

    @Test
    fun safeRequestWithExecutionEnabledStillNeedsConfirmation() {
        val policy = GenericAppExecutionPolicy(executionEnabled = true)
        val decision = policy.decide(
            GenericAppActionRequest(
                packageName = "com.example.app",
                capability = GenericAppCapability.SUMMARIZE_SCREEN,
                description = "resumí"
            )
        )
        assertTrue(decision is GenericAppExecutionDecision.AwaitingConfirmation)
    }
}
