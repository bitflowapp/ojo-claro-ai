package com.ojoclaro.android.agent.apps

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class SafeAppLauncherTest {

    private val registry = AppCapabilityRegistry()

    @Test
    fun launcherDoesNotExecuteIfAppIsNotInstalled() {
        val starter = RecordingStarter()
        val launcher = SafeAppLauncher(
            resolver = FakeInstalledAppResolver(emptySet()),
            starter = starter
        )

        val result = launcher.launch(registry.findByAppName("Uber")!!)

        assertTrue(result is SafeAppLaunchResult.NotInstalled)
        assertFalse(starter.started)
    }

    @Test
    fun launcherBlocksSensitiveAppWithoutConfirmation() {
        val mercadoPago = registry.findByAppName("Mercado Pago")!!
        val starter = RecordingStarter()
        val launcher = SafeAppLauncher(
            resolver = FakeInstalledAppResolver(setOf(mercadoPago.packageName)),
            starter = starter
        )

        val result = launcher.launch(mercadoPago, userConfirmed = false)

        assertTrue(result is SafeAppLaunchResult.BlockedSensitiveApp)
        assertFalse(starter.started)
    }

    @Test
    fun launcherAllowsOpeningUberWithoutInternalActions() {
        val uber = registry.findByAppName("Uber")!!
        val starter = RecordingStarter()
        val launcher = SafeAppLauncher(
            resolver = FakeInstalledAppResolver(setOf(uber.packageName)),
            starter = starter
        )

        val result = launcher.launch(uber)

        assertTrue(result is SafeAppLaunchResult.Launched)
        assertTrue(starter.started)
        assertEquals(SafeAppLaunchIntentSpec.ACTION_MAIN, starter.lastSpec?.action)
        assertEquals(SafeAppLaunchIntentSpec.CATEGORY_LAUNCHER, starter.lastSpec?.category)
        assertEquals(AppCapabilityRegistry.UBER_PACKAGE, starter.lastSpec?.packageName)
        assertFalse(result.spokenText.contains("viaje " + "solicitado", ignoreCase = true))
    }

    @Test
    fun launcherAllowsOpeningCabifyWithoutInternalActions() {
        val cabify = registry.findByAppName("Cabify")!!
        val starter = RecordingStarter()
        val launcher = SafeAppLauncher(
            resolver = FakeInstalledAppResolver(setOf(cabify.packageName)),
            starter = starter
        )

        val result = launcher.launch(cabify)

        assertTrue(result is SafeAppLaunchResult.Launched)
        assertEquals(AppCapabilityRegistry.CABIFY_PACKAGE, starter.lastSpec?.packageName)
        assertFalse(result.spokenText.contains("taxi " + "pedido", ignoreCase = true))
    }

    @Test
    fun launcherPackageDoesNotInvokeDangerousAccessibilityActions() {
        val baseDir = listOf(
            File("src/main/java/com/ojoclaro/android/agent/apps"),
            File("androidApp/src/main/java/com/ojoclaro/android/agent/apps")
        ).firstOrNull { it.exists() }
            ?: fail("could not locate app capability source dir")
        val forbidden = listOf(
            Regex("\\bperformClick\\s*\\("),
            Regex("\\bdispatchGesture\\s*\\("),
            Regex("\\bperformGlobalAction\\s*\\(")
        )

        val offenders = baseDir.walkTopDown()
            .filter { it.isFile && it.extension in setOf("kt", "java") }
            .flatMap { file ->
                val text = file.readText()
                forbidden.mapNotNull { regex ->
                    if (regex.containsMatchIn(text)) file.name else null
                }
            }
            .toList()

        assertTrue(offenders.isEmpty(), "forbidden action invocations: $offenders")
    }

    private class RecordingStarter : SafeAppStarter {
        var started: Boolean = false
        var lastSpec: SafeAppLaunchIntentSpec? = null

        override fun start(spec: SafeAppLaunchIntentSpec): Boolean {
            started = true
            lastSpec = spec
            return true
        }
    }
}
