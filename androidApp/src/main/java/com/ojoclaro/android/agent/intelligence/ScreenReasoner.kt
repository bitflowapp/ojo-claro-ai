package com.ojoclaro.android.agent.intelligence

import java.text.Normalizer

/**
 * V2.2 — Convierte un snapshot de accesibilidad en un MODELO de pantalla útil
 * para que Estela "entienda" qué está mirando antes de actuar.
 *
 * Componente PURO (sin Android): recibe nodos ya leídos (un adapter los llena
 * desde OjoClaroAccessibilityService.readVisibleNodeSummaries()). [ReasonerNode]
 * es isomorfo a AccessibilityNodeSummary.
 *
 * Filosofía: mirar y EXPLICAR con un nivel de confianza. Si no está seguro, lo
 * dice (confidence LOW/UNKNOWN). Nunca decide acciones acá: solo describe.
 *
 * NOTA V2.2: código real SIN COMPILAR/SIN TESTEAR (disco C: crítico; ver
 * docs/V22_SCREEN_REASONER_REPORT.md).
 */
data class ReasonerNode(
    val text: String? = null,
    val contentDescription: String? = null,
    val hint: String? = null,
    val className: String? = null,
    val isClickable: Boolean = false,
    val isEditable: Boolean = false,
    val isPassword: Boolean = false,
    val isHeading: Boolean = false,
) {
    val label: String get() = (text ?: contentDescription ?: hint ?: "")
}

data class RawScreen(
    val packageName: String?,
    val nodes: List<ReasonerNode>,
    val screenLocked: Boolean = false,
)

enum class ActiveApp { WHATSAPP, WHATSAPP_BUSINESS, INSTAGRAM, OTHER }
enum class ScreenType { CHAT_LIST, CONVERSATION, SEARCH, COMPOSER, UNKNOWN }
enum class RiskyControl { CALL, VIDEO_CALL, AUDIO, PAYMENT, CARD, ATTACH }
enum class Confidence { HIGH, MEDIUM, LOW, UNKNOWN }

data class ScreenModel(
    val activeApp: ActiveApp,
    val screenType: ScreenType,
    val currentChatTitle: String?,
    val visibleContacts: List<ContactCandidate>,
    val visibleMessagesCount: Int,
    val hasMessageInput: Boolean,
    val hasSendButton: Boolean,
    val riskyControls: List<RiskyControl>,
    val screenLocked: Boolean,
    val confidence: Confidence,
    val explanation: String,
)

object ScreenReasoner {

    private val MESSAGE_FIELD_HINTS = setOf(
        "mensaje", "message", "escribe un mensaje", "type a message",
        "mensaje de texto", "escribi un mensaje", "enviar mensaje"
    )
    private val SEND_LABELS = setOf("enviar", "send")
    private val SEARCH_HINTS = setOf("buscar", "search", "busca")
    private val CALL_LABELS = setOf("llamar", "call", "llamada")
    private val VIDEO_LABELS = setOf("videollamada", "video call", "video llamada", "llamada de video")
    private val AUDIO_LABELS = setOf("mensaje de voz", "audio", "grabar audio", "voice message")
    private val PAY_LABELS = setOf("pagar", "pago", "pay", "enviar dinero", "payment")
    private val CARD_LABELS = setOf("tarjeta", "card")
    private val ATTACH_LABELS = setOf("adjuntar", "attach", "adjuntos")

