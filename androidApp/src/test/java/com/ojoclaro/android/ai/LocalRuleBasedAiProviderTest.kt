package com.ojoclaro.android.ai

import com.ojoclaro.android.model.AppState
import com.ojoclaro.shared.model.ConfidenceLevel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LocalRuleBasedAiProviderTest {

    private val provider = LocalRuleBasedAiProvider()

    private fun context(rawCommand: String = "") = AiContext(
        rawCommand = rawCommand,
        appState = AppState.IDLE
    )

    @Test
    fun readTextWithOcrReturnsSpokenText() = runTest {
        val ctx = context().copy(ocrText = "Precio 250")
        val result = provider.process(AiTask.READ_TEXT, ctx)
        assertTrue(result.spokenText.contains("El texto dice:"))
        assertTrue(result.spokenText.contains("250"))
        assertEquals(ConfidenceLevel.HIGH, result.confidence)
    }

    @Test
    fun readTextWithoutOcrHintsButton() = runTest {
        val result = provider.process(AiTask.READ_TEXT, context())
        assertTrue(result.spokenText.contains("Leer texto"))
        assertEquals(ConfidenceLevel.MEDIUM, result.confidence)
    }

    @Test
    fun readVisibleScreenWithTextReturnsContent() = runTest {
        val ctx = context().copy(visibleScreenText = "WhatsApp — Sofi: hola")
        val result = provider.process(AiTask.READ_VISIBLE_SCREEN, ctx)
        assertTrue(result.spokenText.contains("La pantalla dice:"))
        assertTrue(result.spokenText.contains("Sofi"))
    }

    @Test
    fun readVisibleScreenWithoutTextLowConfidence() = runTest {
        val result = provider.process(AiTask.READ_VISIBLE_SCREEN, context())
        assertEquals(ConfidenceLevel.LOW, result.confidence)
        assertTrue(result.spokenText.contains("accesibilidad"))
    }

    @Test
    fun describeSceneFallbackMentionsAi() = runTest {
        val result = provider.process(AiTask.DESCRIBE_SCENE, context())
        assertTrue(result.spokenText.contains("IA avanzada"))
    }

    @Test
    fun emergencyHelpReturnsSafetyNotice() = runTest {
        val result = provider.process(AiTask.EMERGENCY_HELP, context())
        assertEquals(ConfidenceLevel.HIGH, result.confidence)
        assertNotNull(result.safetyNotice)
        assertTrue(result.spokenText.contains("emergencia") || result.spokenText.contains("peligro"))
    }

    @Test
    fun helpResponseListsCommands() = runTest {
        val result = provider.process(AiTask.HELP, context())
        assertTrue(result.spokenText.contains("leer texto") || result.spokenText.contains("WhatsApp"))
    }

    @Test
    fun unknownCommandSuggestsHelp() = runTest {
        val result = provider.process(AiTask.UNKNOWN, context())
        assertEquals(ConfidenceLevel.LOW, result.confidence)
        assertTrue(result.spokenText.contains("ayuda") || result.spokenText.contains("decir"))
    }

    @Test
    fun composeMessageHintMentionsSyntax() = runTest {
        val result = provider.process(AiTask.COMPOSE_MESSAGE, context())
        assertTrue(result.spokenText.contains("mandale"))
    }

    @Test
    fun resultDoesNotRequireConfirmationByDefault() = runTest {
        val result = provider.process(AiTask.READ_TEXT, context())
        assertFalse(result.requiresConfirmation)
    }

    @Test
    fun ocrTextIsTruncatedToSafeLength() = runTest {
        val longText = "A".repeat(2_000)
        val ctx = context().copy(ocrText = longText)
        val result = provider.process(AiTask.READ_TEXT, ctx)
        assertTrue(result.spokenText.length <= 1_250) // 1200 chars + "El texto dice: " prefix
    }
}
