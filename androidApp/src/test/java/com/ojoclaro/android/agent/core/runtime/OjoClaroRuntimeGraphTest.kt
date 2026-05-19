package com.ojoclaro.android.agent.core.runtime

import com.ojoclaro.android.agent.core.AgentCoreFeatureFlags
import com.ojoclaro.android.agent.core.screen.AccessibilitySnapshotEventRouter
import com.ojoclaro.android.agent.core.screen.ScreenContextProvider
import com.ojoclaro.android.agent.core.screen.ScreenSignals
import com.ojoclaro.android.agent.core.screen.ScreenSnapshot
import com.ojoclaro.android.agent.core.screen.StructuredScreenSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class OjoClaroRuntimeGraphTest {

    private val now = 1_700_000_000_000L

    private class FakeProvider : ScreenContextProvider {
        var nextSnapshot: ScreenSnapshot? = null
        override fun current(): ScreenSnapshot? = nextSnapshot
    }

    private class FakeInstaller {
        var registeredRouter: AccessibilitySnapshotEventRouter? = null
        var installCalls: Int = 0
        var nullCalls: Int = 0
        operator fun invoke(router: AccessibilitySnapshotEventRouter?) {
            if (router == null) nullCalls += 1 else installCalls += 1
            registeredRouter = router
        }
    }

    private fun build(
        flags: AgentCoreFeatureFlags = AgentCoreFeatureFlags(
            typedConfirmationEnabled = true,
            accessibilityRuntimeContextEnabled = true
        )
    ): Triple<OjoClaroRuntimeGraph, FakeProvider, FakeInstaller> {
        val provider = FakeProvider()
        val installer = FakeInstaller()
        val graph = OjoClaroRuntimeGraph.createForTesting(
            flags = { flags },
            provider = provider,
            routerInstaller = { installer(it) }
        )
        return Triple(graph, provider, installer)
    }

    @Test
    fun `before install no router is registered`() {
        val (_, _, installer) = build()

        assertNull(installer.registeredRouter)
        assertEquals(0, installer.installCalls)
    }

    @Test
    fun `install registers router exactly once`() {
        val (graph, _, installer) = build()

        graph.install()
        graph.install() // segundo call idempotente

        assertEquals(1, installer.installCalls)
        assertSame(graph.snapshotRouter, installer.registeredRouter)
        assertTrue(graph.isInstalled())
    }

    @Test
    fun `tearDown unregisters router and clears state`() {
        val (graph, _, installer) = build()
        graph.install()
        graph.screenRepository.publish(
            StructuredScreenSnapshot(
                packageName = "com.whatsapp",
                appLabel = "WhatsApp",
                capturedAtMillis = now,
                redactedTextLines = listOf("Sofi"),
                buttons = emptyList(),
                editableFields = emptyList(),
                focusedLabel = "Sofi",
                totalNodes = 1,
                signals = ScreenSignals(isMessagingApp = true),
                warnings = emptyList(),
                isLimited = false
            )
        )
        graph.dispatchController.dispatch("mandale a sofi que estoy llegando")
        assertTrue(graph.screenRepository.current() != null)
        assertTrue(graph.bridge.currentPending() != null)

        graph.tearDown()

        assertEquals(1, installer.nullCalls)
        assertNull(graph.screenRepository.current())
        assertNull(graph.bridge.currentPending())
        assertFalse(graph.isInstalled())
    }

    @Test
    fun `tearDown without prior install is safe`() {
        val (graph, _, installer) = build()

        graph.tearDown()

        assertEquals(0, installer.nullCalls)
        assertEquals(0, installer.installCalls)
    }

    @Test
    fun `flags off still allows graph construction without leaks`() {
        val (graph, _, installer) = build(flags = AgentCoreFeatureFlags.DISABLED)

        graph.install()

        // Se registra el router igual: el router internamente respeta el flag.
        assertEquals(1, installer.installCalls)
        // Pero el repository no debe tener nada porque el flag bloquea publish.
        assertNull(graph.screenRepository.current())
    }

    @Test
    fun `dispatch controller is exposed and usable`() {
        val (graph, _, _) = build()
        graph.install()

        val outcome = graph.dispatchController.dispatch("repeti")

        assertTrue(outcome is BridgeDispatchOutcome.Handled)
    }

    @Test
    fun `voice coordinator is exposed by the graph`() {
        val (graph, _, _) = build()

        // No es nulo; lo expone para que el HomeViewModel/HomeScreen lo inyecten.
        assertSame(graph.voiceCoordinator, graph.voiceCoordinator)
    }

    @Test
    fun `voice coordinator survives recompositions style retrievals`() {
        val (graph, _, _) = build()
        graph.install()

        val a = graph.voiceCoordinator
        val b = graph.voiceCoordinator
        val c = graph.voiceCoordinator

        // El graph mantiene la misma instancia process-scope, sin recrearla.
        assertSame(a, b)
        assertSame(a, c)
    }

    @Test
    fun `tearDown resets voice coordinator dedup memory`() {
        val (graph, _, _) = build()
        graph.install()

        val pendingOutcome = BridgeDispatchOutcome.Handled(
            speakText = "Esta acción requiere confirmación. Decime confirmar o cancelar.",
            pendingPrompt = "Vas a abrir WhatsApp. ¿Confirmás?",
            hasPending = true,
            kind = BridgeDispatchKind.PENDING
        )
        // Primer route: speak fresh.
        val first = graph.voiceCoordinator.route(pendingOutcome)
        assertTrue(first is com.ojoclaro.android.voice.BridgeVoiceRoute.Speak)
        // Segundo route inmediato: dedup → suppress.
        val second = graph.voiceCoordinator.route(pendingOutcome)
        assertTrue(second is com.ojoclaro.android.voice.BridgeVoiceRoute.Suppress)

        graph.tearDown()

        // Después de tearDown la memoria se limpia: vuelve a hablar.
        val afterTeardown = graph.voiceCoordinator.route(pendingOutcome)
        assertTrue(
            afterTeardown is com.ojoclaro.android.voice.BridgeVoiceRoute.Speak,
            "Expected Speak after tearDown, got $afterTeardown"
        )
    }

    @Test
    fun `dispatch falls back to legacy when typedConfirmation flag is off`() {
        val (graph, _, _) = build(
            flags = AgentCoreFeatureFlags(
                typedConfirmationEnabled = false,
                accessibilityRuntimeContextEnabled = true
            )
        )

        val outcome = graph.dispatchController.dispatch("mandale a sofi que ya salgo")

        assertTrue(outcome is BridgeDispatchOutcome.FallbackToLegacy)
    }
}
