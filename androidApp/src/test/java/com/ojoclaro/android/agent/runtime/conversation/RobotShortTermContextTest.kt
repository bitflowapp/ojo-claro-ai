package com.ojoclaro.android.agent.runtime.conversation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RobotShortTermContextTest {

    @Test
    fun countsConsecutiveFailures() {
        val context = RobotShortTermContext()
            .recordFailure(RobotFailureReason.RECOGNIZER_NOISE)
            .recordFailure(RobotFailureReason.CONFIRMATION_UNCLEAR)

        assertEquals(2, context.consecutiveFailures)
        assertEquals(RobotFailureReason.CONFIRMATION_UNCLEAR, context.lastFailureReason)
    }

    @Test
    fun resetClearsFailures() {
        val context = RobotShortTermContext()
            .recordFailure(RobotFailureReason.RECOGNIZER_NOISE)
            .reset()

        assertEquals(0, context.consecutiveFailures)
        assertEquals(RobotRecognizedKind.NONE, context.lastRecognizedKind)
        assertEquals(RobotFailureReason.NONE, context.lastFailureReason)
    }

    @Test
    fun successClearsFailures() {
        val context = RobotShortTermContext()
            .recordFailure(RobotFailureReason.RECOGNIZER_NOISE)
            .recordSuccess(RobotActiveHandler.SCREEN_UNDERSTANDING)

        assertEquals(0, context.consecutiveFailures)
        assertEquals(RobotRecognizedKind.SUCCESS, context.lastRecognizedKind)
        assertEquals(RobotFailureReason.NONE, context.lastFailureReason)
    }

    @Test
    fun contextDoesNotRequireRealUserText() {
        val fieldNames = RobotShortTermContext::class.java.declaredFields
            .map { it.name.lowercase() }

        assertFalse(fieldNames.any { it == "text" || it.endsWith("text") }, "text")

        listOf("message", "chat", "ocr", "location", "snapshot").forEach { unsafeName ->
            assertFalse(fieldNames.any { unsafeName in it }, unsafeName)
        }
    }

    @Test
    fun pendingConfirmationIsRepresentedWithoutPrivateContent() {
        val context = RobotShortTermContext()
            .withConfirmation(
                suggestedIntent = RepairSuggestedIntent.OPEN_WHATSAPP,
                activeHandler = RobotActiveHandler.VOICE_CORRECTION,
                externalApp = RobotExternalApp.WHATSAPP
            )

        assertTrue(context.lastAskedConfirmation)
        assertEquals(RobotPendingState.CONFIRMATION, context.lastPendingState)
        assertEquals(RepairSuggestedIntent.OPEN_WHATSAPP, context.lastSuggestedIntent)
        assertEquals(RobotActiveHandler.VOICE_CORRECTION, context.lastActiveHandler)
        assertEquals(RobotExternalApp.WHATSAPP, context.lastExternalApp)
    }
}
