package com.ojoclaro.android.agent.core.screen

import com.ojoclaro.android.agent.core.AgentCoreFeatureFlags
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holder thread-safe del último [StructuredScreenSnapshot] publicado.
 *
 * Diseño:
 *  - Es la "antena" que cualquier capa (HomeViewModel, runtime bridge) puede
 *    consultar sin tocar el provider directamente.
 *  - Publica un `StateFlow<StructuredScreenSnapshot?>` para reactividad en
 *    Compose/Coroutines.
 *  - El control de "se actualiza o no" pasa por el flag
 *    [AgentCoreFeatureFlags.accessibilityRuntimeContextEnabled]: si está OFF,
 *    [publish] descarta el snapshot y deja el state como null.
 *  - No persiste. No serializa. Si el AccessibilityService se desconecta,
 *    el caller debe llamar [clear] para evitar exponer un snapshot stale.
 *
 * Concurrencia:
 *  - `MutableStateFlow` es thread-safe para writes/reads.
 *  - El gating por flag se evalúa en el momento de [publish], no
 *    al inicializar — esto permite cambiar flags en runtime sin recrear el
 *    repository.
 */
class ScreenContextRepository(
    private val flags: () -> AgentCoreFeatureFlags = { AgentCoreFeatureFlags.DISABLED }
) {

    private val internalState: MutableStateFlow<StructuredScreenSnapshot?> =
        MutableStateFlow(null)

    val state: StateFlow<StructuredScreenSnapshot?> = internalState.asStateFlow()

    /**
     * Publica un nuevo snapshot. Si el flag está OFF, el call es no-op y
     * además limpia cualquier snapshot anterior (defensivo: si el flag se
     * apaga en runtime, no queremos que un snapshot stale siga visible).
     *
     * Devuelve true si el snapshot fue publicado, false si fue ignorado.
     */
    fun publish(snapshot: StructuredScreenSnapshot?): Boolean {
        if (!flags().accessibilityRuntimeContextEnabled) {
            // Limpiar es defensivo: si justo se apagó el flag, no dejamos basura.
            internalState.value = null
            return false
        }
        internalState.value = snapshot
        return true
    }

    fun current(): StructuredScreenSnapshot? = internalState.value

    /**
     * Limpia el snapshot. Pensado para llamar desde el AccessibilityService
     * cuando se desconecta (`onUnbind`/`onDestroy`) o cuando la pantalla
     * cambia de paquete a una de privacidad/login.
     */
    fun clear() {
        internalState.value = null
    }
}
