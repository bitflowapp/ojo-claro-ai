package com.ojoclaro.android.agent.runtime.whatsapp

import com.ojoclaro.android.outdoor.removeSpanishAccents
import com.ojoclaro.android.voice.VoicePhraseNormalizer

/**
 * Blind Safety — guardia LOCAL de última línea antes de cualquier salida a
 * LLM/backend (handleFreeConversation -> /conversation, y el fallback del
 * orchestrator). Detecta frases WhatsApp-CRÍTICAS (mutar/enviar/borrar/pagar/
 * foto/llamar/audio) para que NUNCA viajen fuera del dispositivo.
 *
 * Reglas:
 *  - Solo verbos MUTANTES o de ACCIÓN. Los verbos de LECTURA ("leé", "leeme",
 *    "mostrame", "qué dice", "qué chats hay") NO son críticos: deben seguir su
 *    ruta local de lectura. Por eso este set NO incluye leer/mostrar/qué.
 *  - El caller decide el alcance (solo bloquea si la frase nombra WhatsApp o el
 *    contexto activo ES WhatsApp); este objeto solo clasifica el verbo.
 *
 * PURO: sin Android, sin estado, sin IO. Mismo fold() que los parsers hermanos.
 */
object WhatsAppCriticalGuard {

    private val CRITICAL_MARKERS = listOf(
        // responder / contestar
        "respond", "contestale", "contesta",
        // mandar / enviar / escribir a alguien
        "mandar", "manda", "mandale", "envia", "enviar", "enviale",
        "escribile", "escribir a", "escribile a",
        // eliminar (el "borrar" se chequea aparte, anclado a palabra, abajo)
        "elimina", "eliminar",
        // bloquear
        "bloquear", "bloquea", "bloque",
        // reenviar
        "reenvi", "reenviar", "reenvia",
        // reportar / denunciar
        "reporta", "reportar", "denunci",
        // archivar
        "archiva", "archivar",
        // silenciar / mutear
        "silenci", "mutea", "mutear",
        // fijar
        "fijar", "pinea",
        // multimedia / adjuntos / ubicación
        "foto", "imagen", "sticker", "figurita", "adjunta", "adjuntar", "ubicacion",
        // pagos
        "pagar", "pagale", "plata", "transfer",
        // llamadas / audio
        "llamar", "llamale", "videollamada", "nota de voz", "mandale un audio",
        "mandar un audio", "enviar un audio"
    )

    // Pedido de TOCAR un botón PELIGROSO por descripción visual ("tocá el botón
    // verde", "apretá el botón de enviar", "dale al botón de videollamada"). Para
    // un no-vidente el "botón verde" ES enviar/llamar: debe quedar como negativa
    // LOCAL, no viajar al LLM. Exige verbo de toque + "boton" + objetivo peligroso
    // (verde/enviar/mandar/llamar/videollamada/audio) para NO robar botones seguros
    // ("botón de atrás", "botón de inicio") ni preguntas ("qué hace el botón verde").
    private val DANGEROUS_BUTTON = Regex(
        "\\b(?:toca\\w*|apreta\\w*|apriet\\w*|presiona\\w*|oprimi\\w*|dale|cliquea\\w*|" +
            "clicke\\w*|marca\\w*|selecciona\\w*)\\b.{0,18}\\bboton\\b.{0,14}" +
            "\\b(?:verde|enviar|mandar|llamar|llamada|videollamada|audio|voz)\\b" +
            // "dale al botón verde": el normalizer puede comerse "dale" como muletilla
            // (queda "al boton verde", sin verbo) → cubrir la forma pelada
            // "boton verde" / "boton de <acción>". Trade-off aceptado: una pregunta
            // pelada ("qué hace el botón verde") cae a negativa conservadora.
            "|\\bboton\\s+(?:verde\\b|de (?:enviar|mandar|llamar|videollamada|video|audio|voz)\\b)" +
            // Botón de ENVIAR descrito por ÍCONO/COLOR, SIN la palabra "botón": para
            // un no-vidente "tocá el avioncito" / "el avión de papel" / "la flechita
            // de enviar" / "el verde" ES enviar. Anclado a verbo de toque + ARTÍCULO
            // para NO robar preguntas ("qué es el avioncito"), status ("tiene doble
            // marca verde") ni "modo avión" (sin verbo de toque + artículo+ícono).
            "|\\b(?:toca\\w*|apreta\\w*|apriet\\w*|presiona\\w*|oprimi\\w*|cliquea\\w*|" +
            "clicke\\w*|selecciona\\w*)\\b\\s+(?:el|al|la|lo)\\s+" +
            "(?:avioncito|avion de papel|flechita de enviar|flechita|verde)\\b" +
            // "dale al verde": el normalizer se come "dale" (muletilla) y queda
            // "al verde" / "el avioncito" pelado al inicio → cubrir el remanente.
            // Una pregunta nunca arranca con "el/al/lo + ícono" (arranca con qué/cómo).
            "|^(?:el|al|lo)\\s+(?:avioncito|avion de papel|flechita de enviar|verde)\\b"
    )

    // "borrá/borrar/borralo/borrame..." como VERBO de borrado, anclado a límite de
    // palabra para NO flaggear el sustantivo "borrador" (= draft): "prepará
    // borrador" / "leeme el borrador" son SEGUROS, no un borrado peligroso. El verbo
    // real ("borrá el chat") sí matchea por el primer token.
    private val DELETE_VERB = Regex("\\bborra(?:r|lo|la|los|las|le|les|me|en|ron|ndo)?\\b")

    fun isCritical(rawText: String): Boolean {
        val folded = fold(rawText)
        if (folded.isBlank()) return false
        if (DANGEROUS_BUTTON.containsMatchIn(folded)) return true
        if (DELETE_VERB.containsMatchIn(folded)) return true
        return CRITICAL_MARKERS.any { folded.contains(it) }
    }

    private fun fold(rawText: String): String =
        VoicePhraseNormalizer.normalizeForParser(rawText)
            .lowercase()
            .removeSpanishAccents()
            .replace(Regex("[¿?¡!.,;:]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
