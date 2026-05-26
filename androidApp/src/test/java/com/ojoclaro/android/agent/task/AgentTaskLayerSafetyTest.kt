package com.ojoclaro.android.agent.task

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Paquete 6H -- guarda de seguridad consolidada de toda la capa de tareas.
 *
 * Escanea todo el arbol `agent/task` (planner, memory, orchestrator,
 * observer, follow-up, action, execution, capability) y garantiza que ningun
 * archivo llame APIs de ejecucion de accesibilidad. Es la red de seguridad
 * que respalda el smoke harness: el codigo del agente nunca toca botones.
 */
class AgentTaskLayerSafetyTest {

    private val forbiddenApis = listOf(
        "performClick",
        "dispatchGesture",
        "performGlobalAction"
    )

    @Test
    fun taskLayerSourceNeverCallsExecutionApis() {
        val files = taskLayerSourceFiles()
        assertTrue(files.size >= 10, "Expected the full task layer, found ${files.size} files")
        files.forEach { file ->
            val content = file.readText()
            forbiddenApis.forEach { api ->
                assertFalse(
                    content.contains("$api("),
                    "Forbidden execution API call '$api(' found in ${file.path}"
                )
            }
        }
    }

    private fun taskLayerSourceFiles(): List<File> {
        val candidates = listOf(
            File("src/main/java/com/ojoclaro/android/agent/task"),
            File("androidApp/src/main/java/com/ojoclaro/android/agent/task")
        )
        val root = candidates.firstOrNull { it.isDirectory }
            ?: error("Task layer source dir not found. Working dir: ${File(".").absolutePath}")
        return root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()
    }
}
