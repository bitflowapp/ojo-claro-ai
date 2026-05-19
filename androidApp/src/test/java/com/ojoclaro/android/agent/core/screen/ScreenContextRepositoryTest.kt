package com.ojoclaro.android.agent.core.screen

import com.ojoclaro.android.agent.core.AgentCoreFeatureFlags
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScreenContextRepositoryTest {

    private val now = 1_700_000_000_000L

    private fun makeSnapshot(): StructuredScreenSnapshot = StructuredScreenSnapshot(
        packageName = "com.whatsapp",
        appLabel = "WhatsApp",
        capturedAtMillis = now,
        redactedTextLines = listOf("Hola"),
        buttons = emptyList(),
        editableFields = emptyList(),
        focusedLabel = null,
        totalNodes = 1,
        signals = ScreenSignals(isMessagingApp = true),
        warnings = emptyList(),
        isLimited = false
    )

    @Test
    fun `initial state is null`() {
        val repo = ScreenContextRepository()
        assertNull(repo.current())
        assertNull(repo.state.value)
    }

    @Test
    fun `publish with flag off is rejected and clears state`() {
        val repo = ScreenContextRepository(
            flags = { AgentCoreFeatureFlags.DISABLED }
        )

        val published = repo.publish(makeSnapshot())

        assertFalse(published)
        assertNull(repo.current())
    }

    @Test
    fun `publish with flag on stores snapshot`() {
        val repo = ScreenContextRepository(
            flags = { AgentCoreFeatureFlags(accessibilityRuntimeContextEnabled = true) }
        )

        val published = repo.publish(makeSnapshot())

        assertTrue(published)
        assertEquals("WhatsApp", repo.current()?.appLabel)
        assertEquals("WhatsApp", repo.state.value?.appLabel)
    }

    @Test
    fun `publish null while enabled clears`() {
        val repo = ScreenContextRepository(
            flags = { AgentCoreFeatureFlags(accessibilityRuntimeContextEnabled = true) }
        )
        repo.publish(makeSnapshot())

        repo.publish(null)

        assertNull(repo.current())
    }

    @Test
    fun `clear resets state`() {
        val repo = ScreenContextRepository(
            flags = { AgentCoreFeatureFlags(accessibilityRuntimeContextEnabled = true) }
        )
        repo.publish(makeSnapshot())

        repo.clear()

        assertNull(repo.current())
    }

    @Test
    fun `flag flipping from on to off clears stale snapshot on next publish`() {
        var enabled = true
        val repo = ScreenContextRepository(
            flags = { AgentCoreFeatureFlags(accessibilityRuntimeContextEnabled = enabled) }
        )
        repo.publish(makeSnapshot())
        assertTrue(repo.current() != null)

        // El usuario apaga el flag y vuelve a llegar un publish.
        enabled = false
        repo.publish(makeSnapshot())

        assertNull(repo.current())
    }
}
