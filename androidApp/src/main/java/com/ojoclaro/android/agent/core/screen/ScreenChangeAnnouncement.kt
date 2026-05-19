package com.ojoclaro.android.agent.core.screen

/**
 * Paquete 5E — Resultado del [ScreenChangeAwarenessEngine].
 *
 * Pensado para ser pasado tal cual al pipeline de voz semántica:
 *  - El caller decide entre emitir o suprimir basado en [shouldAnnounce] +
 *    [safeForSpeech].
 *  - El cooldown ya se aplica internamente en el engine; este value-class no
 *    vuelve a evaluar reglas — solo describe.
 *  - [spokenText] está acotado y nunca contiene datos sensibles literales del
 *    snapshot. Las advertencias de seguridad nombran la categoría, no el
 *    contenido.
 */
data class ScreenChangeAnnouncement(
    val event: ScreenChangeEvent,
    val importance: ScreenChangeImportance,
    val semanticKey: String,
    val spokenText: String,
    val reasonKey: String?,
    val shouldAnnounce: Boolean,
    val safeForSpeech: Boolean,
    val cooldownMs: Long = DEFAULT_COOLDOWN_MS
) {
    companion object {
        /** Cooldown por defecto entre dos anuncios con la misma `semanticKey`. */
        const val DEFAULT_COOLDOWN_MS: Long = 15_000L

        /** Cooldown más largo para anuncios LOW/NORMAL repetitivos. */
        const val LOW_NOISE_COOLDOWN_MS: Long = 30_000L

        /** Centinela "no hay nada para anunciar". */
        val NONE: ScreenChangeAnnouncement = ScreenChangeAnnouncement(
            event = ScreenChangeEvent.NONE,
            importance = ScreenChangeImportance.LOW,
            semanticKey = "screen.change.none",
            spokenText = "",
            reasonKey = null,
            shouldAnnounce = false,
            safeForSpeech = true,
            cooldownMs = 0L
        )
    }
}
