package com.ojoclaro.android.agent.core.tool

import com.ojoclaro.android.agent.AgentIntent
import com.ojoclaro.android.agent.core.AgentExecutionMode
import com.ojoclaro.android.agent.core.AgentRiskLevel
import com.ojoclaro.android.agent.core.AgentToolCapability
import com.ojoclaro.android.agent.core.AgentToolId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AgentToolRegistryTest {

    private val registry = AgentToolRegistry()

    @Test
    fun registryContainsAllKnownTools() {
        AgentToolId.entries.forEach { id ->
            assertNotNull(registry.byId(id), "tool $id should be registered")
        }
    }

    @Test
    fun whatsAppToolNeverAutoCompletes() {
        val tool = registry.byId(AgentToolId.WHATSAPP)!!
        assertTrue(AgentToolCapability.NEVER_AUTO_COMPLETES in tool.capabilities)
        assertTrue(AgentToolCapability.PREPARES_MESSAGE_WITHOUT_SENDING in tool.capabilities)
        assertTrue(tool.requiresConfirmation)
    }

    @Test
    fun mapsToolNeverAutoCompletes() {
        val tool = registry.byId(AgentToolId.MAPS)!!
        assertTrue(AgentToolCapability.NEVER_AUTO_COMPLETES in tool.capabilities)
    }

    @Test
    fun phoneToolNeverAutoCompletes() {
        val tool = registry.byId(AgentToolId.PHONE)!!
        assertTrue(AgentToolCapability.NEVER_AUTO_COMPLETES in tool.capabilities)
    }

    @Test
    fun genericAppIsBlockedByDefault() {
        val tool = registry.byId(AgentToolId.GENERIC_APP)!!
        assertEquals(AgentRiskLevel.BLOCKED, tool.risk)
        assertFalse(tool.isAvailableIn(AgentExecutionMode.ACCESSIBILITY_VOICE))
        assertFalse(tool.isAvailableIn(AgentExecutionMode.SIGHTED))
        assertFalse(tool.isAvailableIn(AgentExecutionMode.EMERGENCY))
    }

    @Test
    fun emergencyToolNeverClaimsCompletion() {
        val tool = registry.byId(AgentToolId.EMERGENCY)!!
        assertTrue(AgentToolCapability.NEVER_AUTO_COMPLETES in tool.capabilities)
        assertTrue(tool.requiresConfirmation)
        assertTrue(tool.emergencyCapable)
    }

    @Test
    fun availableInVoiceModeExcludesBlockedTools() {
        val available = registry.availableIn(AgentExecutionMode.ACCESSIBILITY_VOICE)
        assertFalse(available.any { it.id == AgentToolId.GENERIC_APP })
    }

    @Test
    fun firstForReturnsExpectedTools() {
        assertEquals(AgentToolId.WHATSAPP,
            registry.firstFor(AgentIntent.COMPOSE_WHATSAPP_MESSAGE, AgentExecutionMode.ACCESSIBILITY_VOICE)?.id)
        assertEquals(AgentToolId.MAPS,
            registry.firstFor(AgentIntent.NAVIGATE_TO_DESTINATION, AgentExecutionMode.ACCESSIBILITY_VOICE)?.id)
        assertEquals(AgentToolId.PHONE,
            registry.firstFor(AgentIntent.OPEN_PHONE, AgentExecutionMode.ACCESSIBILITY_VOICE)?.id)
        assertEquals(AgentToolId.REPEAT_LAST,
            registry.firstFor(AgentIntent.REPEAT_LAST, AgentExecutionMode.ACCESSIBILITY_VOICE)?.id)
    }

    @Test
    fun firstForReturnsNullForUnsupportedIntent() {
        // CREATE_REMINDER no está cubierto por ningún tool actualmente.
        assertNull(registry.firstFor(AgentIntent.CREATE_REMINDER, AgentExecutionMode.ACCESSIBILITY_VOICE))
    }

    @Test
    fun whitelistRejectsUnknownToolName() {
        assertFalse(registry.isWhitelistedToolName("pay_bill"))
        assertFalse(registry.isWhitelistedToolName("send_message_silently"))
        assertFalse(registry.isWhitelistedToolName(""))
    }

    @Test
    fun whitelistAcceptsKnownToolName() {
        assertTrue(registry.isWhitelistedToolName("WHATSAPP"))
        assertTrue(registry.isWhitelistedToolName("whatsapp"))
        assertTrue(registry.isWhitelistedToolName("Maps"))
    }

    @Test
    fun toolsThatTouchExternalAppsHaveForbiddenOnBanking() {
        val externalTools = setOf(
            AgentToolId.WHATSAPP,
            AgentToolId.MAPS,
            AgentToolId.PHONE,
            AgentToolId.SCREEN_READER,
            AgentToolId.OCR,
            AgentToolId.MEMORY
        )
        externalTools.forEach { id ->
            val tool = registry.byId(id)!!
            assertTrue(
                AgentToolCapability.FORBIDDEN_ON_BANKING in tool.capabilities,
                "tool $id should declare FORBIDDEN_ON_BANKING"
            )
        }
    }
}
