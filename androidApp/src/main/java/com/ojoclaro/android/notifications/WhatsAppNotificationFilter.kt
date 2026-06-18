package com.ojoclaro.android.notifications

import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppScreenDetector

/**
 * WA-3 — compuerta "SOLO WhatsApp".
 *
 * El SO entrega TODAS las notificaciones a un [android.service.notification.NotificationListenerService];
 * no existe un filtro por app a nivel manifest. El alcance "solo WhatsApp" se
 * garantiza acá, en código: cualquier paquete que no sea WhatsApp se descarta
 * antes de mirar el contenido.
 *
 * Reutiliza la lista canónica [WhatsAppScreenDetector.KNOWN_PACKAGES]
 * (com.whatsapp / com.whatsapp.w4b) para no duplicar la fuente de verdad.
 */
object WhatsAppNotificationFilter {

    /** Paquetes de WhatsApp que el listener tiene permitido leer. */
    val WHATSAPP_PACKAGES: Set<String> = WhatsAppScreenDetector.KNOWN_PACKAGES

    /** true solo si el paquete es WhatsApp (igualdad exacta, sin heurísticas). */
    fun isWhatsApp(packageName: String?): Boolean =
        packageName != null && WHATSAPP_PACKAGES.contains(packageName)
}
