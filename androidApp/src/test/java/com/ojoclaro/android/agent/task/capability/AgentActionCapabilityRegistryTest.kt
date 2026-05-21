package com.ojoclaro.android.agent.task.capability

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgentActionCapabilityRegistryTest {

    private val registry = AgentActionCapabilityRegistry()

    @Test
    fun openAppIsSupportedSafe() {
        assertEquals(
            AgentActionCapabilityDecision.SUPPORTED_SAFE,
            registry.decision(AgentActionCapabilityType.OPEN_APP)
        )
    }

    @Test
    fun prepareTextInMemoryIsSupportedSafe() {
        assertEquals(
            AgentActionCapabilityDecision.SUPPORTED_SAFE,
            registry.decision(AgentActionCapabilityType.PREPARE_TEXT_IN_MEMORY)
        )
    }

    @Test
    fun prepareAudioScriptInMemoryIsSupportedSafe() {
        assertEquals(
            AgentActionCapabilityDecision.SUPPORTED_SAFE,
            registry.decision(AgentActionCapabilityType.PREPARE_AUDIO_SCRIPT_IN_MEMORY)
        )
    }

    @Test
    fun prepareSearchQueryInMemoryIsSupportedSafe() {
        assertEquals(
            AgentActionCapabilityDecision.SUPPORTED_SAFE,
            registry.decision(AgentActionCapabilityType.PREPARE_SEARCH_QUERY_IN_MEMORY)
        )
    }

    @Test
    fun readTaskStateIsSupportedSafe() {
        assertEquals(
            AgentActionCapabilityDecision.SUPPORTED_SAFE,
            registry.decision(AgentActionCapabilityType.READ_TASK_STATE)
        )
    }

    @Test
    fun focusFieldIsNotSupportedSafe() {
        assertFalse(registry.isSafeToExecuteNow(AgentActionCapabilityType.FOCUS_FIELD))
        assertEquals(
            AgentActionCapabilityDecision.INSTRUMENTED_TEST_REQUIRED,
            registry.decision(AgentActionCapabilityType.FOCUS_FIELD)
        )
    }

    @Test
    fun writeTextExternalAppIsNotSupportedSafe() {
        assertFalse(registry.isSafeToExecuteNow(AgentActionCapabilityType.WRITE_TEXT_EXTERNAL_APP))
    }

    @Test
    fun clickButtonIsBlockedDangerous() {
        assertEquals(
            AgentActionCapabilityDecision.BLOCKED_DANGEROUS,
            registry.decision(AgentActionCapabilityType.CLICK_BUTTON)
        )
    }

    @Test
    fun sendMessageIsBlockedSensitive() {
        assertEquals(
            AgentActionCapabilityDecision.BLOCKED_SENSITIVE,
            registry.decision(AgentActionCapabilityType.SEND_MESSAGE)
        )
    }

    @Test
    fun sendAudioIsBlockedSensitive() {
        assertEquals(
            AgentActionCapabilityDecision.BLOCKED_SENSITIVE,
            registry.decision(AgentActionCapabilityType.SEND_AUDIO)
        )
    }

    @Test
    fun requestRideIsBlockedSensitive() {
        assertEquals(
            AgentActionCapabilityDecision.BLOCKED_SENSITIVE,
            registry.decision(AgentActionCapabilityType.REQUEST_RIDE)
        )
    }

    @Test
    fun confirmPaymentIsBlockedDangerous() {
        assertEquals(
            AgentActionCapabilityDecision.BLOCKED_DANGEROUS,
            registry.decision(AgentActionCapabilityType.CONFIRM_PAYMENT)
        )
    }

    @Test
    fun placeCallIsNotSupportedSafeWithoutConfirmation() {
        assertFalse(registry.isSafeToExecuteNow(AgentActionCapabilityType.PLACE_CALL))
    }

    @Test
    fun everyCapabilityTypeHasAnAuditedEntry() {
        val all = registry.all()
        assertEquals(AgentActionCapabilityType.values().size, all.size)
        all.forEach { capability ->
            assertTrue(capability.safeDescription.isNotBlank(), "type=${capability.type}")
        }
    }

    @Test
    fun noCapabilityDescriptionClaimsCompletedSensitiveActions() {
        registry.all().forEach { capability ->
            val text = capability.safeDescription.lowercase()
            assertFalse(text.contains("mensaje " + "enviado"), "type=${capability.type}")
            assertFalse(text.contains("audio " + "enviado"), "type=${capability.type}")
            assertFalse(text.contains("taxi " + "pedido"), "type=${capability.type}")
            assertFalse(text.contains("viaje " + "solicitado"), "type=${capability.type}")
        }
    }
}
