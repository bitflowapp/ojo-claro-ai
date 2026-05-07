package com.ojoclaro.android.global

import com.ojoclaro.android.agent.AgentState
import com.ojoclaro.android.external.ExternalCommand
import com.ojoclaro.android.external.ExternalCommandType
import com.ojoclaro.android.external.PendingConfirmation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExternalConversationContextTest {

    private var now = 1_000L
    private val context = ExternalConversationContext(
        ttlMillis = 60_000L,
        nowMillis = { now }
    )

    @Test
    fun startsExternalContextForSixtySeconds() {
        val snapshot = context.start(
            externalApp = ExternalAppName.WHATSAPP,
            reason = "Abro WhatsApp.",
            returnHint = "Volve a Ojo Claro.",
            agentState = AgentState.WAITING_WHATSAPP_ACTION
        )

        assertTrue(snapshot.active)
        assertEquals(ExternalAppName.WHATSAPP, snapshot.externalApp)
        assertEquals(61_000L, snapshot.expiresAtMillis)
        assertEquals(AgentState.WAITING_WHATSAPP_ACTION, snapshot.agentState)
    }

    @Test
    fun expiresAfterTtl() {
        context.start(ExternalAppName.MAPS, "Abro mapas.", "Volve.", null)

        now = 61_001L

        assertFalse(context.current.active)
        assertEquals(ExternalAppName.UNKNOWN, context.current.externalApp)
    }

    @Test
    fun touchExtendsTtl() {
        context.start(ExternalAppName.PHONE, "Abro telefono.", "Volve.", null)
        now = 30_000L

        val snapshot = context.touch()

        assertEquals(90_000L, snapshot.expiresAtMillis)
        assertTrue(snapshot.active)
    }

    @Test
    fun stopClearsContactMessageAndPending() {
        context.start(ExternalAppName.WHATSAPP, "Abro WhatsApp.", "Volve.", null)
        context.updateContact("Marco")
        context.updatePendingMessage("llego en 10")
        context.updatePendingConfirmation(
            PendingConfirmation(
                id = "p1",
                command = ExternalCommand(ExternalCommandType.COMPOSE_WHATSAPP_MESSAGE, "x"),
                spokenText = "Confirmar.",
                createdAtMillis = now
            )
        )

        val snapshot = context.clear()

        assertFalse(snapshot.active)
        assertNull(snapshot.lastContactName)
        assertNull(snapshot.pendingMessage)
        assertNull(snapshot.pendingConfirmation)
    }

    @Test
    fun silenceDoesNotClearContext() {
        context.start(ExternalAppName.WHATSAPP, "Abro WhatsApp.", "Volve.", null)
        context.updateContact("Marco")

        val snapshot = context.silence()

        assertTrue(snapshot.active)
        assertEquals("Marco", snapshot.lastContactName)
    }

    @Test
    fun cancelClearsContext() {
        context.start(ExternalAppName.WHATSAPP, "Abro WhatsApp.", "Volve.", AgentState.WAITING_WHATSAPP_ACTION)
        context.updateContact("Marco")

        val snapshot = context.cancel()

        assertFalse(snapshot.active)
        assertNull(snapshot.lastContactName)
        assertNull(snapshot.agentState)
    }

    @Test
    fun stopClearsContext() {
        context.start(ExternalAppName.MAPS, "Abro mapas.", "Volve.", AgentState.WAITING_DESTINATION)

        val snapshot = context.stop()

        assertFalse(snapshot.active)
        assertEquals(ExternalAppName.UNKNOWN, snapshot.externalApp)
    }
}
