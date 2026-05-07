package com.ojoclaro.android.voice

/**
 * Constantes y helpers para el intent que dispara "modo escucha" en Ojo Claro.
 *
 * Quién lo emite:
 *  - Quick Settings tile (OjoClaroQuickTileService).
 *  - Botón flotante de Accesibilidad (OjoClaroAccessibilityService.onAccessibilityButtonClicked).
 *  - Cualquier deep link autorizado, ej. shortcuts del sistema.
 *
 * Quién lo recibe:
 *  - MainActivity, en onCreate y onNewIntent.
 *
 * Reglas duras:
 *  - El intent NUNCA inicia el micrófono por sí solo. Solo le dice a la UI que el
 *    usuario quiere arrancar. La UI levanta el voice loop con la app visible.
 *  - Si falta RECORD_AUDIO, la UI pide permiso con explicación humana.
 *  - El saludo "Ojo Claro listo. Decime qué necesitás." se emite UNA vez por proceso.
 */
object OjoClaroIntents {

    const val ACTION_START_LISTENING = "com.ojoclaro.android.ACTION_START_LISTENING"
    const val ACTION_STOP_SPEAKING = "com.ojoclaro.android.ACTION_STOP_SPEAKING"
    const val EXTRA_START_LISTENING = "start_listening"
    const val EXTRA_STOP_SPEAKING = "stop_speaking"

    /**
     * Variante pura para tests: no depende de Bundle/Intent.
     * Devuelve true si el intent representa una solicitud explícita de "modo escucha".
     */
    fun isListeningRequest(action: String?, startListeningExtra: Boolean): Boolean {
        if (action == ACTION_START_LISTENING) return true
        return startListeningExtra
    }

    fun isStopSpeakingRequest(action: String?, stopSpeakingExtra: Boolean): Boolean {
        if (action == ACTION_STOP_SPEAKING) return true
        return stopSpeakingExtra
    }
}
