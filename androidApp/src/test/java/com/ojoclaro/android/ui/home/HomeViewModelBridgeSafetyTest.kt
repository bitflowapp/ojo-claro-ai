package com.ojoclaro.android.ui.home

import com.ojoclaro.android.agent.core.runtime.BridgeDispatchKind
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeViewModelBridgeSafetyTest {

    @Test
    fun `bridge pending clears legacy pending state`() {
        assertTrue(shouldClearLegacyPendingForBridgeOutcome(BridgeDispatchKind.PENDING))
    }

    @Test
    fun `bridge confirmation and cancellation clear legacy pending state`() {
        assertTrue(shouldClearLegacyPendingForBridgeOutcome(BridgeDispatchKind.CONFIRMED))
        assertTrue(shouldClearLegacyPendingForBridgeOutcome(BridgeDispatchKind.CANCELLED))
    }

    @Test
    fun `no pending does not clear legacy pending state`() {
        assertFalse(shouldClearLegacyPendingForBridgeOutcome(BridgeDispatchKind.NO_PENDING))
    }
}
