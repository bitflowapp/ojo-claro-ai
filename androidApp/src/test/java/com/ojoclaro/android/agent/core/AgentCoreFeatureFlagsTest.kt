package com.ojoclaro.android.agent.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgentCoreFeatureFlagsTest {

    @Test
    fun disabledIsAllOff() {
        val flags = AgentCoreFeatureFlags.DISABLED
        assertFalse(flags.anyEnabled)
        assertFalse(flags.plannerEnabled)
        assertFalse(flags.chainedActionsEnabled)
        assertFalse(flags.screenSummarizationEnabled)
        assertFalse(flags.llmFallbackEnabled)
        assertFalse(flags.preferenceLearningEnabled)
        assertFalse(flags.emergencyModeEnabled)
        assertFalse(flags.genericAppExecutionEnabled)
    }

    @Test
    fun defaultIsDisabled() {
        val flags = AgentCoreFeatureFlags()
        assertFalse(flags.anyEnabled)
    }

    @Test
    fun qaPreviewKeepsGenericAppOff() {
        val flags = AgentCoreFeatureFlags.QA_PREVIEW
        assertTrue(flags.anyEnabled)
        assertFalse(
            flags.genericAppExecutionEnabled,
            "Generic app execution must NEVER be enabled by default — even in QA preview"
        )
    }

    @Test
    fun qaPreviewKeepsLlmFallbackOffUntilConfigured() {
        val flags = AgentCoreFeatureFlags.QA_PREVIEW
        assertFalse(
            flags.llmFallbackEnabled,
            "LLM fallback must stay off until safe config is in place"
        )
    }
}
