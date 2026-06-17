package com.ojoclaro.android.agent.core.screen

import com.ojoclaro.android.agent.core.AgentCoreFeatureFlags
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScreenChangeAwarenessCoordinatorTest {

    private var now: Long = 1_000L

    @Test
    fun flagOffMakesCoordinatorPassThroughWithoutAnnouncements() {
        val coordinator = ScreenChangeAwarenessCoordinator(
            engine = ScreenChangeAwarenessEngine(clock = { now }),
            flags = { AgentCoreFeatureFlags.DISABLED },
            clock = { now }
        )

        coordinator.onSnapshot(snapshot(packageName = "com.android.launcher"))
        val ann = coordinator.onSnapshot(
            snapshot(packageName = "com.whatsapp", appLabel = "WhatsApp")
        )

        // Flag OFF: el coordinator no evalúa anuncios.
        assertEquals(ScreenChangeEvent.NONE, ann.event)
        assertFalse(ann.shouldAnnounce)
        assertTrue(coordinator.announcements.replayCache.isEmpty())
    }

    @Test
    fun flagOnEvaluatesAppChange() {
        val coordinator = ScreenChangeAwarenessCoordinator(
            engine = ScreenChangeAwarenessEngine(clock = { now }),
            flags = {
                AgentCoreFeatureFlags(
                    accessibilityRuntimeContextEnabled = true,
                    screenChangeAwarenessEnabled = true
                )
            },
            clock = { now }
        )

        coordinator.onSnapshot(snapshot(packageName = "com.android.launcher"))
        now += 100L
        val ann = coordinator.onSnapshot(
            snapshot(packageName = "com.whatsapp", appLabel = "WhatsApp")
        )

        assertEquals(ScreenChangeEvent.APP_CHANGED, ann.event)
        assertTrue(ann.spokenText.contains("WhatsApp", ignoreCase = true))
    }

    @Test
    fun flagOnAndSafetyTransitionAnnouncesCritical() {
        val coordinator = ScreenChangeAwarenessCoordinator(
            engine = ScreenChangeAwarenessEngine(clock = { now }),
            flags = {
                AgentCoreFeatureFlags(
                    accessibilityRuntimeContextEnabled = true,
                    screenChangeAwarenessEnabled = true
                )
            },
            clock = { now }
        )

        coordinator.onSnapshot(snapshot(packageName = "com.example"))
        now += 100L
        val ann = coordinator.onSnapshot(
            snapshot(
                packageName = "com.example",
                signals = ScreenSignals(hasPasswordField = true)
            )
        )

        assertEquals(ScreenChangeEvent.PASSWORD_SCREEN_ENTERED, ann.event)
        assertEquals(ScreenChangeImportance.CRITICAL, ann.importance)
        assertTrue(ann.shouldAnnounce)
    }

    @Test
    fun coordinatorResetClearsInternalState() {
        val flagsOn = AgentCoreFeatureFlags(
            accessibilityRuntimeContextEnabled = true,
            screenChangeAwarenessEnabled = true
        )
        val coordinator = ScreenChangeAwarenessCoordinator(
            engine = ScreenChangeAwarenessEngine(clock = { now }),
            flags = { flagsOn },
            clock = { now }
        )

        coordinator.onSnapshot(snapshot(packageName = "com.whatsapp", appLabel = "WhatsApp"))
        now += 100L
        coordinator.reset()

        // Tras reset, una pantalla con WhatsApp llega de nuevo y se anuncia
        // como si fuera la primera vez: prev=null + content normal = NONE,
        // a menos que sea hot zone. Demostramos que el snapshot anterior se
        // perdió: si NO se hubiera limpiado, el siguiente onSnapshot vería
        // mismo package y devolvería NONE; si sí se limpió, vería prev=null
        // y devolvería NONE también (no hot zone) — la diferencia se prueba
        // mejor con un app change.
        coordinator.onSnapshot(snapshot(packageName = "com.android.launcher"))
        now += 100L
        val ann = coordinator.onSnapshot(
            snapshot(packageName = "com.whatsapp", appLabel = "WhatsApp")
        )
        assertEquals(ScreenChangeEvent.APP_CHANGED, ann.event)
        assertTrue(ann.shouldAnnounce, "After reset the cooldown memory should also be cleared")
    }

    @Test
    fun consecutiveSamePackageDoesNotReAnnounce() {
        val coordinator = ScreenChangeAwarenessCoordinator(
            engine = ScreenChangeAwarenessEngine(clock = { now }),
            flags = {
                AgentCoreFeatureFlags(
                    accessibilityRuntimeContextEnabled = true,
                    screenChangeAwarenessEnabled = true
                )
            },
            clock = { now }
        )

        coordinator.onSnapshot(snapshot(packageName = "com.android.launcher"))
        now += 100L
        val first = coordinator.onSnapshot(
            snapshot(packageName = "com.whatsapp", appLabel = "WhatsApp")
        )
        assertTrue(first.shouldAnnounce)

        // El usuario vuelve al launcher y regresa a WhatsApp.
        now += 100L
        coordinator.onSnapshot(snapshot(packageName = "com.android.launcher"))
        now += 100L
        val second = coordinator.onSnapshot(
            snapshot(packageName = "com.whatsapp", appLabel = "WhatsApp")
        )

        // El engine no debe re-anunciar APP_CHANGED al mismo packageName.
        assertFalse(
            second.shouldAnnounce,
            "Returning to same packageName should not re-announce, got '${second.spokenText}'"
        )
    }

    private fun snapshot(
        packageName: String?,
        appLabel: String? = null,
        buttons: List<String> = emptyList(),
        editableFields: List<String> = emptyList(),
        signals: ScreenSignals = ScreenSignals.EMPTY,
        capturedAtMillis: Long = 1_000L
    ): StructuredScreenSnapshot = StructuredScreenSnapshot(
        packageName = packageName,
        appLabel = appLabel,
        capturedAtMillis = capturedAtMillis,
        redactedTextLines = emptyList(),
        buttons = buttons,
        editableFields = editableFields,
        focusedLabel = null,
        totalNodes = buttons.size + editableFields.size,
        signals = signals,
        warnings = emptyList(),
        isLimited = false
    )
}
