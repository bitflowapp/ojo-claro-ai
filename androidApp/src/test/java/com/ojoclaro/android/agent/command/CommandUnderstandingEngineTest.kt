package com.ojoclaro.android.agent.command

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommandUnderstandingEngineTest {
    private val engine = CommandUnderstandingEngine()

    @Test
    fun detectsReadScreen() {
        val parsed = engine.parse("le\u00e9 la pantalla")

        assertEquals(CommandIntent.READ_SCREEN, parsed.intent)
        assertEquals(CommandConfidence.HIGH, parsed.confidence)
        assertTrue(parsed.requiresContext)
    }

    @Test
    fun detectsSummarizeScreen() {
        val parsed = engine.parse("haceme un resumen")

        assertEquals(CommandIntent.SUMMARIZE_SCREEN, parsed.intent)
    }

    @Test
    fun detectsExplainScreen() {
        val parsed = engine.parse("qu\u00e9 bot\u00f3n tengo que tocar")

        assertEquals(CommandIntent.EXPLAIN_SCREEN, parsed.intent)
    }

    @Test
    fun detectsOpenAppAndExtractsAppName() {
        val parsed = engine.parse("abr\u00ed Mercado Pago")

        assertEquals(CommandIntent.OPEN_APP, parsed.intent)
        assertEquals("Mercado Pago", parsed.slotValue(CommandSlotName.APP_NAME))
        assertFalse(parsed.requiresContext)
    }

    @Test
    fun detectsPrepareMessageAndExtractsContactName() {
        val parsed = engine.parse("prepar\u00e1 un mensaje para Sofi")

        assertEquals(CommandIntent.PREPARE_MESSAGE, parsed.intent)
        assertEquals("Sofi", parsed.slotValue(CommandSlotName.CONTACT_NAME))
        assertFalse(parsed.isPotentiallySensitive)
    }

    @Test
    fun detectsConfirm() {
        val parsed = engine.parse("s\u00ed, confirmo")

        assertEquals(CommandIntent.CONFIRM, parsed.intent)
    }

    @Test
    fun detectsCancel() {
        val parsed = engine.parse("par\u00e1")

        assertEquals(CommandIntent.CANCEL, parsed.intent)
    }

    @Test
    fun detectsRepeat() {
        val parsed = engine.parse("decilo de nuevo")

        assertEquals(CommandIntent.REPEAT, parsed.intent)
    }

    @Test
    fun detectsShorter() {
        val parsed = engine.parse("m\u00e1s corto")

        assertEquals(CommandIntent.SHORTER, parsed.intent)
    }

    @Test
    fun detectsMoreDetail() {
        val parsed = engine.parse("ampli\u00e1")

        assertEquals(CommandIntent.MORE_DETAIL, parsed.intent)
    }

    @Test
    fun detectsRiskCheck() {
        val parsed = engine.parse("revis\u00e1 si hay riesgo")

        assertEquals(CommandIntent.RISK_CHECK, parsed.intent)
        assertTrue(parsed.requiresContext)
    }

    @Test
    fun normalizesAccents() {
        val parsed = engine.parse("le\u00e9 esto")

        assertEquals("lee esto", parsed.normalizedInput)
        assertEquals(CommandIntent.READ_SCREEN, parsed.intent)
    }

    @Test
    fun normalizesWhitespaceAndCase() {
        val parsed = engine.parse("   QU\u00c9     DICE    AC\u00c1   ")

        assertEquals("que dice aca", parsed.normalizedInput)
        assertEquals(CommandIntent.READ_SCREEN, parsed.intent)
    }

    @Test
    fun blankCommandReturnsUnknownWithLowConfidence() {
        val parsed = engine.parse("   ")

        assertEquals(CommandIntent.UNKNOWN, parsed.intent)
        assertEquals(CommandConfidence.LOW, parsed.confidence)
        assertFalse(parsed.isPotentiallySensitive)
        assertEquals("blank_input", parsed.debugReason)
    }

    @Test
    fun sendNowCommandIsMarkedSensitive() {
        val parsed = engine.parse("envi\u00e1 ya este mensaje")

        assertTrue(parsed.isPotentiallySensitive)
        assertTrue(parsed.debugReason.contains("dangerous_send_now"))
    }

    @Test
    fun payCommandIsMarkedSensitive() {
        val parsed = engine.parse("pagar")

        assertTrue(parsed.isPotentiallySensitive)
        assertTrue(parsed.debugReason.contains("dangerous_financial"))
    }

    @Test
    fun deleteCommandIsMarkedSensitive() {
        val parsed = engine.parse("borrar esto")

        assertTrue(parsed.isPotentiallySensitive)
        assertTrue(parsed.debugReason.contains("dangerous_destructive"))
    }

    @Test
    fun commandLayerDoesNotReferenceLegacyRuntime() {
        val commandSourceDir = listOf(
            File("src/main/java/com/ojoclaro/android/agent/command"),
            File("androidApp/src/main/java/com/ojoclaro/android/agent/command")
        ).first { it.exists() }

        val sourceText = commandSourceDir
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString(separator = "\n") { it.readText() }

        listOf(
            "HomeViewModel",
            "AssistantOrchestrator",
            "OjoClaroAccessibilityService",
            "AndroidAccessibilityScreenContextProvider",
            "AccessibilityScreenReader",
            "AgentRuntimeBridge",
            "AgentContext",
            "AgentContextSnapshot",
            "RiskDetector",
            "PrivacyGuard"
        ).forEach { forbiddenReference ->
            assertFalse(
                sourceText.contains(forbiddenReference),
                "command layer must not reference $forbiddenReference"
            )
        }
    }
}
