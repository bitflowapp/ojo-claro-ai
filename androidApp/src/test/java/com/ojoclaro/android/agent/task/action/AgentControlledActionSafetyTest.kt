package com.ojoclaro.android.agent.task.action

import com.ojoclaro.android.agent.task.AgentTaskPlanner
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Paquete 6E -- guardas de seguridad estaticas.
 *
 * La capa de Controlled Action Proposal NO ejecuta nada. Estos tests escanean
 * el codigo fuente de la capa para garantizar que nunca aparezcan APIs de
 * ejecucion ([performClick] / [dispatchGesture] / [performGlobalAction]) y que
 * ningun texto afirme que una accion sensible ya se ejecuto.
 */
class AgentControlledActionSafetyTest {

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
    fun actionLayerSourceNeverCallsExecutionApis() {
        actionSourceFiles().forEach { file ->
            val content = file.readText()
            forbiddenApis.forEach { api ->
                // Buscamos la llamada real ("api(") -- una mencion en un
                // comentario de documentacion ("api /") es valida y deseada.
                assertFalse(
                    content.contains("$api("),
                    "Forbidden execution API call '$api(' found in ${file.name}"
                )
            }
        }
    }

    @Test
    fun actionLayerSourceNeverClaimsCompletedSensitiveActions() {
        actionSourceFiles().forEach { file ->
            val normalized = AgentTaskPlanner.normalize(file.readText())
            forbiddenClaims.forEach { claim ->
                assertFalse(
                    normalized.contains(claim),
                    "Forbidden completion claim '$claim' found in ${file.name}"
                )
            }
        }
    }

    private fun actionSourceFiles(): List<File> {
        val candidates = listOf(
            File("src/main/java/com/ojoclaro/android/agent/task/action"),
            File("androidApp/src/main/java/com/ojoclaro/android/agent/task/action")
        )
        val dir = candidates.firstOrNull { it.isDirectory }
            ?: error(
                "Controlled action source dir not found. " +
                    "Working dir: ${File(".").absolutePath}"
            )
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".kt") }?.toList()
            ?: emptyList()
        assertTrue(files.isNotEmpty(), "Expected Kotlin sources in ${dir.absolutePath}")
        return files
    }
}
