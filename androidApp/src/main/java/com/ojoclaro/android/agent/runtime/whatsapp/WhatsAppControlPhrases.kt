package com.ojoclaro.android.agent.runtime.whatsapp

import java.text.Normalizer
import java.util.Locale

/**
 * Frases de CONTROL de WhatsApp que deben rutearse local y temprano (antes del
 * agente/LLM/fallback): abrir WhatsApp y el diagnóstico de WhatsApp.
 *
 * Solo clasifica intención; no toca nada, no expone contenido. Robusto a
 * tildes/mayúsculas. La lectura/navegación/respuesta tienen sus propios
 * matchers (WhatsAppChatListPhrases, WhatsAppMessageReadPhrases, etc.).
 */
object WhatsAppControlPhrases {

    private val WA_ALIASES = setOf(
        "whatsapp", "whats app", "whatsap", "whatssap", "whats",
        "wasap", "guasap", "watsap", "whasap", "wp", "wsp", "wpp", "wasá"
    )

    private val OPEN_VERBS = setOf(
        "abri", "abrir", "abrime", "abrila", "abrilo", "abre",
        "anda a", "andate a", "anda al", "ir a", "llevame a", "llevame al",
        "entrar a", "entra a", "entrar en", "quiero entrar", "vamos a", "vamos al"
    )

    private val DIAGNOSTIC_MARKERS = setOf(
        "diagnostico", "revisar", "revisa", "revisame", "chequear", "chequea",
        "esta listo", "esta lista", "estado de", "anda bien", "funciona"
    )

    /** "abrí WhatsApp", "andá a WhatsApp", "quiero entrar a WhatsApp", ... */
    fun isOpenWhatsAppCommand(rawText: String): Boolean {
        val n = norm(rawText)
        if (!mentionsWhatsApp(n)) return false
        // No confundir con "abrí el chat de ..." (ese flujo es de chats, no de
        // abrir la app): si menciona "chat"/"conversacion", no es abrir-app.
        if (n.contains("chat") || n.contains("conversacion")) return false
        return OPEN_VERBS.any { n.contains(it) }
    }

    /** "diagnóstico WhatsApp", "revisar WhatsApp", "está listo WhatsApp", ... */
    fun isDiagnosticCommand(rawText: String): Boolean {
        val n = norm(rawText)
        if (!mentionsWhatsApp(n)) return false
        return DIAGNOSTIC_MARKERS.any { n.contains(it) }
    }

    private fun mentionsWhatsApp(normalized: String): Boolean =
        WA_ALIASES.any { normalized.contains(it) }

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
