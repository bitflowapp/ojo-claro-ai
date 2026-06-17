package com.ojoclaro.android.notifications

/**
 * WA-3 — captura inmutable de UNA notificación de WhatsApp leída por el listener.
 *
 * Modelo de dominio PURO (sin tipos de Android) → testeable y seguro de pasar.
 *
 * Privacidad: el listener SOLO LEE; nunca envía, responde ni cancela. Esta
 * clase guarda lo mínimo para que una fase futura (consumidor) pueda decir
 * "tenés un mensaje de X". El contenido vive en memoria acotada
 * ([WhatsAppNotificationStore]); nunca se persiste ni se manda a la red.
 *
 * [redactedForLog] JAMÁS expone el remitente ni el contenido del mensaje: solo
 * longitudes y flags, para diagnósticos seguros en builds debug.
 */
data class WhatsAppNotification(
    val packageName: String,
    val sender: String,
    val messagePreview: String,
    val isGroup: Boolean,
    val postedAtMillis: Long
) {

    /** Línea de log SIN PII: ni remitente ni texto, solo metadatos. */
    fun redactedForLog(): String =
        "pkg=$packageName group=$isGroup senderLen=${sender.length} previewLen=${messagePreview.length}"
}
