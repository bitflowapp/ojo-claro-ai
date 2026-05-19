package com.ojoclaro.android.agent.core.screen

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Test de garantía: ningún archivo introducido por el paquete 4A debe
 * referenciar `performClick`, `dispatchGesture`, `performAction` ni cualquier
 * API que provoque acción de accesibilidad.
 *
 * El test es agresivo a propósito: si un futuro cambio en estas piezas mete
 * por error una llamada a performClick, falla acá antes de llegar al APK.
 */
class AccessibilityNoActionGuardTest {

    @Test
    fun `package 4A files do not invoke click or gesture apis`() {
        // Buscamos INVOCACIONES (con paréntesis), no menciones en comentarios.
        // Los docstrings explican que NO se usan estas APIs — eso es OK.
        val forbiddenInvocations = listOf(
            Regex("\\bperformClick\\s*\\("),
            Regex("\\bperformLongClick\\s*\\("),
            Regex("\\bdispatchGesture\\s*\\("),
            Regex("\\bperformGlobalAction\\s*\\(")
        )
        val filesToCheck = listOf(
            "AccessibilityEventClassifier.kt",
            "ScreenContextCollectionPolicy.kt",
            "AccessibilitySnapshotEventRouter.kt"
        )
        val relativePath = "src/main/java/com/ojoclaro/android/agent/core/screen"
        val candidates = listOf(
            File(relativePath),
            File("androidApp/$relativePath"),
            File(System.getProperty("user.dir") ?: ".", relativePath),
            File(System.getProperty("user.dir") ?: ".", "androidApp/$relativePath")
        )
        val baseDir = candidates.firstOrNull { it.exists() }
            ?: fail("could not locate screen source dir; tried: ${candidates.map { it.absolutePath }}")

        val offenders = mutableListOf<String>()
        for (name in filesToCheck) {
            val file = File(baseDir, name)
            if (!file.exists()) {
                fail("expected $name to exist under ${baseDir.absolutePath}")
            }
            val content = file.readText()
            for (regex in forbiddenInvocations) {
                if (regex.containsMatchIn(content)) {
                    offenders.add("$name invokes forbidden API matching '${regex.pattern}'")
                }
            }
        }

        assertTrue(offenders.isEmpty(), "forbidden invocations detected: $offenders")
    }
}
