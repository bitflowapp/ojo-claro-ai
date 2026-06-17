package com.ojoclaro.android.speech

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper

/**
 * V1.7 — earcons sobrios de Estela.
 *
 * Reglas:
 *  - tonos cortos (<300 ms), nunca en loop, nunca pisan al TTS (suenan
 *    ANTES de hablar, no durante);
 *  - respetan el volumen/modo del sistema (stream de notificación);
 *  - fallback silencioso: cualquier error de audio se ignora;
 *  - `enabled=false` apaga todo (setting interno).
 *
 * Preparado para recursos futuros premium (res/raw/estela_listen.wav,
 * estela_confirm.wav, estela_error.wav, estela_pending.wav): si algún día
 * existen, este objeto es el único punto a tocar.
 */
object EstelaEarcons {

    @Volatile
    var enabled: Boolean = true

    /** Escucha iniciada: tono suave corto. */
    fun listenStart() = play(ToneGenerator.TONE_PROP_BEEP, 110)

    /** Comando entendido / acción completada: ack sutil. */
    fun confirm() = play(ToneGenerator.TONE_PROP_ACK, 90)

    /** No pude: tono suave, no alarmante. */
    fun error() = play(ToneGenerator.TONE_PROP_NACK, 140)

    /** Acción sensible pendiente (p. ej. envío esperando confirmación). */
    fun pendingSensitive() = play(ToneGenerator.TONE_PROP_PROMPT, 160)

    private fun play(tone: Int, durationMs: Int) {
        if (!enabled) return
        runCatching {
            val generator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, VOLUME)
            generator.startTone(tone, durationMs)
            Handler(Looper.getMainLooper()).postDelayed(
                { runCatching { generator.release() } },
                durationMs + 90L
            )
        }
    }

    /** 0-100: sobrio, nunca invasivo. */
    private const val VOLUME = 55
}
