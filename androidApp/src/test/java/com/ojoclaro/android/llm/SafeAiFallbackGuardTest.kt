package com.ojoclaro.android.llm

import com.ojoclaro.android.agent.AgentIntent
import com.ojoclaro.android.model.AppState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SafeAiFallbackGuardTest {

    private fun guardWith(proxyConfigured: Boolean): SafeAiFallbackGuard =
        SafeAiFallbackGuard(isProxyConfigured = { proxyConfigured })

    private fun safeInput(): SafeAiFallbackInput =
        SafeAiFallbackInput(
            userText = "qué hay en pantalla",
            appState = AppState.IDLE,
            screenIsSensitive = false,
            hasPendingConfirmation = false,
            fullOcrCaptured = false,
            fullChatVisible = false
        )

    @Test
    fun deniesWhenProxyNotConfigured() {
        val verdict = guardWith(false).evaluate(safeInput())
        assertTrue(verdict is SafeAiFallbackVerdict.Denied)
        assertEquals(SafeAiFallbackReason.PROXY_NOT_CONFIGURED, (verdict).reason)
    }

    @Test
    fun deniesOnSensitiveScreen() {
        val verdict = guardWith(true).evaluate(safeInput().copy(screenIsSensitive = true))
        assertTrue(verdict is SafeAiFallbackVerdict.Denied)
        assertEquals(SafeAiFallbackReason.SENSITIVE_SCREEN, (verdict).reason)
    }

    @Test
    fun deniesOnPendingConfirmation() {
        val verdict = guardWith(true).evaluate(safeInput().copy(hasPendingConfirmation = true))
        assertTrue(verdict is SafeAiFallbackVerdict.Denied)
        assertEquals(SafeAiFallbackReason.PENDING_CONFIRMATION, (verdict).reason)
    }

    @Test
    fun deniesOnBankOrPasswordWordsInUserText() {
        val bankInputs = listOf(
            "transferí mil pesos al banco",
            "cuál es mi contraseña",
            "el pin de la tarjeta",
            "pago con tarjeta",
            "código de verificación"
        )
        bankInputs.forEach { txt ->
            val verdict = guardWith(true).evaluate(safeInput().copy(userText = txt))
            assertTrue(verdict is SafeAiFallbackVerdict.Denied, "Should deny for: $txt")
            assertEquals(SafeAiFallbackReason.SENSITIVE_INPUT, (verdict as SafeAiFallbackVerdict.Denied).reason)
        }
    }

    @Test
    fun deniesWhenFullOcrCapturedOrChatVisible() {
        val v1 = guardWith(true).evaluate(safeInput().copy(fullOcrCaptured = true))
        assertTrue(v1 is SafeAiFallbackVerdict.Denied)
        assertEquals(SafeAiFallbackReason.PRIVATE_CONTENT_VISIBLE, (v1).reason)
        val v2 = guardWith(true).evaluate(safeInput().copy(fullChatVisible = true))
        assertTrue(v2 is SafeAiFallbackVerdict.Denied)
        assertEquals(SafeAiFallbackReason.PRIVATE_CONTENT_VISIBLE, (v2).reason)
    }

    @Test
    fun allowsWhenAllChecksPass() {
        val verdict = guardWith(true).evaluate(safeInput())
        assertTrue(verdict.isAllowed)
    }

    @Test
    fun filterIntentEnforcesV1Whitelist() {
        val guard = guardWith(true)
        // Allowed:
        SafeAiFallbackGuard.WHITELIST_V1.forEach { intent ->
            assertEquals(intent, guard.filterIntent(intent), "v1 should allow $intent")
        }
        // Forbidden examples:
        val forbidden = listOf(
            AgentIntent.COMPOSE_WHATSAPP_MESSAGE,
            AgentIntent.OPEN_WHATSAPP_CHAT,
            AgentIntent.SAVE_CONTACT,
            AgentIntent.CALL_CONTACT,
            AgentIntent.NAVIGATE_TO_DESTINATION,
            AgentIntent.PLAY_MUSIC,
            AgentIntent.OPEN_APP,
            AgentIntent.CREATE_ALARM,
            AgentIntent.REMEMBER_MEMORY
        )
        forbidden.forEach { intent ->
            assertEquals(
                AgentIntent.UNKNOWN,
                guard.filterIntent(intent),
                "v1 should reject $intent"
            )
        }
    }

    @Test
    fun whitelistContainsOnlySafeReadOrHelpIntents() {
        val safe = setOf(
            AgentIntent.HELP,
            AgentIntent.READ_VISIBLE_SCREEN,
            AgentIntent.OPEN_WHATSAPP,
            AgentIntent.REPEAT_LAST,
            AgentIntent.STOP_SPEAKING,
            AgentIntent.CANCEL,
            AgentIntent.UNKNOWN
        )
        assertEquals(safe, SafeAiFallbackGuard.WHITELIST_V1)
        // Doble check: nada peligroso colado.
        assertFalse(SafeAiFallbackGuard.WHITELIST_V1.contains(AgentIntent.COMPOSE_WHATSAPP_MESSAGE))
        assertFalse(SafeAiFallbackGuard.WHITELIST_V1.contains(AgentIntent.CALL_CONTACT))
        assertFalse(SafeAiFallbackGuard.WHITELIST_V1.contains(AgentIntent.SAVE_CONTACT))
    }
}
