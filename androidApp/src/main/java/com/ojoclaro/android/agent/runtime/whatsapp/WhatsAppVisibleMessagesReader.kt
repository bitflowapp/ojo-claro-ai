package com.ojoclaro.android.agent.runtime.whatsapp

import com.ojoclaro.android.agent.core.screen.ScreenContextProvider
import com.ojoclaro.android.agent.core.screen.ScreenElement
import com.ojoclaro.android.agent.core.screen.ScreenElementRole
import com.ojoclaro.android.agent.runtime.util.TextMatchNormalizer
import com.ojoclaro.android.privacy.PrivacyGuard

/**
 * Respuesta verbal del lector de mensajes visibles de un chat de WhatsApp.
 */
sealed class WhatsAppMessagesResponse {
    object NotAMessageCommand : WhatsAppMessagesResponse()
    data class NeedsAccessibilityService(val spokenText: String) : WhatsAppMessagesResponse()
    data class NotInWhatsApp(val spokenText: String) : WhatsAppMessagesResponse()
    data class NotInChat(val spokenText: String) : WhatsAppMessagesResponse()
    data class StaleSnapshot(val spokenText: String) : WhatsAppMessagesResponse()
    data class NoMessages(val spokenText: String) : WhatsAppMessagesResponse()
    data class Read(
        val messages: List<String>,
        val spokenText: String
    ) : WhatsAppMessagesResponse()
}

/**
 * Lee mensajes visibles dentro de un chat de WhatsApp, SOLO cuando el usuario lo
 * pide explícitamente ([WhatsAppMessageReadPhrases]).
 *
 * Reglas hard:
 *  - Solo actúa si la detección confirma que estamos DENTRO de un chat.
 *  - Lee solo texto visible de nodos TEXT no interactivos. Descarta botones,
 *    labels de UI ("escribe un mensaje", "enviar", "cámara"...), horas, números
 *    sueltos y contenido financiero/sensible.
 *  - No inventa: si no hay texto de mensajes accesible, lo dice con honestidad.
 *  - No persiste, no manda a red, no usa LLM. El contenido vive solo en memoria
 *    el tiempo de armar la respuesta hablada.
 *  - Rechaza snapshots viejos (defensa ante un provider que cachee).
 */
