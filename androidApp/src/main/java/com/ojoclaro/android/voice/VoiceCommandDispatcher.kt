package com.ojoclaro.android.voice

import java.text.Normalizer
import java.util.Locale

class VoiceCommandDispatcher(
    private val executeCommand: (String) -> Unit,
    private val stopSpeechNow: () -> Unit,
    private val updatePartialText: (String) -> Unit = {}
) {

    fun onPartialText(text: String) {
        if (isStopCommand(text)) {
            stopSpeechNow()
        } else {
            updatePartialText(text)
        }
    }

    fun onFinalText(text: String) {
        val cleanText = text.trim()
        if (cleanText.isBlank()) return

        if (isStopCommand(cleanText)) {
            stopSpeechNow()
        } else {
            executeCommand(cleanText)
        }
    }

    companion object {
        fun isStopCommand(text: String): Boolean {
            val normalized = normalize(text)
            // "stop now" / barge-in: cortar TTS y volver a IDLE. NO incluye
            // "cancelar" (tiene semántica de pendiente: se maneja aparte para
            // dar feedback "cancelado" y no pisar una confirmación en curso).
            return normalized in setOf(
                "callar", "callate", "silencio", "para", "parar", "pare",
                "basta", "basta ya", "stop", "frena", "frenar", "frenate",
                "detente", "detenete", "detene", "deteni", "detener",
                "detener ahora", "ya basta"
            ) ||
                normalized.contains(" callar ") ||
                normalized.startsWith("callar ") ||
                normalized.endsWith(" callar") ||
                normalized.startsWith("para ") ||
                normalized.startsWith("parar ")
        }

        fun isHelpCommand(text: String): Boolean =
            normalize(text) in setOf(
                "que puedo decir",
                "que puedo decirte",
                "que te puedo decir",
                "que puedo hacer",
                "que podes hacer",
                "que puedes hacer",
                "que sabes hacer",
                "ayuda",
                "ayudame",
                "opciones",
                "comandos",
                "menu",
                "que comandos hay",
                "que comandos tenes",
                "lista de comandos",
                "como me podes ayudar",
                "como me puedes ayudar",
                "hola estela",
                "hola que podes hacer",
                "hola estela que podes hacer",
                "explicame como usar esto",
                "explicame como usar la app"
            )

        /**
         * Hardening Alexa-like: "repetí lo último" en todas sus variantes. NO
         * incluye la repetición de una indicación de ruta activa (esa la maneja
         * OutdoorForegroundService cuando hay guía en curso). Robusto a acentos.
         */
        fun isRepeatCommand(text: String): Boolean =
            normalize(text) in setOf(
                "repetir", "repeti", "repetilo", "repetila", "repetimelo",
                "repetime", "repetimela", "repetir eso", "repeti eso",
                "decilo de nuevo", "deci de nuevo", "decilo otra vez",
                "de nuevo", "otra vez", "una vez mas",
                "no escuche", "no te escuche", "no escuche bien", "no entendi",
                "que dijiste", "que dijiste recien", "como dijiste",
                // Anxiety hardening: "no entendí, repetí" y variantes juntas.
                "no entendi repeti", "no entendi repetilo", "no te entendi",
                "perdon no entendi", "no entendi nada repeti", "no te escuche repeti"
            )

        /**
         * "cancelar" a secas (sin acción pendiente). Se evalúa DESPUÉS de los
         * manejadores de pendientes: con un envío/llamada esperando respuesta,
         * la frase la consume el pendiente y nunca llega acá.
         */
        fun isBareCancelCommand(text: String): Boolean =
            normalize(text) in setOf(
                "cancelar", "cancela", "cancelalo", "cancelala",
                "anula", "anular", "anulalo", "dejalo", "olvidalo",
                // Anxiety hardening: "me equivoqué" como cancelación suave global.
                "me equivoque", "me equivoco", "me arrepenti",
                // Fluency: "no, me equivoqué" entero.
                "no me equivoque", "no me equivoco",
                // QA fuzz (cancelación): formas claras que faltaban. Membership EXACTA
                // (no roba frases largas). Dirección segura: sin pending no hace nada
                // peligroso; con pending crítico, ese ya lo consumió antes.
                "cancela todo", "cancelar todo", "no cancela", "deja", "olvidate",
                "no hagas nada", "no toques nada", "no mandes nada"
            )

        fun isReadTextCommand(text: String): Boolean {
            val normalized = normalize(text)
            return normalized in setOf("leer texto", "lee texto", "leeme texto", "leer un texto") ||
                normalized.startsWith("leer texto ") ||
                normalized.startsWith("leeme texto ")
        }

        private fun normalize(text: String): String {
            val parserReadyText = VoicePhraseNormalizer.normalizeForParser(text)
            val withoutAccents = Normalizer.normalize(
                parserReadyText.lowercase(Locale("es", "AR")),
                Normalizer.Form.NFD
            ).replace(Regex("\\p{Mn}+"), "")

            return withoutAccents
                .replace(Regex("[^a-z0-9\\s]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
                .trim('.', '!', '?', '¿', '¡')
        }
    }
}
