package com.ojoclaro.android.smoke

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ojoclaro.android.agent.core.runtime.OjoClaroRuntimeGraph
import com.ojoclaro.android.agent.core.runtime.RuntimeGraphOwner
import com.ojoclaro.android.agent.core.screen.DisabledScreenContextProvider
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Paquete 6H -- Smoke harness instrumentado.
 *
 * Verifica que [RuntimeGraphOwner] puede instalar y liberar el runtime graph
 * sin crashear, usando un grafo de test (provider inerte, router installer
 * no-op) para no tocar el AccessibilityService real.
 */
@RunWith(AndroidJUnit4::class)
class RuntimeGraphOwnerInstrumentedTest {

    private fun testOwner(): RuntimeGraphOwner = RuntimeGraphOwner { flags ->
        OjoClaroRuntimeGraph.createForTesting(
            flags = flags,
            provider = DisabledScreenContextProvider,
            routerInstaller = { /* no-op: smoke test no toca el servicio real */ }
        )
    }

    @Test
    fun installOnceThenReleaseDoesNotCrash() {
        val owner = testOwner()

        val graph = owner.installOnce { RuntimeGraphOwner.debugSmokeTestFlags() }

        assertNotNull(graph)
        assertSame(graph, owner.currentGraph())

        owner.release()

        assertNull(owner.currentGraph())
    }

    @Test
    fun installOnceIsIdempotent() {
        val owner = testOwner()

        val first = owner.installOnce { RuntimeGraphOwner.debugSmokeTestFlags() }
        val second = owner.installOnce { RuntimeGraphOwner.debugSmokeTestFlags() }

        assertSame(first, second)

        owner.release()
    }
}