class WhatsAppVisibleMessagesReader(
    private val provider: ScreenContextProvider,
    private val detector: WhatsAppScreenDetector = WhatsAppScreenDetector(),
    private val isAccessibilityReady: () -> Boolean = { true },
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val maxSnapshotAgeMillis: Long = DEFAULT_MAX_SNAPSHOT_AGE_MILLIS
) {

    fun handle(rawText: String): WhatsAppMessagesResponse {
        val mode = WhatsAppMessageReadPhrases.classify(rawText)
            ?: return WhatsAppMessagesResponse.NotAMessageCommand

        if (!isAccessibilityReady()) {
            return WhatsAppMessagesResponse.NeedsAccessibilityService(NEEDS_ACCESSIBILITY_TEXT)
        }

        val snapshot = runCatching { provider.current() }.getOrNull()
            ?: return WhatsAppMessagesResponse.NotInWhatsApp(NOT_IN_WHATSAPP_TEXT)

        if (clock() - snapshot.capturedAtMillis > maxSnapshotAgeMillis) {
            return WhatsAppMessagesResponse.StaleSnapshot(STALE_TEXT)
        }

        val state = detector.detect(snapshot)
        if (state.isUnknown || !state.isOpen) {
            return WhatsAppMessagesResponse.NotInWhatsApp(NOT_IN_WHATSAPP_TEXT)
        }
        if (!state.isInChat) {
            return WhatsAppMessagesResponse.NotInChat(NOT_IN_CHAT_TEXT)
        }

        val messages = extractMessages(snapshot.elements)
        if (messages.isEmpty()) {
            return WhatsAppMessagesResponse.NoMessages(NO_MESSAGES_TEXT)
        }

        val selected = when (mode) {
            WhatsAppMessageReadMode.LAST -> messages.takeLast(1)
            WhatsAppMessageReadMode.ALL -> messages.takeLast(MAX_MESSAGES_SPOKEN)
        }
        return WhatsAppMessagesResponse.Read(
            messages = selected,
            spokenText = buildSpokenText(selected, mode)
        )
    }

    /**
     * Extrae texto de mensajes desde los elementos visibles. Mantiene el orden
     * del snapshot (los más recientes suelen quedar al final en la lista del
     * chat), descarta UI/horas/números y deduplica.
     */
    private fun extractMessages(elements: List<ScreenElement>): List<String> {
        val seen = LinkedHashSet<String>()
        val out = mutableListOf<String>()
        for (element in elements) {
            if (out.size >= MAX_MESSAGES_COLLECTED) break
            val candidate = messageCandidate(element) ?: continue
            val normalized = TextMatchNormalizer.normalize(candidate)
            if (normalized.isBlank()) continue
            if (!seen.add(normalized)) continue
            out.add(candidate)
        }
        return out
    }

    private fun messageCandidate(element: ScreenElement): String? {
        if (element.isPassword) return null
        if (element.isInteractive) return null
        if (element.role != ScreenElementRole.TEXT && element.role != ScreenElementRole.UNKNOWN) {
            return null
        }
        val label = element.label.trim()
        if (label.length < MIN_MESSAGE_LENGTH) return null

        val normalized = TextMatchNormalizer.normalize(label)
        if (normalized.isBlank()) return null
        if (normalized in UI_LABELS_EXACT) return null
        if (UI_LABELS_CONTAINS.any { normalized.contains(it) }) return null
        if (looksLikeTimeOnly(label, normalized)) return null
        if (NUMERIC_ONLY.matches(label.trim())) return null
        if (PrivacyGuard.containsSensitiveFinancialData(label)) return null

        return label.take(MAX_SINGLE_MESSAGE_CHARS)
    }

    private fun looksLikeTimeOnly(label: String, normalized: String): Boolean {
        if (TIME_PATTERN.matches(label.trim())) return true
        if (normalized in TIME_WORDS) return true
        return false
    }

    private fun buildSpokenText(messages: List<String>, mode: WhatsAppMessageReadMode): String {
        if (mode == WhatsAppMessageReadMode.LAST || messages.size == 1) {
            return "El último mensaje visible dice: ${messages.last()}."
        }
        val joined = messages.joinToString(separator = ". ")
        return "Los últimos mensajes visibles dicen: $joined."
    }

    companion object {
        const val MAX_MESSAGES_SPOKEN: Int = 5
        const val MAX_MESSAGES_COLLECTED: Int = 12
        const val MIN_MESSAGE_LENGTH: Int = 2
        const val MAX_SINGLE_MESSAGE_CHARS: Int = 160
        const val DEFAULT_MAX_SNAPSHOT_AGE_MILLIS: Long = 5_000L

        const val NEEDS_ACCESSIBILITY_TEXT: String =
            "Para leer los mensajes necesito el servicio de Accesibilidad activo. " +
                "Activá Estela en Ajustes de Accesibilidad."
        const val NOT_IN_WHATSAPP_TEXT: String =
            "No estás en WhatsApp. Abrílo y entrá a un chat para que pueda leer los mensajes."
        const val NOT_IN_CHAT_TEXT: String =
            "Estás en WhatsApp pero no veo un chat abierto. Abrí un chat y volvé a pedirme."
        const val STALE_TEXT: String =
            "No tengo una lectura fresca de la pantalla. Tocá la pantalla o esperá un momento y volvé a pedirme."
        const val NO_MESSAGES_TEXT: String =
            "No encontré mensajes que pueda leer en esta pantalla. Puede que WhatsApp no los exponga."

        private val UI_LABELS_EXACT: Set<String> = setOf(
            "escribe un mensaje",
            "mensaje",
            "message",
            "type a message",
            "enviar",
            "send",
            "camara",
            "camera",
            "adjuntar",
            "attach",
            "microfono",
            "microphone",
            "audio",
            "volver",
            "back",
            "atras",
            "buscar",
            "search",
            "escribiendo",
            "typing",
            "en linea",
            "online",
            "visto",
            "entregado",
            "leido"
        )

        private val UI_LABELS_CONTAINS: Set<String> = setOf(
            "manten para grabar",
            "hold to record",
            "toca para",
            "tap to",
            "ultima vez",
            "last seen"
        )

        private val TIME_WORDS: Set<String> = setOf(
            "ayer",
            "hoy",
            "yesterday",
            "today",
            "ahora",
            "now"
        )

        private val TIME_PATTERN: Regex = Regex("^\\d{1,2}:\\d{2}(\\s?[ap]\\.?\\s?m\\.?)?$")
        private val NUMERIC_ONLY: Regex = Regex("^[\\d\\s.,]+$")
    }
}
