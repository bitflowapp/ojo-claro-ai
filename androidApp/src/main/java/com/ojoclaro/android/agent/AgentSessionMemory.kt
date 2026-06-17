package com.ojoclaro.android.agent

import com.ojoclaro.android.external.PendingConfirmation

data class AgentSessionSnapshot(
    val lastSpokenResponse: String = "",
    val lastPendingActionLabel: String = "",
    val lastContactName: String = "",
    val lastProposedMessage: String = ""
)

/**
 * Memoria efimera de sesion. No persiste datos y no guarda conversaciones largas.
 */
class AgentSessionMemory {
    private var snapshot = AgentSessionSnapshot()

    fun snapshot(): AgentSessionSnapshot = snapshot

    fun rememberSpokenResponse(text: String) {
        val clean = text.trim()
        if (clean.isBlank()) return
        snapshot = snapshot.copy(lastSpokenResponse = clean.take(MAX_RESPONSE_CHARS))
    }

    fun rememberPendingAction(pending: PendingConfirmation?) {
        snapshot = snapshot.copy(lastPendingActionLabel = pending?.command?.type?.name.orEmpty())
    }

    fun clearPendingAction() {
        snapshot = snapshot.copy(lastPendingActionLabel = "")
    }

    fun rememberContactAndMessage(contactName: String, proposedMessage: String) {
        snapshot = snapshot.copy(
            lastContactName = contactName.trim().take(MAX_CONTACT_CHARS),
            lastProposedMessage = proposedMessage.trim().take(MAX_MESSAGE_CHARS)
        )
    }

    fun clearConversationContext() {
        snapshot = snapshot.copy(
            lastPendingActionLabel = "",
            lastContactName = "",
            lastProposedMessage = ""
        )
    }

    companion object {
        private const val MAX_RESPONSE_CHARS = 600
        private const val MAX_CONTACT_CHARS = 80
        private const val MAX_MESSAGE_CHARS = 500
    }
}
