package com.ojoclaro.android.llm

class LlmUsageGuard(
    private val budget: LlmUsageBudget = LlmUsageBudget(),
    private val clockMillis: () -> Long = System::currentTimeMillis
) {
    private var sessionCalls = 0
    private var dayCalls = 0
    private var consecutiveFailures = 0
    private var lastCallMillis = 0L
    private var dayKey: Long = currentDayKey(clockMillis())
    private var lastDecisionReason: String = "ready"

    fun canUse(reason: String): LlmUsageDecision {
        resetIfNewDay()

        if (sessionCalls >= budget.maxCallsPerSession) {
            return blocked("session_limit", "No uso la IA ahora. Ya se alcanzó el límite de la sesión.")
        }
        if (dayCalls >= budget.maxCallsPerDay) {
            return blocked("daily_limit", "No uso la IA ahora. Ya se alcanzó el límite de hoy.")
        }
        if (consecutiveFailures >= budget.disableAfterConsecutiveFailures) {
            return blocked("failure_backoff", "No uso la IA ahora. Hubo varios errores seguidos.")
        }
        val now = clockMillis()
        if (lastCallMillis > 0 && now - lastCallMillis < budget.minMillisBetweenCalls) {
            return blocked("cooldown", "No uso la IA ahora. Esperá un momento y probá otra vez.")
        }
        lastDecisionReason = reason
        return LlmUsageDecision.Allowed
    }

    fun recordSuccess(reason: String = "success") {
        resetIfNewDay()
        sessionCalls += 1
        dayCalls += 1
        lastCallMillis = clockMillis()
        consecutiveFailures = 0
        lastDecisionReason = reason
    }

    fun recordFailure(reason: String = "failure") {
        resetIfNewDay()
        sessionCalls += 1
        dayCalls += 1
        lastCallMillis = clockMillis()
        consecutiveFailures += 1
        lastDecisionReason = reason
    }

    fun snapshot(): LlmUsageSnapshot =
        LlmUsageSnapshot(
            sessionCalls = sessionCalls,
            dayCalls = dayCalls,
            consecutiveFailures = consecutiveFailures,
            lastCallMillis = lastCallMillis,
            lastDecisionReason = lastDecisionReason
        )

    fun debugReason(): String = lastDecisionReason

    private fun resetIfNewDay() {
        val today = currentDayKey(clockMillis())
        if (today != dayKey) {
            dayKey = today
            dayCalls = 0
            consecutiveFailures = 0
            lastCallMillis = 0L
            lastDecisionReason = "new_day"
        }
    }

    private fun currentDayKey(millis: Long): Long = millis / 86_400_000L

    private fun blocked(code: String, humanMessage: String): LlmUsageDecision {
        lastDecisionReason = code
        return LlmUsageDecision.Blocked(code = code, humanMessage = humanMessage)
    }
}

sealed class LlmUsageDecision {
    data object Allowed : LlmUsageDecision()
    data class Blocked(val code: String, val humanMessage: String) : LlmUsageDecision()
}

