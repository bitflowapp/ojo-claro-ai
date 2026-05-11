package com.ojoclaro.android.agent.runtime.screen

import com.ojoclaro.android.accessibility.AccessibilityNodeSummary
import com.ojoclaro.android.accessibility.OjoClaroAccessibilityService
import com.ojoclaro.android.agent.core.screen.ScreenContextProvider
import com.ojoclaro.android.agent.core.screen.ScreenSnapshot

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
        val text = runCatching { readText() }.getOrDefault("").trim()
        val pkg = runCatching { readPackageName() }.getOrNull()
            ?.takeIf { it.isNotBlank() }
        val summaries = runCatching { readNodeSummaries() }.getOrDefault(emptyList())
        val elements = AccessibilityNodeMapper.map(summaries)

        if (text.isBlank() && pkg.isNullOrBlank() && elements.isEmpty()) return null

        return ScreenSnapshot(
            packageName = pkg,
            text = text,
            elements = elements,
            capturedAtMillis = clock()
        )
    }
}
