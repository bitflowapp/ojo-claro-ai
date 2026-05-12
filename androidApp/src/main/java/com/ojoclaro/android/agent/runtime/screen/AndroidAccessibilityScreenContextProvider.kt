package com.ojoclaro.android.agent.runtime.screen

import com.ojoclaro.android.accessibility.AccessibilityNodeSummary
import com.ojoclaro.android.accessibility.OjoClaroAccessibilityService
import com.ojoclaro.android.agent.core.screen.ScreenContextProvider
import com.ojoclaro.android.agent.core.screen.ScreenSnapshot
import com.ojoclaro.android.performance.RobotLoopInstrumentation
import com.ojoclaro.android.performance.RobotLoopLogResult
import com.ojoclaro.android.performance.RobotLoopLogStage
import com.ojoclaro.android.performance.RobotLoopMetric
import com.ojoclaro.android.performance.RobotLoopSafeLogEvent

/**
 * Provider que arma un [ScreenSnapshot] desde el AccessibilityService de
 * Ojo Claro.
 *
 * Reglas:
 *  - Si el servicio no devuelve nada útil, retorna null y el use case decide.
 *  - NO almacena el snapshot en ningún lado: cada llamada lee de nuevo.
 *  - Toma el package name (cuando está disponible) para que el resumidor
 *    pueda clasificar pantallas bancarias por paquete además de por texto.
 *  - Extrae elementos estructurados (Structured Screen Snapshot v1) a partir
 *    de [AccessibilityNodeSummary] que expone el servicio. El mapeo y filtro
 *    de sensibles vive en [AccessibilityNodeMapper] — puro Kotlin, testeable.
 *
 * Inyectable: los lambdas se pueden sustituir en tests para no depender de
 * la JVM Android.
 */
class AndroidAccessibilityScreenContextProvider(
    private val readText: () -> String = { OjoClaroAccessibilityService.readVisibleText() },
    private val readPackageName: () -> String? = { OjoClaroAccessibilityService.readActivePackageName() },
    private val readNodeSummaries: () -> List<AccessibilityNodeSummary> = {
        OjoClaroAccessibilityService.readVisibleNodeSummaries()
    },
    private val clock: () -> Long = { System.currentTimeMillis() }
) : ScreenContextProvider {

    override fun current(): ScreenSnapshot? {
        val start = System.nanoTime()
        var snapshot: ScreenSnapshot? = null
        try {
            val text = runCatching { readText() }.getOrDefault("").trim()
            val pkg = runCatching { readPackageName() }.getOrNull()
                ?.takeIf { it.isNotBlank() }
            val summaries = runCatching { readNodeSummaries() }.getOrDefault(emptyList())
            val elements = AccessibilityNodeMapper.map(summaries)

            if (text.isBlank() && pkg.isNullOrBlank() && elements.isEmpty()) return null

            snapshot = ScreenSnapshot(
                packageName = pkg,
                text = text,
                elements = elements,
                capturedAtMillis = clock()
            )
            return snapshot
        } finally {
            val elapsedNanos = (System.nanoTime() - start).coerceAtLeast(0L)
            RobotLoopInstrumentation.recordElapsedNanos(
                metric = RobotLoopMetric.SCREEN_SNAPSHOT,
                elapsedNanos = elapsedNanos
            )
            val stats = snapshot.safeStats()
            RobotLoopInstrumentation.recordSafeLog(
                RobotLoopSafeLogEvent(
                    stage = RobotLoopLogStage.STRUCTURED_SCREEN_SNAPSHOT,
                    result = if (snapshot == null) RobotLoopLogResult.NO_SNAPSHOT else RobotLoopLogResult.OK,
                    durationMillis = elapsedNanos / 1_000_000L,
                    packageName = stats.packageName,
                    elementCount = stats.elementCount,
                    buttonCount = stats.buttonCount,
                    fieldCount = stats.fieldCount
                )
            )
        }
    }
}
