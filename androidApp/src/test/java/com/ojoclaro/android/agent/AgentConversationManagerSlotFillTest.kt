package com.ojoclaro.android.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for the LLM-driven slot_fill entry point on AgentConversationManager.
 *
 * The legacy slot-filling path (UNKNOWN intent against a pending request) is
 * exercised by AgentConversationManagerTest. This file only covers the new
 * handleSlotFill(slot, value) method introduced for prompt v3:
 *
 *   waiting_contact → waiting_message → waiting_confirmation
 *
 * Privacy invariants are mirrored from the legacy path.
 */
class AgentConversationManagerSlotFillTest {

    private val parser = LocalIntentParser()

    private fun whatsAppGuidedIntent(): ParsedAgentIntent =
        ParsedAgentIntent(
            intent = AgentIntent.OPEN_WHATSAPP,
            slots = emptyList(),
            rawText = "WhatsApp",
            confidence = 0.92f,
            missingSlots = listOf(AgentSlotName.WHATSAPP_ACTION)
        )

    // --- waiting_contact + contact_query → waiting_message ---

    @Test
    fun waitingContactWithContactQueryTransitionsToWaitingMessage() {
        val manager = AgentConversationManager()
        manager.handle(parser.parse("mandale un mensaje"))
        assertEquals(AgentState.WAITING_CONTACT, manager.currentState)

        val outcome = manager.handleSlotFill(
            slot = AgentConversationManager.SLOT_FILL_CONTACT_QUERY,
            value = "mamá"
        )

        assertEquals(AgentState.WAITING_MESSAGE, outcome.targetState)
        assertEquals(AgentState.WAITING_MESSAGE, manager.currentState)
        assertEquals(AgentSlotName.MESSAGE_TEXT, outcome.missingSlot)
        assertEquals("¿Qué mensaje querés mandarle?", outcome.spokenText)
    }

    @Test
    fun waitingContactCallContactDoesNotTransitionToWaitingMessage() {
        val manager = managerWaitingForContact(AgentIntent.CALL_CONTACT)

        val outcome = manager.handleSlotFill(
            slot = AgentConversationManager.SLOT_FILL_CONTACT_QUERY,
            value = "mama"
        )

        assertEquals(AgentState.PROCESSING, outcome.targetState)
        assertEquals(AgentState.IDLE, manager.currentState)
        assertEquals(AgentIntent.CALL_CONTACT, outcome.suggestedIntent?.intent)
        assertTrue(outcome.suggestedIntent?.requiresConfirmation == true)
    }

    @Test
    fun waitingContactOpenWhatsappChatDoesNotTransitionToWaitingMessage() {
        val manager = managerWaitingForContact(AgentIntent.OPEN_WHATSAPP_CHAT)

        val outcome = manager.handleSlotFill(
            slot = AgentConversationManager.SLOT_FILL_CONTACT_QUERY,
            value = "mama"
        )

        assertEquals(AgentState.PROCESSING, outcome.targetState)
        assertEquals(AgentState.IDLE, manager.currentState)
        assertEquals(AgentIntent.OPEN_WHATSAPP_CHAT, outcome.suggestedIntent?.intent)
        assertTrue(outcome.suggestedIntent?.requiresConfirmation == true)
    }

    @Test
    fun waitingContactDeleteContactDoesNotTransitionToWaitingMessage() {
        val manager = managerWaitingForContact(AgentIntent.DELETE_CONTACT)

        val outcome = manager.handleSlotFill(
            slot = AgentConversationManager.SLOT_FILL_CONTACT_QUERY,
            value = "mama"
        )

        assertEquals(AgentState.WAITING_CONFIRMATION, outcome.targetState)
        assertEquals(AgentState.WAITING_CONFIRMATION, manager.currentState)
        assertEquals(AgentIntent.DELETE_CONTACT, outcome.suggestedIntent?.intent)
        assertTrue(outcome.needsConfirmation)
    }

