package com.ojoclaro.android.llm

data class LlmUsageBudget(
    val maxCallsPerSession: Int = 20,
    val maxCallsPerDay: Int = 100,
    val minMillisBetweenCalls: Long = 1_500L,
    val disableAfterConsecutiveFailures: Int = 3
)

data class LlmUsageSnapshot(
    val sessionCalls: Int,
    val dayCalls: Int,
    val consecutiveFailures: Int,
    val lastCallMillis: Long,
    val lastDecisionReason: String
)

