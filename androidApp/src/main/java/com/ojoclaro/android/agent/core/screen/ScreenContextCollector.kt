package com.ojoclaro.android.agent.core.screen

import com.ojoclaro.android.agent.core.AgentCoreFeatureFlags

/**
 * Pieza de orquestación que une [ScreenContextProvider] + [StructuredScreenSnapshotBuilder] +
 * [ScreenContextRepository].
 *
 * **Qué hace:**
 *  - Cuando se invoca [collect], pregunta al provider, lo pasa por el
 *    builder y publica el structured snapshot en el repository.
 *  - Aplica throttle interno: si pasa menos de [throttleMillis] entre llamadas,
 *    devuelve el último snapshot sin volver a leer el árbol de accesibilidad.
 *  - Si el flag está OFF, no hace nada.
 *
 * **Qué NO hace:**
 *  - No corre coroutines propias. El caller decide cuándo invocar `collect`.
 *  - No registra eventos del AccessibilityService — para eso el caller debe
 *    usar `onAccessibilityEvent` (o un timer suyo).
 *  - No habla, no ejecuta acciones, no toca clicks.
 *
 * **Wiring sugerido (paquete 4 o posterior):**
 *  ```
 *  val collector = ScreenContextCollector(
 *      provider = AndroidAccessibilityScreenContextProvider(),
 *      repository = ScreenContextRepository(flags = { config.featureFlags }),
 *      flags = { config.featureFlags }
 *  )
 *  // En el AccessibilityService:
 *  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
 *      collector.collect()  // throttled internamente
 *  }
 *  ```
 *
 * Thread-safety: el throttle usa un lock simple. `collect` puede llamarse
 * desde múltiples threads sin corromper estado.
 */
class ScreenContextCollector(
    private val provider: ScreenContextProvider,
    private val repository: ScreenContextRepository,
    private val builder: StructuredScreenSnapshotBuilder = StructuredScreenSnapshotBuilder(),
    private val flags: () -> AgentCoreFeatureFlags = { AgentCoreFeatureFlags.DISABLED },
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val throttleMillis: Long = DEFAULT_THROTTLE_MILLIS
) {

    private val lock = Any()

    @Volatile
    private var lastCollectMillis: Long = 0L

    @Volatile
    private var lastResult: CollectOutcome = CollectOutcome.Skipped(reason = "never_collected")

    /**
     * Lee del provider, construye el structured snapshot y lo publica.
     *
     * Reglas:
     *  - Si el flag está OFF → [CollectOutcome.Skipped] (`flag_disabled`).
     *  - Si pasó menos de [throttleMillis] desde la última llamada exitosa →
     *    devuelve el outcome anterior sin re-leer.
     *  - Si el provider devuelve null → publica null en el repository y
     *    devuelve [CollectOutcome.NoSnapshot].
     *  - Si todo OK → [CollectOutcome.Published] con el snapshot.
     */
    fun collect(): CollectOutcome {
        val activeFlags = flags()
        if (!activeFlags.accessibilityRuntimeContextEnabled) {
            repository.clear()
            val out = CollectOutcome.Skipped(reason = "flag_disabled")
            synchronized(lock) { lastResult = out }
            return out
        }

        val now = clock()
        synchronized(lock) {
            val sinceLast = now - lastCollectMillis
            if (sinceLast in 0 until throttleMillis && lastResult is CollectOutcome.Published) {
                return CollectOutcome.Throttled(
                    sinceLastMillis = sinceLast,
                    cached = (lastResult as CollectOutcome.Published).snapshot
                )
            }
        }

        val rawSnapshot = runCatching { provider.current() }.getOrNull()
        val structured = builder.build(rawSnapshot, capturedAtMillis = now)

        val outcome = if (rawSnapshot == null && structured.isEmpty) {
            repository.publish(null)
            CollectOutcome.NoSnapshot
        } else {
            val published = repository.publish(structured)
            if (published) {
                CollectOutcome.Published(structured)
            } else {
                CollectOutcome.Skipped(reason = "publish_rejected_by_flag")
            }
        }

        synchronized(lock) {
            lastCollectMillis = now
            lastResult = outcome
        }
        return outcome
    }

    /**
     * Limpia el snapshot publicado y resetea el throttle. Para usar cuando
     * el AccessibilityService se desconecta o el usuario apaga el modo
     * asistido.
     */
    fun reset() {
        synchronized(lock) {
            lastCollectMillis = 0L
            lastResult = CollectOutcome.Skipped(reason = "reset")
        }
        repository.clear()
    }

    companion object {
        /**
         * 400 ms entre re-lecturas. Los eventos de accesibilidad pueden venir
         * en ráfaga (scroll, animaciones) y leer el árbol cada vez es caro.
         */
        const val DEFAULT_THROTTLE_MILLIS: Long = 400L
    }
}

sealed class CollectOutcome {
    data class Published(val snapshot: StructuredScreenSnapshot) : CollectOutcome()
    data class Throttled(val sinceLastMillis: Long, val cached: StructuredScreenSnapshot) :
        CollectOutcome()
    data object NoSnapshot : CollectOutcome()
    data class Skipped(val reason: String) : CollectOutcome()
}
