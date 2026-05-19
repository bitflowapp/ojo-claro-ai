package com.ojoclaro.android.ui.home

import com.ojoclaro.android.agent.core.AgentCoreFeatureFlags
import com.ojoclaro.android.agent.core.runtime.OjoClaroRuntimeGraph
import com.ojoclaro.android.agent.core.runtime.RuntimeGraphOwner
import com.ojoclaro.android.agent.core.screen.AccessibilitySnapshotEventRouter
import com.ojoclaro.android.agent.core.screen.ScreenContextProvider
import com.ojoclaro.android.agent.core.screen.ScreenSnapshot
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertSame

class HomeBridgeWiringTest {

    private class FakeProvider : ScreenContextProvider {
        override fun current(): ScreenSnapshot? = null
    }

    @Test
    fun `home wiring returns null before runtime graph install`() {
        val owner = owner()

        assertNull(selectAgentBridgeDispatchControllerForHome(owner))
    }

    @Test
    fun `home wiring returns dispatch controller when graph is installed`() {
        val owner = owner()
        val graph = owner.installOnce {
            AgentCoreFeatureFlags(
                typedConfirmationEnabled = true,
                accessibilityRuntimeContextEnabled = true
            )
        }

        assertSame(graph.dispatchController, selectAgentBridgeDispatchControllerForHome(owner))
    }

    private fun owner(): RuntimeGraphOwner =
        RuntimeGraphOwner(
            graphFactory = { flags ->
                OjoClaroRuntimeGraph.createForTesting(
                    flags = flags,
                    provider = FakeProvider(),
                    routerInstaller = { _: AccessibilitySnapshotEventRouter? -> }
                )
            }
        )
}