    @Test
    fun waitingContactSaveContactPhoneAsksForPhoneInsteadOfWaitingMessage() {
        val manager = managerWaitingForContact(AgentIntent.SAVE_CONTACT_PHONE)

        val outcome = manager.handleSlotFill(
            slot = AgentConversationManager.SLOT_FILL_CONTACT_QUERY,
            value = "mama"
        )

        assertEquals(AgentState.WAITING_PHONE_NUMBER, outcome.targetState)
        assertEquals(AgentState.WAITING_PHONE_NUMBER, manager.currentState)
        assertEquals(AgentSlotName.PHONE_NUMBER, outcome.missingSlot)
    }

    @Test
    fun waitingContactUnknownPendingActionFailsSafe() {
        val manager = managerWithRawPendingContactAction(AgentIntent.NAVIGATE_TO_DESTINATION)

        val outcome = manager.handleSlotFill(
            slot = AgentConversationManager.SLOT_FILL_CONTACT_QUERY,
            value = "mama"
        )

        assertTrue(outcome.isError)
        assertEquals(AgentState.ERROR_RECOVERABLE, outcome.targetState)
        assertEquals(AgentState.ERROR_RECOVERABLE, manager.currentState)
    }

    @Test
    fun waitingContactSlotFillStoresContactNameInPending() {
        val manager = AgentConversationManager()
        manager.handle(parser.parse("mandale un mensaje"))

        manager.handleSlotFill(AgentConversationManager.SLOT_FILL_CONTACT_QUERY, "mamá")

        val nextOutcome = manager.handleSlotFill(
            slot = AgentConversationManager.SLOT_FILL_MESSAGE_TEXT,
            value = "estoy llegando"
        )
        val pending = nextOutcome.suggestedIntent
        assertNotNull(pending)
        assertEquals("mamá", pending.slotValue(AgentSlotName.CONTACT_NAME))
    }

    @Test
    fun waitingContactSlotFillTrimsWhitespace() {
        val manager = AgentConversationManager()
        manager.handle(parser.parse("mandale un mensaje"))

        manager.handleSlotFill(
            AgentConversationManager.SLOT_FILL_CONTACT_QUERY,
            "  mamá   "
        )
        val outcome = manager.handleSlotFill(
            AgentConversationManager.SLOT_FILL_MESSAGE_TEXT,
            "estoy llegando"
        )

        assertEquals("mamá", outcome.suggestedIntent?.slotValue(AgentSlotName.CONTACT_NAME))
    }

    // --- waiting_message + message_text → waiting_confirmation ---

    @Test
    fun waitingMessageWithMessageTextTransitionsToWaitingConfirmation() {
        val manager = AgentConversationManager()
        manager.handle(parser.parse("mandale a ContactoDemo"))
        assertEquals(AgentState.WAITING_MESSAGE, manager.currentState)

        val outcome = manager.handleSlotFill(
            slot = AgentConversationManager.SLOT_FILL_MESSAGE_TEXT,
            value = "estoy llegando"
        )

        assertEquals(AgentState.WAITING_CONFIRMATION, outcome.targetState)
        assertEquals(AgentState.WAITING_CONFIRMATION, manager.currentState)
        assertTrue(outcome.needsConfirmation)
    }

    @Test
    fun waitingMessageSlotFillStoresMessageInPending() {
        val manager = AgentConversationManager()
        manager.handle(parser.parse("mandale a ContactoDemo"))

        val outcome = manager.handleSlotFill(
            AgentConversationManager.SLOT_FILL_MESSAGE_TEXT,
            "estoy llegando"
        )

        val pending = outcome.suggestedIntent
        assertNotNull(pending)
        assertEquals("estoy llegando", pending.slotValue(AgentSlotName.MESSAGE_TEXT))
        assertTrue(pending.missingSlots.isEmpty())
        assertTrue(pending.requiresConfirmation)
    }

