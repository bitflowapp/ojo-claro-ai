package com.ojoclaro.android.notifications

/**
 * WA-5 read-path — constructor PURO de la respuesta hablada a una consulta de
 * notificaciones de WhatsApp, a partir de un snapshot del
 * [WhatsAppNotificationStore].
 *
 * Privacidad:
 *  - [Response.spokenText] SÍ puede incluir remitente y preview: viene de la
 *    notificación real y el usuario lo pidió (lectura en voz alta).
 *  - [Response.logSummary] NUNCA incluye remitente ni preview: solo metadatos
 *    redactados (count, package, group, longitudes, timestamp) para logs.
 *  - No red, no LLM, no disco: solo transforma la lista que recibe.
 *
 * Defensa en profundidad: aunque el store ya es WhatsApp-only, el responder
 * vuelve a filtrar por paquete WhatsApp (no confía en el caller).
 */
object WhatsAppNotificationQueryResponder {

    enum class Outcome { EMPTY, CONTENT_HIDDEN, SINGLE, MULTIPLE }

    data class Response(
        val outcome: Outcome,
        val spokenText: String,
        /** Resumen SIN PII para logs/reportes. */
        val logSummary: String
    )

    fun respond(notifications: List<WhatsAppNotification>): Response {
        val wa = notifications.filter { WhatsAppNotificationFilter.isWhatsApp(it.packageName) }
        if (wa.isEmpty()) {
            return Response(
                outcome = Outcome.EMPTY,
                spokenText = "Por ahora no tengo mensajes nuevos de WhatsApp registrados.",
                logSummary = "count=0"
            )
        }

        // El store mantiene orden cronologico: el ultimo es el mas reciente.
        val latest = wa.last()
        val count = wa.size
        val sender = latest.sender.trim()
        val preview = latest.messagePreview.trim()
        val summary = "count=$count latestPkg=${latest.packageName} latestGroup=${latest.isGroup} " +
            "latestSenderLen=${sender.length} latestPreviewLen=${preview.length} " +
            "latestTs=${latest.postedAtMillis}"

        return when {
            // Una sola y sin contenido legible: respuesta segura.
            count == 1 && preview.isEmpty() -> Response(
                outcome = Outcome.CONTENT_HIDDEN,
                spokenText = "Tenés un mensaje nuevo de WhatsApp, pero Android ocultó el contenido.",
                logSummary = summary
            )
            count == 1 -> Response(
                outcome = Outcome.SINGLE,
                spokenText = buildSingle(sender, preview),
                logSummary = summary
            )
            else -> Response(
                outcome = Outcome.MULTIPLE,
                spokenText = buildMultiple(count, sender),
                logSummary = summary
            )
        }
    }

    private fun buildSingle(sender: String, preview: String): String {
        val who = if (sender.isNotEmpty()) " de $sender" else ""
        // preview no vacio en esta rama (CONTENT_HIDDEN ya se atendio arriba).
        return "Tenés un mensaje nuevo de WhatsApp$who: $preview."
    }

    private fun buildMultiple(count: Int, sender: String): String {
        val who = if (sender.isNotEmpty()) " El último es de $sender." else ""
        return "Tenés $count mensajes recientes de WhatsApp.$who"
    }
}