    /**
     * [activeAppOverride] permite que el caller inyecte una app activa resuelta
     * con señales más estables que el solo packageName (ver ActiveAppResolver):
     * el packageName puede venir vacío/del overlay justo tras un cambio de app.
     * Si es null, se cae a la deducción por packageName (comportamiento previo).
     */
    fun reason(raw: RawScreen, activeAppOverride: ActiveApp? = null): ScreenModel {
        val app = activeAppOverride ?: activeApp(raw.packageName)
        if (raw.screenLocked) {
            return ScreenModel(app, ScreenType.UNKNOWN, null, emptyList(), 0, false, false,
                emptyList(), true, Confidence.LOW, "La pantalla está bloqueada; no puedo leer el contenido.")
        }

        val labels = raw.nodes.map { normalize(it.label) }.filter { it.isNotBlank() }
        val hasMessageInput = raw.nodes.any { node ->
            node.isEditable && (
                node.className?.contains("EditText", true) == true ||
                    MESSAGE_FIELD_HINTS.any { normalize(node.hint ?: "").contains(it) || normalize(node.contentDescription ?: "").contains(it) }
                )
        }
        val hasSendButton = raw.nodes.any { node ->
            (node.isClickable || node.className?.contains("Button", true) == true) &&
                SEND_LABELS.any { normalize(node.label) == it || normalize(node.label).contains(it) }
        }
        val hasSearchField = raw.nodes.any { node ->
            node.isEditable && SEARCH_HINTS.any { normalize(node.hint ?: "").contains(it) || normalize(node.label).contains(it) }
        }

        val risky = mutableListOf<RiskyControl>()
        fun anyLabel(set: Set<String>) = raw.nodes.any { n -> set.any { normalize(n.label).contains(it) } }
        if (anyLabel(VIDEO_LABELS)) risky.add(RiskyControl.VIDEO_CALL)
        if (anyLabel(CALL_LABELS)) risky.add(RiskyControl.CALL)
        if (anyLabel(AUDIO_LABELS)) risky.add(RiskyControl.AUDIO)
        if (anyLabel(PAY_LABELS)) risky.add(RiskyControl.PAYMENT)
        if (anyLabel(CARD_LABELS)) risky.add(RiskyControl.CARD)
        if (anyLabel(ATTACH_LABELS)) risky.add(RiskyControl.ATTACH)

        // Candidatos = filas clickeables con texto que no son botones de acción.
        val visibleContacts = raw.nodes.filterIndexed { i, _ -> i >= 0 }
            .filter { it.isClickable && !it.isEditable && it.text?.isNotBlank() == true }
            .filter { node -> SEND_LABELS.none { normalize(node.label) == it } &&
                CALL_LABELS.none { normalize(node.label) == it } }
            .mapIndexed { i, node ->
                ContactCandidate(
                    primaryText = node.text!!.trim(),
                    secondaryText = node.contentDescription?.trim(),
                    index = i,
                    app = when (app) {
                        ActiveApp.INSTAGRAM -> TargetApp.INSTAGRAM
                        ActiveApp.WHATSAPP, ActiveApp.WHATSAPP_BUSINESS -> TargetApp.WHATSAPP
                        else -> TargetApp.UNKNOWN
                    },
                    kind = CandidateKind.CHAT,
                )
            }

        val screenType = when {
            hasMessageInput -> ScreenType.CONVERSATION
            hasSearchField -> ScreenType.SEARCH
            visibleContacts.size >= 2 -> ScreenType.CHAT_LIST
            else -> ScreenType.UNKNOWN
        }

        val currentChatTitle = if (screenType == ScreenType.CONVERSATION) {
            // Heurística: el primer encabezado o el primer texto prominente que
            // NO es un mensaje ni un botón. Best-effort (confianza menor).
            raw.nodes.firstOrNull { it.isHeading && it.text?.isNotBlank() == true }?.text?.trim()
                ?: raw.nodes.firstOrNull { it.text?.isNotBlank() == true && !it.isEditable && !it.isClickable }?.text?.trim()
        } else null

        val messagesCount = if (screenType == ScreenType.CONVERSATION) {
            raw.nodes.count { it.text?.isNotBlank() == true && !it.isClickable && !it.isEditable && !it.isHeading }
        } else 0

        val confidence = when {
            app == ActiveApp.OTHER -> Confidence.LOW
            screenType == ScreenType.UNKNOWN -> Confidence.LOW
            (screenType == ScreenType.CONVERSATION && hasSendButton) ||
                (screenType == ScreenType.CHAT_LIST && visibleContacts.size >= 3) -> Confidence.HIGH
            else -> Confidence.MEDIUM
        }

        val explanation = buildExplanation(app, screenType, currentChatTitle, visibleContacts.size, risky)
        return ScreenModel(app, screenType, currentChatTitle, visibleContacts, messagesCount,
            hasMessageInput, hasSendButton, risky.distinct(), false, confidence, explanation)
    }

    private fun activeApp(pkg: String?): ActiveApp {
        val p = pkg.orEmpty().lowercase()
        return when {
            p == "com.whatsapp.w4b" -> ActiveApp.WHATSAPP_BUSINESS
            "whatsapp" in p -> ActiveApp.WHATSAPP
            "instagram" in p -> ActiveApp.INSTAGRAM
            else -> ActiveApp.OTHER
        }
    }

    private fun buildExplanation(
        app: ActiveApp, type: ScreenType, title: String?, contacts: Int, risky: List<RiskyControl>
    ): String {
        val appName = when (app) {
            ActiveApp.WHATSAPP -> "WhatsApp"
            ActiveApp.WHATSAPP_BUSINESS -> "WhatsApp Business"
            ActiveApp.INSTAGRAM -> "Instagram"
            ActiveApp.OTHER -> "otra app"
        }
        val where = when (type) {
            ScreenType.CONVERSATION -> if (title != null) "en el chat con $title" else "en un chat abierto"
            ScreenType.CHAT_LIST -> "en la lista de chats ($contacts visibles)"
            ScreenType.SEARCH -> "en el buscador"
            ScreenType.COMPOSER -> "escribiendo un mensaje"
            ScreenType.UNKNOWN -> "en una pantalla que no reconozco del todo"
        }
        val riskNote = if (risky.isNotEmpty()) " Hay controles sensibles a la vista." else ""
        return "Estás en $appName, $where.$riskNote"
    }

    private fun normalize(text: String): String {
        val lower = text.lowercase()
        val stripped = Normalizer.normalize(lower, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
        return stripped.replace(Regex("[¿?¡!.,;:]"), " ").replace(Regex("\\s+"), " ").trim()
    }
}
