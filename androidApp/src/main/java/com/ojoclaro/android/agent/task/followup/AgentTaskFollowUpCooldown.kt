package com.ojoclaro.android.agent.task.followup

class AgentTaskFollowUpCooldown(
    private val defaultCooldownMs: Long = DEFAULT_COOLDOWN_MS
) {
    private data class Entry(
        val announcedAtMillis: Long,
        val reasonKey: String?
    )

    private val lock = Any()
    private val entries: MutableMap<String, Entry> = HashMap()

    fun shouldAllow(
        semanticKey: String,
        importance: AgentTaskFollowUpImportance,
        reasonKey: String?,
        nowMillis: Long,
        cooldownMs: Long = defaultCooldownMs
    ): Boolean {
        if (cooldownMs <= 0L) return true
        val previous = synchronized(lock) { entries[semanticKey] } ?: return true
        val withinCooldown = nowMillis - previous.announcedAtMillis < cooldownMs
        if (!withinCooldown) return true
        return importance == AgentTaskFollowUpImportance.CRITICAL &&
            !reasonKey.isNullOrBlank() &&
            reasonKey != previous.reasonKey
    }

    fun remember(
        semanticKey: String,
        reasonKey: String?,
        nowMillis: Long
    ) {
        synchronized(lock) {
            entries[semanticKey] = Entry(
                announcedAtMillis = nowMillis,
                reasonKey = reasonKey
            )
        }
    }

    fun reset() {
        synchronized(lock) {
            entries.clear()
        }
    }

    companion object {
        const val DEFAULT_COOLDOWN_MS: Long = 15_000L
    }
}
