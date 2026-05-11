package com.ojoclaro.android.agent.runtime.screen

import com.ojoclaro.android.accessibility.OjoClaroAccessibilityService
import com.ojoclaro.android.agent.core.screen.ScreenContextProvider
import com.ojoclaro.android.agent.core.screen.ScreenSnapshot

/**
 * Provider que arma un [ScreenSnapshot] desde el AccessibilityService de
 * Ojo Claro.
 *
 * Reglas:
 *  - Si el servicio no devuelve texto, retorna null y el use case decide.
 *  - NO almacena el snapshot en ningún lado: cada llamada lee de nuevo.
 *  - Toma el package name (cuando está disponible) para que el resumidor
 *    pueda clasificar pantallas bancarias por paquete además de por texto.
 *  - NO extrae elementos estructurados todavía: en v1 trabajamos solo con
 *    el texto plano que el servicio expone. Eso ya excluye campos password
 *    a nivel de nodo (ver OjoClaroAccessibilityService.isReadableNode).
 *
 * Inyectable: los lambdas readText/readPackageName/clock se pueden
 * sustituir en tests para no depender de la JVM Android.
 */
class AndroidAccessibilityScreenContextProvider(
    private val readText: () -> String = { OjoClaroAccessibilityService.readVisibleText() },
    private val readPackageName: () -> String? = { OjoClaroAccessibilityService.readActivePackageName() },
    private val clock: () -> Long = { System.currentTimeMillis() }
) : ScreenContextProvider {

    override fun current(): ScreenSnapshot? {
        val text = runCatching { readText() }.getOrDefault("").trim()
        val pkg = runCatching { readPackageName() }.getOrNull()
            ?.takeIf { it.isNotBlank() }

        if (text.isBlank() && pkg.isNullOrBlank()) return null

        return ScreenSnapshot(
            packageName = pkg,
            text = text,
            elements = emptyList(),
            capturedAtMillis = clock()
        )
    }
}
