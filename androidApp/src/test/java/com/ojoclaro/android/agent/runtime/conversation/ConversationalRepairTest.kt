package com.ojoclaro.android.agent.runtime.conversation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConversationalRepairTest {

    @Test
    fun emptyInputReturnsUsefulSuggestions() {
        val response = ConversationalRepair.response(
            ConversationalRepairRequest(
                reason = RobotFailureReason.EMPTY_INPUT,
                context = RobotShortTermContext().recordFailure(
                    reason = RobotFailureReason.EMPTY_INPUT,
                    kind = RobotRecognizedKind.EMPTY_INPUT
                )
            )
        ).spokenText

        assertEquals(ConversationalRepair.NOT_HEARD, response)
        assertTrue(response.contains("qué hay en pantalla", ignoreCase = true))
        assertTrue(response.contains("WhatsApp", ignoreCase = true))
        assertTrue(response.contains("repetí", ignoreCase = true))
    }

    @Test
    fun noiseReturnsUsefulSuggestions() {
        val response = ConversationalRepair.response(
            ConversationalRepairRequest(
                reason = RobotFailureReason.RECOGNIZER_NOISE,
                context = RobotShortTermContext().recordFailure(
                    reason = RobotFailureReason.RECOGNIZER_NOISE,
                    kind = RobotRecognizedKind.NOISE
                )
            )
        ).spokenText

        assertEquals(ConversationalRepair.NOISE, response)
        assertTrue(response.contains("acción", ignoreCase = true))
        assertTrue(response.contains("resetear", ignoreCase = true))
    }

    @Test
    fun repeatedFailuresEscalateWithoutRepeatingExactly() {
        val firstContext = RobotShortTermContext().recordFailure(RobotFailureReason.RECOGNIZER_NOISE)
        val first = ConversationalRepair.response(
            ConversationalRepairRequest(RobotFailureReason.RECOGNIZER_NOISE, firstContext)
        ).spokenText

        val secondContext = firstContext.recordFailure(RobotFailureReason.RECOGNIZER_NOISE)
        val second = ConversationalRepair.response(
            ConversationalRepairRequest(RobotFailureReason.RECOGNIZER_NOISE, secondContext)
        ).spokenText

        assertEquals(ConversationalRepair.NOISE, first)
        assertEquals(ConversationalRepair.SECOND_FAILURE, second)
        assertFalse(first == second)
    }

    @Test
    fun thirdFailureSuggestsReset() {
        val context = RobotShortTermContext()
            .recordFailure(RobotFailureReason.RECOGNIZER_NOISE)
            .recordFailure(RobotFailureReason.RECOGNIZER_NOISE)
            .recordFailure(RobotFailureReason.RECOGNIZER_NOISE)

        val response = ConversationalRepair.response(
            ConversationalRepairRequest(RobotFailureReason.RECOGNIZER_NOISE, context)
        ).spokenText

        assertEquals(ConversationalRepair.THIRD_FAILURE, response)
        assertTrue(response.contains("resetear", ignoreCase = true))
    }

    @Test
    fun pendingWhatsAppGivesWhatsAppSuggestions() {
        val response = ConversationalRepair.response(
            ConversationalRepairRequest(
                reason = RobotFailureReason.WAITING_WHATSAPP,
                pendingState = RobotPendingState.WHATSAPP
            )
        ).spokenText

        assertEquals(ConversationalRepair.WAITING_WHATSAPP, response)
        assertTrue(response.contains("chat de un contacto", ignoreCase = true))
        assertTrue(response.contains("mensaje para un contacto", ignoreCase = true))
        assertTrue(response.contains("cancelar", ignoreCase = true))
    }

    @Test
    fun pendingConfirmationAsksForYesOrCancel() {
        val response = ConversationalRepair.response(
            ConversationalRepairRequest(
                reason = RobotFailureReason.CONFIRMATION_UNCLEAR,
                pendingState = RobotPendingState.CONFIRMATION
            )
        ).spokenText

        assertEquals(ConversationalRepair.CONFIRMATION_UNCLEAR, response)
        assertTrue(response.contains("sí", ignoreCase = true))
        assertTrue(response.contains("cancelar", ignoreCase = true))
    }

    @Test
    fun sensitiveScreenBlocksReading() {
        val response = ConversationalRepair.response(
            ConversationalRepairRequest(RobotFailureReason.SENSITIVE_SCREEN)
        ).spokenText

        assertEquals(ConversationalRepair.SENSITIVE_SCREEN, response)
        assertTrue(response.contains("No voy a leerla", ignoreCase = true))
    }

    @Test
    fun possibleCommandUsesHumanConfirmationCopy() {
        assertEquals(
            "¿Quisiste decir abrir WhatsApp?",
            ConversationalRepair.possibleCommand(RepairSuggestedIntent.OPEN_WHATSAPP)
        )
    }

    @Test
    fun publicCopyDoesNotContainInternalWords() {
        val publicCopy = listOf(
            ConversationalRepair.NOT_HEARD,
            ConversationalRepair.NOISE,
            ConversationalRepair.SECOND_FAILURE,
            ConversationalRepair.THIRD_FAILURE,
            ConversationalRepair.WAITING_WHATSAPP,
            ConversationalRepair.SENSITIVE_SCREEN,
            ConversationalRepair.SAFE_AI_UNAVAILABLE,
            ConversationalRepair.CONFIRMATION_UNCLEAR,
            ConversationalRepair.CONFIRMATION_CANCELLED,
            ConversationalRepair.ROBOT_OFF,
            ConversationalRepair.NORMAL_SUGGESTIONS,
            ConversationalRepair.WHATSAPP_OPEN_SUGGESTIONS,
            ConversationalRepair.possibleCommand(RepairSuggestedIntent.OPEN_WHATSAPP)
        )

        publicCopy.forEach { text ->
            assertFalse(ConversationalRepair.containsPublicDebugToken(text), text)
        }
    }
}
