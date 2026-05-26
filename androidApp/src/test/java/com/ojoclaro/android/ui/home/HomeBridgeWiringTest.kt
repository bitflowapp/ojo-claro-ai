package com.ojoclaro.android.ui.home

import com.ojoclaro.android.agent.core.AgentCoreFeatureFlags
import com.ojoclaro.android.agent.core.runtime.OjoClaroRuntimeGraph
import com.ojoclaro.android.agent.core.runtime.RuntimeGraphOwner
import com.ojoclaro.android.agent.core.screen.AccessibilitySnapshotEventRouter
import com.ojoclaro.android.agent.core.screen.ScreenContextProvider
import com.ojoclaro.android.agent.core.screen.ScreenSnapshot
import kotlin.test.Test
import kotlin.test.assertNotNull
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
        assertNull(selectAgentTaskFollowUpCoordinatorForHome(owner))
        assertNull(selectTaskAutoFollowUpSnapshotsForHome(owner))
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

    @Test
    fun `voice coordinator wiring returns null before runtime graph install`() {
        val owner = owner()

        assertNull(selectAgentBridgeVoiceCoordinatorForHome(owner))
    }

    @Test
    fun `voice coordinator wiring returns graph coordinator when installed`() {
        val owner = owner()
        val graph = owner.installOnce {
            AgentCoreFeatureFlags(
                typedConfirmationEnabled = true,
                accessibilityRuntimeContextEnabled = true
            )
        }

        assertSame(graph.voiceCoordinator, selectAgentBridgeVoiceCoordinatorForHome(owner))
    }

    @Test
    fun `screen change announcements wiring returns null before install`() {
        val owner = owner()
        assertNull(selectScreenChangeAnnouncementsForHome(owner))
    }

    @Test
    fun `screen change announcements wiring returns graph flow when installed`() {
        val owner = owner()
        val graph = owner.installOnce {
            AgentCoreFeatureFlags(
                typedConfirmationEnabled = true,
                accessibilityRuntimeContextEnabled = true,
                screenChangeAwarenessEnabled = true
            )
        }

        assertSame(
            graph.screenChangeAwarenessCoordinator.announcements,
            selectScreenChangeAnnouncementsForHome(owner)
        )
    }

    @Test
    fun `task follow up wiring returns graph dependencies when installed`() {
        val owner = owner()
        val graph = owner.installOnce {
            AgentCoreFeatureFlags(
                typedConfirmationEnabled = true,
                accessibilityRuntimeContextEnabled = true,
                screenChangeAwarenessEnabled = true,
                taskAutoFollowUpEnabled = true
            )
        }

        assertSame(
            graph.taskFollowUpCoordinator,
            selectAgentTaskFollowUpCoordinatorForHome(owner)
        )
        assertSame(
            graph.screenRepository.state,
            selectTaskAutoFollowUpSnapshotsForHome(owner)
        )
    }

    @Test
    fun `voice coordinator is stable across repeated wiring calls`() {
        val owner = owner()
        owner.installOnce {
            AgentCoreFeatureFlags(
                typedConfirmationEnabled = true,
                accessibilityRuntimeContextEnabled = true
            )
        }

        val first = selectAgentBridgeVoiceCoordinatorForHome(owner)
        val second = selectAgentBridgeVoiceCoordinatorForHome(owner)
        val third = selectAgentBridgeVoiceCoordinatorForHome(owner)

        assertNotNull(first)
        assertSame(first, second)
        assertSame(first, third)
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
