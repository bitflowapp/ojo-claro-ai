package com.ojoclaro.android.notifications

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.ojoclaro.android.BuildConfig

/**
 * WA-3 — listener de notificaciones EXCLUSIVO de WhatsApp.
 *
 * Contrato de seguridad/privacidad:
 *  - SOLO procesa paquetes de WhatsApp ([WhatsAppNotificationFilter]). Todo lo
 *    demás se descarta en la primera línea, sin leer extras ni loguear nada.
 *  - SOLO LEE. Nunca responde, nunca cancela, nunca dispara acciones. No usa
 *    RemoteInput ni `Notification.Action`. No existe una sola llamada a
 *    `cancelNotification`, `cancelAllNotifications` ni `snoozeNotification`.
 *  - NO red, NO disco. El contenido vive en memoria acotada
 *    ([WhatsAppNotificationStore]) y nada más.
 *  - Logs SOLO en builds debug y SIN contenido del mensaje
 *    ([WhatsAppNotification.redactedForLog]).
 *
 * El acceso lo concede el usuario en Ajustes ("Acceso a notificaciones"); no es
 * un `uses-permission`. El SO exige `BIND_NOTIFICATION_LISTENER_SERVICE` para
 * vincularse, y entrega TODAS las notificaciones: el alcance "solo WhatsApp" es
 * responsabilidad de este código, no del sistema.
 */
class OjoClaroWhatsAppNotificationListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn ?: return
        // Corre en el hilo binder del SISTEMA: una excepción acá (extras
        // malformados, BadParcelableException al desempaquetar un CharSequence
        // de terceros, etc.) no debe escalar al proceso del sistema. Defensa en
        // profundidad: aislamos toda la lectura y solo logueamos el TIPO de
        // error (sin PII), en debug.
        runCatching { handleNotificationPosted(notification) }
            .onFailure { error -> log("WA_NOTIF_ERROR err=${error.javaClass.simpleName}") }
    }

    private fun handleNotificationPosted(notification: StatusBarNotification) {
        // Compuerta WhatsApp-only ANTES de mirar cualquier extra: si no es
        // WhatsApp, ni lo tocamos.
        if (!WhatsAppNotificationFilter.isWhatsApp(notification.packageName)) return

        val raw = readRaw(notification) ?: return
        val parsed = WhatsAppNotificationExtractor.extract(raw)
        if (parsed == null) {
            log("WA_NOTIF_IGNORED pkg=${notification.packageName}")
            return
        }
        WhatsAppNotificationStore.record(parsed)
        log("WA_NOTIF_CAPTURED ${parsed.redactedForLog()}")
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // No-op intencional. WA-3 solo lee al llegar la notificación; no
        // reacciona a las que el usuario o WhatsApp descartan.
    }

    private fun readRaw(sbn: StatusBarNotification): RawWhatsAppNotification? {
        val n = sbn.notification ?: return null
        val extras = n.extras ?: return null
        return RawWhatsAppNotification(
            packageName = sbn.packageName,
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            category = n.category,
            isOngoing = (n.flags and Notification.FLAG_ONGOING_EVENT) != 0,
            isGroupSummary = (n.flags and Notification.FLAG_GROUP_SUMMARY) != 0,
            postedAtMillis = sbn.postTime
        )
    }

    private fun log(message: String) {
        if (BuildConfig.DEBUG) Log.i(TAG, "route=$ROUTE_TAG $message")
    }

    private companion object {
        const val TAG = "EstelaWhatsAppNotif"
        const val ROUTE_TAG = "WA3_NOTIF_LISTENER"
    }
}
