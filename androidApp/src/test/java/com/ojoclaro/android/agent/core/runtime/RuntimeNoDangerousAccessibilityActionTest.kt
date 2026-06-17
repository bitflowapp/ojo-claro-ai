package com.ojoclaro.android.agent.core.runtime

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

class RuntimeNoDangerousAccessibilityActionTest {

    @Test
    fun `main sources do not invoke accessibility action apis`() {
        val sourceDir = listOf(
            File("src/main/java"),
            File("androidApp/src/main/java")
        ).firstOrNull { it.exists() }
            ?: fail("could not locate main source dir")

        val forbiddenInvocations = listOf(
            Regex("\\bperformClick\\s*\\("),
            Regex("\\bdispatchGesture\\s*\\("),
            // Fase 2A autoriza explícitamente el BACK global (navegación segura y
            // reversible: "volver"). Cualquier OTRA acción global —HOME, RECENTS,
            // POWER_DIALOG, LOCK_SCREEN, TAKE_SCREENSHOT...— sigue prohibida en
            // sources de producción. El lookahead negativo deja pasar SOLO
            // performGlobalAction(GLOBAL_ACTION_BACK).
            Regex("\\bperformGlobalAction\\s*\\(\\s*(?!GLOBAL_ACTION_BACK\\b)")
        )
        val offenders = sourceDir
            .walkTopDown()
            .filter { it.isFile && it.extension in setOf("kt", "java") }
            .flatMap { file ->
                val text = file.readText()
                forbiddenInvocations.mapNotNull { regex ->
                    if (regex.containsMatchIn(text)) {
                        "${file.relativeTo(sourceDir).path} matches ${regex.pattern}"
                    } else {
                        null
                    }
                }
            }
            .toList()

        assertTrue(offenders.isEmpty(), "forbidden accessibility actions: $offenders")
    }
}
