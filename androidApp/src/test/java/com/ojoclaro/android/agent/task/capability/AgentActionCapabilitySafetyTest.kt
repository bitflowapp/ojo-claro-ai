package com.ojoclaro.android.agent.task.capability

import com.ojoclaro.android.agent.task.AgentTaskPlanner
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Paquete 6G -- guardas de seguridad estaticas de la capa de capacidades.
 *
 * La capa de capacidades es pura: clasifica, no ejecuta. Nunca debe llamar
 * APIs de ejecucion de accesibilidad ni afirmar acciones completadas.
 */
class AgentActionCapabilitySafetyTest {

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
    fun capabilityLayerSourceNeverCallsExecutionApis() {
        capabilitySourceFiles().forEach { file ->
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
    fun capabilityLayerSourceNeverClaimsCompletedSensitiveActions() {
        capabilitySourceFiles().forEach { file ->
            val normalized = AgentTaskPlanner.normalize(file.readText())
            forbiddenClaims.forEach { claim ->
                assertFalse(
                    normalized.contains(claim),
                    "Forbidden completion claim '$claim' found in ${file.name}"
                )
            }
        }
    }

    private fun capabilitySourceFiles(): List<File> {
        val candidates = listOf(
            File("src/main/java/com/ojoclaro/android/agent/task/capability"),
            File("androidApp/src/main/java/com/ojoclaro/android/agent/task/capability")
        )
        val dir = candidates.firstOrNull { it.isDirectory }
            ?: error(
                "Capability source dir not found. " +
                    "Working dir: ${File(".").absolutePath}"
            )
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".kt") }?.toList()
            ?: emptyList()
        assertTrue(files.isNotEmpty(), "Expected Kotlin sources in ${dir.absolutePath}")
        return files
    }
}
