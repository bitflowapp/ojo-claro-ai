package com.ojoclaro.android.agent.core.screen

import android.view.accessibility.AccessibilityEvent
import com.ojoclaro.android.agent.core.AgentCoreFeatureFlags
import com.ojoclaro.android.performance.RobotLoopLogResult
import com.ojoclaro.android.performance.RobotLoopLogStage
import com.ojoclaro.android.performance.RobotLoopSafeLogEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class AccessibilitySnapshotEventRouterTest {

    private val now = 1_700_000_000_000L

    private class FakeProvider(var next: ScreenSnapshot? = null, var throws: Boolean = false) :
        ScreenContextProvider {
        var calls: Int = 0
        override fun current(): ScreenSnapshot? {
            calls += 1
            if (throws) throw IllegalStateException("provider failure")
            return next
        }
    }

    private fun buildEnv(
        flag: Boolean = true,
        providerSnapshot: ScreenSnapshot? = ScreenSnapshot(
            packageName = "com.whatsapp",
            text = "Hola",
            elements = emptyList(),
            capturedAtMillis = now
        ),
        providerThrows: Boolean = false
    ): Env {
        val flags = AgentCoreFeatureFlags(accessibilityRuntimeContextEnabled = flag)
        val provider = FakeProvider(next = providerSnapshot, throws = providerThrows)
        val repo = ScreenContextRepository(flags = { flags })
        val collector = ScreenContextCollector(
            provider = provider,
            repository = repo,
            flags = { flags },
            clock = { now },
            throttleMillis = 0L // sin throttle para que tests sean deterministas
        )
        val logged = mutableListOf<RobotLoopSafeLogEvent>()
        val router = AccessibilitySnapshotEventRouter(
            collector = collector,
            flags = { flags },
            instrumentation = { logged += it }
        )
        return Env(router, repo, provider, logged)
    }

    private data class Env(
        val router: AccessibilitySnapshotEventRouter,
        val repository: ScreenContextRepository,
        val provider: FakeProvider,
        val logged: MutableList<RobotLoopSafeLogEvent>
    )

    @Test
    fun `flag off does not collect on relevant event`() {
        val env = buildEnv(flag = false)

        val action = env.router.onEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)

        val skipped = action as? RouterAction.Skipped
            ?: fail("expected Skipped, got $action")
        assertEquals("flag_disabled", skipped.reason)
        assertEquals(0, env.provider.calls)
        assertNull(env.repository.current())
        // No queremos spamear logs por evento off-flag.
        assertTrue(env.logged.isEmpty(), "expected no log entries, got ${env.logged.size}")
    }

    @Test
    fun `irrelevant event does not collect`() {
        val env = buildEnv(flag = true)

        val action = env.router.onEvent(AccessibilityEvent.TYPE_VIEW_CLICKED)

        val skipped = action as? RouterAction.Skipped
            ?: fail("expected Skipped, got $action")
        assertTrue(skipped.reason.startsWith("event_irrelevant"))
        assertEquals(0, env.provider.calls)
        assertNull(env.repository.current())
    }

    @Test
    fun `window state changed collects and publishes snapshot`() {
        val env = buildEnv(flag = true)

        val action = env.router.onEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)

        val collected = action as? RouterAction.Collected
            ?: fail("expected Collected, got $action")
        assertTrue(collected.outcome is CollectOutcome.Published, "got ${collected.outcome}")
        assertEquals(1, env.provider.calls)
        assertEquals("com.whatsapp", env.repository.current()?.packageName)
        assertEquals(1, env.logged.size)
        assertEquals(RobotLoopLogStage.STRUCTURED_SCREEN_SNAPSHOT, env.logged.first().stage)
        assertEquals(RobotLoopLogResult.OK, env.logged.first().result)
        assertEquals("com.whatsapp", env.logged.first().packageName)
    }

    @Test
    fun `window content changed also collects`() {
        val env = buildEnv(flag = true)

        val action = env.router.onEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)

        assertTrue(action is RouterAction.Collected, "got $action")
        assertEquals(1, env.provider.calls)
    }

    @Test
    fun `provider returning null yields NoSnapshot and clears repo`() {
        val env = buildEnv(flag = true, providerSnapshot = null)

        val action = env.router.onEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)

        val collected = action as? RouterAction.Collected
            ?: fail("expected Collected, got $action")
        assertTrue(collected.outcome is CollectOutcome.NoSnapshot, "got ${collected.outcome}")
        assertNull(env.repository.current())
        assertEquals(RobotLoopLogResult.NO_SNAPSHOT, env.logged.last().result)
    }

    @Test
    fun `provider throwing does not crash and yields safe outcome`() {
        val env = buildEnv(flag = true, providerThrows = true)

        val action = env.router.onEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)

        // El collector ya captura la excepción y devuelve NoSnapshot.
        val collected = action as? RouterAction.Collected
            ?: fail("expected Collected (with safe outcome), got $action")
        assertTrue(
            collected.outcome is CollectOutcome.NoSnapshot ||
                collected.outcome is CollectOutcome.Skipped,
            "got ${collected.outcome}"
        )
        // Si por alguna razón colectara una excepción del propio router, igual
        // no debe propagarla — el test ya termina sin crash, eso es la
        // garantía mínima.
    }

    @Test
    fun `onServiceDisconnected clears repository and logs reset`() {
        val env = buildEnv(flag = true)
        env.router.onEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
        assertTrue(env.repository.current() != null)

        val action = env.router.onServiceDisconnected()

        assertEquals(RouterAction.Cleared, action)
        assertNull(env.repository.current())
        assertEquals(RobotLoopLogResult.RESET, env.logged.last().result)
    }

    @Test
    fun `safe log never includes text or pii beyond sanitized package`() {
        val env = buildEnv(flag = true)
        env.router.onEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)

        val entry = env.logged.first()
        // Estos son los únicos campos que el router setea explícitamente.
        // Verificamos que nada de texto crudo se filtre.
        assertEquals("com.whatsapp", entry.packageName)
        assertNull(entry.robotState)
        assertNull(entry.handler)
        // RobotLoopInstrumentation.sanitizePackageName aplica al sink real,
        // acá testeamos que el router no pasa nada raro al sink.
    }

    @Test
    fun `disconnected without prior collect is safe`() {
        val env = buildEnv(flag = true)

        val action = env.router.onServiceDisconnected()

        assertEquals(RouterAction.Cleared, action)
        assertNull(env.repository.current())
    }

    @Test
    fun `flag flipped from on to off does not crash`() {
        var flagOn = true
        val flags = { AgentCoreFeatureFlags(accessibilityRuntimeContextEnabled = flagOn) }
        val provider = FakeProvider(
            next = ScreenSnapshot(
                packageName = "com.example",
                text = "Hola",
                elements = emptyList(),
                capturedAtMillis = now
            )
        )
        val repo = ScreenContextRepository(flags = flags)
        val collector = ScreenContextCollector(
            provider = provider,
            repository = repo,
            flags = flags,
            clock = { now },
            throttleMillis = 0L
        )
        val router = AccessibilitySnapshotEventRouter(
            collector = collector,
            flags = flags
        )

        router.onEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
        assertTrue(repo.current() != null)

        flagOn = false
        val action = router.onEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
        assertTrue(action is RouterAction.Skipped)
    }
}
