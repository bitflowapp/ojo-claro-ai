package com.ojoclaro.android.notifications

import java.text.Normalizer
import java.util.Locale

/**
 * WA-5 read-path — clasificador PURO de las preguntas del usuario que se
 * responden LEYENDO el [WhatsAppNotificationStore] (no abre WhatsApp, no abre
 * chats, no envía, no responde).
 *
 * Cubre:
 *   "¿tengo mensajes nuevos de WhatsApp?", "¿quién me escribió?",
 *   "leeme las (últimas) notificaciones de WhatsApp", "qué llegó de WhatsApp",
 *   "leeme mensajes nuevos", "hay mensajes nuevos".
 *
 * Robusto a tildes/mayúsculas/voseo. NO roba frases de otras rutas:
 *  - excluye verbos de acción/compose (abrí, mandale, escribile, enviá, ...) con
 *    límite de palabra, así "quién me escribió" (consulta) entra pero
 *    "escribile a X" (compose) NO;
 *  - "leeme los mensajes" / "qué mensajes me llegaron" (lectura de la pantalla
 *    VISIBLE) no traen marca de "nuevo/notificación" → no matchean acá.
 */
object WhatsAppNotificationQueryPhrases {

    // Verbos de accion/compose: si aparecen, NO es una consulta de notificaciones
    // (que la tome el router de enviar/responder/abrir). Limite de palabra para
    // no excluir "me escribio" por culpa de "escribi".
    private val ACTION_EXCLUDE = Regex(
        "\\b(?:abri|abrir|abrime|abrila|abrilo|abre|" +
            "manda|mandala|mandalo|mandale|mandales|mandar|mandarle|mandame|" +
            "envia|enviar|enviale|enviame|enviarle|" +
            "escribi|escribir|escribile|escribirle|escribime|" +
            "decile|decirle|avisale|avisar|avisame|contale|contarle|" +
            "responde|respondele|responder|contesta|contestale|contestar|" +
            "llama|llamar|llamale|llamala|" +
            "borra|borrar|elimina|eliminar|" +
            "paga|pagar|pagale|transferir|" +
            "audio|grabar|foto|fotos|imagen|ubicacion)\\b"
    )

    // Marcas inequivocas de "novedad / quien me escribio".
    private val STRONG_MARKERS = listOf(
        "mensajes nuevos", "mensaje nuevo", "nuevos mensajes", "nuevo mensaje",
        "mensajes recientes",
        "quien me escribio", "quien escribio", "quien me hablo", "quien me mando",
        "me escribio",
        "que llego de whatsapp", "que me llego de whatsapp",
        "que llego por whatsapp", "que llego al whatsapp"
    )

    private val WA_ALIASES = setOf(
        "whatsapp", "whats app", "wasap", "guasap", "watsap", "whasap", "wsp", "wpp"
    )

    private val READ_VERBS = listOf(
        "lee", "leeme", "leelo", "leela", "mostra", "mostrame",
        "deci", "decime", "contame", "que hay"
    )

    // Anxiety hardening: variantes coloquiales "leeme wasap/guasap" (lectura
    // genérica de lo nuevo de WhatsApp). Igualdad EXACTA: no roba "leé los
    // chats"/"leé los mensajes" (lectura de pantalla VISIBLE, otra ruta).
    private val BARE_WA_READ = setOf(
        "leeme wasap", "leeme guasap", "leeme el wasap", "leeme el guasap",
        "leeme whatsapp", "leeme el whatsapp", "leeme wasa", "leeme guasa",
        "que hay en wasap", "que hay en guasap", "que hay en whatsapp"
    )

    // Consultas exactas "qué llegó / qué decía". EXACTAS para no robar
    // "qué llegó a casa" (llegada) ni "qué decía el cartel" (OCR/visión).
    private val EXACT_QUERY_MARKERS = setOf(
        "que llego", "que me llego", "que llego nuevo",
        "que decia", "que decian", "que me decia", "que decia el ultimo"
    )

    fun isNotificationQuery(rawText: String): Boolean {
        val n = norm(rawText)
        if (n.isBlank()) return false
        if (ACTION_EXCLUDE.containsMatchIn(n)) return false
        if (STRONG_MARKERS.any { n.contains(it) }) return true
        if (n in EXACT_QUERY_MARKERS) return true
        if (n in BARE_WA_READ) return true
        // "notificacion(es)" es ambiguo solo: lo aceptamos si ademas menciona
        // WhatsApp o trae un verbo de lectura (evita "activa las notificaciones").
        if (n.contains("notificacion") &&
            (WA_ALIASES.any { n.contains(it) } || READ_VERBS.any { n.contains(it) })
        ) {
            return true
        }
        return false
    }

    private fun norm(text: String): String {
        val lower = text.lowercase(Locale("es", "AR"))
        val noAccents = Normalizer.normalize(lower, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return noAccents
            .replace(Regex("[¿?¡!.,;:]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
