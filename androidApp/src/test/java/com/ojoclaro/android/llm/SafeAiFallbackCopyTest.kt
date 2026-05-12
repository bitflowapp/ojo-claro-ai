package com.ojoclaro.android.llm

import com.ojoclaro.android.agent.AgentState
import com.ojoclaro.android.model.AppState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SafeAiFallbackCopyTest {

    @Test
    fun noBakedCopyContainsForbiddenAiDebugPhrases() {
        val texts = listOf(
            SafeAiFallbackCopy.GENERAL,
            SafeAiFallbackCopy.WHATSAPP_OPEN,
            SafeAiFallbackCopy.WHATSAPP_WAITING,
            SafeAiFallbackCopy.SENSITIVE_SCREEN,
            SafeAiFallbackCopy.SAFE_MODE_REMINDER,
            SafeAiFallbackCopy.UNABLE_TO_RESOLVE,
            SafeAiFallbackCopy.CAPABILITIES_SUMMARY
        )
        texts.forEach { text ->
            assertFalse(text.contains("No estoy usando la IA", ignoreCase = true), "leak in: $text")
            assertFalse(text.contains("No uso la IA", ignoreCase = true), "leak in: $text")
            assertFalse(text.contains("IA flexible", ignoreCase = true), "leak in: $text")
            assertFalse(text.contains("modo IA", ignoreCase = true), "leak in: $text")
            assertFalse(text.contains("no tengo IA", ignoreCase = true), "leak in: $text")
            assertFalse(text.contains("proxy", ignoreCase = true), "leak in: $text")
        }
    }

    @Test
    fun contextualReturnsWhatsappOpenWhenExternalAppIsWhatsapp() {
        val text = SafeAiFallbackCopy.contextual(
            appState = AppState.LISTENING,
            externalApp = "whatsapp"
        )
        assertEquals(SafeAiFallbackCopy.WHATSAPP_OPEN, text)
    }

    @Test
    fun contextualReturnsWhatsappWaitingWhenAgentStateIsWhatsappFlow() {
        val text = SafeAiFallbackCopy.contextual(
            appState = AppState.WAITING_WHATSAPP_ACTION,
            agentState = AgentState.WAITING_WHATSAPP_ACTION
        )
        assertEquals(SafeAiFallbackCopy.WHATSAPP_WAITING, text)
    }

    @Test
    fun contextualReturnsSensitiveCopyWhenSensitiveScreen() {
        val text = SafeAiFallbackCopy.contextual(
            appState = AppState.LISTENING,
            sensitiveScreen = true
        )
        assertEquals(SafeAiFallbackCopy.SENSITIVE_SCREEN, text)
        assertTrue(text.contains("sensible", ignoreCase = true))
    }

    @Test
    fun contextualReturnsGeneralCopyForNormalScreen() {
        val text = SafeAiFallbackCopy.contextual(
            appState = AppState.LISTENING
        )
        assertEquals(SafeAiFallbackCopy.GENERAL, text)
    }

    @Test
    fun contextualMentionsConfirmCancelWhenWaitingConfirmation() {
        val text = SafeAiFallbackCopy.contextual(
            appState = AppState.WAITING_CONFIRMATION
        )
        assertTrue(text.contains("confirmar", ignoreCase = true))
        assertTrue(text.contains("cancelar", ignoreCase = true))
    }

    @Test
    fun modeLabelHidesAiAndProxyTerms() {
        val labels = listOf(
            SafeAiFallbackCopy.modeLabel(AppState.IDLE),
            SafeAiFallbackCopy.modeLabel(AppState.SCANNING),
            SafeAiFallbackCopy.modeLabel(AppState.WAITING_CONFIRMATION),
            SafeAiFallbackCopy.modeLabel(AppState.WAITING_WHATSAPP_ACTION),
            SafeAiFallbackCopy.modeLabel(AppState.LISTENING, externalApp = "whatsapp")
        )
        labels.forEach { label ->
            assertFalse(label.contains("IA", ignoreCase = false))
            assertFalse(label.contains("proxy", ignoreCase = true))
            assertTrue(label.startsWith("Modo:"))
        }
    }

    @Test
    fun looksLikeAiDebugCopyDetectsForbiddenTokens() {
        assertTrue(SafeAiFallbackCopy.looksLikeAiDebugCopy("No estoy usando la IA"))
        assertTrue(SafeAiFallbackCopy.looksLikeAiDebugCopy("proxy no configurado"))
        assertTrue(SafeAiFallbackCopy.looksLikeAiDebugCopy("LLM disabled"))
        assertTrue(SafeAiFallbackCopy.looksLikeAiDebugCopy("low confidence"))
        assertFalse(SafeAiFallbackCopy.looksLikeAiDebugCopy("No entendí. Probá de nuevo."))
        assertFalse(SafeAiFallbackCopy.looksLikeAiDebugCopy(""))
    }
}
