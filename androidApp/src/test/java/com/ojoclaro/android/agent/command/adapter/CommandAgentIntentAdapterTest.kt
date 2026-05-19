package com.ojoclaro.android.agent.command.adapter

import com.ojoclaro.android.agent.command.CommandConfidence
import com.ojoclaro.android.agent.command.CommandIntent
import com.ojoclaro.android.agent.command.CommandNormalizer
import com.ojoclaro.android.agent.command.CommandSlot
import com.ojoclaro.android.agent.command.CommandSlotName
import com.ojoclaro.android.agent.command.CommandUnderstandingEngine
import com.ojoclaro.android.agent.command.ParsedCommand
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CommandAgentIntentAdapterTest {
    private val adapter = CommandAgentIntentAdapter()
    private val engine = CommandUnderstandingEngine()

    @Test
    fun readScreenAdaptsCorrectly() {
        val result = adapter.adapt(command(CommandIntent.READ_SCREEN))

        assertReady(result, CommandAgentIntent.READ_VISIBLE_SCREEN)
    }

    @Test
    fun summarizeScreenAdaptsCorrectly() {
        val result = adapter.adapt(command(CommandIntent.SUMMARIZE_SCREEN))

        assertReady(result, CommandAgentIntent.SUMMARIZE_VISIBLE_SCREEN)
    }

    @Test
    fun explainScreenAdaptsCorrectly() {
        val result = adapter.adapt(command(CommandIntent.EXPLAIN_SCREEN))

        assertReady(result, CommandAgentIntent.EXPLAIN_VISIBLE_SCREEN)
    }

    @Test
    fun openAppKeepsAppName() {
        val result = adapter.adapt(
            command(
                intent = CommandIntent.OPEN_APP,
                rawInput = "abri Mercado Pago",
                slots = listOf(CommandSlot(CommandSlotName.APP_NAME, "Mercado Pago"))
            )
        )

        assertReady(result, CommandAgentIntent.OPEN_APP)
        assertEquals("Mercado Pago", result.slotValue(CommandAgentSlotName.APP_NAME))
    }

    @Test
    fun openAppWithoutAppNameNeedsSlot() {
        val result = adapter.adapt(command(CommandIntent.OPEN_APP, rawInput = "abri"))

        assertEquals(CommandAdapterStatus.NEEDS_SLOT, result.status)
        assertEquals(CommandAgentIntent.OPEN_APP, result.intent)
        assertEquals(listOf(CommandAgentSlotName.APP_NAME), result.missingSlots)
        assertEquals(CommandAdapterReason.MISSING_REQUIRED_SLOT, result.reason)
        assertFalse(result.isExecutable)
    }

    @Test
    fun prepareMessageKeepsContactName() {
        val result = adapter.adapt(
            command(
                intent = CommandIntent.PREPARE_MESSAGE,
                rawInput = "prepara un mensaje para Sofi",
                slots = listOf(CommandSlot(CommandSlotName.CONTACT_NAME, "Sofi"))
            )
        )

        assertReady(result, CommandAgentIntent.PREPARE_MESSAGE)
        assertEquals("Sofi", result.slotValue(CommandAgentSlotName.CONTACT_NAME))
    }

    @Test
    fun prepareMessageNeverBecomesSendMessage() {
        val result = adapter.adapt(
            command(
                intent = CommandIntent.PREPARE_MESSAGE,
                rawInput = "escribile a Juan",
                slots = listOf(CommandSlot(CommandSlotName.CONTACT_NAME, "Juan"))
            )
        )

        assertEquals(CommandAgentIntent.PREPARE_MESSAGE, result.intent)
        assertFalse(result.intent?.name == "SEND_MESSAGE")
        assertFalse(result.isExecutable)
    }

    @Test
    fun confirmAdaptsToConfirmPending() {
        val result = adapter.adapt(command(CommandIntent.CONFIRM))

        assertReady(result, CommandAgentIntent.CONFIRM_PENDING_ACTION)
    }

    @Test
    fun cancelAdaptsToCancelPending() {
        val result = adapter.adapt(command(CommandIntent.CANCEL))

        assertReady(result, CommandAgentIntent.CANCEL_PENDING_ACTION)
    }

    @Test
    fun repeatAdaptsCorrectly() {
        val result = adapter.adapt(command(CommandIntent.REPEAT))

        assertReady(result, CommandAgentIntent.REPEAT_LAST_RESPONSE)
    }

    @Test
    fun shorterAdaptsCorrectly() {
        val result = adapter.adapt(command(CommandIntent.SHORTER))

        assertReady(result, CommandAgentIntent.MODIFY_RESPONSE_SHORTER)
    }

    @Test
    fun moreDetailAdaptsCorrectly() {
        val result = adapter.adapt(command(CommandIntent.MORE_DETAIL))

        assertReady(result, CommandAgentIntent.MODIFY_RESPONSE_MORE_DETAIL)
    }

    @Test
    fun riskCheckAdaptsCorrectly() {
        val result = adapter.adapt(command(CommandIntent.RISK_CHECK))

        assertReady(result, CommandAgentIntent.CHECK_SCREEN_RISK)
    }

    @Test
    fun unknownDoesNotProduceExecutableAction() {
        val result = adapter.adapt(command(CommandIntent.UNKNOWN, rawInput = "hace algo raro"))

        assertEquals(CommandAdapterStatus.UNSUPPORTED, result.status)
        assertNull(result.intent)
        assertFalse(result.isExecutable)
        assertEquals(CommandAdapterReason.UNSUPPORTED_INTENT, result.reason)
    }

    @Test
    fun potentiallySensitiveSignalIsPreserved() {
        val result = adapter.adapt(
            command(
                intent = CommandIntent.PREPARE_MESSAGE,
                rawInput = "prepara un mensaje para Sofi",
                slots = listOf(CommandSlot(CommandSlotName.CONTACT_NAME, "Sofi")),
                isPotentiallySensitive = true
            )
        )

        assertTrue(result.isPotentiallySensitive)
        assertTrue(result.requiresConfirmation)
        assertEquals(CommandAdapterReason.SENSITIVE_COMMAND_REQUIRES_CONFIRMATION, result.reason)
    }

    @Test
    fun sendNowCommandStaysSensitiveAndRequiresConfirmation() {
        val parsed = engine.parse("envia ya este mensaje")

        val result = adapter.adapt(parsed)

        assertTrue(result.isPotentiallySensitive)
        assertTrue(result.requiresConfirmation)
        assertFalse(result.isExecutable)
    }

    @Test
    fun payInvoiceCommandStaysSensitiveAndRequiresConfirmation() {
        val parsed = engine.parse("pagar factura")

        val result = adapter.adapt(parsed)

        assertTrue(result.isPotentiallySensitive)
        assertTrue(result.requiresConfirmation)
        assertFalse(result.isExecutable)
    }

    @Test
    fun adapterDoesNotReferenceRuntimeUiAccessibilityOrAndroidApis() {
        val adapterSourceDir = listOf(
            File("src/main/java/com/ojoclaro/android/agent/command/adapter"),
            File("androidApp/src/main/java/com/ojoclaro/android/agent/command/adapter")
        ).first { it.exists() }

        val sourceText = adapterSourceDir
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString(separator = "\n") { it.readText() }

        listOf(
            "HomeViewModel",
            "AssistantOrchestrator",
            "OjoClaroAccessibilityService",
            "AndroidAccessibilityScreenContextProvider",
            "AccessibilityScreenReader",
            "AgentRuntimeBridge"
        ).forEach { forbiddenReference ->
            assertFalse(
                sourceText.contains(forbiddenReference),
                "adapter layer must not reference $forbiddenReference"
            )
        }
        assertFalse(
            sourceText.lineSequence().any { line ->
                line.startsWith("import android.") || line.startsWith("import androidx.")
            },
            "adapter layer must not import Android APIs"
        )
    }

    private fun command(
        intent: CommandIntent,
        rawInput: String = intent.name.lowercase(),
        slots: List<CommandSlot> = emptyList(),
        isPotentiallySensitive: Boolean = false
    ): ParsedCommand =
        ParsedCommand(
            rawInput = rawInput,
            normalizedInput = CommandNormalizer.normalize(rawInput),
            intent = intent,
            confidence = CommandConfidence.HIGH,
            slots = slots,
            requiresContext = intent in setOf(
                CommandIntent.READ_SCREEN,
                CommandIntent.SUMMARIZE_SCREEN,
                CommandIntent.EXPLAIN_SCREEN,
                CommandIntent.RISK_CHECK
            ),
            isPotentiallySensitive = isPotentiallySensitive
        )

    private fun assertReady(result: CommandAdapterResult, expectedIntent: CommandAgentIntent) {
        assertEquals(CommandAdapterStatus.READY, result.status)
        assertEquals(expectedIntent, result.intent)
        assertEquals(CommandAdapterReason.MAPPED, result.reason)
        assertFalse(result.isExecutable)
    }
}
