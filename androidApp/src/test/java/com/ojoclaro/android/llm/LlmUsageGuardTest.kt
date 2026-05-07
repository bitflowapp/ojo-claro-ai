package com.ojoclaro.android.llm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LlmUsageGuardTest {

    @Test
    fun respectsSessionAndCooldownLimits() {
        var now = 1_000L
        val guard = LlmUsageGuard(
            budget = LlmUsageBudget(
                maxCallsPerSession = 2,
                maxCallsPerDay = 2,
                minMillisBetweenCalls = 500L,
                disableAfterConsecutiveFailures = 2
            ),
            clockMillis = { now }
        )

        assertIs<LlmUsageDecision.Allowed>(guard.canUse("first"))
        guard.recordSuccess()

        now += 200L
        val cooldown = guard.canUse("cooldown")
        assertIs<LlmUsageDecision.Blocked>(cooldown)
        assertEquals("cooldown", cooldown.code)

        now += 600L
        assertIs<LlmUsageDecision.Allowed>(guard.canUse("second"))
        guard.recordFailure()

        now += 600L
        val blocked = guard.canUse("third")
        assertIs<LlmUsageDecision.Blocked>(blocked)
        assertTrue(blocked.code == "session_limit" || blocked.code == "daily_limit" || blocked.code == "failure_backoff")
    }
}