    @Test
    fun waitingMessageSensitiveContentIsBlocked() {
        val manager = AgentConversationManager()
        manager.handle(parser.parse("mandale a ContactoDemo"))

        val outcome = manager.handleSlotFill(
            AgentConversationManager.SLOT_FILL_MESSAGE_TEXT,
            "mi código de verificación es 123456"
        )

        assertTrue(outcome.isError)
        assertEquals(AgentState.ERROR_RECOVERABLE, outcome.targetState)
        assertEquals(
            "Mensaje sensible bloqueado antes de confirmar.",
            outcome.safetyNotice
        )
        assertFalse(manager.hasPendingSlotRequest)
    }

    // --- waiting_whatsapp_action + whatsapp_action → waiting_confirmation ---

    @Test
    fun waitingWhatsAppActionWithSlotTransitionsToWaitingConfirmation() {
        val manager = AgentConversationManager()
        manager.handle(whatsAppGuidedIntent())
        assertEquals(AgentState.WAITING_WHATSAPP_ACTION, manager.currentState)

        val outcome = manager.handleSlotFill(
            slot = AgentConversationManager.SLOT_FILL_WHATSAPP_ACTION,
            value = "mensaje"
        )

        assertEquals(AgentState.WAITING_CONFIRMATION, outcome.targetState)
        assertEquals(AgentState.WAITING_CONFIRMATION, manager.currentState)
        assertTrue(outcome.needsConfirmation)
    }

    @Test
    fun waitingWhatsAppActionSlotFillStoresActionInPending() {
        val manager = AgentConversationManager()
        manager.handle(whatsAppGuidedIntent())

        val outcome = manager.handleSlotFill(
            AgentConversationManager.SLOT_FILL_WHATSAPP_ACTION,
            "chat"
        )

        val pending = outcome.suggestedIntent
        assertNotNull(pending)
        assertEquals("chat", pending.slotValue(AgentSlotName.WHATSAPP_ACTION))
        assertTrue(pending.requiresConfirmation)
    }

    // --- Full cycle: waiting_contact → waiting_message → waiting_confirmation ---

    @Test
    fun fullSlotFillCycleClosesAtConfirmation() {
        val manager = AgentConversationManager()

        val askContact = manager.handle(parser.parse("mandale un mensaje"))
        assertEquals(AgentState.WAITING_CONTACT, askContact.targetState)

        val askMessage = manager.handleSlotFill(
            AgentConversationManager.SLOT_FILL_CONTACT_QUERY,
            "mamá"
        )
        assertEquals(AgentState.WAITING_MESSAGE, askMessage.targetState)

        val askConfirm = manager.handleSlotFill(
            AgentConversationManager.SLOT_FILL_MESSAGE_TEXT,
            "estoy llegando"
        )
        assertEquals(AgentState.WAITING_CONFIRMATION, askConfirm.targetState)
        assertTrue(askConfirm.needsConfirmation)

        val ready = askConfirm.suggestedIntent
        assertNotNull(ready)
        assertEquals("mamá", ready.slotValue(AgentSlotName.CONTACT_NAME))
        assertEquals("estoy llegando", ready.slotValue(AgentSlotName.MESSAGE_TEXT))
    }

    @Test
    fun fullCycleAllowsLegacyConfirmToExecute() {
        val manager = AgentConversationManager()
        manager.handle(parser.parse("mandale un mensaje"))
        manager.handleSlotFill(AgentConversationManager.SLOT_FILL_CONTACT_QUERY, "mamá")
        manager.handleSlotFill(
            AgentConversationManager.SLOT_FILL_MESSAGE_TEXT,
            "estoy llegando"
        )

        // Confirm uses the legacy entry point — proves we did not break it.
        val confirmed = manager.handle(
            ParsedAgentIntent(
                intent = AgentIntent.CONFIRM,
                slots = emptyList(),
                rawText = "confirmar",
                confidence = 1f
            )
        )

        assertEquals(AgentState.PROCESSING, confirmed.targetState)
        assertEquals(AgentIntent.COMPOSE_WHATSAPP_MESSAGE, confirmed.suggestedIntent?.intent)
        assertFalse(manager.hasPendingSlotRequest)
    }

