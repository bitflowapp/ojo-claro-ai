package com.ojoclaro.android.agent.core.runtime

import com.ojoclaro.android.agent.core.AgentCoreFeatureFlags
import com.ojoclaro.android.agent.core.screen.AccessibilitySnapshotEventRouter
import com.ojoclaro.android.agent.core.screen.ScreenContextProvider
import com.ojoclaro.android.agent.core.screen.ScreenSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class RuntimeGraphOwnerTest {

    private class FakeProvider : ScreenContextProvider {
        override fun current(): ScreenSnapshot? = null
    }

    private class FakeInstaller {
        var installCalls: Int = 0
        var nullCalls: Int = 0
        var registered: AccessibilitySnapshotEventRouter? = null
        operator fun invoke(router: AccessibilitySnapshotEventRouter?) {
            if (router == null) nullCalls += 1 else installCalls += 1
            registered = router
        }
    }

    private fun makeOwner(
        installer: FakeInstaller = FakeInstaller(),
        flags: AgentCoreFeatureFlags = AgentCoreFeatureFlags.DISABLED
    ): Pair<RuntimeGraphOwner, FakeInstaller> {
        val owner = RuntimeGraphOwner(
            graphFactory = { flagsProvider ->
                OjoClaroRuntimeGraph.createForTesting(
                    flags = flagsProvider,
                    provider = FakeProvider(),
                    routerInstaller = { installer(it) }
                )
            }
        )
        return owner to installer
    }

    @Test
    fun `currentGraph is null before installOnce`() {
        val (owner, _) = makeOwner()
        assertNull(owner.currentGraph())
    }

    @Test
    fun `installOnce creates and installs the graph`() {
        val (owner, installer) = makeOwner()

        val graph = owner.installOnce(flags = { AgentCoreFeatureFlags.DISABLED })

        assertNotNull(graph)
        assertSame(graph, owner.currentGraph())
        assertEquals(1, installer.installCalls)
        assertEquals(graph.snapshotRouter, installer.registered)
    }

    @Test
    fun `installOnce is idempotent`() {
        val (owner, installer) = makeOwner()

        val a = owner.installOnce(flags = { AgentCoreFeatureFlags.DISABLED })
        val b = owner.installOnce(flags = { AgentCoreFeatureFlags.DISABLED })

        assertSame(a, b)
        assertEquals(1, installer.installCalls)
    }

    @Test
    fun `release unregisters router and clears current`() {
        val (owner, installer) = makeOwner()
        owner.installOnce(flags = { AgentCoreFeatureFlags.DISABLED })

        owner.release()

        assertEquals(1, installer.nullCalls)
        assertNull(owner.currentGraph())
    }

    @Test
    fun `release without prior installOnce is safe noop`() {
        val (owner, installer) = makeOwner()

        owner.release()

        assertEquals(0, installer.installCalls)
        assertEquals(0, installer.nullCalls)
    }

    @Test
    fun `installOnce after release creates a fresh graph`() {
        val (owner, installer) = makeOwner()
        val first = owner.installOnce(flags = { AgentCoreFeatureFlags.DISABLED })
        owner.release()

        val second = owner.installOnce(flags = { AgentCoreFeatureFlags.DISABLED })

        assertNotNull(second)
        // Distintas instancias.
        kotlin.test.assertNotSame(first, second)
        assertEquals(2, installer.installCalls)
    }

    @Test
    fun `productionDefaultFlags returns DISABLED so APK starts in legacy mode`() {
        val flags = RuntimeGraphOwner.productionDefaultFlags()
        assertEquals(false, flags.typedConfirmationEnabled)
        assertEquals(false, flags.accessibilityRuntimeContextEnabled)
    }

    @Test
    fun `debugSmokeTestFlags enables awareness layers without dangerous features`() {
        val flags = RuntimeGraphOwner.debugSmokeTestFlags()

        // ON: capas seguras de awareness y bridge tipado.
        assertEquals(true, flags.typedConfirmationEnabled)
        assertEquals(true, flags.accessibilityRuntimeContextEnabled)
        assertEquals(true, flags.screenChangeAwarenessEnabled)

        // OFF: capas que ejecutan acciones reales o envían contenido al cloud.
        assertEquals(false, flags.llmFallbackEnabled)
        assertEquals(false, flags.genericAppExecutionEnabled)
    }
}
