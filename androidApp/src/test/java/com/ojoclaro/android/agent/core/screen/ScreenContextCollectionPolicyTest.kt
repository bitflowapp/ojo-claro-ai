package com.ojoclaro.android.agent.core.screen

import com.ojoclaro.android.agent.core.AgentCoreFeatureFlags
import kotlin.test.Test
import kotlin.test.assertEquals

class ScreenContextCollectionPolicyTest {

    @Test
    fun `flag off always skips even on relevant event`() {
        val decision = ScreenContextCollectionPolicy.decide(
            flags = AgentCoreFeatureFlags.DISABLED,
            relevance = AccessibilityEventClassifier.Relevance.RELEVANT_NOW
        )
        assertEquals(ScreenContextCollectionPolicy.Decision.SKIP_FLAG_OFF, decision)
    }

    @Test
    fun `flag off skips on irrelevant event too`() {
        val decision = ScreenContextCollectionPolicy.decide(
            flags = AgentCoreFeatureFlags.DISABLED,
            relevance = AccessibilityEventClassifier.Relevance.IRRELEVANT
        )
        assertEquals(ScreenContextCollectionPolicy.Decision.SKIP_FLAG_OFF, decision)
    }

    @Test
    fun `flag on plus relevant event collects`() {
        val decision = ScreenContextCollectionPolicy.decide(
            flags = AgentCoreFeatureFlags(accessibilityRuntimeContextEnabled = true),
            relevance = AccessibilityEventClassifier.Relevance.RELEVANT_NOW
        )
        assertEquals(ScreenContextCollectionPolicy.Decision.COLLECT, decision)
    }

    @Test
    fun `flag on plus irrelevant event skips`() {
        val decision = ScreenContextCollectionPolicy.decide(
            flags = AgentCoreFeatureFlags(accessibilityRuntimeContextEnabled = true),
            relevance = AccessibilityEventClassifier.Relevance.IRRELEVANT
        )
        assertEquals(ScreenContextCollectionPolicy.Decision.SKIP_IRRELEVANT_EVENT, decision)
    }
}