    // --- Edge cases ---

    @Test
    fun slotFillWithoutPendingReturnsRecoverableError() {
        val manager = AgentConversationManager()
        // No previous handle() call → no pending action.

        val outcome = manager.handleSlotFill(
            AgentConversationManager.SLOT_FILL_CONTACT_QUERY,
            "mamá"
        )

        assertTrue(outcome.isError)
        assertEquals(AgentState.ERROR_RECOVERABLE, outcome.targetState)
        assertEquals("No hay ninguna acción pendiente.", outcome.spokenText)
    }

    @Test
    fun slotFillWithBlankValueReturnsRecoverableError() {
        val manager = AgentConversationManager()
        manager.handle(parser.parse("mandale un mensaje"))

        val outcome = manager.handleSlotFill(
            AgentConversationManager.SLOT_FILL_CONTACT_QUERY,
            "   "
        )

        assertTrue(outcome.isError)
        assertEquals("No escuché un comando claro.", outcome.spokenText)
    }

    @Test
    fun slotFillWithStateMismatchReturnsRecoverableError() {
        val manager = AgentConversationManager()
        manager.handle(parser.parse("mandale un mensaje"))
        // state is WAITING_CONTACT, but caller sends message_text slot.

        val outcome = manager.handleSlotFill(
            AgentConversationManager.SLOT_FILL_MESSAGE_TEXT,
            "estoy llegando"
        )

        assertTrue(outcome.isError)
        assertEquals(AgentState.ERROR_RECOVERABLE, outcome.targetState)
    }

    @Test
    fun slotFillWithUnknownSlotNameReturnsRecoverableError() {
        val manager = AgentConversationManager()
        manager.handle(parser.parse("mandale un mensaje"))

        val outcome = manager.handleSlotFill("totally_made_up_slot", "anything")

        assertTrue(outcome.isError)
        assertEquals(AgentState.ERROR_RECOVERABLE, outcome.targetState)
    }

    // --- Non-regression: existing handle() flow still works ---

    @Test
    fun legacyUnknownPathStillFillsContactSlot() {
        // The legacy path treats raw text as a slot value when an UNKNOWN intent
        // arrives during a pending slot request. This must keep working unchanged.
        val manager = AgentConversationManager()
        manager.handle(parser.parse("mandale un mensaje"))

        val outcome = manager.handle(parser.parse("ContactoDemo"))

        assertEquals(AgentState.WAITING_MESSAGE, outcome.targetState)
        assertEquals(AgentSlotName.MESSAGE_TEXT, outcome.missingSlot)
    }

    private fun managerWaitingForContact(intent: AgentIntent): AgentConversationManager =
        AgentConversationManager().apply {
            handle(contactPendingIntent(intent))
            assertEquals(AgentState.WAITING_CONTACT, currentState)
        }

    private fun managerWithRawPendingContactAction(intent: AgentIntent): AgentConversationManager =
        AgentConversationManager().apply {
            val pendingField = javaClass.getDeclaredField("pendingIntent")
            pendingField.isAccessible = true
            pendingField.set(this, contactPendingIntent(intent))

            val stateField = javaClass.getDeclaredField("currentState")
            stateField.isAccessible = true
            stateField.set(this, AgentState.WAITING_CONTACT)
        }

    private fun contactPendingIntent(intent: AgentIntent): ParsedAgentIntent =
        ParsedAgentIntent(
            intent = intent,
            slots = emptyList(),
            rawText = "contacto pendiente",
            confidence = 0.9f,
            missingSlots = listOf(AgentSlotName.CONTACT_NAME)
        )
}
