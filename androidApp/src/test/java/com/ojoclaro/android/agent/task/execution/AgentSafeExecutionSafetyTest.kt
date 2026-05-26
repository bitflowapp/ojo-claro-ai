package com.ojoclaro.android.agent.task.execution

import com.ojoclaro.android.agent.task.AgentTaskPlanner
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Paquete 6F -- guardas de seguridad estaticas del Safe Execution Gate.
 *
 * La capa de ejecucion NUNCA debe llamar APIs de ejecucion de accesibilidad
 * ([performClick] / [dispatchGesture] / [performGlobalAction]) ni afirmar que
 * una accion sensible ya se completo.
 */
class AgentSafeExecutionSafetyTest {

    private val forbiddenApis = listOf(
        "performClick",
        "dispatchGesture",
        "performGlobalAction"
    )

    private val forbiddenClaims = listOf(
        "mensaje " + "enviado",
        "audio " + "enviado",
        "taxi " + "pedido",
        "viaje " + "solicitado"
    )

    @Test
    fun executionLayerSourceNeverCallsExecutionApis() {
        executionSourceFiles().forEach { file ->
            val content = file.readText()
            forbiddenApis.forEach { api ->
                assertFalse(
                    content.contains("$api("),
                    "Forbidden execution API call '$api(' found in ${file.name}"
                )
            }
        }
    }

    @Test
    fun executionLayerSourceNeverClaimsCompletedSensitiveActions() {
        executionSourceFiles().forEach { file ->
            val normalized = AgentTaskPlanner.normalize(file.readText())
            forbiddenClaims.forEach { claim ->
                assertFalse(
                    normalized.contains(claim),
                    "Forbidden completion claim '$claim' found in ${file.name}"
                )
            }
        }
    }

    private fun executionSourceFiles(): List<File> {
        val candidates = listOf(
            File("src/main/java/com/ojoclaro/android/agent/task/execution"),
            File("androidApp/src/main/java/com/ojoclaro/android/agent/task/execution")
        )
        val dir = candidates.firstOrNull { it.isDirectory }
            ?: error(
                "Safe execution source dir not found. " +
                    "Working dir: ${File(".").absolutePath}"
            )
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".kt") }?.toList()
            ?: emptyList()
        assertTrue(files.isNotEmpty(), "Expected Kotlin sources in ${dir.absolutePath}")
        return files
    }
}
