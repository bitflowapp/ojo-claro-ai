package com.ojoclaro.android.notifications

/**
 * WA-3 — almacén en memoria, acotado y thread-safe, de las últimas
 * notificaciones de WhatsApp leídas por el listener.
 *
 * Esta es la ÚNICA superficie de integración hacia adelante (la fase
 * consumidora, p. ej. WA-5, leería de acá). WA-3 solo PRODUCE; no hay ningún
 * consumidor cableado todavía, por diseño.
 *
 * Garantías:
 *  - solo memoria: nunca toca disco ni red;
 *  - acotado a [MAX_ENTRIES] (ring buffer): no crece sin límite;
 *  - thread-safe: el SO puede entregar notificaciones desde su propio hilo.
 */
object WhatsAppNotificationStore {

    /** Cota dura de elementos retenidos; los más viejos se descartan. */
    const val MAX_ENTRIES: Int = 50

    private val lock = Any()
    private val entries = ArrayDeque<WhatsAppNotification>()

    /** Agrega una notificación y mantiene el tamaño dentro de [MAX_ENTRIES]. */
    fun record(notification: WhatsAppNotification) {
        synchronized(lock) {
            entries.addLast(notification)
            while (entries.size > MAX_ENTRIES) {
                entries.removeFirst()
            }
        }
    }

    /** Copia de las más recientes (orden cronológico), como mucho [limit]. */
    fun recent(limit: Int = MAX_ENTRIES): List<WhatsAppNotification> =
        synchronized(lock) {
            if (limit <= 0) emptyList() else entries.toList().takeLast(limit)
        }

    /** La última notificación capturada, o null si no hay ninguna. */
    fun latest(): WhatsAppNotification? =
        synchronized(lock) { entries.lastOrNull() }

    /** Cantidad de notificaciones retenidas en este momento. */
    fun size(): Int = synchronized(lock) { entries.size }

    /** Vacía el almacén (útil para tests y para un futuro "borrar todo"). */
    fun clear() {
        synchronized(lock) { entries.clear() }
    }
}
