package com.ojoclaro.android.agent.core.screen

import com.ojoclaro.android.agent.core.AgentCoreFeatureFlags
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Paquete 5E — Coordinador application-scope que une el engine puro con un
 * `SharedFlow<ScreenChangeAnnouncement>` que la capa de presentación consume.
 *
 * **Diseño:**
 *  - Mantiene el snapshot anterior internamente. El caller (típicamente la
 *    repository vía un listener) invoca [onSnapshot] cada vez que se publica
 *    un snapshot nuevo.
 *  - Si el flag `screenChangeAwarenessEnabled` está OFF, [onSnapshot] solo
 *    actualiza el snapshot anterior y NO emite anuncios — comportamiento
 *    legacy intacto.
 *  - Si el engine devuelve un `ScreenChangeAnnouncement.shouldAnnounce = true`
 *    y `safeForSpeech = true`, lo emite por [announcements] y marca el anuncio
 *    en la memoria del engine.
 *  - Sin Android UI APIs (no Context, no TTS). Solo coroutines flow.
 *
 * **Por qué no observa el StateFlow directamente:**
 *  - Mantener la observación sincrónica en `onSnapshot` permite testear sin
 *    spawnear coroutines.
 *  - El graph cablea un listener simple en `ScreenContextRepository`, lo que
 *    da control determinista sobre el ciclo de vida.
 */
class ScreenChangeAwarenessCoordinator(
    private val engine: ScreenChangeAwarenessEngine = ScreenChangeAwarenessEngine(),
    private val flags: () -> AgentCoreFeatureFlags = { AgentCoreFeatureFlags.DISABLED },
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    private val _announcements: MutableSharedFlow<ScreenChangeAnnouncement> = MutableSharedFlow(
        replay = 0,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /**
     * Flujo de anuncios que pasaron el gate del engine y el flag.
     *
     * Consumidores típicos:
     *  - HomeViewModel (opcional): lo observa en `viewModelScope` y reenvía al
     *    `_speechEvents` con `force = true` para HIGH/CRITICAL.
     *  - Tests: collect a un List para asertar.
     */
    val announcements: SharedFlow<ScreenChangeAnnouncement> = _announcements.asSharedFlow()

    @Volatile
    private var previous: StructuredScreenSnapshot? = null

    /**
     * Punto de entrada. Idempotente: con flag OFF solo actualiza el snapshot
     * interno; nunca emite si el engine retorna NONE; nunca emite si
     * `safeForSpeech = false`.
     *
     * Devuelve el `ScreenChangeAnnouncement` evaluado (puede ser NONE) para
     * facilitar logging/observabilidad sin necesidad de subscribirse al flow.
     */
    fun onSnapshot(current: StructuredScreenSnapshot?): ScreenChangeAnnouncement {
        if (!flags().screenChangeAwarenessEnabled) {
            previous = current
            return ScreenChangeAnnouncement.NONE
        }
        val nowMillis = clock()
        val announcement = engine.evaluate(previous, current, nowMillis)
        previous = current
        if (announcement.shouldAnnounce && announcement.safeForSpeech) {
            engine.rememberAnnounced(announcement, nowMillis)
            _announcements.tryEmit(announcement)
        }
        return announcement
    }

    /**
     * Limpia el estado: snapshot anterior + memoria de cooldown del engine.
     * Llamar desde el graph al hacer `tearDown` para evitar exponer estado
     * stale después de desactivar el modo asistido.
     */
    fun reset() {
        previous = null
        engine.reset()
    }
}
