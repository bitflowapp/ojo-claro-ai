package com.ojoclaro.android.notifications

import java.util.Locale

/**
 * Datos primitivos leídos de una notificación, SIN tipos de Android, para que
 * [WhatsAppNotificationExtractor] sea 100% testeable en JVM.
 *
 * El servicio rellena esto desde `StatusBarNotification` / `Notification.extras`.
 */
data class RawWhatsAppNotification(
    val packageName: String?,
    val title: String?,
    val text: String?,
    /** `Notification.category` (p. ej. "call", "msg"); null si no la trae. */
    val category: String?,
    /** FLAG_ONGOING_EVENT: servicio en primer plano / llamada en curso. */
    val isOngoing: Boolean,
    /** FLAG_GROUP_SUMMARY: resumen "N mensajes de M chats", no un mensaje. */
    val isGroupSummary: Boolean,
    val postedAtMillis: Long
)

/**
 * WA-3 — convierte una [RawWhatsAppNotification] en un [WhatsAppNotification]
 * de dominio, o `null` si NO es un mensaje real de WhatsApp que valga capturar.
 *
 * Puro y sin estado: misma entrada → misma salida. Toda la lógica de filtrado
 * (qué se ignora, truncado, detección de grupo) vive acá para poder probarla
 * sin un dispositivo.
 *
 * Se ignoran a propósito:
 *  - paquetes que no son WhatsApp (defensa en profundidad sobre el gate del servicio);
 *  - notificaciones "ongoing" (el servicio en foreground de WhatsApp, llamadas en curso);
 *  - resúmenes de grupo de notificaciones (FLAG_GROUP_SUMMARY);
 *  - llamadas (category == "call"): fuera de alcance de WA-3;
 *  - notificaciones de sistema de WhatsApp ("Buscando mensajes nuevos…", backup);
 *  - cualquier cosa sin remitente o sin texto.
 */
object WhatsAppNotificationExtractor {

    /** Tope defensivo de longitud por campo; evita guardar payloads enormes. */
    const val MAX_FIELD_CHARS: Int = 300

    /**
     * Títulos que NO corresponden a un mensaje de una persona, sino a estados
     * propios de WhatsApp. Comparación exacta tras normalizar a minúsculas.
     */
    private val NON_MESSAGE_TITLES: Set<String> = setOf(
        "whatsapp",
        "whatsapp business",
        "buscando mensajes nuevos",
        "buscando mensajes nuevos…",
        "comprobando si hay mensajes nuevos",
        "checking for new messages",
        "backup in progress",
        "copia de seguridad en curso"
    )

    /**
     * Sub-cadenas que delatan una notificación de sistema/agregada aunque el
     * título sea genérico (p. ej. "5 mensajes de 3 chats").
     */
    private val SYSTEM_TEXT_MARKERS: List<String> = listOf(
        "mensajes de ", // "5 mensajes de 3 chats"
        "messages from "
    )

    /** Detecta el patrón de grupo "Remitente: mensaje" en el texto. */
    private val GROUP_TEXT_PREFIX = Regex("^[^:\\n]{1,30}: .+", RegexOption.DOT_MATCHES_ALL)

    fun extract(raw: RawWhatsAppNotification): WhatsAppNotification? {
        val pkg = raw.packageName ?: return null
        if (!WhatsAppNotificationFilter.isWhatsApp(pkg)) return null
        if (raw.isOngoing) return null
        if (raw.isGroupSummary) return null
        if (raw.category.equals("call", ignoreCase = true)) return null

        val sender = raw.title?.trim().orEmpty()
        val preview = raw.text?.trim().orEmpty()
        if (sender.isEmpty() || preview.isEmpty()) return null

        val lowerSender = sender.lowercase(Locale.ROOT)
        if (NON_MESSAGE_TITLES.contains(lowerSender)) return null

        val lowerPreview = preview.lowercase(Locale.ROOT)
        if (SYSTEM_TEXT_MARKERS.any { lowerPreview.contains(it) }) return null

        return WhatsAppNotification(
            packageName = pkg,
            sender = sender.take(MAX_FIELD_CHARS),
            messagePreview = preview.take(MAX_FIELD_CHARS),
            isGroup = GROUP_TEXT_PREFIX.matches(preview),
            postedAtMillis = raw.postedAtMillis
        )
    }
}
