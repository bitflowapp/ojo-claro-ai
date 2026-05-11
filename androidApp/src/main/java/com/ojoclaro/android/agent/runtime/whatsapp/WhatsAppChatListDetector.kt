package com.ojoclaro.android.agent.runtime.whatsapp

import com.ojoclaro.android.agent.core.screen.ScreenElement
import com.ojoclaro.android.agent.core.screen.ScreenElementRole
import com.ojoclaro.android.agent.core.screen.ScreenSnapshot
import com.ojoclaro.android.privacy.PrivacyGuard
import java.text.Normalizer

/**
 * Detector puro de "¿qué chats visibles aparecen en la pantalla principal
 * de WhatsApp?"
 *
 * Reglas hard:
 *  - NO emite previews de mensajes (rechaza labels con patrón "Nombre: texto").
 *  - NO emite horas/fechas ("14:30", "ayer", "lun", etc.).
 *  - NO emite números sueltos ni teléfonos.
 *  - NO emite labels de UI conocidos (Cámara, Enviar, Buscar, Chats, etc.).
 *  - NO emite contenido financiero/sensible (vía PrivacyGuard).
 *  - NO emite valores de campos password (defensa redundante con el mapper).
 *  - Acepta solo elementos role TEXT o LIST_ITEM (no BUTTON/EDIT_TEXT/IMAGE/etc.)
 *    O elementos cuya clave parezca un nombre humano corto y que sean clickeables
 *    (típica fila de chat con contentDescription = nombre).
 *  - Trunca duros y dedupe.
 *  - Cap final de [MAX_VISIBLE_CHATS] = 5 chats.
 */
class WhatsAppChatListDetector {

    fun extractChats(snapshot: ScreenSnapshot?): List<WhatsAppVisibleChat> {
        if (snapshot == null || !snapshot.hasElements) return emptyList()

        val seen = LinkedHashSet<String>()
        val result = mutableListOf<WhatsAppVisibleChat>()

        for (element in snapshot.elements) {
            if (result.size >= MAX_VISIBLE_CHATS) break
            if (!shouldKeep(element)) continue

            val name = element.label.trim()
            val dedupeKey = normalize(name)
            if (dedupeKey.isBlank()) continue
            if (!seen.add(dedupeKey)) continue

            result.add(WhatsAppVisibleChat(displayName = name))
        }

        return result
    }

    private fun shouldKeep(element: ScreenElement): Boolean {
        if (element.isPassword) return false
        if (element.label.isBlank()) return false

        // Roles válidos para nombres de chat. En WhatsApp el TextView del nombre
        // dentro de la fila aparece como role TEXT. LIST_ITEM se acepta también.
        // Los contenedores clickeables a veces el mapper los clasifica BUTTON;
        // los aceptamos solo si el label es muy corto (≤ MAX_NAME_LENGTH) — eso
        // sugiere que es el contentDescription del row con el nombre, no botón UI.
        when (element.role) {
            ScreenElementRole.TEXT,
            ScreenElementRole.LIST_ITEM -> Unit
            ScreenElementRole.BUTTON -> {
                if (!element.isInteractive) return false
                // Botón cuyo label coincide con UI conocida → no es chat.
                if (looksLikeUiKeyword(element.label)) return false
            }
            else -> return false
        }

        val label = element.label
        if (label.length > WhatsAppVisibleChat.MAX_NAME_LENGTH) return false
        if (looksLikeUiKeyword(label)) return false
        if (looksLikeTime(label)) return false
        if (looksLikePreview(label)) return false
        if (looksLikeNumeric(label)) return false
        if (PrivacyGuard.containsSensitiveFinancialData(label)) return false
        if (label.trim().length < MIN_NAME_LENGTH) return false
        return true
    }

    private fun looksLikeUiKeyword(label: String): Boolean {
        val normalized = normalize(label)
        if (normalized.isBlank()) return false
        return normalized in UI_LABELS_EXACT ||
            UI_LABELS_CONTAINS.any { normalized.contains(it) }
    }

    private fun looksLikeTime(label: String): Boolean {
        val normalized = normalize(label)
        if (TIME_PATTERN.containsMatchIn(label)) return true
        if (normalized in TIME_WORDS) return true
        if (DAY_PATTERN.matches(normalized)) return true
        return false
    }

    private fun looksLikePreview(label: String): Boolean {
        if (label.contains("…")) return true
        if (label.endsWith("...")) return true
        // Patrón "Nombre: contenido": si lo que sigue al ": " es > 3 chars,
        // es preview de mensaje. Un nombre estilo "Dr: Marco" raro pero ese
        // caso queda fuera por simplicidad.
        val colonIndex = label.indexOf(": ")
        if (colonIndex >= 0) {
            val tail = label.substring(colonIndex + 2)
            if (tail.length > 3) return true
        }
        return false
    }

    private fun looksLikeNumeric(label: String): Boolean {
        val trimmed = label.trim()
        if (trimmed.isEmpty()) return false
        // Todo dígitos / dígitos con espacios o guiones / teléfonos.
        if (NUMERIC_ONLY.matches(trimmed)) return true
        if (PHONE_LIKE.matches(trimmed)) return true
        return false
    }

    private fun normalize(text: String): String {
        val lower = text.lowercase()
        return Normalizer.normalize(lower, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[¿?¡!.,;:]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    companion object {
        const val MAX_VISIBLE_CHATS: Int = 5
        const val MIN_NAME_LENGTH: Int = 2

        /** Labels exactos de UI de WhatsApp en es/en — nunca deben ser tratados como nombres de chat. */
        private val UI_LABELS_EXACT: Set<String> = setOf(
            "chats",
            "estado",
            "estados",
            "comunidades",
            "llamadas",
            "configuracion",
            "ajustes",
            "buscar",
            "search",
            "camara",
            "camera",
            "enviar",
            "send",
            "adjuntar",
            "attach",
            "microfono",
            "microphone",
            "audio",
            "volver",
            "back",
            "atras",
            "navegar hacia arriba",
            "navigate up",
            "mensaje",
            "message",
            "whatsapp",
            "nuevo chat",
            "new chat",
            "calls",
            "status",
            "communities",
            "settings"
        )

        /** Fragmentos de label que disparan rechazo aunque vengan dentro de un texto más largo. */
        private val UI_LABELS_CONTAINS: Set<String> = setOf(
            "marcar como",
            "mark as",
            "archivar",
            "archive",
            "silenciar",
            "mute",
            "no leido",
            "unread"
        )

        private val TIME_WORDS: Set<String> = setOf(
            "ayer",
            "hoy",
            "yesterday",
            "today",
            "now",
            "ahora"
        )

        private val TIME_PATTERN: Regex = Regex("\\b\\d{1,2}:\\d{2}\\b")

        private val DAY_PATTERN: Regex = Regex(
            "^(lun|mar|mie|jue|vie|sab|dom|mon|tue|wed|thu|fri|sat|sun)\\.?$"
        )

        private val NUMERIC_ONLY: Regex = Regex("^[\\d\\s.,]+$")

        private val PHONE_LIKE: Regex = Regex("^[+]?[\\d\\s\\-()]{6,}$")
    }
}
