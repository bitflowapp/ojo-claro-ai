package com.ojoclaro.android.smoke

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ojoclaro.android.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Paquete 6H -- Smoke harness instrumentado.
 *
 * Verifica que la app abre y el runtime no crashea: lanzar [MainActivity]
 * ejercita `onCreate`, que instala el runtime graph process-scope con
 * `RuntimeGraphOwner.debugSmokeTestFlags()` en builds debug.
 *
 * No toca apps de terceros, no ejecuta acciones, no usa el microfono.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityLaunchTest {

    @Test
    fun mainActivityLaunchesAndReachesResumed() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
            scenario.onActivity { activity -> assertNotNull(activity) }
        }
    }

    @Test
    fun mainActivitySurvivesRecreate() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.recreate()
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }
}
