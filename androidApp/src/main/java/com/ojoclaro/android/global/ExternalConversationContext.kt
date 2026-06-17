package com.ojoclaro.android.global

import com.ojoclaro.android.agent.AgentState
import com.ojoclaro.android.external.PendingConfirmation

data class ExternalConversationSnapshot(
    val active: Boolean,
    val externalApp: ExternalAppName,
    val startedAtMillis: Long,
    val expiresAtMillis: Long,
    val reason: String,
    val returnHint: String,
    val lastContactName: String?,
    val pendingMessage: String?,
    val pendingConfirmation: PendingConfirmation?,
    val agentState: AgentState?
)

class ExternalConversationContext(
    private val ttlMillis: Long = GlobalAssistantMode.TTL_MILLIS,
    private val nowMillis: () -> Long = { System.currentTimeMillis() }
) {
    private var snapshot = ExternalConversationSnapshot(
        active = false,
        externalApp = ExternalAppName.UNKNOWN,
        startedAtMillis = 0L,
        expiresAtMillis = 0L,
        reason = "",
        returnHint = "",
        lastContactName = null,
        pendingMessage = null,
        pendingConfirmation = null,
        agentState = null
    )

    val current: ExternalConversationSnapshot
        get() {
            expireIfNeeded()
            return snapshot
        }

    fun start(
        externalApp: ExternalAppName,
        reason: String,
        returnHint: String,
        agentState: AgentState? = null
    ): ExternalConversationSnapshot {
        val now = nowMillis()
        snapshot = ExternalConversationSnapshot(
            active = true,
            externalApp = externalApp,
            startedAtMillis = now,
            expiresAtMillis = now + ttlMillis,
            reason = reason,
            returnHint = returnHint,
            lastContactName = null,
            pendingMessage = null,
            pendingConfirmation = null,
            agentState = agentState
        )
        return snapshot
    }

    fun touch(): ExternalConversationSnapshot {
        if (!snapshot.active) return snapshot
        val now = nowMillis()
        snapshot = snapshot.copy(expiresAtMillis = now + ttlMillis)
        return snapshot
    }

    fun updateContact(contactName: String?) {
        snapshot = snapshot.copy(lastContactName = contactName?.trim()?.takeIf { it.isNotBlank() })
    }

    fun updatePendingMessage(message: String?) {
        snapshot = snapshot.copy(pendingMessage = message?.trim()?.takeIf { it.isNotBlank() })
    }

    fun updatePendingConfirmation(pending: PendingConfirmation?) {
        snapshot = snapshot.copy(pendingConfirmation = pending)
    }

    fun updateAgentState(state: AgentState?) {
        snapshot = snapshot.copy(agentState = state)
    }

    fun silence(): ExternalConversationSnapshot {
        expireIfNeeded()
        return snapshot
    }

    fun clear(): ExternalConversationSnapshot {
        snapshot = ExternalConversationSnapshot(
            active = false,
            externalApp = ExternalAppName.UNKNOWN,
            startedAtMillis = 0L,
            expiresAtMillis = 0L,
            reason = "",
            returnHint = "",
            lastContactName = null,
            pendingMessage = null,
            pendingConfirmation = null,
            agentState = null
        )
        return snapshot
    }

    fun cancel(): ExternalConversationSnapshot = clear()

    fun stop(): ExternalConversationSnapshot = clear()

    fun expireIfNeeded(now: Long = nowMillis()): Boolean {
        if (!snapshot.active || now < snapshot.expiresAtMillis) return false
        clear()
        return true
    }
}
