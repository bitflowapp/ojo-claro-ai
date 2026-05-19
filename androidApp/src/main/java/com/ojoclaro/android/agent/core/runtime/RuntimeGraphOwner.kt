package com.ojoclaro.android.agent.core.runtime

import com.ojoclaro.android.agent.core.AgentCoreFeatureFlags

/**
 * Owner process-scope del [OjoClaroRuntimeGraph].
 *
 * **Por qué existe:**
 *  - Necesitamos un graph único en el proceso para que el router instalado en
 *    [com.ojoclaro.android.accessibility.OjoClaroAccessibilityService] sea
 *    consistente entre invocaciones (el servicio sobrevive a la Activity).
 *  - Necesitamos un mecanismo testeable que evite Activity leaks: el owner
 *    NO guarda Context ni Activity. Recibe sólo `flags` y un installer
 *    inyectable.
 *  - Necesitamos `installOnce` idempotente: el caller (MainActivity.onCreate)
 *    puede llamarlo en cada onCreate sin reinstanciar.
 *
 * **Diseño:**
 *  - Process-scope vía `companion object` mutable + lock. El primer
 *    `installOnce` crea el graph y lo instala; los siguientes son no-op y
 *    devuelven el graph existente.
 *  - `release()` desinstala y limpia. Solo lo usan tests o un shutdown
 *    explícito de la app.
 *  - Default safe: si `flags()` cambia entre llamadas, el graph mantiene su
 *    proveedor de flags (que es una función), así que respeta valores en
 *    runtime. NO recrea el graph para reflejar flag changes — los flags
 *    granulares ya gating dentro de cada pieza.
 *
 * **Seguridad:**
 *  - No expone Context al graph (el graph internamente usa el provider real
 *    que sí toca el AccessibilityService — pero el provider es companion-based
 *    y no atrapa Activity).
 *  - Si `OjoClaroAccessibilityService` no está activo, el router queda
 *    registrado pero no se invoca — el servicio solo llama al router cuando
 *    está conectado y recibe eventos.
 */
class RuntimeGraphOwner internal constructor(
    private val graphFactory: (() -> AgentCoreFeatureFlags) -> OjoClaroRuntimeGraph
) {

    private val lock = Any()

    @Volatile
    private var current: OjoClaroRuntimeGraph? = null

    /**
     * Devuelve el graph instalado si existe. Útil para el caller (Activity,
     * Composable) que quiere consultar `dispatchController` sin gatillar
     * `installOnce`.
     */
    fun currentGraph(): OjoClaroRuntimeGraph? = current

    /**
     * Crea el graph (si no existe) y lo instala. Idempotente: llamadas
     * subsiguientes devuelven la misma instancia.
     *
     * Solo registra el router en el AccessibilityService — el flag interno
     * decide si efectivamente colecta. Esto significa que registrar es siempre
     * seguro aunque el flag esté OFF: el router consultará el flag en cada
     * evento y se irá temprano si no debe colectar.
     */
    fun installOnce(flags: () -> AgentCoreFeatureFlags): OjoClaroRuntimeGraph {
        val existing = current
        if (existing != null) return existing
        return synchronized(lock) {
            val again = current
            if (again != null) return@synchronized again
            val graph = graphFactory(flags)
            graph.install()
            current = graph
            graph
        }
    }

    /**
     * Desinstala el graph actual (si hay) y lo descarta. Próximo
     * `installOnce` creará uno nuevo. Idempotente.
     */
    fun release() {
        val graph = synchronized(lock) {
            val g = current
            current = null
            g
        }
        graph?.tearDown()
    }

    companion object {
        /**
         * Singleton process-scope con el graph factory por defecto (usa el
         * AccessibilityService real). Para tests instanciar [RuntimeGraphOwner]
         * directamente con un factory que use `createForTesting`.
         */
        val INSTANCE: RuntimeGraphOwner = RuntimeGraphOwner(
            graphFactory = { flags -> OjoClaroRuntimeGraph.create(flags = flags) }
        )

        /**
         * Resolver de flags por defecto. Hoy devuelve DISABLED para mantener
         * comportamiento de producción intacto. Cuando se decida activar 4C
         * en producción, basta cambiar este resolver (o pasar otro a
         * `installOnce`).
         */
        fun productionDefaultFlags(): AgentCoreFeatureFlags = AgentCoreFeatureFlags.DISABLED

        /**
         * Conjunto seguro de flags para smoke test en builds debug:
         *  - `typedConfirmationEnabled = true` (bridge tipado activo)
         *  - `accessibilityRuntimeContextEnabled = true` (snapshots reales)
         *  - `screenChangeAwarenessEnabled = true` (5E avisa cambios)
         *
         * No habilita `llmFallbackEnabled` ni `genericAppExecutionEnabled`:
         * el smoke test no debe encender capas que ejecuten acciones reales
         * sobre apps de terceros ni que envíen contenido al cloud. El
         * AccessibilityService sigue siendo read-only por contrato.
         *
         * El caller decide cuándo usar este resolver. `MainActivity` lo
         * elige sólo cuando `BuildConfig.DEBUG` es true; release queda
         * intacto en producción.
         */
        fun debugSmokeTestFlags(): AgentCoreFeatureFlags = AgentCoreFeatureFlags(
            typedConfirmationEnabled = true,
            accessibilityRuntimeContextEnabled = true,
            screenChangeAwarenessEnabled = true
        )
    }
}
