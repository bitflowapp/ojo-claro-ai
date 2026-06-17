package com.ojoclaro.android.agent.runtime.whatsapp

import com.ojoclaro.android.agent.core.screen.ScreenElement
import com.ojoclaro.android.agent.core.screen.ScreenElementRole
import com.ojoclaro.android.agent.core.screen.ScreenSnapshot
import com.ojoclaro.android.agent.runtime.util.TextMatchNormalizer
import com.ojoclaro.android.privacy.PrivacyGuard

/**
 * Pure detector for visible chat names on WhatsApp's main chat list.
 *
 * Hard rules:
 *  - emits names only, never message previews;
 *  - rejects timestamps, numeric-only strings, phone-like strings, UI labels,
 *    password nodes, and financial/sensitive content;
 *  - accepts TEXT/LIST_ITEM names and short clickable row labels;
 *  - scans and emits bounded lists.
 */
class WhatsAppChatListDetector {

    fun extractChats(snapshot: ScreenSnapshot?): List<WhatsAppVisibleChat> {
        if (snapshot == null || !snapshot.hasElements) return emptyList()

        val seen = LinkedHashSet<String>()
        val result = mutableListOf<WhatsAppVisibleChat>()

        var scanned = 0
        for (element in snapshot.elements) {
            if (result.size >= MAX_VISIBLE_CHATS) break
            if (scanned >= MAX_ELEMENTS_TO_SCAN) break
            scanned += 1

            val candidate = chatNameCandidate(element) ?: continue
            if (!seen.add(candidate.normalized)) continue

            result.add(WhatsAppVisibleChat(displayName = candidate.displayName))
        }

        return result
    }

    private fun chatNameCandidate(element: ScreenElement): Candidate? {
        if (element.isPassword) return null
        val label = element.label.trim()
        if (label.isBlank()) return null
        if (label.length > WhatsAppVisibleChat.MAX_NAME_LENGTH) return null
        if (label.length < MIN_NAME_LENGTH) return null

        val normalized = normalize(label)
        if (normalized.isBlank()) return null

        when (element.role) {
            ScreenElementRole.TEXT,
            ScreenElementRole.LIST_ITEM -> Unit
            ScreenElementRole.BUTTON -> {
                if (!element.isInteractive) return null
                if (looksLikeUiKeyword(normalized)) return null
            }
            else -> return null
        }

        if (looksLikeUiKeyword(normalized)) return null
        if (looksLikeTime(label, normalized)) return null
        if (looksLikePreview(label)) return null
        if (looksLikeNumeric(label)) return null
        if (PrivacyGuard.containsSensitiveFinancialData(label)) return null

        return Candidate(displayName = label, normalized = normalized)
    }

    private fun looksLikeUiKeyword(normalized: String): Boolean {
        if (normalized.isBlank()) return false
        return normalized in UI_LABELS_EXACT ||
            UI_LABELS_CONTAINS.any { normalized.contains(it) }
    }

    private fun looksLikeTime(label: String, normalized: String): Boolean {
        if (TIME_PATTERN.containsMatchIn(label)) return true
        if (normalized in TIME_WORDS) return true
        if (DAY_PATTERN.matches(normalized)) return true
        return false
    }

    private fun looksLikePreview(label: String): Boolean {
        if (label.contains("...")) return true
        if (label.contains("\u2026")) return true
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
        if (NUMERIC_ONLY.matches(trimmed)) return true
        if (PHONE_LIKE.matches(trimmed)) return true
        return false
    }

    private fun normalize(text: String): String = TextMatchNormalizer.normalize(text)

    private data class Candidate(
        val displayName: String,
        val normalized: String
    )

    companion object {
        const val MAX_VISIBLE_CHATS: Int = 5
        const val MIN_NAME_LENGTH: Int = 2
        const val MAX_ELEMENTS_TO_SCAN: Int = 48

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
