package com.ojoclaro.android.agent.runtime.whatsapp

import java.util.concurrent.atomic.AtomicLong

/**
 * Contadores AUDITABLES de los toques peligrosos de WhatsApp.
 *
 * Por qué existe: el contrato de seguridad ("sendTap=0", "callTap=0", etc.) se
 * verificaba sólo por inspección de logs. Esto da un contador en runtime,
 * thread-safe, que un smoke o un test puede leer directamente.
 *
 * Sólo cuenta. No expone PII (ni números ni contenido). Thread-safe porque los
 * toques pueden venir del hilo de accesibilidad.
 */
object WhatsAppActionAudit {

    private val sendTaps = AtomicLong(0)
    private val callTaps = AtomicLong(0)
    private val videoCallTaps = AtomicLong(0)
    private val audioSendTaps = AtomicLong(0)
    private val audioRecordStarts = AtomicLong(0)
    /** Acciones peligrosas bloqueadas (flag off / sin confirmación / sin destino). */
    private val blocked = AtomicLong(0)

    fun recordSendTap() { sendTaps.incrementAndGet() }
    fun recordCallTap() { callTaps.incrementAndGet() }
    fun recordVideoCallTap() { videoCallTaps.incrementAndGet() }
    fun recordAudioSendTap() { audioSendTaps.incrementAndGet() }
    fun recordAudioRecordStart() { audioRecordStarts.incrementAndGet() }
    fun recordBlocked() { blocked.incrementAndGet() }

    fun sendTapCount(): Long = sendTaps.get()
    fun callTapCount(): Long = callTaps.get()
    fun videoCallTapCount(): Long = videoCallTaps.get()
    fun audioSendTapCount(): Long = audioSendTaps.get()
    fun audioRecordStartCount(): Long = audioRecordStarts.get()
    fun blockedCount(): Long = blocked.get()

    /** Resumen SIN PII, listo para loguear. */
    fun redactedSummary(): String =
        "sendTap=${sendTaps.get()} callTap=${callTaps.get()} " +
            "videoCallTap=${videoCallTaps.get()} audioSendTap=${audioSendTaps.get()} " +
            "audioRecord=${audioRecordStarts.get()} blocked=${blocked.get()}"

    /** Reinicia todo (tests / inicio de un smoke). */
    fun reset() {
        sendTaps.set(0); callTaps.set(0); videoCallTaps.set(0)
        audioSendTaps.set(0); audioRecordStarts.set(0); blocked.set(0)
    }
}
