package com.ojoclaro.android.agent.core.screen

import com.ojoclaro.android.agent.core.AgentCoreFeatureFlags
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class ScreenContextCollectorTest {

    private val now = 1_700_000_000_000L

    private fun raw(text: String = "Hola", pkg: String? = "com.whatsapp") = ScreenSnapshot(
        packageName = pkg,
        text = text,
        elements = emptyList(),
        capturedAtMillis = now
    )

    private class FakeProvider(var next: ScreenSnapshot? = null, var failures: Int = 0) :
        ScreenContextProvider {
        var calls: Int = 0
        override fun current(): ScreenSnapshot? {
            calls += 1
            if (failures > 0) {
                failures -= 1
                throw IllegalStateException("provider failure for test")
            }
            return next
        }
    }

    @Test
    fun `flag off returns Skipped and clears repository`() {
        val provider = FakeProvider(next = raw())
        val repo = ScreenContextRepository(
            flags = { AgentCoreFeatureFlags(accessibilityRuntimeContextEnabled = true) }
        )
        // Pre-poblamos el repo para verificar que la limpieza ocurre.
        repo.publish(StructuredScreenSnapshotBuilder().build(raw()))

        val collector = ScreenContextCollector(
            provider = provider,
            repository = repo,
            flags = { AgentCoreFeatureFlags.DISABLED },
            clock = { now }
        )

        val outcome = collector.collect()

        val skipped = outcome as? CollectOutcome.Skipped ?: fail("expected Skipped, got $outcome")
        assertEquals("flag_disabled", skipped.reason)
        assertNull(repo.current())
        assertEquals(0, provider.calls)
    }

    @Test
    fun `provider null with flag on yields NoSnapshot and null in repo`() {
        val provider = FakeProvider(next = null)
        val repo = ScreenContextRepository(
            flags = { AgentCoreFeatureFlags(accessibilityRuntimeContextEnabled = true) }
        )
        val collector = ScreenContextCollector(
            provider = provider,
            repository = repo,
            flags = { AgentCoreFeatureFlags(accessibilityRuntimeContextEnabled = true) },
            clock = { now }
        )

        val outcome = collector.collect()

        assertTrue(outcome is CollectOutcome.NoSnapshot, "got $outcome")
        assertNull(repo.current())
    }

    @Test
    fun `successful collect publishes structured snapshot`() {
        val provider = FakeProvider(next = raw(text = "Hola Sofi", pkg = "com.whatsapp"))
        val repo = ScreenContextRepository(
            flags = { AgentCoreFeatureFlags(accessibilityRuntimeContextEnabled = true) }
        )
        val collector = ScreenContextCollector(
            provider = provider,
            repository = repo,
            flags = { AgentCoreFeatureFlags(accessibilityRuntimeContextEnabled = true) },
            clock = { now }
        )

        val outcome = collector.collect()

        val published = outcome as? CollectOutcome.Published
            ?: fail("expected Published, got $outcome")
        assertEquals("WhatsApp", published.snapshot.appLabel)
        assertEquals("WhatsApp", repo.current()?.appLabel)
    }

    @Test
    fun `throttle returns cached snapshot on rapid second call`() {
        val provider = FakeProvider(next = raw())
        val repo = ScreenContextRepository(
            flags = { AgentCoreFeatureFlags(accessibilityRuntimeContextEnabled = true) }
        )
        var clockMillis = now
        val collector = ScreenContextCollector(
            provider = provider,
            repository = repo,
            flags = { AgentCoreFeatureFlags(accessibilityRuntimeContextEnabled = true) },
            clock = { clockMillis },
            throttleMillis = 500L
        )

        collector.collect()
        assertEquals(1, provider.calls)

        // 100ms después, debe devolver throttled sin re-leer del provider.
        clockMillis = now + 100
        val outcome = collector.collect()
        assertTrue(outcome is CollectOutcome.Throttled, "got $outcome")
        assertEquals(1, provider.calls)
    }

    @Test
    fun `collect after throttle window re-reads provider`() {
        val provider = FakeProvider(next = raw())
        val repo = ScreenContextRepository(
            flags = { AgentCoreFeatureFlags(accessibilityRuntimeContextEnabled = true) }
        )
        var clockMillis = now
        val collector = ScreenContextCollector(
            provider = provider,
            repository = repo,
            flags = { AgentCoreFeatureFlags(accessibilityRuntimeContextEnabled = true) },
            clock = { clockMillis },
            throttleMillis = 500L
        )

        collector.collect()
        clockMillis = now + 600
        collector.collect()

        assertEquals(2, provider.calls)
    }

    @Test
    fun `provider exception is caught and treated as no snapshot`() {
        val provider = FakeProvider(next = raw(), failures = 1)
        val repo = ScreenContextRepository(
            flags = { AgentCoreFeatureFlags(accessibilityRuntimeContextEnabled = true) }
        )
        val collector = ScreenContextCollector(
            provider = provider,
            repository = repo,
            flags = { AgentCoreFeatureFlags(accessibilityRuntimeContextEnabled = true) },
            clock = { now }
        )

        val outcome = collector.collect()

        assertTrue(outcome is CollectOutcome.NoSnapshot, "got $outcome")
    }

    @Test
    fun `reset clears repository and resets throttle`() {
        val provider = FakeProvider(next = raw())
        val repo = ScreenContextRepository(
            flags = { AgentCoreFeatureFlags(accessibilityRuntimeContextEnabled = true) }
        )
        var clockMillis = now
        val collector = ScreenContextCollector(
            provider = provider,
            repository = repo,
            flags = { AgentCoreFeatureFlags(accessibilityRuntimeContextEnabled = true) },
            clock = { clockMillis },
            throttleMillis = 1000L
        )

        collector.collect()
        assertTrue(repo.current() != null)

        collector.reset()
        assertNull(repo.current())

        // Después del reset, una nueva collect debe NO ser throttled.
        clockMillis = now + 100
        val outcome = collector.collect()
        assertTrue(outcome is CollectOutcome.Published, "got $outcome")
    }
}
